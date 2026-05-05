package com.analysis.persistence;

import java.sql.PreparedStatement;
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

import com.analysis.model.entity.AnalysisArtifact;
import com.analysis.model.entity.ArtifactMemory;
import com.analysis.model.entity.ContextTrace;

@Repository
@ConditionalOnProperty(prefix = "app.persistence", name = "artifact-store", havingValue = "postgres")
public class PostgresAnalysisArtifactStore implements AnalysisArtifactStore {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<AnalysisArtifact> artifactMapper = (rs, rowNum) -> mapArtifact(rs);
    private final RowMapper<ArtifactMemory> memoryMapper = (rs, rowNum) -> mapMemory(rs);
    private final RowMapper<ContextTrace> traceMapper = (rs, rowNum) -> mapTrace(rs);

    public PostgresAnalysisArtifactStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Long saveAnalysisArtifact(AnalysisArtifact artifact) {
        String sql = """
                INSERT INTO analysis_artifacts (
                    workspace_id, session_id, dataset_id, context_trace_id, user_query, analysis_type,
                    generated_code_or_sql, summary, chart_type, result_preview_json,
                    artifact_schema_version, analysis_report_json, evidence_summary_json,
                    execution_logs_json, validation_report_json, risk_notices_json, artifact_status,
                    archived_at, deleted_at, updated_at, created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB), ?, CAST(? AS JSONB),
                    CAST(? AS JSONB), CAST(? AS JSONB), CAST(? AS JSONB), CAST(? AS JSONB), ?,
                    ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                RETURNING id
                """;
        return jdbcTemplate.query(sql, ps -> {
            setNullableLong(ps, 1, artifact.getGroupId());
            setNullableLong(ps, 2, artifact.getSessionId());
            setNullableLong(ps, 3, artifact.getDatasetId());
            setNullableLong(ps, 4, artifact.getContextTraceId());
            ps.setString(5, artifact.getUserQuery());
            ps.setString(6, artifact.getMode());
            ps.setString(7, artifact.getGeneratedCodeOrSql());
            ps.setString(8, artifact.getSummary());
            ps.setString(9, artifact.getChartType());
            ps.setString(10, jsonOrNull(artifact.getResultPreviewJson()));
            if (artifact.getArtifactSchemaVersion() != null) {
                ps.setInt(11, artifact.getArtifactSchemaVersion());
            } else {
                ps.setNull(11, Types.INTEGER);
            }
            ps.setString(12, jsonOrNull(artifact.getAnalysisReportJson()));
            ps.setString(13, jsonOrNull(artifact.getEvidenceSummaryJson()));
            ps.setString(14, jsonOrNull(artifact.getExecutionLogsJson()));
            ps.setString(15, jsonOrNull(artifact.getValidationReportJson()));
            ps.setString(16, jsonOrNull(artifact.getRiskNoticesJson()));
            ps.setString(17, defaultArtifactStatus(artifact.getArtifactStatus()));
            setNullableTimestamp(ps, 18, artifact.getArchivedAt());
            setNullableTimestamp(ps, 19, artifact.getDeletedAt());
        }, rs -> rs.next() ? rs.getLong(1) : null);
    }

    @Override
    public boolean updateAnalysisArtifactStatus(Long id, String status) {
        String normalizedStatus = defaultArtifactStatus(status);
        String sql = switch (normalizedStatus) {
            case "ARCHIVED" -> """
                    UPDATE analysis_artifacts
                    SET artifact_status = 'ARCHIVED',
                        archived_at = CURRENT_TIMESTAMP,
                        deleted_at = NULL,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """;
            case "DELETED" -> """
                    UPDATE analysis_artifacts
                    SET artifact_status = 'DELETED',
                        deleted_at = CURRENT_TIMESTAMP,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """;
            case "ACTIVE" -> """
                    UPDATE analysis_artifacts
                    SET artifact_status = 'ACTIVE',
                        archived_at = NULL,
                        deleted_at = NULL,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """;
            default -> throw new IllegalArgumentException("Unsupported artifact status: " + status);
        };
        return jdbcTemplate.update(sql, id) > 0;
    }

    @Override
    public AnalysisArtifact findAnalysisArtifactById(Long id) {
        try {
            return jdbcTemplate.queryForObject("SELECT * FROM analysis_artifacts WHERE id = ?", artifactMapper, id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Override
    public List<AnalysisArtifact> findRecentArtifactsBySessionId(Long sessionId, int limit, String status) {
        String sql = """
                SELECT *
                FROM analysis_artifacts
                WHERE session_id = ?
                  AND COALESCE(artifact_status, 'ACTIVE') = ?
                ORDER BY created_at DESC, id DESC
                LIMIT ?
                """;
        return jdbcTemplate.query(sql, artifactMapper, sessionId, defaultArtifactStatus(status), Math.max(1, limit));
    }

    @Override
    public List<AnalysisArtifact> findRecentArtifactsByGroupId(Long groupId, int limit, String status) {
        String sql = """
                SELECT *
                FROM analysis_artifacts
                WHERE workspace_id = ?
                  AND COALESCE(artifact_status, 'ACTIVE') = ?
                ORDER BY created_at DESC, id DESC
                LIMIT ?
                """;
        return jdbcTemplate.query(sql, artifactMapper, groupId, defaultArtifactStatus(status), Math.max(1, limit));
    }

    @Override
    public Long saveArtifactMemory(ArtifactMemory memory) {
        String sql = """
                INSERT INTO artifact_memories (
                    artifact_id, workspace_id, dataset_id, memory_type, scope, content,
                    summary, importance, confidence, status, created_at, updated_at, last_used_at, use_count
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?, ?)
                RETURNING id
                """;
        return jdbcTemplate.query(sql, ps -> {
            setNullableLong(ps, 1, memory.getArtifactId());
            setNullableLong(ps, 2, memory.getGroupId());
            setNullableLong(ps, 3, memory.getDatasetId());
            ps.setString(4, memory.getMemoryType());
            ps.setString(5, memory.getScope());
            ps.setString(6, memory.getContent());
            ps.setString(7, memory.getSummary());
            setNullableDouble(ps, 8, memory.getImportance());
            setNullableDouble(ps, 9, memory.getConfidence());
            ps.setString(10, defaultMemoryStatus(memory.getStatus()));
            setNullableTimestamp(ps, 11, memory.getLastUsedAt());
            ps.setLong(12, memory.getUseCount() != null ? memory.getUseCount() : 0L);
        }, rs -> rs.next() ? rs.getLong(1) : null);
    }

    @Override
    public ArtifactMemory findArtifactMemoryById(Long id) {
        try {
            return jdbcTemplate.queryForObject("SELECT * FROM artifact_memories WHERE id = ?", memoryMapper, id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Override
    public List<ArtifactMemory> findArtifactMemoriesByArtifactId(Long artifactId) {
        String sql = """
                SELECT *
                FROM artifact_memories
                WHERE artifact_id = ?
                  AND COALESCE(status, 'ACTIVE') = 'ACTIVE'
                ORDER BY importance DESC NULLS LAST, created_at DESC, id DESC
                """;
        return jdbcTemplate.query(sql, memoryMapper, artifactId);
    }

    @Override
    public List<ArtifactMemory> findActiveArtifactMemoriesByGroupId(Long groupId, int limit) {
        String sql = """
                SELECT *
                FROM artifact_memories
                WHERE workspace_id = ?
                  AND COALESCE(status, 'ACTIVE') = 'ACTIVE'
                ORDER BY importance DESC NULLS LAST, updated_at DESC, id DESC
                LIMIT ?
                """;
        return jdbcTemplate.query(sql, memoryMapper, groupId, Math.max(1, limit));
    }

    @Override
    public List<ArtifactMemory> findRecentArtifactMemoriesByGroupId(Long groupId, int limit, String status) {
        String sql = """
                SELECT *
                FROM artifact_memories
                WHERE workspace_id = ?
                  AND COALESCE(status, 'ACTIVE') = ?
                ORDER BY last_used_at DESC NULLS LAST, importance DESC NULLS LAST, updated_at DESC, id DESC
                LIMIT ?
                """;
        return jdbcTemplate.query(sql, memoryMapper, groupId, defaultMemoryStatus(status), Math.max(1, limit));
    }

    @Override
    public int recordArtifactMemoryUsage(List<Long> memoryIds) {
        if (memoryIds == null || memoryIds.isEmpty()) {
            return 0;
        }
        String sql = """
                UPDATE artifact_memories
                SET use_count = COALESCE(use_count, 0) + 1,
                    last_used_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND COALESCE(status, 'ACTIVE') = 'ACTIVE'
                """;
        int updated = 0;
        for (Long id : memoryIds.stream().filter(value -> value != null).distinct().toList()) {
            updated += jdbcTemplate.update(sql, id);
        }
        return updated;
    }

    @Override
    public boolean updateArtifactMemoryStatus(Long id, String status) {
        String sql = """
                UPDATE artifact_memories
                SET status = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """;
        return jdbcTemplate.update(sql, defaultMemoryStatus(status), id) > 0;
    }

    @Override
    public boolean updateArtifactMemoryImportance(Long id, Double importance) {
        String sql = """
                UPDATE artifact_memories
                SET importance = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """;
        return jdbcTemplate.update(sql, importance, id) > 0;
    }

    @Override
    public Long saveContextTrace(ContextTrace trace) {
        String sql = """
                INSERT INTO context_traces (
                    workspace_id, session_id, query, selected_schema_ids,
                    selected_document_chunk_ids, selected_memory_ids,
                    filtered_items_json, packed_context, created_at
                )
                VALUES (?, ?, ?, CAST(? AS JSONB), CAST(? AS JSONB), CAST(? AS JSONB),
                    CAST(? AS JSONB), ?, CURRENT_TIMESTAMP)
                RETURNING id
                """;
        return jdbcTemplate.query(sql, ps -> {
            setNullableLong(ps, 1, trace.getGroupId());
            setNullableLong(ps, 2, trace.getSessionId());
            ps.setString(3, trace.getQuery());
            ps.setString(4, jsonOrArray(trace.getSelectedSchemaIdsJson()));
            ps.setString(5, jsonOrArray(trace.getSelectedDocumentChunkIdsJson()));
            ps.setString(6, jsonOrArray(trace.getSelectedMemoryIdsJson()));
            ps.setString(7, jsonOrObject(trace.getFilteredItemsJson()));
            ps.setString(8, trace.getPackedContext());
        }, rs -> rs.next() ? rs.getLong(1) : null);
    }

    @Override
    public ContextTrace findContextTraceById(Long id) {
        try {
            return jdbcTemplate.queryForObject("SELECT * FROM context_traces WHERE id = ?", traceMapper, id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Override
    public List<ContextTrace> findRecentContextTracesByGroupId(Long groupId, int limit) {
        String sql = """
                SELECT *
                FROM context_traces
                WHERE workspace_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT ?
                """;
        return jdbcTemplate.query(sql, traceMapper, groupId, Math.max(1, limit));
    }

    private AnalysisArtifact mapArtifact(ResultSet rs) throws SQLException {
        AnalysisArtifact artifact = new AnalysisArtifact();
        artifact.setId(rs.getLong("id"));
        artifact.setMode(rs.getString("analysis_type"));
        artifact.setSessionId(getNullableLong(rs, "session_id"));
        artifact.setGroupId(getNullableLong(rs, "workspace_id"));
        artifact.setDatasetId(getNullableLong(rs, "dataset_id"));
        artifact.setContextTraceId(getNullableLong(rs, "context_trace_id"));
        artifact.setUserQuery(rs.getString("user_query"));
        artifact.setGeneratedCodeOrSql(rs.getString("generated_code_or_sql"));
        artifact.setSummary(rs.getString("summary"));
        artifact.setChartType(rs.getString("chart_type"));
        artifact.setResultPreviewJson(rs.getString("result_preview_json"));
        artifact.setArtifactSchemaVersion(getNullableInteger(rs, "artifact_schema_version"));
        artifact.setAnalysisReportJson(rs.getString("analysis_report_json"));
        artifact.setEvidenceSummaryJson(rs.getString("evidence_summary_json"));
        artifact.setExecutionLogsJson(rs.getString("execution_logs_json"));
        artifact.setValidationReportJson(rs.getString("validation_report_json"));
        artifact.setRiskNoticesJson(rs.getString("risk_notices_json"));
        artifact.setArtifactStatus(defaultArtifactStatus(rs.getString("artifact_status")));
        artifact.setArchivedAt(toLocalDateTime(rs.getTimestamp("archived_at")));
        artifact.setDeletedAt(toLocalDateTime(rs.getTimestamp("deleted_at")));
        artifact.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        artifact.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        return artifact;
    }

    private ArtifactMemory mapMemory(ResultSet rs) throws SQLException {
        ArtifactMemory memory = new ArtifactMemory();
        memory.setId(rs.getLong("id"));
        memory.setArtifactId(getNullableLong(rs, "artifact_id"));
        memory.setGroupId(getNullableLong(rs, "workspace_id"));
        memory.setDatasetId(getNullableLong(rs, "dataset_id"));
        memory.setMemoryType(rs.getString("memory_type"));
        memory.setScope(rs.getString("scope"));
        memory.setContent(rs.getString("content"));
        memory.setSummary(rs.getString("summary"));
        memory.setImportance(getNullableDouble(rs, "importance"));
        memory.setConfidence(getNullableDouble(rs, "confidence"));
        memory.setStatus(defaultMemoryStatus(rs.getString("status")));
        memory.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        memory.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        memory.setLastUsedAt(toLocalDateTime(rs.getTimestamp("last_used_at")));
        memory.setUseCount(getNullableLong(rs, "use_count"));
        return memory;
    }

    private ContextTrace mapTrace(ResultSet rs) throws SQLException {
        ContextTrace trace = new ContextTrace();
        trace.setId(rs.getLong("id"));
        trace.setGroupId(getNullableLong(rs, "workspace_id"));
        trace.setSessionId(getNullableLong(rs, "session_id"));
        trace.setQuery(rs.getString("query"));
        trace.setSelectedSchemaIdsJson(rs.getString("selected_schema_ids"));
        trace.setSelectedDocumentChunkIdsJson(rs.getString("selected_document_chunk_ids"));
        trace.setSelectedMemoryIdsJson(rs.getString("selected_memory_ids"));
        trace.setFilteredItemsJson(rs.getString("filtered_items_json"));
        trace.setPackedContext(rs.getString("packed_context"));
        trace.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        return trace;
    }

    private void setNullableLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value != null) {
            ps.setLong(index, value);
        } else {
            ps.setNull(index, Types.BIGINT);
        }
    }

    private void setNullableDouble(PreparedStatement ps, int index, Double value) throws SQLException {
        if (value != null) {
            ps.setDouble(index, value);
        } else {
            ps.setNull(index, Types.DOUBLE);
        }
    }

    private void setNullableTimestamp(PreparedStatement ps, int index, java.time.LocalDateTime value)
            throws SQLException {
        if (value != null) {
            ps.setTimestamp(index, Timestamp.valueOf(value));
        } else {
            ps.setNull(index, Types.TIMESTAMP);
        }
    }

    private String jsonOrNull(String value) {
        return value != null && !value.isBlank() ? value : null;
    }

    private String jsonOrArray(String value) {
        return value != null && !value.isBlank() ? value : "[]";
    }

    private String jsonOrObject(String value) {
        return value != null && !value.isBlank() ? value : "{}";
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

    private Double getNullableDouble(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private String defaultArtifactStatus(String status) {
        if (status == null || status.isBlank()) {
            return "ACTIVE";
        }
        String normalizedStatus = status.trim().toUpperCase();
        return switch (normalizedStatus) {
            case "ACTIVE", "ARCHIVED", "DELETED" -> normalizedStatus;
            default -> "ACTIVE";
        };
    }

    private String defaultMemoryStatus(String status) {
        if (status == null || status.isBlank()) {
            return "ACTIVE";
        }
        String normalizedStatus = status.trim().toUpperCase();
        return switch (normalizedStatus) {
            case "ACTIVE", "ARCHIVED", "SUPERSEDED", "DELETED" -> normalizedStatus;
            default -> "ACTIVE";
        };
    }
}
