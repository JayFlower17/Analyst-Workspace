package com.analysis.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.analysis.model.dto.DatasetInfo;
import com.analysis.model.dto.MarkdownDescriptionRequest;
import com.analysis.model.entity.Dataset;
import com.analysis.service.DatasetService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/datasets")
@RequiredArgsConstructor
public class DatasetController {

    private final DatasetService datasetService;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadDataset(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "groupId", required = false) Long groupId) {

        Map<String, Object> response = new HashMap<>();

        try {
            String datasetName = name != null ? name : file.getOriginalFilename();
            Dataset dataset = datasetService.uploadDataset(file, datasetName, groupId);
            // 上传数据集的详细细节在 datasetService.uploadDataset(...) 中
            response.put("success", true);
            response.put("message", "数据集上传成功");
            response.put("data", dataset);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to upload dataset: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "上传失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllDatasets(
            @RequestParam(value = "groupId", required = false) Long groupId) {
        Map<String, Object> response = new HashMap<>();

        try {
            List<Dataset> datasets = datasetService.getAllDatasets(groupId);
            // 从 DuckDB 的 meta_datasets 表里查出所有数据集列表返回给前端
            response.put("success", true);
            response.put("data", datasets);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to get datasets: {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getDataset(@PathVariable("id") Long id) {
        Map<String, Object> response = new HashMap<>();

        try {
            Dataset dataset = datasetService.getDatasetById(id);
            if (dataset == null) {
                response.put("success", false);
                response.put("message", "数据集不存在");
                return ResponseEntity.notFound().build();
            }

            response.put("success", true);
            response.put("data", dataset);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to get dataset: {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/{id}/metadata")
    public ResponseEntity<Map<String, Object>> getDatasetMetadata(@PathVariable("id") Long id) {
        Map<String, Object> response = new HashMap<>();

        try {
            DatasetInfo info = datasetService.getDatasetInfo(id);
            if (info == null) {
                response.put("success", false);
                response.put("message", "数据集不存在");
                return ResponseEntity.notFound().build();
            }

            response.put("success", true);
            response.put("data", info);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to get dataset metadata: {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteDataset(@PathVariable("id") Long id) {
        Map<String, Object> response = new HashMap<>();

        try {
            datasetService.deleteDataset(id);
            response.put("success", true);
            response.put("message", "数据集删除成功");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to delete dataset: {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PutMapping("/{id}/description")
    public ResponseEntity<Map<String, Object>> updateDatasetDescription(
            @PathVariable("id") Long id,
            @RequestBody MarkdownDescriptionRequest request) {
        Map<String, Object> response = new HashMap<>();

        try {
            Dataset updated = datasetService.updateDatasetDescription(id, request.getDescriptionMd());
            response.put("success", true);
            response.put("message", "数据表描述更新成功");
            response.put("data", updated);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to update dataset description: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
