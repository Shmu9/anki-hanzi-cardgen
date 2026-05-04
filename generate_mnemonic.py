import argparse
import json
import sqlite3
import sys
import urllib.error
import urllib.request
from pathlib import Path

# Ollama model name.
MODEL_NAME = "gemma4:e4b"
DEFAULT_OLLAMA_URL = "http://localhost:11434"
DEFAULT_DB_PATH = Path(__file__).resolve().parent / "dictionary" / "dict.sqlite3"
DEFAULT_MAX_NEW_TOKENS = 1000


def get_constituent_meanings(character, db_path=DEFAULT_DB_PATH):
    """Return base components and meanings for a character from the dictionary DB."""
    db_path = Path(db_path)
    if not db_path.exists():
        raise FileNotFoundError(f"Dictionary database not found: {db_path}")

    with sqlite3.connect(db_path) as conn:
        conn.row_factory = sqlite3.Row
        rows = conn.execute(
            """
            SELECT
                summary.component_glyph,
                summary.component_token,
                summary.position,
                summary.depth,
                glyphs.k_definition
            FROM glyph_base_component_summary AS summary
            LEFT JOIN glyphs
                ON glyphs.token = summary.component_token
            WHERE summary.root_glyph = ?
            ORDER BY summary.depth, summary.position, summary.component_token
            """,
            (character,),
        ).fetchall()

    return [
        {
            "glyph": row["component_glyph"] or row["component_token"],
            "token": row["component_token"],
            "meaning": row["k_definition"] or "meaning unavailable",
            "position": row["position"],
            "depth": row["depth"],
        }
        for row in rows
    ]


def get_character_meaning(character, db_path=DEFAULT_DB_PATH):
    """Return the dictionary meaning for the target character."""
    db_path = Path(db_path)
    if not db_path.exists():
        raise FileNotFoundError(f"Dictionary database not found: {db_path}")

    with sqlite3.connect(db_path) as conn:
        row = conn.execute(
            "SELECT k_definition FROM glyphs WHERE glyph = ?",
            (character,),
        ).fetchone()

    if row is None or not row[0]:
        return "meaning unavailable"
    return row[0]


def format_constituents_for_prompt(constituents):
    """Format component meanings for the model prompt."""
    return "\n".join(
        f"- {component['glyph']} ({component['token']}): {component['meaning']}"
        for component in constituents
    )


def build_prompt(
    character,
    constituents=None,
    target_meaning=None,
):
    """Build the mnemonic prompt from dictionary constituent meanings."""
    if constituents is None:
        constituents = get_constituent_meanings(character)
    if target_meaning is None:
        target_meaning = get_character_meaning(character)

    if constituents:
        constituent_text = format_constituents_for_prompt(constituents)
    else:
        constituent_text = "- No constituent data found in dict.sqlite3."

    return f"""Create a simple, vivid mnemonic to remember the Chinese character "{character}".
Target character meaning: {target_meaning}
Use only these constituent meanings as the building blocks:
{constituent_text}

Think briefly to create a mnemonic sentence that is grammatically correct. Do not invent extra radicals or components.
Radicals/components meanings should be in italics, and the target character meaning should be in bold.
Ensure the Radicals/components themselves are included in brackets () without italics.

Example 1:
钞 / 鈔 (money) has constituents 钅 (metal; gold; money) and 少 (few; little).
Mnemonic: A small piece of *metal* (钅)is shaved into a *few* (少) tiny valuable bits, becoming **money**.

Example 2:
乖 (disobedient; rebellious) has constituents 千 (thousand) and 北 (north).
A *thousand* (千) people decided to **disobey/rebel** against the government and fight back against *North* (北) Korea. And it worked! Here we see the thousand piercing the North!

Mnemonic for "{character}":"""


def call_ollama(
    prompt,
    model_name=MODEL_NAME,
    ollama_url=DEFAULT_OLLAMA_URL,
    max_new_tokens=DEFAULT_MAX_NEW_TOKENS,
    think=True,
    show_thinking=True,
):
    """Generate text from Ollama's local HTTP API."""
    endpoint = f"{ollama_url.rstrip('/')}/api/generate"
    payload = {
        "model": model_name,
        "prompt": prompt,
        "stream": think,
        "think": think,
        "options": {
            "temperature": 0.7,
            "num_predict": max_new_tokens,
        },
    }
    request = urllib.request.Request(
        endpoint,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )

    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            if think:
                thinking_parts = []
                response_parts = []
                done_reason = None
                for line in response:
                    if not line.strip():
                        continue
                    data = json.loads(line.decode("utf-8"))
                    if "error" in data:
                        raise RuntimeError(f"Ollama error: {data['error']}")

                    thinking = data.get("thinking", "")
                    if thinking:
                        if show_thinking and not thinking_parts:
                            print("Thinking:", file=sys.stdout)
                        if show_thinking:
                            print(thinking, end="", file=sys.stdout, flush=True)
                        thinking_parts.append(thinking)

                    generated = data.get("response", "")
                    if generated:
                        response_parts.append(generated)

                    if data.get("done"):
                        done_reason = data.get("done_reason")
                        break

                if show_thinking and thinking_parts:
                    print("\n", file=sys.stdout)
                if done_reason == "length":
                    print(
                        "Warning: Ollama stopped because it hit --max-new-tokens; "
                        "increase the value if output is still incomplete.",
                        file=sys.stderr,
                    )
                return "".join(response_parts).strip()

            data = json.loads(response.read().decode("utf-8"))
    except urllib.error.URLError as exc:
        raise RuntimeError(
            f"Could not reach Ollama at {ollama_url}. Is Ollama running, and is "
            f"the model pulled with `ollama pull {model_name}`?"
        ) from exc

    if "error" in data:
        raise RuntimeError(f"Ollama error: {data['error']}")
    return data.get("response", "").strip()


def generate_mnemonic(
    character,
    constituents=None,
    target_meaning=None,
    model_name=MODEL_NAME,
    ollama_url=DEFAULT_OLLAMA_URL,
    max_new_tokens=DEFAULT_MAX_NEW_TOKENS,
    think=True,
    show_thinking=True,
):
    """Generate a mnemonic for a given character using Ollama."""
    prompt = build_prompt(character, constituents, target_meaning)
    return call_ollama(
        prompt,
        model_name,
        ollama_url,
        max_new_tokens,
        think,
        show_thinking,
    )

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Generate a Chinese character mnemonic.")
    parser.add_argument("character", nargs="?", default="好")
    parser.add_argument(
        "--db-path",
        type=Path,
        default=DEFAULT_DB_PATH,
        help=f"Path to dict.sqlite3 (default: {DEFAULT_DB_PATH})",
    )
    parser.add_argument(
        "--model",
        default=MODEL_NAME,
        help=f"Ollama model name (default: {MODEL_NAME})",
    )
    parser.add_argument(
        "--ollama-url",
        default=DEFAULT_OLLAMA_URL,
        help=f"Ollama base URL (default: {DEFAULT_OLLAMA_URL})",
    )
    parser.add_argument(
        "--max-new-tokens",
        type=int,
        default=DEFAULT_MAX_NEW_TOKENS,
        help="Maximum tokens to ask Ollama to generate.",
    )
    parser.add_argument(
        "--hide-thinking",
        action="store_true",
        help="Request Ollama thinking but do not print it.",
    )
    parser.add_argument(
        "--no-thinking",
        action="store_true",
        help="Do not request Ollama thinking.",
    )
    args = parser.parse_args()

    char = args.character
    constituents = get_constituent_meanings(char, args.db_path)
    target_meaning = get_character_meaning(char, args.db_path)
    print(f"Meaning for '{char}': {target_meaning}")
    if constituents:
        print("Constituents:")
        for component in constituents:
            print(f"- {component['glyph']}: {component['meaning']}")
    else:
        print(f"No constituents found for '{char}' in {args.db_path}")

    mnemonic = generate_mnemonic(
        char,
        constituents=constituents,
        target_meaning=target_meaning,
        model_name=args.model,
        ollama_url=args.ollama_url,
        max_new_tokens=args.max_new_tokens,
        think=not args.no_thinking,
        show_thinking=not args.hide_thinking and not args.no_thinking,
    )
    print(f"Mnemonic for '{char}': {mnemonic}")
