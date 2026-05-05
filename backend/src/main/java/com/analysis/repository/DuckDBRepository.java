package com.analysis.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import com.analysis.model.entity.ColumnMetadata;
import com.analysis.model.entity.Dataset;
import com.analysis.model.entity.DatasetGroup;
import com.analysis.model.entity.DatasetRelation;
import com.analysis.model.entity.DocumentAsset;
import com.analysis.model.entity.DocumentChunk;
import com.analysis.model.entity.AnalysisArtifact;
import com.analysis.model.entity.ChatMessage;
import com.analysis.model.entity.ChatSession;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Repository
//负责调用duckdb，
public class DuckDBRepository {

    private final DataSource dataSource;

    public DuckDBRepository(@Qualifier("duckdbDataSource") DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void initSchema() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement()) {
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS meta_dataset_groups (
                            id INTEGER PRIMARY KEY,
                            name VARCHAR,
                            description VARCHAR,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        )
                    """);
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS meta_datasets (
                            id INTEGER PRIMARY KEY,
                            group_id INTEGER,
                            name VARCHAR,
                            description_md VARCHAR,
                            table_name VARCHAR UNIQUE,
                            original_file_name VARCHAR,
                            row_count BIGINT,
                            column_count INTEGER,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        )
                    """);
            ensureMetaDatasetsGroupIdColumn(connection, stmt);
            ensureMetaDatasetsDescriptionColumn(connection, stmt);
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS meta_dataset_relations (
                            id INTEGER PRIMARY KEY,
                            group_id INTEGER,
                            source_dataset_id INTEGER,
                            source_table_name VARCHAR,
                            source_column_name VARCHAR,
                            target_dataset_id INTEGER,
                            target_table_name VARCHAR,
                            target_column_name VARCHAR,
                            relation_type VARCHAR,
                            confidence DOUBLE,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        )
                    """);
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS meta_columns (
                            id INTEGER PRIMARY KEY,
                            dataset_id INTEGER,
                            column_name VARCHAR,
                            data_type VARCHAR,
                            nullable BOOLEAN,
                            distinct_count BIGINT,
                            min_value VARCHAR,
                            max_value VARCHAR,
                            sample_values VARCHAR
                        )
                    """);
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS analysis_history (
                            id INTEGER PRIMARY KEY,
                            dataset_id INTEGER,
                            user_query VARCHAR,
                            generated_sql VARCHAR,
                            generated_code VARCHAR,
                            result_data VARCHAR,
                            chart_type VARCHAR,
                            summary VARCHAR,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        )
                    """);
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS meta_chat_sessions (
                            id INTEGER PRIMARY KEY,
                            title VARCHAR,
                            status VARCHAR,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            expires_at TIMESTAMP
                        )
                    """);
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS meta_chat_messages (
                            id INTEGER PRIMARY KEY,
                            session_id INTEGER,
                            role VARCHAR,
                            content VARCHAR,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        )
                    """);
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS meta_chat_session_datasets (
                            id INTEGER PRIMARY KEY,
                            session_id INTEGER,
                            dataset_id INTEGER,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        )
                    """);
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS analysis_artifacts (
                            id INTEGER PRIMARY KEY,
                            mode VARCHAR,
                            session_id INTEGER,
                            group_id INTEGER,
                            dataset_id INTEGER,
                            user_query VARCHAR,
                            generated_code_or_sql VARCHAR,
                            summary VARCHAR,
                            chart_type VARCHAR,
                            result_preview_json VARCHAR,
                            artifact_schema_version INTEGER,
                            analysis_report_json VARCHAR,
                            evidence_summary_json VARCHAR,
                            execution_logs_json VARCHAR,
                            validation_report_json VARCHAR,
                            risk_notices_json VARCHAR,
                            artifact_status VARCHAR DEFAULT 'ACTIVE',
                            archived_at TIMESTAMP,
                            deleted_at TIMESTAMP,
                            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        )
                    """);
            ensureAnalysisArtifactPhase6Columns(connection, stmt);
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS meta_documents (
                            id INTEGER PRIMARY KEY,
                            group_id INTEGER,
                            name VARCHAR,
                            original_file_name VARCHAR,
                            stored_path VARCHAR,
                            file_type VARCHAR,
                            mime_type VARCHAR,
                            size_bytes BIGINT,
                            processing_status VARCHAR,
                            processing_error VARCHAR,
                            chunk_count INTEGER,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        )
                    """);
            ensureMetaDocumentsChunkCountColumn(connection, stmt);
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS meta_document_chunks (
                            id INTEGER PRIMARY KEY,
                            document_id INTEGER,
                            chunk_index INTEGER,
                            chunk_text VARCHAR,
                            metadata_json VARCHAR,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        )
                    """);

            stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_dataset_group START 1");
            stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_dataset START 1");
            stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_dataset_relation START 1");
            stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_column START 1");
            stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_history START 1");
            stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_chat_session START 1");
            stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_chat_message START 1");
            stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_chat_session_dataset START 1");
            stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_analysis_artifact START 1");
            stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_document_asset START 1");
            stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_document_chunk START 1");
        }
    }

    /**
     * 兼容旧库：若 meta_datasets 早期未包含 group_id，则补列迁移。
     */
    private void ensureMetaDatasetsGroupIdColumn(Connection connection, Statement stmt) throws SQLException {
        String checkSql = """
                SELECT 1
                FROM information_schema.columns
                WHERE table_name = 'meta_datasets' AND column_name = 'group_id'
                LIMIT 1
                """;
        try (PreparedStatement ps = connection.prepareStatement(checkSql);
                ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                stmt.execute("ALTER TABLE meta_datasets ADD COLUMN group_id INTEGER");
                log.info("Schema migration applied: meta_datasets.group_id added");
            }
        }
    }

    /**
     * 兼容旧库：若 meta_datasets 早期未包含 description_md，则补列迁移。
     */
    private void ensureMetaDatasetsDescriptionColumn(Connection connection, Statement stmt) throws SQLException {
        String checkSql = """
                SELECT 1
                FROM information_schema.columns
                WHERE table_name = 'meta_datasets' AND column_name = 'description_md'
                LIMIT 1
                """;
        try (PreparedStatement ps = connection.prepareStatement(checkSql);
                ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                stmt.execute("ALTER TABLE meta_datasets ADD COLUMN description_md VARCHAR");
                log.info("Schema migration applied: meta_datasets.description_md added");
            }
        }
    }

    private void ensureMetaDocumentsChunkCountColumn(Connection connection, Statement stmt) throws SQLException {
        String checkSql = """
                SELECT 1
                FROM information_schema.columns
                WHERE table_name = 'meta_documents' AND column_name = 'chunk_count'
                LIMIT 1
                """;
        try (PreparedStatement ps = connection.prepareStatement(checkSql);
                ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                stmt.execute("ALTER TABLE meta_documents ADD COLUMN chunk_count INTEGER");
                log.info("Schema migration applied: meta_documents.chunk_count added");
            }
        }
    }

    private void ensureAnalysisArtifactPhase6Columns(Connection connection, Statement stmt) throws SQLException {
        ensureColumn(connection, stmt, "analysis_artifacts", "artifact_schema_version", "INTEGER");
        ensureColumn(connection, stmt, "analysis_artifacts", "analysis_report_json", "VARCHAR");
        ensureColumn(connection, stmt, "analysis_artifacts", "evidence_summary_json", "VARCHAR");
        ensureColumn(connection, stmt, "analysis_artifacts", "execution_logs_json", "VARCHAR");
        ensureColumn(connection, stmt, "analysis_artifacts", "validation_report_json", "VARCHAR");
        ensureColumn(connection, stmt, "analysis_artifacts", "risk_notices_json", "VARCHAR");
        ensureColumn(connection, stmt, "analysis_artifacts", "artifact_status", "VARCHAR DEFAULT 'ACTIVE'");
        ensureColumn(connection, stmt, "analysis_artifacts", "archived_at", "TIMESTAMP");
        ensureColumn(connection, stmt, "analysis_artifacts", "deleted_at", "TIMESTAMP");
        ensureColumn(connection, stmt, "analysis_artifacts", "updated_at", "TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
    }

    private void ensureColumn(
            Connection connection,
            Statement stmt,
            String tableName,
            String columnName,
            String columnType) throws SQLException {
        String checkSql = """
                SELECT 1
                FROM information_schema.columns
                WHERE table_name = ? AND column_name = ?
                LIMIT 1
                """;
        try (PreparedStatement ps = connection.prepareStatement(checkSql)) {
            ps.setString(1, tableName);
            ps.setString(2, columnName);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    stmt.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnType);
                    log.info("Schema migration applied: {}.{} added", tableName, columnName);
                }
            }
        }
    }

    public Long saveDataset(Dataset dataset) throws SQLException {
        String sql = """
                    INSERT INTO meta_datasets (id, group_id, name, description_md, table_name, original_file_name, row_count, column_count, created_at, updated_at)
                    VALUES (nextval('seq_dataset'), ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    RETURNING id
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            if (dataset.getGroupId() != null) {
                stmt.setLong(1, dataset.getGroupId());
            } else {
                stmt.setNull(1, Types.BIGINT);
            }
            stmt.setString(2, dataset.getName());
            stmt.setString(3, dataset.getDescriptionMd());
            stmt.setString(4, dataset.getTableName());
            stmt.setString(5, dataset.getOriginalFileName());
            stmt.setLong(6, dataset.getRowCount() != null ? dataset.getRowCount() : 0);
            stmt.setInt(7, dataset.getColumnCount() != null ? dataset.getColumnCount() : 0);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return null;
    }

    public void saveColumnMetadata(ColumnMetadata metadata) throws SQLException {
        String sql = """
                    INSERT INTO meta_columns (id, dataset_id, column_name, data_type, nullable, distinct_count, min_value, max_value, sample_values)
                    VALUES (nextval('seq_column'), ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, metadata.getDatasetId());
            stmt.setString(2, metadata.getColumnName());
            stmt.setString(3, metadata.getDataType());
            stmt.setBoolean(4, metadata.getNullable() != null && metadata.getNullable());
            stmt.setLong(5, metadata.getDistinctCount() != null ? metadata.getDistinctCount() : 0);
            stmt.setString(6, metadata.getMinValue());
            stmt.setString(7, metadata.getMaxValue());
            stmt.setString(8, metadata.getSampleValues());
            stmt.executeUpdate();
        }
    }

    public Dataset findDatasetById(Long id) throws SQLException {
        String sql = "SELECT * FROM meta_datasets WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapDataset(rs);
                }
            }
        }
        return null;
    }

    public List<Dataset> findAllDatasets() throws SQLException {
        List<Dataset> datasets = new ArrayList<>();
        String sql = "SELECT * FROM meta_datasets ORDER BY created_at DESC";
        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                datasets.add(mapDataset(rs));
            }
        }
        return datasets;
    }

    public List<Dataset> findAllDatasets(Long groupId) throws SQLException {
        if (groupId == null) {
            return findAllDatasets();
        }
        return findDatasetsByGroupId(groupId);
    }

    public Long saveDatasetGroup(DatasetGroup group) throws SQLException {
        String sql = """
                INSERT INTO meta_dataset_groups (id, name, description, created_at, updated_at)
                VALUES (nextval('seq_dataset_group'), ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                RETURNING id
                """;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, group.getName());
            stmt.setString(2, group.getDescription());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return null;
    }

    public DatasetGroup findDatasetGroupById(Long id) throws SQLException {
        String sql = "SELECT * FROM meta_dataset_groups WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapDatasetGroup(rs);
                }
            }
        }
        return null;
    }

    public List<DatasetGroup> findAllDatasetGroups() throws SQLException {
        List<DatasetGroup> groups = new ArrayList<>();
        String sql = "SELECT * FROM meta_dataset_groups ORDER BY created_at DESC";
        try (Connection connection = dataSource.getConnection();
                Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                groups.add(mapDatasetGroup(rs));
            }
        }
        return groups;
    }

    public List<Dataset> findDatasetsByGroupId(Long groupId) throws SQLException {
        List<Dataset> datasets = new ArrayList<>();
        String sql = "SELECT * FROM meta_datasets WHERE group_id = ? ORDER BY created_at DESC";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, groupId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    datasets.add(mapDataset(rs));
                }
            }
        }
        return datasets;
    }

    public void deleteDatasetGroup(Long id) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement stmt = connection
                        .prepareStatement("DELETE FROM meta_dataset_relations WHERE group_id = ?")) {
                    stmt.setLong(1, id);
                    stmt.executeUpdate();
                }
                try (PreparedStatement stmt = connection
                        .prepareStatement("UPDATE meta_datasets SET group_id = NULL WHERE group_id = ?")) {
                    stmt.setLong(1, id);
                    stmt.executeUpdate();
                }
                try (PreparedStatement stmt = connection
                        .prepareStatement("DELETE FROM meta_dataset_groups WHERE id = ?")) {
                    stmt.setLong(1, id);
                    stmt.executeUpdate();
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public Long saveDatasetRelation(DatasetRelation relation) throws SQLException {
        String sql = """
                INSERT INTO meta_dataset_relations (
                    id, group_id, source_dataset_id, source_table_name, source_column_name,
                    target_dataset_id, target_table_name, target_column_name, relation_type,
                    confidence, created_at, updated_at
                )
                VALUES (nextval('seq_dataset_relation'), ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                RETURNING id
                """;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, relation.getGroupId());
            stmt.setLong(2, relation.getSourceDatasetId());
            stmt.setString(3, relation.getSourceTableName());
            stmt.setString(4, relation.getSourceColumnName());
            stmt.setLong(5, relation.getTargetDatasetId());
            stmt.setString(6, relation.getTargetTableName());
            stmt.setString(7, relation.getTargetColumnName());
            stmt.setString(8, relation.getRelationType());
            if (relation.getConfidence() != null) {
                stmt.setDouble(9, relation.getConfidence());
            } else {
                stmt.setNull(9, Types.DOUBLE);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return null;
    }

    public List<DatasetRelation> findRelationsByGroupId(Long groupId) throws SQLException {
        List<DatasetRelation> relations = new ArrayList<>();
        String sql = "SELECT * FROM meta_dataset_relations WHERE group_id = ? ORDER BY created_at DESC";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, groupId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    relations.add(mapDatasetRelation(rs));
                }
            }
        }
        return relations;
    }

    public DatasetRelation findDatasetRelationById(Long relationId) throws SQLException {
        String sql = "SELECT * FROM meta_dataset_relations WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, relationId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapDatasetRelation(rs);
                }
            }
        }
        return null;
    }

    public boolean updateDatasetRelation(DatasetRelation relation) throws SQLException {
        String sql = """
                UPDATE meta_dataset_relations
                SET group_id = ?,
                    source_dataset_id = ?,
                    source_table_name = ?,
                    source_column_name = ?,
                    target_dataset_id = ?,
                    target_table_name = ?,
                    target_column_name = ?,
                    relation_type = ?,
                    confidence = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, relation.getGroupId());
            stmt.setLong(2, relation.getSourceDatasetId());
            stmt.setString(3, relation.getSourceTableName());
            stmt.setString(4, relation.getSourceColumnName());
            stmt.setLong(5, relation.getTargetDatasetId());
            stmt.setString(6, relation.getTargetTableName());
            stmt.setString(7, relation.getTargetColumnName());
            stmt.setString(8, relation.getRelationType());
            if (relation.getConfidence() != null) {
                stmt.setDouble(9, relation.getConfidence());
            } else {
                stmt.setNull(9, Types.DOUBLE);
            }
            stmt.setLong(10, relation.getId());
            return stmt.executeUpdate() > 0;
        }
    }

    public void deleteRelationsByGroupId(Long groupId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement stmt = connection
                        .prepareStatement("DELETE FROM meta_dataset_relations WHERE group_id = ?")) {
            stmt.setLong(1, groupId);
            stmt.executeUpdate();
        }
    }

    public void deleteRelationById(Long relationId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement stmt = connection
                        .prepareStatement("DELETE FROM meta_dataset_relations WHERE id = ?")) {
            stmt.setLong(1, relationId);
            stmt.executeUpdate();
        }
    }

    public boolean updateDatasetDescription(Long datasetId, String descriptionMd) throws SQLException {
        String sql = "UPDATE meta_datasets SET description_md = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, descriptionMd);
            stmt.setLong(2, datasetId);
            return stmt.executeUpdate() > 0;
        }
    }

    public boolean updateDatasetGroupDescription(Long groupId, String descriptionMd) throws SQLException {
        String sql = "UPDATE meta_dataset_groups SET description = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, descriptionMd);
            stmt.setLong(2, groupId);
            return stmt.executeUpdate() > 0;
        }
    }

    public boolean updateDatasetGroupId(Long datasetId, Long groupId) throws SQLException {
        String sql = "UPDATE meta_datasets SET group_id = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {
            if (groupId != null) {
                stmt.setLong(1, groupId);
            } else {
                stmt.setNull(1, Types.BIGINT);
            }
            stmt.setLong(2, datasetId);
            return stmt.executeUpdate() > 0;
        }
    }

    public Long saveChatSession(ChatSession session) throws SQLException {
        String sql = """
                INSERT INTO meta_chat_sessions (id, title, status, created_at, updated_at, expires_at)
                VALUES (nextval('seq_chat_session'), ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?)
                RETURNING id
                """;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, session.getTitle());
            stmt.setString(2, session.getStatus());
            if (session.getExpiresAt() != null) {
                stmt.setTimestamp(3, Timestamp.valueOf(session.getExpiresAt()));
            } else {
                stmt.setNull(3, Types.TIMESTAMP);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return null;
    }

    public ChatSession findChatSessionById(Long sessionId) throws SQLException {
        String sql = "SELECT * FROM meta_chat_sessions WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, sessionId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapChatSession(rs);
                }
            }
        }
        return null;
    }

    public List<ChatSession> findAllChatSessions() throws SQLException {
        List<ChatSession> sessions = new ArrayList<>();
        String sql = "SELECT * FROM meta_chat_sessions WHERE status = 'ACTIVE' ORDER BY updated_at DESC";
        try (Connection connection = dataSource.getConnection();
                Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                sessions.add(mapChatSession(rs));
            }
        }
        return sessions;
    }

    public boolean updateChatSessionTitle(Long sessionId, String title) throws SQLException {
        String sql = "UPDATE meta_chat_sessions SET title = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND status = 'ACTIVE'";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, title);
            stmt.setLong(2, sessionId);
            return stmt.executeUpdate() > 0;
        }
    }

    public boolean deleteChatSession(Long sessionId) throws SQLException {
        String sql = "UPDATE meta_chat_sessions SET status = 'DELETED', updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, sessionId);
            return stmt.executeUpdate() > 0;
        }
    }

    public boolean touchChatSession(Long sessionId) throws SQLException {
        String sql = "UPDATE meta_chat_sessions SET updated_at = CURRENT_TIMESTAMP WHERE id = ? AND status = 'ACTIVE'";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, sessionId);
            return stmt.executeUpdate() > 0;
        }
    }

    public Long saveChatMessage(ChatMessage message) throws SQLException {
        String sql = """
                INSERT INTO meta_chat_messages (id, session_id, role, content, created_at)
                VALUES (nextval('seq_chat_message'), ?, ?, ?, CURRENT_TIMESTAMP)
                RETURNING id
                """;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, message.getSessionId());
            stmt.setString(2, message.getRole());
            stmt.setString(3, message.getContent());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return null;
    }

    public List<ChatMessage> findChatMessagesBySessionId(Long sessionId) throws SQLException {
        List<ChatMessage> messages = new ArrayList<>();
        String sql = "SELECT * FROM meta_chat_messages WHERE session_id = ? ORDER BY created_at ASC, id ASC";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, sessionId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    messages.add(mapChatMessage(rs));
                }
            }
        }
        return messages;
    }

    public Long bindDatasetToChatSession(Long sessionId, Long datasetId) throws SQLException {
        String sql = """
                INSERT INTO meta_chat_session_datasets (id, session_id, dataset_id, created_at)
                VALUES (nextval('seq_chat_session_dataset'), ?, ?, CURRENT_TIMESTAMP)
                RETURNING id
                """;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, sessionId);
            stmt.setLong(2, datasetId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return null;
    }

    public List<Dataset> findDatasetsByChatSessionId(Long sessionId) throws SQLException {
        List<Dataset> datasets = new ArrayList<>();
        String sql = """
                SELECT d.*
                FROM meta_chat_session_datasets c
                JOIN meta_datasets d ON d.id = c.dataset_id
                WHERE c.session_id = ?
                ORDER BY c.created_at DESC, c.id DESC
                """;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, sessionId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    datasets.add(mapDataset(rs));
                }
            }
        }
        return datasets;
    }

    public Long saveAnalysisArtifact(AnalysisArtifact artifact) throws SQLException {
        String sql = """
                INSERT INTO analysis_artifacts (
                    id, mode, session_id, group_id, dataset_id, user_query,
                    generated_code_or_sql, summary, chart_type, result_preview_json,
                    artifact_schema_version, analysis_report_json, evidence_summary_json,
                    execution_logs_json, validation_report_json, risk_notices_json,
                    artifact_status, archived_at, deleted_at, updated_at, created_at
                )
                VALUES (nextval('seq_analysis_artifact'), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                RETURNING id
                """;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, artifact.getMode());
            if (artifact.getSessionId() != null) {
                stmt.setLong(2, artifact.getSessionId());
            } else {
                stmt.setNull(2, Types.BIGINT);
            }
            if (artifact.getGroupId() != null) {
                stmt.setLong(3, artifact.getGroupId());
            } else {
                stmt.setNull(3, Types.BIGINT);
            }
            if (artifact.getDatasetId() != null) {
                stmt.setLong(4, artifact.getDatasetId());
            } else {
                stmt.setNull(4, Types.BIGINT);
            }
            stmt.setString(5, artifact.getUserQuery());
            stmt.setString(6, artifact.getGeneratedCodeOrSql());
            stmt.setString(7, artifact.getSummary());
            stmt.setString(8, artifact.getChartType());
            stmt.setString(9, artifact.getResultPreviewJson());
            if (artifact.getArtifactSchemaVersion() != null) {
                stmt.setInt(10, artifact.getArtifactSchemaVersion());
            } else {
                stmt.setNull(10, Types.INTEGER);
            }
            stmt.setString(11, artifact.getAnalysisReportJson());
            stmt.setString(12, artifact.getEvidenceSummaryJson());
            stmt.setString(13, artifact.getExecutionLogsJson());
            stmt.setString(14, artifact.getValidationReportJson());
            stmt.setString(15, artifact.getRiskNoticesJson());
            stmt.setString(16, defaultArtifactStatus(artifact.getArtifactStatus()));
            if (artifact.getArchivedAt() != null) {
                stmt.setTimestamp(17, Timestamp.valueOf(artifact.getArchivedAt()));
            } else {
                stmt.setNull(17, Types.TIMESTAMP);
            }
            if (artifact.getDeletedAt() != null) {
                stmt.setTimestamp(18, Timestamp.valueOf(artifact.getDeletedAt()));
            } else {
                stmt.setNull(18, Types.TIMESTAMP);
            }
            if (artifact.getUpdatedAt() != null) {
                stmt.setTimestamp(19, Timestamp.valueOf(artifact.getUpdatedAt()));
            } else {
                stmt.setTimestamp(19, Timestamp.valueOf(java.time.LocalDateTime.now()));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return null;
    }

    public boolean updateAnalysisArtifactStatus(Long id, String status) throws SQLException {
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

        try (Connection connection = dataSource.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, id);
            return stmt.executeUpdate() > 0;
        }
    }

    public AnalysisArtifact findAnalysisArtifactById(Long id) throws SQLException {
        String sql = "SELECT * FROM analysis_artifacts WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapAnalysisArtifact(rs);
                }
            }
        }
        return null;
    }

    public Long saveDocumentAsset(DocumentAsset asset) throws SQLException {
        String sql = """
                INSERT INTO meta_documents (
                    id, group_id, name, original_file_name, stored_path, file_type,
                    mime_type, size_bytes, processing_status, processing_error, chunk_count, created_at, updated_at
                )
                VALUES (nextval('seq_document_asset'), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                RETURNING id
                """;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, asset.getGroupId());
            stmt.setString(2, asset.getName());
            stmt.setString(3, asset.getOriginalFileName());
            stmt.setString(4, asset.getStoredPath());
            stmt.setString(5, asset.getFileType());
            stmt.setString(6, asset.getMimeType());
            if (asset.getSizeBytes() != null) {
                stmt.setLong(7, asset.getSizeBytes());
            } else {
                stmt.setNull(7, Types.BIGINT);
            }
            stmt.setString(8, asset.getProcessingStatus());
            stmt.setString(9, asset.getProcessingError());
            if (asset.getChunkCount() != null) {
                stmt.setInt(10, asset.getChunkCount());
            } else {
                stmt.setNull(10, Types.INTEGER);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return null;
    }

    public boolean updateDocumentAssetProcessing(Long documentId, String processingStatus, String processingError,
            Integer chunkCount) throws SQLException {
        String sql = """
                UPDATE meta_documents
                SET processing_status = ?,
                    processing_error = ?,
                    chunk_count = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, processingStatus);
            stmt.setString(2, processingError);
            if (chunkCount != null) {
                stmt.setInt(3, chunkCount);
            } else {
                stmt.setNull(3, Types.INTEGER);
            }
            stmt.setLong(4, documentId);
            return stmt.executeUpdate() > 0;
        }
    }

    public Long saveDocumentChunk(DocumentChunk chunk) throws SQLException {
        String sql = """
                INSERT INTO meta_document_chunks (
                    id, document_id, chunk_index, chunk_text, metadata_json, created_at
                )
                VALUES (nextval('seq_document_chunk'), ?, ?, ?, ?, CURRENT_TIMESTAMP)
                RETURNING id
                """;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, chunk.getDocumentId());
            stmt.setInt(2, chunk.getChunkIndex());
            stmt.setString(3, chunk.getChunkText());
            stmt.setString(4, chunk.getMetadataJson());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return null;
    }

    public DocumentAsset findDocumentAssetById(Long documentId) throws SQLException {
        String sql = "SELECT * FROM meta_documents WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, documentId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapDocumentAsset(rs);
                }
            }
        }
        return null;
    }

    public List<DocumentAsset> findDocumentAssetsByGroupId(Long groupId) throws SQLException {
        List<DocumentAsset> documents = new ArrayList<>();
        String sql = "SELECT * FROM meta_documents WHERE group_id = ? ORDER BY created_at DESC, id DESC";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, groupId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    documents.add(mapDocumentAsset(rs));
                }
            }
        }
        return documents;
    }

    public List<DocumentChunk> findDocumentChunksByDocumentId(Long documentId) throws SQLException {
        List<DocumentChunk> chunks = new ArrayList<>();
        String sql = "SELECT * FROM meta_document_chunks WHERE document_id = ? ORDER BY chunk_index ASC, id ASC";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, documentId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    chunks.add(mapDocumentChunk(rs));
                }
            }
        }
        return chunks;
    }

    public void deleteDocumentChunksByDocumentId(Long documentId) throws SQLException {
        String sql = "DELETE FROM meta_document_chunks WHERE document_id = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, documentId);
            stmt.executeUpdate();
        }
    }

    public void deleteDocumentAssetById(Long documentId) throws SQLException {
        String sql = "DELETE FROM meta_documents WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, documentId);
            stmt.executeUpdate();
        }
    }

    public List<AnalysisArtifact> findRecentArtifactsBySessionId(Long sessionId, int limit) throws SQLException {
        return findRecentArtifactsBySessionId(sessionId, limit, "ACTIVE");
    }

    public List<AnalysisArtifact> findRecentArtifactsBySessionId(Long sessionId, int limit, String status) throws SQLException {
        List<AnalysisArtifact> artifacts = new ArrayList<>();
        String sql = """
                SELECT *
                FROM analysis_artifacts
                WHERE session_id = ?
                  AND COALESCE(artifact_status, 'ACTIVE') = ?
                ORDER BY created_at DESC, id DESC
                LIMIT ?
                """;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, sessionId);
            stmt.setString(2, defaultArtifactStatus(status));
            stmt.setInt(3, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    artifacts.add(mapAnalysisArtifact(rs));
                }
            }
        }
        return artifacts;
    }

    public List<AnalysisArtifact> findRecentArtifactsByGroupId(Long groupId, int limit) throws SQLException {
        return findRecentArtifactsByGroupId(groupId, limit, "ACTIVE");
    }

    public List<AnalysisArtifact> findRecentArtifactsByGroupId(Long groupId, int limit, String status) throws SQLException {
        List<AnalysisArtifact> artifacts = new ArrayList<>();
        String sql = """
                SELECT *
                FROM analysis_artifacts
                WHERE group_id = ?
                  AND COALESCE(artifact_status, 'ACTIVE') = ?
                ORDER BY created_at DESC, id DESC
                LIMIT ?
                """;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, groupId);
            stmt.setString(2, defaultArtifactStatus(status));
            stmt.setInt(3, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    artifacts.add(mapAnalysisArtifact(rs));
                }
            }
        }
        return artifacts;
    }

    public List<ColumnMetadata> findColumnsByDatasetId(Long datasetId) throws SQLException {
        List<ColumnMetadata> columns = new ArrayList<>();
        String sql = "SELECT * FROM meta_columns WHERE dataset_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, datasetId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    columns.add(mapColumnMetadata(rs));
                }
            }
        }
        return columns;
    }

    public List<Map<String, Object>> executeQuery(String sql) throws SQLException {
        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.put(metaData.getColumnName(i), rs.getObject(i));
                }
                results.add(row);
            }
        }
        return results;
    }

    public List<Map<String, Object>> executeQueryWithTimeout(String sql, int timeoutSeconds) throws SQLException {
        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement()) {
            
            stmt.setQueryTimeout(timeoutSeconds);
            
            try (ResultSet rs = stmt.executeQuery(sql)) {
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        row.put(metaData.getColumnName(i), rs.getObject(i));
                    }
                    results.add(row);
                }
            }
        }
        return results;
    }

    public void executeUpdate(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }

    public void executeBatch(String sql, List<List<Object>> batchArgs) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            for (List<Object> args : batchArgs) {
                for (int i = 0; i < args.size(); i++) {
                    stmt.setObject(i + 1, args.get(i));
                }
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    public long getTableRowCount(String tableName) throws SQLException {
        // 安全拦截：防止底层的表名被 SQL 注入
        if (tableName == null || !tableName.matches("^[a-zA-Z0-9_]+$")) {
            throw new IllegalArgumentException("非法的表名: " + tableName);
        }
        String sql = "SELECT COUNT(*) FROM " + tableName;
        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        return 0;
    }

    public void deleteDataset(Long id) throws SQLException {
        Dataset dataset = findDatasetById(id);
        if (dataset != null) {
            try (Connection connection = dataSource.getConnection()) {
                // 开启事务，保证删除动作的要么全成功，要么全失败
                connection.setAutoCommit(false);
                try {
                    try (Statement stmt = connection.createStatement()) {
                        stmt.execute("DROP TABLE IF EXISTS " + dataset.getTableName());
                    }
                    try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM meta_columns WHERE dataset_id = ?")) {
                        stmt.setLong(1, id);
                        stmt.executeUpdate();
                    }
                    try (PreparedStatement stmt = connection.prepareStatement(
                            "DELETE FROM meta_dataset_relations WHERE source_dataset_id = ? OR target_dataset_id = ?")) {
                        stmt.setLong(1, id);
                        stmt.setLong(2, id);
                        stmt.executeUpdate();
                    }
                    try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM meta_datasets WHERE id = ?")) {
                        stmt.setLong(1, id);
                        stmt.executeUpdate();
                    }
                    connection.commit();
                } catch (SQLException e) {
                    connection.rollback();
                    throw e;
                } finally {
                    connection.setAutoCommit(true);
                }
            }
        }
    }

//这两部分是把数据库查询结果映射到实体类
// ResultSet 就是数据库送给 Java 的一份“网络流式表格”。Java 不能长久保存它，只能像看幻灯片一样，通过 rs.next() 一步一步往下按，看完每一页赶紧把重点信息抄写到自己的笔记本（也就是实体类 Entity 对象）上，然后把它销毁。
//虽然duckdb不是服务器数据库，但是由于duckdb是c++底层的原因，为了更好的内存管理，我们依然需要copy过来然后销毁resultset也就是一个跨界指针
    private Dataset mapDataset(ResultSet rs) throws SQLException {
        Dataset dataset = new Dataset();
        dataset.setId(rs.getLong("id"));
        dataset.setGroupId(getNullableLong(rs, "group_id"));
        dataset.setName(rs.getString("name"));
        dataset.setDescriptionMd(rs.getString("description_md"));
        dataset.setTableName(rs.getString("table_name"));
        dataset.setOriginalFileName(rs.getString("original_file_name"));
        dataset.setRowCount(rs.getLong("row_count"));
        dataset.setColumnCount(rs.getInt("column_count"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            dataset.setCreatedAt(createdAt.toLocalDateTime());
        }
        return dataset;
    }

    private DatasetGroup mapDatasetGroup(ResultSet rs) throws SQLException {
        DatasetGroup group = new DatasetGroup();
        group.setId(rs.getLong("id"));
        group.setName(rs.getString("name"));
        group.setDescription(rs.getString("description"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            group.setCreatedAt(createdAt.toLocalDateTime());
        }
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) {
            group.setUpdatedAt(updatedAt.toLocalDateTime());
        }
        return group;
    }

    private DatasetRelation mapDatasetRelation(ResultSet rs) throws SQLException {
        DatasetRelation relation = new DatasetRelation();
        relation.setId(rs.getLong("id"));
        relation.setGroupId(getNullableLong(rs, "group_id"));
        relation.setSourceDatasetId(getNullableLong(rs, "source_dataset_id"));
        relation.setSourceTableName(rs.getString("source_table_name"));
        relation.setSourceColumnName(rs.getString("source_column_name"));
        relation.setTargetDatasetId(getNullableLong(rs, "target_dataset_id"));
        relation.setTargetTableName(rs.getString("target_table_name"));
        relation.setTargetColumnName(rs.getString("target_column_name"));
        relation.setRelationType(rs.getString("relation_type"));
        relation.setConfidence(getNullableDouble(rs, "confidence"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            relation.setCreatedAt(createdAt.toLocalDateTime());
        }
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) {
            relation.setUpdatedAt(updatedAt.toLocalDateTime());
        }
        return relation;
    }

    private DocumentAsset mapDocumentAsset(ResultSet rs) throws SQLException {
        DocumentAsset asset = new DocumentAsset();
        asset.setId(rs.getLong("id"));
        asset.setGroupId(getNullableLong(rs, "group_id"));
        asset.setName(rs.getString("name"));
        asset.setOriginalFileName(rs.getString("original_file_name"));
        asset.setStoredPath(rs.getString("stored_path"));
        asset.setFileType(rs.getString("file_type"));
        asset.setMimeType(rs.getString("mime_type"));
        asset.setSizeBytes(getNullableLong(rs, "size_bytes"));
        asset.setProcessingStatus(rs.getString("processing_status"));
        asset.setProcessingError(rs.getString("processing_error"));
        Object chunkCount = rs.getObject("chunk_count");
        asset.setChunkCount(chunkCount instanceof Number number ? number.intValue() : null);
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            asset.setCreatedAt(createdAt.toLocalDateTime());
        }
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) {
            asset.setUpdatedAt(updatedAt.toLocalDateTime());
        }
        return asset;
    }

    private DocumentChunk mapDocumentChunk(ResultSet rs) throws SQLException {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setId(rs.getLong("id"));
        chunk.setDocumentId(getNullableLong(rs, "document_id"));
        Object chunkIndex = rs.getObject("chunk_index");
        chunk.setChunkIndex(chunkIndex instanceof Number number ? number.intValue() : null);
        chunk.setChunkText(rs.getString("chunk_text"));
        chunk.setMetadataJson(rs.getString("metadata_json"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            chunk.setCreatedAt(createdAt.toLocalDateTime());
        }
        return chunk;
    }

    private ChatSession mapChatSession(ResultSet rs) throws SQLException {
        ChatSession session = new ChatSession();
        session.setId(rs.getLong("id"));
        session.setTitle(rs.getString("title"));
        session.setStatus(rs.getString("status"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            session.setCreatedAt(createdAt.toLocalDateTime());
        }
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) {
            session.setUpdatedAt(updatedAt.toLocalDateTime());
        }
        Timestamp expiresAt = rs.getTimestamp("expires_at");
        if (expiresAt != null) {
            session.setExpiresAt(expiresAt.toLocalDateTime());
        }
        return session;
    }

    private ChatMessage mapChatMessage(ResultSet rs) throws SQLException {
        ChatMessage message = new ChatMessage();
        message.setId(rs.getLong("id"));
        message.setSessionId(getNullableLong(rs, "session_id"));
        message.setRole(rs.getString("role"));
        message.setContent(rs.getString("content"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            message.setCreatedAt(createdAt.toLocalDateTime());
        }
        return message;
    }

    @SuppressWarnings("unused")
    private AnalysisArtifact mapAnalysisArtifact(ResultSet rs) throws SQLException {
        AnalysisArtifact artifact = new AnalysisArtifact();
        artifact.setId(rs.getLong("id"));
        artifact.setMode(rs.getString("mode"));
        artifact.setSessionId(getNullableLong(rs, "session_id"));
        artifact.setGroupId(getNullableLong(rs, "group_id"));
        artifact.setDatasetId(getNullableLong(rs, "dataset_id"));
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
        Timestamp archivedAt = rs.getTimestamp("archived_at");
        if (archivedAt != null) {
            artifact.setArchivedAt(archivedAt.toLocalDateTime());
        }
        Timestamp deletedAt = rs.getTimestamp("deleted_at");
        if (deletedAt != null) {
            artifact.setDeletedAt(deletedAt.toLocalDateTime());
        }
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) {
            artifact.setUpdatedAt(updatedAt.toLocalDateTime());
        }
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            artifact.setCreatedAt(createdAt.toLocalDateTime());
        }
        return artifact;
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

    private Long getNullableLong(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private Integer getNullableInteger(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(value.toString());
    }

    private Double getNullableDouble(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(value.toString());
    }

    private ColumnMetadata mapColumnMetadata(ResultSet rs) throws SQLException {
        ColumnMetadata col = new ColumnMetadata();
        col.setId(rs.getLong("id"));
        col.setDatasetId(rs.getLong("dataset_id"));
        col.setColumnName(rs.getString("column_name"));
        col.setDataType(rs.getString("data_type"));
        col.setNullable(rs.getBoolean("nullable"));
        col.setDistinctCount(rs.getLong("distinct_count"));
        col.setMinValue(rs.getString("min_value"));
        col.setMaxValue(rs.getString("max_value"));
        col.setSampleValues(rs.getString("sample_values"));
        return col;
    }
}
