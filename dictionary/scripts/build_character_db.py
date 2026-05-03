#!/usr/bin/env python3
"""Build a SQLite character/glyph database from Unihan and IDS decomposition data.

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
DEFAULT_DECOMP_PATH = ROOT / "data" / "decomposition" / "ids.txt"
DEFAULT_HANZI_DB_PATH = ROOT / "data" / "hanzi_db.csv"
DEFAULT_KDEFINITION_SUPPLEMENT_PATH = ROOT / "data" / "kdefinition_supplement.csv"
DEFAULT_SCHEMA_PATH = ROOT / "sql" / "schema.sql"
DEFAULT_OUTPUT = ROOT / "dict.sqlite3"
UNIHAN_VARIANTS_FILENAME = "Unihan_Variants.txt"

IDS_OPERATORS = {
    "⿰": 2,
    "⿱": 2,
    "⿲": 3,
    "⿳": 3,
    "⿴": 2,
    "⿵": 2,
    "⿶": 2,
    "⿷": 2,
    "⿸": 2,
    "⿹": 2,
    "⿺": 2,
    "⿻": 2,
}

IDS_REGION_PREFERENCE = ("J", "T", "K", "V", "G", "H", "M", "P", "U", "B", "S")
IDS_VARIANT_RE = re.compile(r"\[(?P<regions>[A-Z]+)\]$")

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


def optional_int(value: str | None) -> int | None:
    if value is None:
        return None
    value = value.strip()
    if not value:
        return None
    return int(value)


def parse_variant_targets(value: str) -> list[str]:
    """Return plain Unihan tokens from a variant field value."""
    targets: list[str] = []
    for raw_target in value.split():
        target = raw_target.split("<", 1)[0]
        if target.startswith("U+"):
            targets.append(target.upper())
    return targets


def strip_ids_variant_tag(expression: str) -> tuple[str, str | None]:
    match = IDS_VARIANT_RE.search(expression)
    if not match:
        return expression, None
    return expression[: match.start()], match.group("regions")


def choose_ids_expression(expressions: list[str]) -> str | None:
    parsed_expressions = [strip_ids_variant_tag(expression.strip()) for expression in expressions]
    parsed_expressions = [
        (expression, regions)
        for expression, regions in parsed_expressions
        if expression
    ]
    if not parsed_expressions:
        return None

    for preferred_region in IDS_REGION_PREFERENCE:
        for expression, regions in parsed_expressions:
            if regions and preferred_region in regions:
                return expression

    for expression, regions in parsed_expressions:
        if regions is None:
            return expression

    return parsed_expressions[0][0]


def parse_ids_expression(expression: str, start: int = 0) -> tuple[str, str | None, list[str], int]:
    if start >= len(expression):
        raise ValueError(f"Unexpected end of IDS expression: {expression!r}")

    char = expression[start]
    arity = IDS_OPERATORS.get(char)
    if arity is None:
        return char, None, [], start + 1

    components: list[str] = []
    index = start + 1
    for _ in range(arity):
        component, _, _, index = parse_ids_expression(expression, index)
        components.append(component)

    return expression[start:index], char, components, index


def store_ids_decomposition(conn: sqlite3.Connection, source_token: str, expression: str) -> None:
    parsed_expression, decomp_type, components, index = parse_ids_expression(expression)
    if index != len(expression):
        raise ValueError(f"Trailing data in IDS expression {expression!r} at index {index}")

    glyph_id = ensure_glyph(conn, source_token)
    if parsed_expression != expression or not decomp_type:
        return

    for component in components:
        ensure_glyph(conn, component)
        if component and component[0] in IDS_OPERATORS:
            store_ids_decomposition(conn, component, component)

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


def load_kdefinition_supplement(conn: sqlite3.Connection, supplement_path: Path) -> int:
    """Fill missing kDefinition values from manually reviewed component meanings."""
    if not supplement_path.exists():
        conn.execute(
            "INSERT OR REPLACE INTO metadata (key, value) VALUES (?, ?)",
            ("kdefinition_supplement_status", "missing"),
        )
        return 0

    conn.execute(
        "INSERT OR REPLACE INTO metadata (key, value) VALUES (?, ?)",
        ("kdefinition_supplement_source", str(supplement_path)),
    )

    inserted_count = 0
    with supplement_path.open("r", encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        for row in reader:
            glyph = (row.get("glyph") or "").strip()
            component_meaning = (row.get("component_meaning") or "").strip()
            if not glyph or not component_meaning:
                continue

            glyph_id = ensure_glyph(conn, glyph)
            cursor = conn.execute(
                """
                UPDATE glyphs
                SET k_definition = ?
                WHERE id = ?
                  AND k_definition IS NULL
                """,
                (component_meaning, glyph_id),
            )
            inserted_count += cursor.rowcount

    conn.execute(
        "INSERT OR REPLACE INTO metadata (key, value) VALUES (?, ?)",
        ("kdefinition_supplement_inserted_count", str(inserted_count)),
    )
    return inserted_count


def resolve_simplified_japanese_readings(conn: sqlite3.Connection, unihan_dir: Path) -> int:
    """Copy kJapanese from traditional variants when simplified rows lack it."""
    variants_path = unihan_dir / UNIHAN_VARIANTS_FILENAME
    if not variants_path.exists():
        return 0

    resolved_count = 0
    for simplified_token, field, value in parse_unihan_lines(variants_path):
        if field != "kTraditionalVariant":
            continue

        simplified_id = ensure_glyph(conn, simplified_token)
        simplified_row = conn.execute(
            "SELECT k_japanese FROM glyphs WHERE id = ?",
            (simplified_id,),
        ).fetchone()
        if simplified_row is None or simplified_row[0]:
            continue

        for traditional_token in parse_variant_targets(value):
            traditional_id = ensure_glyph(conn, traditional_token)
            traditional_row = conn.execute(
                "SELECT k_japanese FROM glyphs WHERE id = ?",
                (traditional_id,),
            ).fetchone()
            if traditional_row is None or not traditional_row[0]:
                continue

            conn.execute(
                "UPDATE glyphs SET k_japanese = ? WHERE id = ?",
                (traditional_row[0], simplified_id),
            )
            resolved_count += 1
            break

    conn.execute(
        "INSERT OR REPLACE INTO metadata (key, value) VALUES (?, ?)",
        ("resolved_simplified_k_japanese_count", str(resolved_count)),
    )
    return resolved_count


def load_decompositions(conn: sqlite3.Connection, decomp_path: Path) -> None:
    conn.execute(
        "INSERT OR REPLACE INTO metadata (key, value) VALUES (?, ?)",
        ("decomposition_source", str(decomp_path)),
    )
    conn.execute(
        "INSERT OR REPLACE INTO metadata (key, value) VALUES (?, ?)",
        ("decomposition_format", "ids"),
    )

    with decomp_path.open("r", encoding="utf-8") as handle:
        for line_number, raw_line in enumerate(handle, start=1):
            line = raw_line.rstrip("\n")
            if not line or line.startswith("#"):
                continue

            fields = line.split("\t")
            if len(fields) < 3:
                raise ValueError(f"Malformed IDS decomposition at {decomp_path}:{line_number}: {line}")

            token, glyph, *expressions = fields
            ensure_glyph(conn, token)
            ensure_glyph(conn, glyph)
            expression = choose_ids_expression(expressions)
            if expression is None or expression == glyph:
                continue

            try:
                store_ids_decomposition(conn, token, expression)
            except ValueError as exc:
                raise ValueError(
                    f"Malformed IDS expression at {decomp_path}:{line_number}: {line}"
                ) from exc


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
        "kdefinition_supplement_path": str(args.kdefinition_supplement),
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
            load_kdefinition_supplement(conn, args.kdefinition_supplement)
            resolve_simplified_japanese_readings(conn, args.unihan_dir)
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
    parser.add_argument("--kdefinition-supplement", type=Path, default=DEFAULT_KDEFINITION_SUPPLEMENT_PATH)
    parser.add_argument("--schema", type=Path, default=DEFAULT_SCHEMA_PATH)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    build_database(args)
    print(f"Built {args.output}")


if __name__ == "__main__":
    main()
