-- Create aimon schema and observations table for PowerMem
CREATE SCHEMA IF NOT EXISTS aimon;
GRANT ALL PRIVILEGES ON SCHEMA aimon TO aimon;

-- Create observations table in aimon schema
CREATE TABLE IF NOT EXISTS aimon.observations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content TEXT NOT NULL,
    embedding vector(384),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    metadata JSONB DEFAULT '{}'::jsonb
);

-- Add table comments
COMMENT ON TABLE aimon.observations IS 'PowerMem observations for conversation memory storage';
COMMENT ON COLUMN aimon.observations.embedding IS 'all-MiniLM-L6-v2 embedding vector (384 dimensions)';
COMMENT ON COLUMN aimon.observations.metadata IS 'JSON metadata: robot_id, category, importance, emotion, tags';

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_observations_created_at ON aimon.observations(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_observations_metadata_gin ON aimon.observations USING gin(metadata);
CREATE INDEX IF NOT EXISTS idx_observations_embedding_ivfflat ON aimon.observations USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
CREATE INDEX IF NOT EXISTS idx_observations_robot_id ON aimon.observations((metadata->>'robot_id'));
CREATE INDEX IF NOT EXISTS idx_observations_user_id ON aimon.observations((metadata->>'user_id'));
CREATE INDEX IF NOT EXISTS idx_observations_category ON aimon.observations((metadata->>'category'));
CREATE INDEX IF NOT EXISTS idx_observations_importance ON aimon.observations(((metadata->>'importance')::float));

-- Create updated_at trigger function
CREATE OR REPLACE FUNCTION aimon.update_observations_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger
DROP TRIGGER IF EXISTS trigger_observations_updated_at ON aimon.observations;
CREATE TRIGGER trigger_observations_updated_at
    BEFORE UPDATE ON aimon.observations
    FOR EACH ROW
    EXECUTE FUNCTION aimon.update_observations_updated_at();

-- Create session_memories table
CREATE TABLE IF NOT EXISTS aimon.session_memories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id VARCHAR(100) NOT NULL,
    robot_id INTEGER NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_session_memories_session_id ON aimon.session_memories(session_id);
CREATE INDEX IF NOT EXISTS idx_session_memories_robot_id ON aimon.session_memories(robot_id);
