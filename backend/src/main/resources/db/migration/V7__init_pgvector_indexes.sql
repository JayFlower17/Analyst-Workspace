CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS vector_store (
    id uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
    content TEXT,
    metadata JSON,
    embedding vector(1024)
);

CREATE INDEX IF NOT EXISTS idx_vector_store_embedding
    ON vector_store USING hnsw (embedding vector_cosine_ops);

CREATE TABLE IF NOT EXISTS document_chunk_embeddings (
    id BIGSERIAL PRIMARY KEY,
    chunk_id BIGINT NOT NULL REFERENCES document_chunks(id) ON DELETE CASCADE,
    workspace_id BIGINT REFERENCES workspaces(id) ON DELETE CASCADE,
    document_id BIGINT NOT NULL REFERENCES document_assets(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    embedding vector(1024),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS schema_embeddings (
    id BIGSERIAL PRIMARY KEY,
    dataset_id BIGINT NOT NULL REFERENCES datasets(id) ON DELETE CASCADE,
    table_name TEXT NOT NULL,
    column_name TEXT,
    semantic_text TEXT NOT NULL,
    embedding vector(1024),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS artifact_memory_embeddings (
    id BIGSERIAL PRIMARY KEY,
    memory_id BIGINT NOT NULL REFERENCES artifact_memories(id) ON DELETE CASCADE,
    workspace_id BIGINT REFERENCES workspaces(id) ON DELETE CASCADE,
    dataset_id BIGINT REFERENCES datasets(id) ON DELETE SET NULL,
    memory_type VARCHAR(64),
    summary TEXT NOT NULL,
    embedding vector(1024),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_document_chunk_embeddings_workspace_id ON document_chunk_embeddings(workspace_id);
CREATE INDEX IF NOT EXISTS idx_schema_embeddings_dataset_id ON schema_embeddings(dataset_id);
CREATE INDEX IF NOT EXISTS idx_artifact_memory_embeddings_workspace_id ON artifact_memory_embeddings(workspace_id);

CREATE INDEX IF NOT EXISTS idx_document_chunk_embeddings_vector
    ON document_chunk_embeddings USING hnsw (embedding vector_cosine_ops);

CREATE INDEX IF NOT EXISTS idx_schema_embeddings_vector
    ON schema_embeddings USING hnsw (embedding vector_cosine_ops);

CREATE INDEX IF NOT EXISTS idx_artifact_memory_embeddings_vector
    ON artifact_memory_embeddings USING hnsw (embedding vector_cosine_ops);
