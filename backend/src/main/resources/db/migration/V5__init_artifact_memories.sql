CREATE TABLE IF NOT EXISTS artifact_memories (
    id BIGSERIAL PRIMARY KEY,
    artifact_id BIGINT REFERENCES analysis_artifacts(id) ON DELETE CASCADE,
    workspace_id BIGINT REFERENCES workspaces(id) ON DELETE CASCADE,
    dataset_id BIGINT REFERENCES datasets(id) ON DELETE SET NULL,
    memory_type VARCHAR(64) NOT NULL,
    scope VARCHAR(64) NOT NULL,
    content TEXT NOT NULL,
    summary TEXT,
    importance DOUBLE PRECISION DEFAULT 0.5,
    confidence DOUBLE PRECISION DEFAULT 0.5,
    status VARCHAR(32) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP,
    use_count BIGINT DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_artifact_memories_workspace_id ON artifact_memories(workspace_id);
CREATE INDEX IF NOT EXISTS idx_artifact_memories_artifact_id ON artifact_memories(artifact_id);
CREATE INDEX IF NOT EXISTS idx_artifact_memories_type ON artifact_memories(memory_type);
CREATE INDEX IF NOT EXISTS idx_artifact_memories_status ON artifact_memories(status);
