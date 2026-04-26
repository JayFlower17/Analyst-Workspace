package com.analysis.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.analysis.model.dto.DocumentChunkSearchResult;
import com.analysis.model.entity.DocumentAsset;
import com.analysis.model.entity.DocumentChunk;
import com.analysis.service.DocumentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("groupId") Long groupId,
            @RequestParam(value = "name", required = false) String name) {
        Map<String, Object> response = new HashMap<>();
        try {
            DocumentAsset asset = documentService.uploadDocument(file, name, groupId);
            response.put("success", true);
            response.put("message", "文档上传成功");
            response.put("data", asset);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to upload document: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "文档上传失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getDocumentsByGroup(
            @RequestParam("groupId") Long groupId) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<DocumentAsset> documents = documentService.getDocumentsByGroupId(groupId);
            response.put("success", true);
            response.put("data", documents);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get documents: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getDocument(@PathVariable("id") Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            DocumentAsset asset = documentService.getDocumentById(id);
            if (asset == null) {
                response.put("success", false);
                response.put("message", "文档不存在");
                return ResponseEntity.notFound().build();
            }
            response.put("success", true);
            response.put("data", asset);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get document {}: {}", id, e.getMessage(), e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/{id}/chunks")
    public ResponseEntity<Map<String, Object>> getDocumentChunks(@PathVariable("id") Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<DocumentChunk> chunks = documentService.getDocumentChunks(id);
            response.put("success", true);
            response.put("data", chunks);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get chunks for document {}: {}", id, e.getMessage(), e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchDocumentChunks(
            @RequestParam("groupId") Long groupId,
            @RequestParam("query") String query,
            @RequestParam(value = "topK", required = false) Integer topK) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<DocumentChunkSearchResult> results = documentService.searchDocumentChunks(groupId, query, topK);
            response.put("success", true);
            response.put("data", results);
            response.put("retrievalMode", results.isEmpty() ? "none" : results.get(0).getRetrievalMode());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to search chunks in group {}: {}", groupId, e.getMessage(), e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteDocument(@PathVariable("id") Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            documentService.deleteDocument(id);
            response.put("success", true);
            response.put("message", "文档删除成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to delete document {}: {}", id, e.getMessage(), e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
