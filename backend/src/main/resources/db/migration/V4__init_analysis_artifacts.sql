CREATE TABLE IF NOT EXISTS analysis_artifacts (
    id BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT REFERENCES workspaces(id) ON DELETE SET NULL,
    session_id BIGINT REFERENCES chat_sessions(id) ON DELETE SET NULL,
    dataset_id BIGINT REFERENCES datasets(id) ON DELETE SET NULL,
    context_trace_id BIGINT,
    user_query TEXT,
    analysis_type VARCHAR(64),
    generated_code_or_sql TEXT,
    summary TEXT,
    chart_type VARCHAR(64),
    result_preview_json JSONB,
    artifact_schema_version INTEGER DEFAULT 2,
    analysis_report_json JSONB,
    evidence_summary_json JSONB,
    execution_logs_json JSONB,
    validation_report_json JSONB,
    risk_notices_json JSONB,
    artifact_status VARCHAR(32) DEFAULT 'ACTIVE',
    archived_at TIMESTAMP,
    deleted_at TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_analysis_artifacts_workspace_id ON analysis_artifacts(workspace_id);
CREATE INDEX IF NOT EXISTS idx_analysis_artifacts_session_id ON analysis_artifacts(session_id);
CREATE INDEX IF NOT EXISTS idx_analysis_artifacts_status ON analysis_artifacts(artifact_status);
CREATE INDEX IF NOT EXISTS idx_analysis_artifacts_created_at ON analysis_artifacts(created_at DESC);
