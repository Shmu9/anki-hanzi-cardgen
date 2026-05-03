# Dictionary Database

This folder contains a ~21MB glyph dictionary database, the source data
used to build it, and the build code/schema.

## Layout

- `dict.sqlite3` - generated SQLite database
- `scripts/build_character_db.py` - build script that populates the database
- `sql/schema.sql` - SQLite schema and recursive component views
- `data/Unihan/` - Unicode Unihan text files
- `data/decomposition/` - CJK decomposition data and its original README
- `data/hanzi_db.csv` - character frequency rank and HSK metadata
- `archive/` - older generated database artifacts kept for reference

## Data Sources

- The Unihan files are from the [Unicode Character Database](https://www.unicode.org/reports/tr38). Download latest [here](https://www.unicode.org/Public/UCD/latest/ucd/Unihan.zip). 
The build script uses selected fields including definitions, Mandarin/Japanese readings, Hanyu Pinlu, radical stroke data, and total stroke counts.

- The decomposition data from [`cjk-decomp.txt`](https://github.com/amake/cjk-decomp/blob/master/cjk-decomp.txt), a graphical decomposition dataset.
See `data/decomposition/README.md` for the upstream notes.
The builder stores each decomposition directly on the `glyphs` row as a type and an ordered JSON component list.

- The [`hanzi_db.csv`](https://github.com/ruddfawcett/hanziDB.csv/blob/master/hanzi_db.csv) file supplies `frequency_rank` and `hsk_level` for characters the ~10000 characters present there.

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
  --decomposition dictionary/data/decomposition/cjk-decomp.txt \
  --hanzi-db dictionary/data/hanzi_db.csv \
  --output dictionary/dict.sqlite3
```

## Recursive Component Queries

`sql/schema.sql` defines two recursive views:

```sql
SELECT *
FROM glyph_base_components
WHERE root_glyph = '漢';
```

```sql
SELECT *
FROM glyph_component_tree
WHERE root_glyph = '漢'
  AND depth = 2;
```

`glyph_base_components` walks decomposition recursively until leaf/base
components. `glyph_component_tree` exposes every component at every depth, so a
caller can filter by the desired depth.
