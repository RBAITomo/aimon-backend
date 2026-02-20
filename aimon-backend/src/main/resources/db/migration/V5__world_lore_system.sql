SET search_path TO aimon, public;

-- Multi-world lore table
CREATE TABLE IF NOT EXISTS world_lore (
    id            BIGSERIAL    PRIMARY KEY,
    world_code    VARCHAR(50)  NOT NULL,
    title         VARCHAR(100) NOT NULL,
    category      VARCHAR(50)  NOT NULL,
    content       TEXT         NOT NULL,
    min_level     INT          NOT NULL DEFAULT 3,
    interest_tags TEXT[]       NOT NULL DEFAULT '{}',
    shard_type    VARCHAR(20)  NOT NULL DEFAULT 'AMBIENT',
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ           DEFAULT now()
);

CREATE INDEX idx_world_lore_lookup
    ON world_lore(world_code, min_level, shard_type, is_active);

-- User shard tracking (schema ready for Phase 2b)
CREATE TABLE IF NOT EXISTS user_shards (
    id          BIGSERIAL   PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    lore_id     BIGINT      NOT NULL REFERENCES world_lore(id),
    source      VARCHAR(50) NOT NULL DEFAULT 'EXPLORATION',
    unlocked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(user_id, lore_id)
);

CREATE INDEX idx_user_shards_user ON user_shards(user_id);

-- Add world fields to pet_profiles
ALTER TABLE pet_profiles
    ADD COLUMN IF NOT EXISTS active_world      VARCHAR(50) NOT NULL DEFAULT 'COTTON_LAND',
    ADD COLUMN IF NOT EXISTS current_location  VARCHAR(50) NOT NULL DEFAULT 'SWEET_DOMINION';
