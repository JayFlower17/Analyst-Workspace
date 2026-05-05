CREATE TABLE IF NOT EXISTS workspaces (
    id BIGSERIAL PRIMARY KEY,
    owner_user_id BIGINT REFERENCES users(id),
    name TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS datasets (
    id BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT REFERENCES workspaces(id) ON DELETE SET NULL,
    name TEXT NOT NULL,
    description_md TEXT,
    table_name TEXT NOT NULL UNIQUE,
    original_file_name TEXT,
    row_count BIGINT DEFAULT 0,
    column_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dataset_columns (
    id BIGSERIAL PRIMARY KEY,
    dataset_id BIGINT NOT NULL REFERENCES datasets(id) ON DELETE CASCADE,
    column_name TEXT NOT NULL,
    data_type TEXT,
    nullable BOOLEAN DEFAULT FALSE,
    distinct_count BIGINT DEFAULT 0,
    min_value TEXT,
    max_value TEXT,
    sample_values TEXT
);

CREATE TABLE IF NOT EXISTS dataset_relations (
    id BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    source_dataset_id BIGINT REFERENCES datasets(id) ON DELETE CASCADE,
    source_table_name TEXT,
    source_column_name TEXT,
    target_dataset_id BIGINT REFERENCES datasets(id) ON DELETE CASCADE,
    target_table_name TEXT,
    target_column_name TEXT,
    relation_type TEXT,
    confidence DOUBLE PRECISION,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_datasets_workspace_id ON datasets(workspace_id);
CREATE INDEX IF NOT EXISTS idx_dataset_columns_dataset_id ON dataset_columns(dataset_id);
CREATE INDEX IF NOT EXISTS idx_dataset_relations_workspace_id ON dataset_relations(workspace_id);
