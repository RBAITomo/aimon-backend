-- V6: world_vocabulary table for STT phrase hints
-- Standalone table (no FK to world_lore) per Option B design.
-- Terms are fed to Google STT SpeechContext to boost recognition of game-specific proper nouns.

CREATE TABLE aimon.world_vocabulary (
    id         BIGSERIAL    PRIMARY KEY,
    world_code VARCHAR(50)  NOT NULL,
    term       VARCHAR(100) NOT NULL,
    min_level  INT          NOT NULL DEFAULT 1,
    is_active  BOOLEAN      NOT NULL DEFAULT TRUE,
    UNIQUE (world_code, term)
);

CREATE INDEX idx_world_vocabulary_lookup
    ON aimon.world_vocabulary(world_code, min_level, is_active);
