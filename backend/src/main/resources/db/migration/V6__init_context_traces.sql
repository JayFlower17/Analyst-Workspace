CREATE TABLE IF NOT EXISTS context_traces (
    id BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT REFERENCES workspaces(id) ON DELETE SET NULL,
    session_id BIGINT REFERENCES chat_sessions(id) ON DELETE SET NULL,
    query TEXT NOT NULL,
    selected_schema_ids JSONB,
    selected_document_chunk_ids JSONB,
    selected_memory_ids JSONB,
    filtered_items_json JSONB,
    packed_context TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_context_traces_workspace_id ON context_traces(workspace_id);
CREATE INDEX IF NOT EXISTS idx_context_traces_session_id ON context_traces(session_id);
CREATE INDEX IF NOT EXISTS idx_context_traces_created_at ON context_traces(created_at DESC);
