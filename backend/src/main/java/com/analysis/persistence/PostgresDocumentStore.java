package com.analysis.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.analysis.model.entity.DocumentAsset;
import com.analysis.model.entity.DocumentChunk;

@Repository
@ConditionalOnProperty(prefix = "app.persistence", name = "document-store", havingValue = "postgres")
public class PostgresDocumentStore implements DocumentStore {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<DocumentAsset> assetMapper = (rs, rowNum) -> mapDocumentAsset(rs);
    private final RowMapper<DocumentChunk> chunkMapper = (rs, rowNum) -> mapDocumentChunk(rs);

    public PostgresDocumentStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Long saveDocumentAsset(DocumentAsset asset) {
        String sql = """
                INSERT INTO document_assets (
                    workspace_id, name, original_file_name, stored_path, file_type,
                    mime_type, size_bytes, processing_status, processing_error, chunk_count,
                    created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                RETURNING id
                """;
        return jdbcTemplate.query(sql, ps -> {
            ps.setLong(1, asset.getGroupId());
            ps.setString(2, asset.getName());
            ps.setString(3, asset.getOriginalFileName());
            ps.setString(4, asset.getStoredPath());
            ps.setString(5, asset.getFileType());
            ps.setString(6, asset.getMimeType());
            if (asset.getSizeBytes() != null) {
                ps.setLong(7, asset.getSizeBytes());
            } else {
                ps.setNull(7, Types.BIGINT);
            }
            ps.setString(8, asset.getProcessingStatus());
            ps.setString(9, asset.getProcessingError());
            if (asset.getChunkCount() != null) {
                ps.setInt(10, asset.getChunkCount());
            } else {
                ps.setNull(10, Types.INTEGER);
            }
        }, rs -> rs.next() ? rs.getLong(1) : null);
    }

    @Override
    public boolean updateDocumentAssetProcessing(Long documentId, String processingStatus, String processingError,
            Integer chunkCount) {
        String sql = """
                UPDATE document_assets
                SET processing_status = ?,
                    processing_error = ?,
                    chunk_count = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """;
        return jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement(sql);
            ps.setString(1, processingStatus);
            ps.setString(2, processingError);
            if (chunkCount != null) {
                ps.setInt(3, chunkCount);
            } else {
                ps.setNull(3, Types.INTEGER);
            }
            ps.setLong(4, documentId);
            return ps;
        }) > 0;
    }

    @Override
    public Long saveDocumentChunk(DocumentChunk chunk) {
        String sql = """
                INSERT INTO document_chunks (
                    document_id, chunk_index, chunk_text, metadata_json, created_at
                )
                VALUES (?, ?, ?, CAST(? AS JSONB), CURRENT_TIMESTAMP)
                RETURNING id
                """;
        return jdbcTemplate.query(sql, ps -> {
            ps.setLong(1, chunk.getDocumentId());
            if (chunk.getChunkIndex() != null) {
                ps.setInt(2, chunk.getChunkIndex());
            } else {
                ps.setNull(2, Types.INTEGER);
            }
            ps.setString(3, chunk.getChunkText());
            ps.setString(4, chunk.getMetadataJson());
        }, rs -> rs.next() ? rs.getLong(1) : null);
    }

    @Override
    public DocumentAsset findDocumentAssetById(Long documentId) {
        try {
            return jdbcTemplate.queryForObject("SELECT * FROM document_assets WHERE id = ?", assetMapper, documentId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Override
    public List<DocumentAsset> findDocumentAssetsByGroupId(Long groupId) {
        String sql = """
                SELECT *
                FROM document_assets
                WHERE workspace_id = ?
                ORDER BY created_at DESC, id DESC
                """;
        return jdbcTemplate.query(sql, assetMapper, groupId);
    }

    @Override
    public List<DocumentChunk> findDocumentChunksByDocumentId(Long documentId) {
        String sql = """
                SELECT *
                FROM document_chunks
                WHERE document_id = ?
                ORDER BY chunk_index ASC, id ASC
                """;
        return jdbcTemplate.query(sql, chunkMapper, documentId);
    }

    @Override
    public void deleteDocumentChunksByDocumentId(Long documentId) {
        jdbcTemplate.update("DELETE FROM document_chunks WHERE document_id = ?", documentId);
    }

    @Override
    public void deleteDocumentAssetById(Long documentId) {
        jdbcTemplate.update("DELETE FROM document_assets WHERE id = ?", documentId);
    }

    private DocumentAsset mapDocumentAsset(ResultSet rs) throws SQLException {
        DocumentAsset asset = new DocumentAsset();
        asset.setId(rs.getLong("id"));
        asset.setGroupId(getNullableLong(rs, "workspace_id"));
        asset.setName(rs.getString("name"));
        asset.setOriginalFileName(rs.getString("original_file_name"));
        asset.setStoredPath(rs.getString("stored_path"));
        asset.setFileType(rs.getString("file_type"));
        asset.setMimeType(rs.getString("mime_type"));
        asset.setSizeBytes(getNullableLong(rs, "size_bytes"));
        asset.setProcessingStatus(rs.getString("processing_status"));
        asset.setProcessingError(rs.getString("processing_error"));
        asset.setChunkCount(getNullableInteger(rs, "chunk_count"));
        asset.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        asset.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        return asset;
    }

    private DocumentChunk mapDocumentChunk(ResultSet rs) throws SQLException {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setId(rs.getLong("id"));
        chunk.setDocumentId(getNullableLong(rs, "document_id"));
        chunk.setChunkIndex(getNullableInteger(rs, "chunk_index"));
        chunk.setChunkText(rs.getString("chunk_text"));
        chunk.setMetadataJson(rs.getString("metadata_json"));
        chunk.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        return chunk;
    }

    private java.time.LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp != null ? timestamp.toLocalDateTime() : null;
    }

    private Long getNullableLong(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value instanceof Number number ? number.longValue() : null;
    }

    private Integer getNullableInteger(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value instanceof Number number ? number.intValue() : null;
    }
}
