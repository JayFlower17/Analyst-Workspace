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

import com.analysis.model.entity.ColumnMetadata;
import com.analysis.model.entity.Dataset;
import com.analysis.model.entity.DatasetGroup;
import com.analysis.model.entity.DatasetRelation;

@Repository
@ConditionalOnProperty(prefix = "app.persistence", name = "catalog-store", havingValue = "postgres")
public class PostgresWorkspaceCatalogStore implements WorkspaceCatalogStore {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<DatasetGroup> groupMapper = (rs, rowNum) -> mapGroup(rs);
    private final RowMapper<Dataset> datasetMapper = (rs, rowNum) -> mapDataset(rs);
    private final RowMapper<ColumnMetadata> columnMapper = (rs, rowNum) -> mapColumn(rs);
    private final RowMapper<DatasetRelation> relationMapper = (rs, rowNum) -> mapRelation(rs);

    public PostgresWorkspaceCatalogStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Long saveDatasetGroup(DatasetGroup group) {
        String sql = """
                INSERT INTO workspaces (name, description, created_at, updated_at)
                VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                RETURNING id
                """;
        return jdbcTemplate.query(sql, ps -> {
            ps.setString(1, group.getName());
            ps.setString(2, group.getDescription());
        }, rs -> rs.next() ? rs.getLong(1) : null);
    }

    @Override
    public DatasetGroup findDatasetGroupById(Long id) {
        try {
            return jdbcTemplate.queryForObject("SELECT * FROM workspaces WHERE id = ?", groupMapper, id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Override
    public List<DatasetGroup> findAllDatasetGroups() {
        return jdbcTemplate.query("SELECT * FROM workspaces ORDER BY created_at DESC, id DESC", groupMapper);
    }

    @Override
    public boolean updateDatasetGroupDescription(Long groupId, String descriptionMd) {
        String sql = """
                UPDATE workspaces
                SET description = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """;
        return jdbcTemplate.update(sql, descriptionMd, groupId) > 0;
    }

    @Override
    public void deleteDatasetGroup(Long id) {
        jdbcTemplate.update("DELETE FROM workspaces WHERE id = ?", id);
    }

    @Override
    public Long saveDataset(Dataset dataset) {
        String sql = """
                INSERT INTO datasets (
                    workspace_id, name, description_md, table_name, original_file_name,
                    row_count, column_count, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                RETURNING id
                """;
        return jdbcTemplate.query(sql, ps -> {
            setNullableLong(ps, 1, dataset.getGroupId());
            ps.setString(2, dataset.getName());
            ps.setString(3, dataset.getDescriptionMd());
            ps.setString(4, dataset.getTableName());
            ps.setString(5, dataset.getOriginalFileName());
            ps.setLong(6, dataset.getRowCount() != null ? dataset.getRowCount() : 0L);
            ps.setInt(7, dataset.getColumnCount() != null ? dataset.getColumnCount() : 0);
        }, rs -> rs.next() ? rs.getLong(1) : null);
    }

    @Override
    public Dataset findDatasetById(Long id) {
        try {
            return jdbcTemplate.queryForObject("SELECT * FROM datasets WHERE id = ?", datasetMapper, id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Override
    public List<Dataset> findAllDatasets() {
        return jdbcTemplate.query("SELECT * FROM datasets ORDER BY created_at DESC, id DESC", datasetMapper);
    }

    @Override
    public List<Dataset> findAllDatasets(Long groupId) {
        if (groupId == null) {
            return findAllDatasets();
        }
        return findDatasetsByGroupId(groupId);
    }

    @Override
    public List<Dataset> findDatasetsByGroupId(Long groupId) {
        String sql = """
                SELECT *
                FROM datasets
                WHERE workspace_id = ?
                ORDER BY created_at DESC, id DESC
                """;
        return jdbcTemplate.query(sql, datasetMapper, groupId);
    }

    @Override
    public boolean updateDatasetDescription(Long datasetId, String descriptionMd) {
        String sql = """
                UPDATE datasets
                SET description_md = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """;
        return jdbcTemplate.update(sql, descriptionMd, datasetId) > 0;
    }

    @Override
    public boolean updateDatasetGroupId(Long datasetId, Long groupId) {
        String sql = """
                UPDATE datasets
                SET workspace_id = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """;
        return jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement(sql);
            setNullableLong(ps, 1, groupId);
            ps.setLong(2, datasetId);
            return ps;
        }) > 0;
    }

    @Override
    public void deleteDataset(Long id) {
        jdbcTemplate.update("DELETE FROM datasets WHERE id = ?", id);
    }

    @Override
    public void saveColumnMetadata(ColumnMetadata metadata) {
        String sql = """
                INSERT INTO dataset_columns (
                    dataset_id, column_name, data_type, nullable, distinct_count,
                    min_value, max_value, sample_values
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(sql,
                metadata.getDatasetId(),
                metadata.getColumnName(),
                metadata.getDataType(),
                metadata.getNullable() != null && metadata.getNullable(),
                metadata.getDistinctCount() != null ? metadata.getDistinctCount() : 0L,
                metadata.getMinValue(),
                metadata.getMaxValue(),
                metadata.getSampleValues());
    }

    @Override
    public List<ColumnMetadata> findColumnsByDatasetId(Long datasetId) {
        return jdbcTemplate.query("SELECT * FROM dataset_columns WHERE dataset_id = ?", columnMapper, datasetId);
    }

    @Override
    public Long saveDatasetRelation(DatasetRelation relation) {
        String sql = """
                INSERT INTO dataset_relations (
                    workspace_id, source_dataset_id, source_table_name, source_column_name,
                    target_dataset_id, target_table_name, target_column_name,
                    relation_type, confidence, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                RETURNING id
                """;
        return jdbcTemplate.query(sql, ps -> {
            ps.setLong(1, relation.getGroupId());
            setNullableLong(ps, 2, relation.getSourceDatasetId());
            ps.setString(3, relation.getSourceTableName());
            ps.setString(4, relation.getSourceColumnName());
            setNullableLong(ps, 5, relation.getTargetDatasetId());
            ps.setString(6, relation.getTargetTableName());
            ps.setString(7, relation.getTargetColumnName());
            ps.setString(8, relation.getRelationType());
            if (relation.getConfidence() != null) {
                ps.setDouble(9, relation.getConfidence());
            } else {
                ps.setNull(9, Types.DOUBLE);
            }
        }, rs -> rs.next() ? rs.getLong(1) : null);
    }

    @Override
    public DatasetRelation findDatasetRelationById(Long relationId) {
        try {
            return jdbcTemplate.queryForObject("SELECT * FROM dataset_relations WHERE id = ?", relationMapper, relationId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Override
    public List<DatasetRelation> findRelationsByGroupId(Long groupId) {
        String sql = """
                SELECT *
                FROM dataset_relations
                WHERE workspace_id = ?
                ORDER BY created_at DESC, id DESC
                """;
        return jdbcTemplate.query(sql, relationMapper, groupId);
    }

    @Override
    public boolean updateDatasetRelation(DatasetRelation relation) {
        String sql = """
                UPDATE dataset_relations
                SET source_dataset_id = ?,
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
        return jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement(sql);
            setNullableLong(ps, 1, relation.getSourceDatasetId());
            ps.setString(2, relation.getSourceTableName());
            ps.setString(3, relation.getSourceColumnName());
            setNullableLong(ps, 4, relation.getTargetDatasetId());
            ps.setString(5, relation.getTargetTableName());
            ps.setString(6, relation.getTargetColumnName());
            ps.setString(7, relation.getRelationType());
            if (relation.getConfidence() != null) {
                ps.setDouble(8, relation.getConfidence());
            } else {
                ps.setNull(8, Types.DOUBLE);
            }
            ps.setLong(9, relation.getId());
            return ps;
        }) > 0;
    }

    @Override
    public void deleteRelationsByGroupId(Long groupId) {
        jdbcTemplate.update("DELETE FROM dataset_relations WHERE workspace_id = ?", groupId);
    }

    @Override
    public void deleteRelationById(Long relationId) {
        jdbcTemplate.update("DELETE FROM dataset_relations WHERE id = ?", relationId);
    }

    private DatasetGroup mapGroup(ResultSet rs) throws SQLException {
        DatasetGroup group = new DatasetGroup();
        group.setId(rs.getLong("id"));
        group.setName(rs.getString("name"));
        group.setDescription(rs.getString("description"));
        group.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        group.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        return group;
    }

    private Dataset mapDataset(ResultSet rs) throws SQLException {
        Dataset dataset = new Dataset();
        dataset.setId(rs.getLong("id"));
        dataset.setGroupId(getNullableLong(rs, "workspace_id"));
        dataset.setName(rs.getString("name"));
        dataset.setDescriptionMd(rs.getString("description_md"));
        dataset.setTableName(rs.getString("table_name"));
        dataset.setOriginalFileName(rs.getString("original_file_name"));
        dataset.setRowCount(getNullableLong(rs, "row_count"));
        dataset.setColumnCount(getNullableInteger(rs, "column_count"));
        dataset.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        dataset.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        return dataset;
    }

    private ColumnMetadata mapColumn(ResultSet rs) throws SQLException {
        ColumnMetadata column = new ColumnMetadata();
        column.setId(rs.getLong("id"));
        column.setDatasetId(getNullableLong(rs, "dataset_id"));
        column.setColumnName(rs.getString("column_name"));
        column.setDataType(rs.getString("data_type"));
        column.setNullable(rs.getBoolean("nullable"));
        column.setDistinctCount(getNullableLong(rs, "distinct_count"));
        column.setMinValue(rs.getString("min_value"));
        column.setMaxValue(rs.getString("max_value"));
        column.setSampleValues(rs.getString("sample_values"));
        return column;
    }

    private DatasetRelation mapRelation(ResultSet rs) throws SQLException {
        DatasetRelation relation = new DatasetRelation();
        relation.setId(rs.getLong("id"));
        relation.setGroupId(getNullableLong(rs, "workspace_id"));
        relation.setSourceDatasetId(getNullableLong(rs, "source_dataset_id"));
        relation.setSourceTableName(rs.getString("source_table_name"));
        relation.setSourceColumnName(rs.getString("source_column_name"));
        relation.setTargetDatasetId(getNullableLong(rs, "target_dataset_id"));
        relation.setTargetTableName(rs.getString("target_table_name"));
        relation.setTargetColumnName(rs.getString("target_column_name"));
        relation.setRelationType(rs.getString("relation_type"));
        relation.setConfidence(getNullableDouble(rs, "confidence"));
        relation.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        relation.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        return relation;
    }

    private void setNullableLong(java.sql.PreparedStatement ps, int index, Long value) throws SQLException {
        if (value != null) {
            ps.setLong(index, value);
        } else {
            ps.setNull(index, Types.BIGINT);
        }
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
}
