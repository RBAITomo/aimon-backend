-- V2__pet_system_schema.sql
-- Pet system schema for AI-MON gamification

-- Use aimon schema (created in V1)
SET search_path TO aimon, public;

-- Pet variants (base configurations for transformations)
CREATE TABLE IF NOT EXISTS pet_variants (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    persona_template TEXT NOT NULL,
    sprite_prefix VARCHAR(50) NOT NULL,
    min_level INT DEFAULT 12,
    required_badges JSONB NOT NULL DEFAULT '[]',
    transform_base_minutes INT DEFAULT 30,
    cooldown_minutes INT DEFAULT 60,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Pet profiles (one per user, 1:1 relationship)
CREATE TABLE IF NOT EXISTS pet_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(100) DEFAULT 'Mon',
    stage VARCHAR(20) DEFAULT 'EGG',
    variant_id BIGINT REFERENCES pet_variants(id),
    hunger INT DEFAULT 50 CHECK (hunger BETWEEN 0 AND 100),
    energy INT DEFAULT 100 CHECK (energy BETWEEN 0 AND 100),
    happiness INT DEFAULT 80 CHECK (happiness BETWEEN 0 AND 100),
    xp BIGINT DEFAULT 0,
    level INT DEFAULT 1,
    affinity INT DEFAULT 0 CHECK (affinity BETWEEN 0 AND 100),
    variant_expires_at TIMESTAMP,
    variant_cooldown_at TIMESTAMP,
    last_decay_at TIMESTAMP DEFAULT NOW(),
    login_streak INT DEFAULT 0,
    total_sessions INT DEFAULT 0,
    regression_warnings INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Badges catalog (achievements/milestones)
CREATE TABLE IF NOT EXISTS badges (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    category VARCHAR(50) NOT NULL,
    icon VARCHAR(50),
    condition_type VARCHAR(20) NOT NULL,
    condition_config JSONB NOT NULL,
    xp_reward INT DEFAULT 50,
    created_at TIMESTAMP DEFAULT NOW()
);

-- User badges (per-user badge tracking)
CREATE TABLE IF NOT EXISTS user_badges (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    badge_id BIGINT REFERENCES badges(id) ON DELETE CASCADE,
    progress INT DEFAULT 0,
    earned_at TIMESTAMP,
    UNIQUE(user_id, badge_id)
);

-- User variants (permanently unlocked variants)
CREATE TABLE IF NOT EXISTS user_variants (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    variant_id BIGINT REFERENCES pet_variants(id) ON DELETE CASCADE,
    unlocked_at TIMESTAMP DEFAULT NOW(),
    times_used INT DEFAULT 0,
    UNIQUE(user_id, variant_id)
);

-- Action counters (tracking user actions for badges)
CREATE TABLE IF NOT EXISTS action_counters (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    action_type VARCHAR(50) NOT NULL,
    count BIGINT DEFAULT 0,
    last_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(user_id, action_type)
);

-- Indexes
CREATE INDEX idx_pet_profiles_user_id ON pet_profiles(user_id);
CREATE INDEX idx_user_badges_user_id ON user_badges(user_id);
CREATE INDEX idx_user_variants_user_id ON user_variants(user_id);
CREATE INDEX idx_action_counters_user_action ON action_counters(user_id, action_type);

-- Seed badges data
INSERT INTO badges (code, name, category, condition_type, condition_config, xp_reward) VALUES
('bookworm', 'Bookworm', 'academic', 'COUNTER', '{"action":"knowledge_correct","count":20}', 50),
('quiz_master', 'Quiz Master', 'academic', 'COUNTER', '{"action":"quest_complete","count":10}', 75),
('chef', 'Chef', 'care', 'COUNTER', '{"action":"feed","count":15}', 50),
('food_explorer', 'Food Explorer', 'care', 'COUNTER', '{"action":"unique_food","count":10}', 75),
('chatterbox', 'Chatterbox', 'social', 'COUNTER', '{"action":"conversation","count":50}', 50),
('loyal_friend', 'Loyal Friend', 'social', 'STREAK', '{"action":"daily_login","count":7}', 100)
ON CONFLICT (code) DO NOTHING;

-- Seed pet variants data
INSERT INTO pet_variants (code, name, sprite_prefix, min_level, required_badges, persona_template, transform_base_minutes, cooldown_minutes) VALUES
(
    'scholar',
    'Scholar Mon',
    'scholar',
    12,
    '["bookworm","quiz_master"]',
    'Bạn là Scholar Mon - phiên bản học giả thông thái của AI-MON. Bạn thích đọc sách, giải đáp câu hỏi và khuyến khích trẻ học hỏi những điều mới mẻ.',
    30,
    60
),
(
    'foodie',
    'Foodie Mon',
    'foodie',
    12,
    '["chef","food_explorer"]',
    'Bạn là Foodie Mon - phiên bản đầu bếp đáng yêu của AI-MON. Bạn thích nấu ăn, khám phá món ăn mới và chia sẻ công thức nấu ăn với bạn bè.',
    30,
    60
)
ON CONFLICT (code) DO NOTHING;
