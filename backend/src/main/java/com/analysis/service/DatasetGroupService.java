package com.analysis.service;

import java.sql.SQLException;
import java.util.List;

import org.springframework.stereotype.Service;

import com.analysis.model.entity.Dataset;
import com.analysis.model.entity.DatasetGroup;
import com.analysis.model.entity.DatasetRelation;
import com.analysis.persistence.WorkspaceCatalogStore;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DatasetGroupService {

    private final WorkspaceCatalogStore workspaceCatalogStore;

    public DatasetGroup createGroup(String name, String description) throws SQLException {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("工作区名称不能为空");
        }
        DatasetGroup group = new DatasetGroup();
        group.setName(name.trim());
        group.setDescription(description);
        Long groupId = workspaceCatalogStore.saveDatasetGroup(group);
        group.setId(groupId);
        return workspaceCatalogStore.findDatasetGroupById(groupId);
    }

    public List<DatasetGroup> getAllGroups() throws SQLException {
        return workspaceCatalogStore.findAllDatasetGroups();
    }

    public DatasetGroup getGroupById(Long groupId) throws SQLException {
        return workspaceCatalogStore.findDatasetGroupById(groupId);
    }

    public List<Dataset> getDatasetsByGroupId(Long groupId) throws SQLException {
        ensureGroupExists(groupId);
        return workspaceCatalogStore.findDatasetsByGroupId(groupId);
    }

    public void deleteGroup(Long groupId) throws SQLException {
        ensureGroupExists(groupId);
        workspaceCatalogStore.deleteDatasetGroup(groupId);
    }

    public DatasetGroup updateGroupDescription(Long groupId, String descriptionMd) throws SQLException {
        ensureGroupExists(groupId);
        boolean updated = workspaceCatalogStore.updateDatasetGroupDescription(groupId, descriptionMd);
        if (!updated) {
            throw new IllegalStateException("工作区描述更新失败: " + groupId);
        }
        return workspaceCatalogStore.findDatasetGroupById(groupId);
    }

    public DatasetRelation saveRelation(DatasetRelation relation) throws SQLException {
        validateRelationInput(relation);
        validateRelationDatasets(relation);
        Long relationId = workspaceCatalogStore.saveDatasetRelation(relation);
        return workspaceCatalogStore.findDatasetRelationById(relationId);
    }

    public DatasetRelation updateRelation(Long relationId, DatasetRelation relation) throws SQLException {
        if (relationId == null) {
            throw new IllegalArgumentException("关系 ID 不能为空");
        }
        DatasetRelation existing = workspaceCatalogStore.findDatasetRelationById(relationId);
        if (existing == null) {
            throw new IllegalArgumentException("关系不存在: " + relationId);
        }
        relation.setId(relationId);
        validateRelationInput(relation);
        validateRelationDatasets(relation);
        boolean updated = workspaceCatalogStore.updateDatasetRelation(relation);
        if (!updated) {
            throw new IllegalStateException("关系更新失败: " + relationId);
        }
        return workspaceCatalogStore.findDatasetRelationById(relationId);
    }

    public List<DatasetRelation> getRelationsByGroupId(Long groupId) throws SQLException {
        ensureGroupExists(groupId);
        return workspaceCatalogStore.findRelationsByGroupId(groupId);
    }

    public List<DatasetRelation> autoDetectRelations(Long groupId) throws SQLException {
        ensureGroupExists(groupId);
        // 阶段一接口预留：下一步接入规则识别服务后替换此实现
        log.info("[Relation AutoDetect] Placeholder invoked for group {}", groupId);
        return workspaceCatalogStore.findRelationsByGroupId(groupId);
    }

    public void deleteRelationsByGroupId(Long groupId) throws SQLException {
        ensureGroupExists(groupId);
        workspaceCatalogStore.deleteRelationsByGroupId(groupId);
    }

    public void deleteRelationById(Long relationId) throws SQLException {
        DatasetRelation existing = workspaceCatalogStore.findDatasetRelationById(relationId);
        if (existing == null) {
            throw new IllegalArgumentException("关系不存在: " + relationId);
        }
        workspaceCatalogStore.deleteRelationById(relationId);
    }

    private void ensureGroupExists(Long groupId) throws SQLException {
        if (groupId == null) {
            throw new IllegalArgumentException("工作区 ID 不能为空");
        }
        if (workspaceCatalogStore.findDatasetGroupById(groupId) == null) {
            throw new IllegalArgumentException("工作区不存在: " + groupId);
        }
    }

    private void validateRelationInput(DatasetRelation relation) {
        if (relation == null) {
            throw new IllegalArgumentException("关系对象不能为空");
        }
        if (relation.getGroupId() == null) {
            throw new IllegalArgumentException("关系必须绑定工作区");
        }
        if (relation.getSourceDatasetId() == null || relation.getTargetDatasetId() == null) {
            throw new IllegalArgumentException("关系必须包含 source/target 数据集 ID");
        }
        if (relation.getSourceColumnName() == null || relation.getSourceColumnName().isBlank()
                || relation.getTargetColumnName() == null || relation.getTargetColumnName().isBlank()) {
            throw new IllegalArgumentException("关系字段不能为空");
        }
    }

    private void validateRelationDatasets(DatasetRelation relation) throws SQLException {
        ensureGroupExists(relation.getGroupId());
        Dataset source = workspaceCatalogStore.findDatasetById(relation.getSourceDatasetId());
        Dataset target = workspaceCatalogStore.findDatasetById(relation.getTargetDatasetId());
        if (source == null || target == null) {
            throw new IllegalArgumentException("关系中的数据集不存在");
        }
        if (!relation.getGroupId().equals(source.getGroupId())
                || !relation.getGroupId().equals(target.getGroupId())) {
            throw new IllegalArgumentException("关系中的数据集必须属于同一工作区");
        }
        if (relation.getSourceTableName() == null || relation.getSourceTableName().isBlank()) {
            relation.setSourceTableName(source.getTableName());
        }
        if (relation.getTargetTableName() == null || relation.getTargetTableName().isBlank()) {
            relation.setTargetTableName(target.getTableName());
        }
    }
}
