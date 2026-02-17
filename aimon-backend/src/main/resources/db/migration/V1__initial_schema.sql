-- V1__initial_schema.sql
-- Minimal schema for aimon-backend v0.2
-- PowerMem tables managed by memoryService independently

-- Create aimon schema (shared with memoryService)
CREATE SCHEMA IF NOT EXISTS aimon;
SET search_path TO aimon, public;

-- Parents table
CREATE TABLE IF NOT EXISTS parents (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255),
    phone VARCHAR(20),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Users (children) table
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    age INTEGER,
    parent_id BIGINT REFERENCES parents(id) ON DELETE CASCADE,
    preferences JSONB DEFAULT '{}',
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_users_parent_id ON users(parent_id);

-- Banned keywords for Kid Mode content safety
CREATE TABLE IF NOT EXISTS banned_keywords (
    id BIGSERIAL PRIMARY KEY,
    keyword VARCHAR(255) NOT NULL UNIQUE,
    category VARCHAR(50),
    severity VARCHAR(20) DEFAULT 'block',
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_banned_keywords_keyword ON banned_keywords(keyword);

-- Insert sample banned keywords
INSERT INTO banned_keywords (keyword, category, severity) VALUES
('violence', 'safety', 'block'),
('weapon', 'safety', 'block'),
('drug', 'safety', 'block'),
('alcohol', 'safety', 'block')
ON CONFLICT (keyword) DO NOTHING;
