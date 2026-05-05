package com.analysis.persistence;

import java.sql.SQLException;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import com.analysis.model.entity.ColumnMetadata;
import com.analysis.model.entity.Dataset;
import com.analysis.model.entity.DatasetGroup;
import com.analysis.model.entity.DatasetRelation;
import com.analysis.repository.DuckDBRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.persistence",
        name = "catalog-store",
        havingValue = "duckdb",
        matchIfMissing = true)
public class DuckDbWorkspaceCatalogStore implements WorkspaceCatalogStore {

    private final DuckDBRepository duckDBRepository;

    @Override
    public Long saveDatasetGroup(DatasetGroup group) throws SQLException {
        return duckDBRepository.saveDatasetGroup(group);
    }

    @Override
    public DatasetGroup findDatasetGroupById(Long id) throws SQLException {
        return duckDBRepository.findDatasetGroupById(id);
    }

    @Override
    public List<DatasetGroup> findAllDatasetGroups() throws SQLException {
        return duckDBRepository.findAllDatasetGroups();
    }

    @Override
    public boolean updateDatasetGroupDescription(Long groupId, String descriptionMd) throws SQLException {
        return duckDBRepository.updateDatasetGroupDescription(groupId, descriptionMd);
    }

    @Override
    public void deleteDatasetGroup(Long id) throws SQLException {
        duckDBRepository.deleteDatasetGroup(id);
    }

    @Override
    public Long saveDataset(Dataset dataset) throws SQLException {
        return duckDBRepository.saveDataset(dataset);
    }

    @Override
    public Dataset findDatasetById(Long id) throws SQLException {
        return duckDBRepository.findDatasetById(id);
    }

    @Override
    public List<Dataset> findAllDatasets() throws SQLException {
        return duckDBRepository.findAllDatasets();
    }

    @Override
    public List<Dataset> findAllDatasets(Long groupId) throws SQLException {
        return duckDBRepository.findAllDatasets(groupId);
    }

    @Override
    public List<Dataset> findDatasetsByGroupId(Long groupId) throws SQLException {
        return duckDBRepository.findDatasetsByGroupId(groupId);
    }

    @Override
    public boolean updateDatasetDescription(Long datasetId, String descriptionMd) throws SQLException {
        return duckDBRepository.updateDatasetDescription(datasetId, descriptionMd);
    }

    @Override
    public boolean updateDatasetGroupId(Long datasetId, Long groupId) throws SQLException {
        return duckDBRepository.updateDatasetGroupId(datasetId, groupId);
    }

    @Override
    public void deleteDataset(Long id) throws SQLException {
        duckDBRepository.deleteDatasetMetadata(id);
    }

    @Override
    public void saveColumnMetadata(ColumnMetadata metadata) throws SQLException {
        duckDBRepository.saveColumnMetadata(metadata);
    }

    @Override
    public List<ColumnMetadata> findColumnsByDatasetId(Long datasetId) throws SQLException {
        return duckDBRepository.findColumnsByDatasetId(datasetId);
    }

    @Override
    public Long saveDatasetRelation(DatasetRelation relation) throws SQLException {
        return duckDBRepository.saveDatasetRelation(relation);
    }

    @Override
    public DatasetRelation findDatasetRelationById(Long relationId) throws SQLException {
        return duckDBRepository.findDatasetRelationById(relationId);
    }

    @Override
    public List<DatasetRelation> findRelationsByGroupId(Long groupId) throws SQLException {
        return duckDBRepository.findRelationsByGroupId(groupId);
    }

    @Override
    public boolean updateDatasetRelation(DatasetRelation relation) throws SQLException {
        return duckDBRepository.updateDatasetRelation(relation);
    }

    @Override
    public void deleteRelationsByGroupId(Long groupId) throws SQLException {
        duckDBRepository.deleteRelationsByGroupId(groupId);
    }

    @Override
    public void deleteRelationById(Long relationId) throws SQLException {
        duckDBRepository.deleteRelationById(relationId);
    }
}
