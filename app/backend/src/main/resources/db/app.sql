-- PostgreSQL app-state schema for the first hosted slice:
-- authentication plus per-user component meanings for mnemonic generation.
--
-- The generated dictionary remains in dictionary/dict.sqlite3. Component
-- preferences are keyed by the actual component glyph because mnemonic
-- generation already knows the character's component glyphs.

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS citext;

CREATE TABLE IF NOT EXISTS app_metadata (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO app_metadata (key, value)
VALUES ('schema_version', '1')
ON CONFLICT (key) DO NOTHING;

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email CITEXT UNIQUE,
    username CITEXT UNIQUE,
    display_name TEXT,
    password_hash TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'active'
        CHECK (status IN ('active', 'disabled', 'deleted')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_active_at TIMESTAMPTZ,
    password_updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (email IS NOT NULL OR username IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_users_status
    ON users(status);

CREATE TABLE IF NOT EXISTS user_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash TEXT NOT NULL UNIQUE,
    user_agent TEXT,
    ip_address INET,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_user_sessions_user_active
    ON user_sessions(user_id, expires_at)
    WHERE revoked_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_user_sessions_expires
    ON user_sessions(expires_at)
    WHERE revoked_at IS NULL;

-- One row is one user-approved meaning for one component glyph.
--
-- Main mnemonic generation lookup:
--
--   SELECT component_glyph, meaning, rank, is_primary
--   FROM user_component_meanings
--   WHERE user_id = $1
--     AND component_glyph = ANY($2::text[])
--   ORDER BY rank, component_glyph, meaning;
CREATE TABLE IF NOT EXISTS user_component_meanings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    component_glyph TEXT NOT NULL CHECK (component_glyph <> ''),
    component_token TEXT,
    meaning TEXT NOT NULL CHECK (meaning <> ''),
    rank INTEGER NOT NULL CHECK (rank BETWEEN 0 AND 4),
    is_primary BOOLEAN NOT NULL DEFAULT false,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, component_glyph, meaning),
    UNIQUE (user_id, component_glyph, rank)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_user_component_meanings_one_primary
    ON user_component_meanings(user_id, component_glyph)
    WHERE is_primary;

CREATE INDEX IF NOT EXISTS idx_user_component_meanings_lookup
    ON user_component_meanings(user_id, rank ASC, component_glyph)
    INCLUDE (meaning, is_primary);
