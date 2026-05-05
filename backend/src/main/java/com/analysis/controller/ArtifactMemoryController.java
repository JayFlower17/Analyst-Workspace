package com.analysis.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.analysis.service.ArtifactMemoryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/artifact-memories")
@RequiredArgsConstructor
public class ArtifactMemoryController {

    private final ArtifactMemoryService artifactMemoryService;

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getArtifactMemory(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            response.put("success", true);
            response.put("data", artifactMemoryService.getArtifactMemory(id));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get artifact memory {}: {}", id, e.getMessage(), e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/recent")
    public ResponseEntity<Map<String, Object>> getRecentMemories(
            @RequestParam(value = "groupId") Long groupId,
            @RequestParam(value = "limit", defaultValue = "10") int limit,
            @RequestParam(value = "status", defaultValue = "ACTIVE") String status) {
        Map<String, Object> response = new HashMap<>();
        try {
            response.put("success", true);
            response.put("data", artifactMemoryService.getRecentMemories(groupId, limit, status));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get recent artifact memories for group {}: {}", groupId, e.getMessage(), e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PatchMapping("/{id}/archive")
    public ResponseEntity<Map<String, Object>> archiveMemory(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            response.put("success", true);
            response.put("data", artifactMemoryService.archiveMemory(id));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to archive artifact memory {}: {}", id, e.getMessage(), e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PatchMapping("/{id}/restore")
    public ResponseEntity<Map<String, Object>> restoreMemory(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            response.put("success", true);
            response.put("data", artifactMemoryService.restoreMemory(id));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to restore artifact memory {}: {}", id, e.getMessage(), e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PatchMapping("/{id}/supersede")
    public ResponseEntity<Map<String, Object>> supersedeMemory(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            response.put("success", true);
            response.put("data", artifactMemoryService.supersedeMemory(id));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to supersede artifact memory {}: {}", id, e.getMessage(), e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PatchMapping("/{id}/importance")
    public ResponseEntity<Map<String, Object>> updateImportance(
            @PathVariable Long id,
            @RequestParam(value = "importance") Double importance) {
        Map<String, Object> response = new HashMap<>();
        try {
            response.put("success", true);
            response.put("data", artifactMemoryService.updateImportance(id, importance));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to update artifact memory importance {}: {}", id, e.getMessage(), e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteMemory(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            response.put("success", true);
            response.put("data", artifactMemoryService.deleteMemory(id));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to delete artifact memory {}: {}", id, e.getMessage(), e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
