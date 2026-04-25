package com.analysis.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.analysis.model.dto.CreateGroupRequest;
import com.analysis.model.dto.MarkdownDescriptionRequest;
import com.analysis.model.dto.UpsertRelationRequest;
import com.analysis.model.entity.Dataset;
import com.analysis.model.entity.DatasetGroup;
import com.analysis.model.entity.DatasetRelation;
import com.analysis.service.DatasetGroupService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class DatasetGroupController {

    private final DatasetGroupService datasetGroupService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> createGroup(@Valid @RequestBody CreateGroupRequest request) {
        Map<String, Object> response = new HashMap<>();
        try {
            DatasetGroup created = datasetGroupService.createGroup(request.getName(), request.getDescription());
            response.put("success", true);
            response.put("data", created);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to create group: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllGroups() {
        Map<String, Object> response = new HashMap<>();
        try {
            List<DatasetGroup> groups = datasetGroupService.getAllGroups();
            response.put("success", true);
            response.put("data", groups);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get groups: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getGroupById(@PathVariable("id") Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            DatasetGroup group = datasetGroupService.getGroupById(id);
            if (group == null) {
                response.put("success", false);
                response.put("message", "工作区不存在");
                return ResponseEntity.notFound().build();
            }
            response.put("success", true);
            response.put("data", group);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get group by id {}: {}", id, e.getMessage(), e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteGroup(@PathVariable("id") Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            datasetGroupService.deleteGroup(id);
            response.put("success", true);
            response.put("message", "工作区删除成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to delete group {}: {}", id, e.getMessage(), e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PutMapping("/{id}/description")
    public ResponseEntity<Map<String, Object>> updateGroupDescription(
            @PathVariable("id") Long id,
            @RequestBody MarkdownDescriptionRequest request) {
        Map<String, Object> response = new HashMap<>();
        try {
            DatasetGroup updated = datasetGroupService.updateGroupDescription(id, request.getDescriptionMd());
            response.put("success", true);
            response.put("message", "工作区描述更新成功");
            response.put("data", updated);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to update group description {}: {}", id, e.getMessage(), e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/{id}/datasets")
    public ResponseEntity<Map<String, Object>> getDatasetsByGroup(@PathVariable("id") Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Dataset> datasets = datasetGroupService.getDatasetsByGroupId(id);
            response.put("success", true);
            response.put("data", datasets);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get datasets for group {}: {}", id, e.getMessage(), e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/{id}/relations")
    public ResponseEntity<Map<String, Object>> getRelationsByGroup(@PathVariable("id") Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<DatasetRelation> relations = datasetGroupService.getRelationsByGroupId(id);
            response.put("success", true);
            response.put("data", relations);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get relations for group {}: {}", id, e.getMessage(), e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/{id}/relations/auto-detect")
    public ResponseEntity<Map<String, Object>> autoDetectRelations(@PathVariable("id") Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<DatasetRelation> relations = datasetGroupService.autoDetectRelations(id);
            response.put("success", true);
            response.put("message", "自动识别入口已打通，当前返回现有关系。下一步将接入规则识别。");
            response.put("data", relations);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to auto detect relations for group {}: {}", id, e.getMessage(), e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/{id}/relations")
    public ResponseEntity<Map<String, Object>> createRelation(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpsertRelationRequest request) {
        Map<String, Object> response = new HashMap<>();
        try {
            DatasetRelation relation = toEntity(id, request);
            DatasetRelation created = datasetGroupService.saveRelation(relation);
            response.put("success", true);
            response.put("data", created);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to create relation for group {}: {}", id, e.getMessage(), e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PutMapping("/{id}/relations/{relationId}")
    public ResponseEntity<Map<String, Object>> updateRelation(
            @PathVariable("id") Long id,
            @PathVariable("relationId") Long relationId,
            @Valid @RequestBody UpsertRelationRequest request) {
        Map<String, Object> response = new HashMap<>();
        try {
            DatasetRelation relation = toEntity(id, request);
            DatasetRelation updated = datasetGroupService.updateRelation(relationId, relation);
            response.put("success", true);
            response.put("data", updated);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to update relation {} for group {}: {}", relationId, id, e.getMessage(), e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @DeleteMapping("/{id}/relations/{relationId}")
    public ResponseEntity<Map<String, Object>> deleteRelation(
            @PathVariable("id") Long id,
            @PathVariable("relationId") Long relationId) {
        Map<String, Object> response = new HashMap<>();
        try {
            datasetGroupService.deleteRelationById(relationId);
            response.put("success", true);
            response.put("message", "关系删除成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to delete relation {} for group {}: {}", relationId, id, e.getMessage(), e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    private DatasetRelation toEntity(Long groupId, UpsertRelationRequest request) {
        DatasetRelation relation = new DatasetRelation();
        relation.setGroupId(groupId);
        relation.setSourceDatasetId(request.getSourceDatasetId());
        relation.setSourceTableName(request.getSourceTableName());
        relation.setSourceColumnName(request.getSourceColumnName());
        relation.setTargetDatasetId(request.getTargetDatasetId());
        relation.setTargetTableName(request.getTargetTableName());
        relation.setTargetColumnName(request.getTargetColumnName());
        relation.setRelationType(request.getRelationType());
        relation.setConfidence(request.getConfidence());
        return relation;
    }
}
