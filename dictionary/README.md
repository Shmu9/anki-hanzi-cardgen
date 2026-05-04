# Dictionary Database

This folder contains a ~21MB glyph dictionary database, the source data
used to build it, and the build code/schema.

## Layout

- `dict.sqlite3` - generated SQLite database
- `scripts/build_character_db.py` - build script that populates the database
- `sql/schema.sql` - SQLite schema and recursive component views
- `data/Unihan/` - Unicode Unihan text files
- `data/decomposition/` - IDS (Ideographic Description Sequence) decomposition data and source notes
- `data/hanzi_db.csv` - character frequency rank and HSK metadata
- `archive/` - older generated database artifacts kept for reference

## Data Sources

- The Unihan files are from the [Unicode Character Database](https://www.unicode.org/reports/tr38). Download latest [here](https://www.unicode.org/Public/UCD/latest/ucd/Unihan.zip). 
The build script uses selected fields including definitions, Mandarin/Japanese readings, Hanyu Pinlu, radical stroke data, and total stroke counts.

- The decomposition data from `data/decomposition/ids.txt`, sourced from
[`cjkvi/cjkvi-ids`](https://github.com/cjkvi/cjkvi-ids). This IDS data is
preferred over the older [`cjk-decomp.txt`](https://github.com/amake/cjk-decomp/blob/master/cjk-decomp.txt) source because it preserves better
regional component forms, such as `艹` instead of `卄` for characters like
`萌`. The builder stores each IDS operator directly on the `glyphs` row as
`decomp_type` and the ordered operands as JSON in `decomp_components`.

- The [`hanzi_db.csv`](https://github.com/ruddfawcett/hanziDB.csv/blob/master/hanzi_db.csv) file supplies `frequency_rank` and `hsk_level` for characters the ~10000 characters present there.

- `data/kdefinition_supplement.csv` supplies reviewed component meanings for
glyphs that do not have a Unihan `kDefinition`. During database generation,
these values fill `k_definition` only when that field is still empty.

## Regenerating
Once the files specified under **Data Sources** are downloaded into the directories specified by **Layout**, run the python script.

From this folder:

```bash
python3 scripts/build_character_db.py
```

From the repository root:

```bash
python3 dictionary/scripts/build_character_db.py
```

The default output is `dictionary/dict.sqlite3`. You can override inputs or
output with:

```bash
python3 dictionary/scripts/build_character_db.py \
  --schema dictionary/sql/schema.sql \
  --unihan-dir dictionary/data/Unihan \
  --decomposition dictionary/data/decomposition/ids.txt \
  --hanzi-db dictionary/data/hanzi_db.csv \
  --kdefinition-supplement dictionary/data/kdefinition_supplement.csv \
  --output dictionary/dict.sqlite3
```

When multiple IDS expressions are available for the same glyph, the builder
prefers Japanese-tagged forms first, then other CJK regional forms, then
untagged forms. Nested IDS expressions are stored as intermediate literal rows
so the recursive component views can still walk through them.

## Component Views

`sql/schema.sql` defines recursive component views for different levels of
detail.

For normal component lookups, use the compact summary view:

```sql
SELECT *
FROM glyph_base_component_summary
WHERE root_glyph = '漢';
```

`glyph_base_component_summary` returns:

- `root_glyph` - the queried character
- `component_glyph` - a leaf/base component glyph
- `component_token` - the component's canonical token, usually `U+...`
- `position` - the component's order within its parent decomposition
- `depth` - recursive distance from the root glyph

For debugging or joins that need internal ids and traversal details, use the
full base component view:

```sql
SELECT *
FROM glyph_base_components
WHERE root_glyph = '漢';
```

For every component at every recursive level, query the full tree:

```sql
SELECT *
FROM glyph_component_tree
WHERE root_glyph = '漢'
  AND depth = 2;
```

`glyph_base_components` walks decomposition recursively until leaf/base
components. `glyph_component_tree` exposes every component at every depth, so a
caller can filter by the desired depth. Both full views include internal ids,
raw component values, and a traversal `path`; the summary view omits those
debug fields for easier application queries.
