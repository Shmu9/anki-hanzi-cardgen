#!/usr/bin/env python3
"""Build a SQLite character/glyph database from Unihan and CJK decomposition data.

The generated database keeps a convenient denormalized `glyphs` row for each
Unicode character/radical/stroke or decomposition-only intermediate shape.
"""

from __future__ import annotations

import argparse
import csv
import datetime as dt
import json
import re
import sqlite3
from pathlib import Path
from typing import Iterable


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_UNIHAN_DIR = ROOT / "data" / "Unihan"
DEFAULT_DECOMP_PATH = ROOT / "data" / "decomposition" / "cjk-decomp.txt"
DEFAULT_HANZI_DB_PATH = ROOT / "data" / "hanzi_db.csv"
DEFAULT_SCHEMA_PATH = ROOT / "sql" / "schema.sql"
DEFAULT_OUTPUT = ROOT / "dict.sqlite3"

DECOMP_RE = re.compile(r"^(?P<head>[^:]+):(?P<type>[^()]*)\((?P<components>.*)\)$")

SUMMARY_FIELD_COLUMNS = {
    "kDefinition": "k_definition",
    "kMandarin": "k_mandarin",
    "kJapanese": "k_japanese",
    "kHanyuPinlu": "k_hanyu_pinlu",
    "kTotalStrokes": "stroke_count",
    "kRSUnicode": "rs_unicode",
    "kRSAdobe_Japan1_6": "rs_adobe_japan1_6",
}


def unicode_token_to_char(token: str) -> str:
    return chr(int(token[2:], 16))


def char_to_unicode_token(char: str) -> str:
    return f"U+{ord(char):04X}"


def token_from_source_token(source_token: str) -> tuple[str, str | None, str | None, int | None, str]:
    """Return canonical token, glyph, unicode token, codepoint, kind."""
    source_token = source_token.strip()

    if source_token.startswith("U+"):
        unicode_token = source_token.upper()
        codepoint = int(unicode_token[2:], 16)
        glyph = chr(codepoint)
        return unicode_token, glyph, unicode_token, codepoint, "unicode"

    if source_token.isdecimal():
        return source_token, None, None, None, "intermediate"

    if len(source_token) == 1:
        unicode_token = char_to_unicode_token(source_token)
        return unicode_token, source_token, unicode_token, ord(source_token), "unicode"

    return source_token, source_token, None, None, "literal"


def ensure_glyph(conn: sqlite3.Connection, source_token: str) -> int:
    token, glyph, unicode_token, codepoint, kind = token_from_source_token(source_token)
    conn.execute(
        """
        INSERT INTO glyphs (token, glyph, unicode, codepoint, kind)
        VALUES (?, ?, ?, ?, ?)
        ON CONFLICT(token) DO UPDATE SET
            glyph = COALESCE(glyphs.glyph, excluded.glyph),
            unicode = COALESCE(glyphs.unicode, excluded.unicode),
            codepoint = COALESCE(glyphs.codepoint, excluded.codepoint)
        """,
        (token, glyph, unicode_token, codepoint, kind),
    )
    row = conn.execute("SELECT id FROM glyphs WHERE token = ?", (token,)).fetchone()
    if row is None:
        raise RuntimeError(f"Unable to create glyph for {source_token!r}")
    return int(row[0])


def parse_unihan_lines(path: Path) -> Iterable[tuple[str, str, str]]:
    with path.open("r", encoding="utf-8") as handle:
        for raw_line in handle:
            line = raw_line.rstrip("\n")
            if not line or line.startswith("#"):
                continue
            try:
                token, field, value = line.split("\t", 2)
            except ValueError:
                continue
            yield token, field, value


def parse_source_metadata(path: Path) -> dict[str, str]:
    metadata: dict[str, str] = {}
    with path.open("r", encoding="utf-8") as handle:
        for line in handle:
            if not line.startswith("#"):
                break
            line = line.strip()
            if line.startswith("# Date:"):
                metadata[f"{path.name}:date"] = line.removeprefix("# Date:").strip()
            elif line.startswith("# Unicode Version"):
                metadata[f"{path.name}:unicode_version"] = line.removeprefix(
                    "# Unicode Version"
                ).strip()
    return metadata


def split_components(raw_components: str) -> list[str]:
    if not raw_components:
        return []
    return [component.strip() for component in raw_components.split(",") if component.strip()]


def optional_int(value: str | None) -> int | None:
    if value is None:
        return None
    value = value.strip()
    if not value:
        return None
    return int(value)


def reset_database(path: Path) -> None:
    for candidate in (path, Path(f"{path}-wal"), Path(f"{path}-shm")):
        if candidate.exists():
            candidate.unlink()


def create_schema(conn: sqlite3.Connection, schema_path: Path) -> None:
    conn.executescript(schema_path.read_text(encoding="utf-8"))


def load_unihan(conn: sqlite3.Connection, unihan_dir: Path) -> None:
    for path in sorted(unihan_dir.glob("Unihan_*.txt")):
        for key, value in parse_source_metadata(path).items():
            conn.execute(
                "INSERT OR REPLACE INTO metadata (key, value) VALUES (?, ?)",
                (key, value),
            )

        for token, field, value in parse_unihan_lines(path):
            glyph_id = ensure_glyph(conn, token)

            column = SUMMARY_FIELD_COLUMNS.get(field)
            if column == "stroke_count":
                try:
                    conn.execute(
                        "UPDATE glyphs SET stroke_count = ? WHERE id = ?",
                        (int(value), glyph_id),
                    )
                except ValueError:
                    pass
            elif column:
                conn.execute(
                    f"UPDATE glyphs SET {column} = ? WHERE id = ?",
                    (value, glyph_id),
                )


def load_decompositions(conn: sqlite3.Connection, decomp_path: Path) -> None:
    conn.execute(
        "INSERT OR REPLACE INTO metadata (key, value) VALUES (?, ?)",
        ("decomposition_source", str(decomp_path)),
    )

    with decomp_path.open("r", encoding="utf-8") as handle:
        for line_number, raw_line in enumerate(handle, start=1):
            line = raw_line.strip()
            if not line or line.startswith("#"):
                continue
            match = DECOMP_RE.match(line)
            if not match:
                raise ValueError(f"Malformed decomposition at {decomp_path}:{line_number}: {line}")

            glyph_id = ensure_glyph(conn, match.group("head"))
            decomp_type = match.group("type")
            raw_components = match.group("components")
            components = split_components(raw_components)

            for component in components:
                ensure_glyph(conn, component)

            conn.execute(
                """
                UPDATE glyphs
                SET
                    decomp_type = ?,
                    decomp_components = ?
                WHERE id = ?
                """,
                (
                    decomp_type,
                    json.dumps(components, ensure_ascii=False),
                    glyph_id,
                ),
            )


def load_hanzi_db(conn: sqlite3.Connection, hanzi_db_path: Path) -> None:
    conn.execute(
        "INSERT OR REPLACE INTO metadata (key, value) VALUES (?, ?)",
        ("hanzi_db_source", str(hanzi_db_path)),
    )

    with hanzi_db_path.open("r", encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        for row in reader:
            character = (row.get("character") or "").strip()
            if not character:
                continue

            glyph_id = ensure_glyph(conn, character)
            conn.execute(
                """
                UPDATE glyphs
                SET frequency_rank = ?, hsk_level = ?
                WHERE id = ?
                """,
                (
                    optional_int(row.get("frequency_rank")),
                    optional_int(row.get("hsk_level")),
                    glyph_id,
                ),
            )


def insert_build_metadata(conn: sqlite3.Connection, args: argparse.Namespace) -> None:
    rows = {
        "build_timestamp_utc": dt.datetime.now(dt.timezone.utc).isoformat(),
        "builder": Path(__file__).name,
        "unihan_dir": str(args.unihan_dir),
        "decomposition_path": str(args.decomposition),
        "hanzi_db_path": str(args.hanzi_db),
        "schema_path": str(args.schema),
        "frequency_rank_note": "frequency_rank and hsk_level are imported from hanzi_db.csv when a character is present there",
    }
    for key, value in rows.items():
        conn.execute("INSERT OR REPLACE INTO metadata (key, value) VALUES (?, ?)", (key, value))


def build_database(args: argparse.Namespace) -> None:
    reset_database(args.output)
    args.output.parent.mkdir(parents=True, exist_ok=True)

    conn = sqlite3.connect(args.output)
    try:
        conn.execute("PRAGMA journal_mode = DELETE")
        conn.execute("PRAGMA synchronous = NORMAL")
        create_schema(conn, args.schema)
        with conn:
            insert_build_metadata(conn, args)
            load_unihan(conn, args.unihan_dir)
            load_decompositions(conn, args.decomposition)
            load_hanzi_db(conn, args.hanzi_db)
        conn.execute("PRAGMA optimize")
    finally:
        conn.close()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build a character/glyph SQLite database from local Unihan and decomposition files."
    )
    parser.add_argument("--unihan-dir", type=Path, default=DEFAULT_UNIHAN_DIR)
    parser.add_argument("--decomposition", type=Path, default=DEFAULT_DECOMP_PATH)
    parser.add_argument("--hanzi-db", type=Path, default=DEFAULT_HANZI_DB_PATH)
    parser.add_argument("--schema", type=Path, default=DEFAULT_SCHEMA_PATH)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    build_database(args)
    print(f"Built {args.output}")


if __name__ == "__main__":
    main()
