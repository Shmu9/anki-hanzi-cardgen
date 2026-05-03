PRAGMA foreign_keys = ON;

CREATE TABLE metadata (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

CREATE TABLE glyphs (
    id INTEGER PRIMARY KEY,
    token TEXT NOT NULL UNIQUE,
    glyph TEXT,
    unicode TEXT,
    codepoint INTEGER,
    kind TEXT NOT NULL CHECK (kind IN ('unicode', 'intermediate', 'literal')),
    k_definition TEXT,
    k_mandarin TEXT,
    k_japanese TEXT,
    k_hanyu_pinlu TEXT,
    frequency_rank INTEGER,
    hsk_level INTEGER,
    stroke_count INTEGER,
    rs_unicode TEXT,
    rs_adobe_japan1_6 TEXT,
    decomp_type TEXT,
    decomp_components TEXT
);

CREATE UNIQUE INDEX idx_glyphs_glyph
    ON glyphs(glyph)
    WHERE glyph IS NOT NULL;

CREATE UNIQUE INDEX idx_glyphs_unicode
    ON glyphs(unicode)
    WHERE unicode IS NOT NULL;

CREATE INDEX idx_glyphs_frequency_rank ON glyphs(frequency_rank);
CREATE INDEX idx_glyphs_hsk_level ON glyphs(hsk_level);
CREATE INDEX idx_glyphs_stroke_count ON glyphs(stroke_count);
CREATE INDEX idx_glyphs_mandarin ON glyphs(k_mandarin);

CREATE VIEW character_summary AS
SELECT
    glyph,
    unicode,
    codepoint,
    k_definition,
    k_mandarin,
    k_japanese,
    k_hanyu_pinlu,
    frequency_rank,
    hsk_level,
    stroke_count,
    decomp_type,
    decomp_components
FROM glyphs
WHERE kind = 'unicode';

-- Recursive component expansion.
--
-- Base components for a character:
--   SELECT * FROM glyph_base_components WHERE root_glyph = '漢';
--
-- Components at a specified depth:
--   SELECT * FROM glyph_component_tree WHERE root_glyph = '漢' AND depth = 2;
CREATE VIEW glyph_component_tree AS
WITH RECURSIVE component_tree AS (
    SELECT
        root.id AS root_glyph_id,
        root.token AS root_token,
        root.glyph AS root_glyph,
        component.id AS component_glyph_id,
        component.token AS component_token,
        component.glyph AS component_glyph,
        json_each.value AS component_value,
        CAST(json_each.key AS INTEGER) AS position,
        1 AS depth,
        '|' || root.token || '|' || component.token || '|' AS path
    FROM glyphs root
    JOIN json_each(COALESCE(root.decomp_components, '[]'))
    JOIN glyphs component
        ON component.glyph = json_each.value
        OR component.token = json_each.value

    UNION ALL

    SELECT
        tree.root_glyph_id,
        tree.root_token,
        tree.root_glyph,
        component.id AS component_glyph_id,
        component.token AS component_token,
        component.glyph AS component_glyph,
        json_each.value AS component_value,
        CAST(json_each.key AS INTEGER) AS position,
        tree.depth + 1 AS depth,
        tree.path || component.token || '|' AS path
    FROM component_tree tree
    JOIN glyphs parent
        ON parent.id = tree.component_glyph_id
    JOIN json_each(COALESCE(parent.decomp_components, '[]'))
    JOIN glyphs component
        ON component.glyph = json_each.value
        OR component.token = json_each.value
    WHERE parent.kind != 'unicode'
      AND instr(tree.path, '|' || component.token || '|') = 0
)
SELECT
    root_glyph_id,
    root_token,
    root_glyph,
    component_glyph_id,
    component_token,
    component_glyph,
    component_value,
    position,
    depth,
    path
FROM component_tree;

CREATE VIEW glyph_base_components AS
SELECT tree.*
FROM glyph_component_tree tree
JOIN glyphs component
    ON component.id = tree.component_glyph_id
WHERE component.kind = 'unicode'
   OR json_array_length(COALESCE(component.decomp_components, '[]')) = 0;
