package com.analysis.service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.analysis.model.dto.DatasetInfo;
import com.analysis.model.entity.Dataset;
import com.analysis.model.entity.DatasetGroup;
import com.analysis.model.entity.DatasetRelation;
import com.analysis.persistence.WorkspaceCatalogStore;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkspaceSchemaService {

    private final WorkspaceCatalogStore workspaceCatalogStore;
    private final DatasetService datasetService;

    public record WorkspaceSchemaContext(
            Long groupId,
            List<Dataset> datasets,
            List<DatasetRelation> relations,
            String businessContextPrompt,
            String schemaPrompt,
            String relationPrompt) {
    }

    public WorkspaceSchemaContext buildContext(Long groupId, List<Long> focusDatasetIds) throws SQLException {
        if (groupId == null) {
            throw new IllegalArgumentException("groupId 不能为空");
        }

        List<Dataset> groupDatasets = workspaceCatalogStore.findDatasetsByGroupId(groupId);
        if (groupDatasets.isEmpty()) {
            throw new IllegalArgumentException("工作区下没有可分析的数据集");
        }

        List<Dataset> selected = filterDatasets(groupDatasets, focusDatasetIds);
        if (selected.isEmpty()) {
            throw new IllegalArgumentException("focusDatasetIds 与工作区数据集不匹配");
        }

        Set<Long> selectedIds = new HashSet<>();
        for (Dataset ds : selected) {
            selectedIds.add(ds.getId());
        }

        List<DatasetRelation> allRelations = workspaceCatalogStore.findRelationsByGroupId(groupId);
        List<DatasetRelation> selectedRelations = new ArrayList<>();
        for (DatasetRelation relation : allRelations) {
            if (selectedIds.contains(relation.getSourceDatasetId())
                    && selectedIds.contains(relation.getTargetDatasetId())) {
                selectedRelations.add(relation);
            }
        }

        DatasetGroup group = workspaceCatalogStore.findDatasetGroupById(groupId);

        return new WorkspaceSchemaContext(
                groupId,
                selected,
                selectedRelations,
                buildBusinessContextPrompt(group, selected),
                buildSchemaPrompt(selected),
                buildRelationPrompt(selectedRelations));
    }

    private List<Dataset> filterDatasets(List<Dataset> allDatasets, List<Long> focusDatasetIds) {
        if (focusDatasetIds == null || focusDatasetIds.isEmpty()) {
            return allDatasets;
        }
        Set<Long> allowedIds = new HashSet<>(focusDatasetIds);
        List<Dataset> selected = new ArrayList<>();
        for (Dataset ds : allDatasets) {
            if (allowedIds.contains(ds.getId())) {
                selected.add(ds);
            }
        }
        return selected;
    }

    private String buildSchemaPrompt(List<Dataset> datasets) throws SQLException {
        StringBuilder sb = new StringBuilder();
        sb.append("Current Workspace Tables:\n");
        for (Dataset dataset : datasets) {
            DatasetInfo info = datasetService.getDatasetInfo(dataset.getId());
            if (info == null) {
                continue;
            }
            sb.append("- table: ").append(info.getTableName()).append(" (datasetId=").append(info.getId()).append(")\n");
            sb.append("  columns:\n");
            if (info.getColumns() != null) {
                for (DatasetInfo.ColumnInfo col : info.getColumns()) {
                    sb.append("    - ").append(col.getName())
                            .append(" [").append(col.getType() != null ? col.getType() : "UNKNOWN").append("]\n");
                }
            }
        }
        return sb.toString();
    }

    private String buildBusinessContextPrompt(DatasetGroup group, List<Dataset> datasets) {
        StringBuilder sb = new StringBuilder();
        sb.append("Workspace Business Context:\n");
        if (group != null) {
            sb.append("- workspace_name: ").append(group.getName()).append("\n");
            if (group.getDescription() != null && !group.getDescription().isBlank()) {
                sb.append("- workspace_description_markdown:\n");
                sb.append(group.getDescription()).append("\n");
            } else {
                sb.append("- workspace_description_markdown: (not provided)\n");
            }
        }
        sb.append("\nTable Business Descriptions:\n");
        for (Dataset ds : datasets) {
            sb.append("- ").append(ds.getTableName())
                    .append(" (datasetId=").append(ds.getId()).append(")\n");
            if (ds.getDescriptionMd() != null && !ds.getDescriptionMd().isBlank()) {
                sb.append(ds.getDescriptionMd()).append("\n");
            } else {
                sb.append("  (no table description provided)\n");
            }
        }
        return sb.toString();
    }

    private String buildRelationPrompt(List<DatasetRelation> relations) {
        if (relations.isEmpty()) {
            return "Available Relationships:\n- No declared relations. Infer join keys carefully from column semantics.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Available Relationships:\n");
        for (DatasetRelation relation : relations) {
            sb.append("- ")
                    .append(relation.getSourceTableName()).append(".").append(relation.getSourceColumnName())
                    .append(" -> ")
                    .append(relation.getTargetTableName()).append(".").append(relation.getTargetColumnName());
            if (relation.getRelationType() != null && !relation.getRelationType().isBlank()) {
                sb.append(" (").append(relation.getRelationType()).append(")");
            }
            if (relation.getConfidence() != null) {
                sb.append(" confidence=").append(String.format("%.2f", relation.getConfidence()));
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
