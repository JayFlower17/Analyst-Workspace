package com.analysis.persistence;

import java.sql.SQLException;
import java.util.List;

import com.analysis.model.entity.ColumnMetadata;
import com.analysis.model.entity.Dataset;
import com.analysis.model.entity.DatasetGroup;
import com.analysis.model.entity.DatasetRelation;

public interface WorkspaceCatalogStore {

    Long saveDatasetGroup(DatasetGroup group) throws SQLException;

    DatasetGroup findDatasetGroupById(Long id) throws SQLException;

    List<DatasetGroup> findAllDatasetGroups() throws SQLException;

    boolean updateDatasetGroupDescription(Long groupId, String descriptionMd) throws SQLException;

    void deleteDatasetGroup(Long id) throws SQLException;

    Long saveDataset(Dataset dataset) throws SQLException;

    Dataset findDatasetById(Long id) throws SQLException;

    List<Dataset> findAllDatasets() throws SQLException;

    List<Dataset> findAllDatasets(Long groupId) throws SQLException;

    List<Dataset> findDatasetsByGroupId(Long groupId) throws SQLException;

    boolean updateDatasetDescription(Long datasetId, String descriptionMd) throws SQLException;

    boolean updateDatasetGroupId(Long datasetId, Long groupId) throws SQLException;

    void deleteDataset(Long id) throws SQLException;

    void saveColumnMetadata(ColumnMetadata metadata) throws SQLException;

    List<ColumnMetadata> findColumnsByDatasetId(Long datasetId) throws SQLException;

    Long saveDatasetRelation(DatasetRelation relation) throws SQLException;

    DatasetRelation findDatasetRelationById(Long relationId) throws SQLException;

    List<DatasetRelation> findRelationsByGroupId(Long groupId) throws SQLException;

    boolean updateDatasetRelation(DatasetRelation relation) throws SQLException;

    void deleteRelationsByGroupId(Long groupId) throws SQLException;

    void deleteRelationById(Long relationId) throws SQLException;
}
