package com.analysis.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.analysis.service.ArtifactService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/artifacts")
@RequiredArgsConstructor
public class ArtifactController {

    private final ArtifactService artifactService;

    @GetMapping("/recent")
    public ResponseEntity<Map<String, Object>> getRecentArtifacts(
            @RequestParam(value = "sessionId", required = false) Long sessionId,
            @RequestParam(value = "groupId", required = false) Long groupId,
            @RequestParam(value = "limit", defaultValue = "5") int limit,
            @RequestParam(value = "status", defaultValue = "ACTIVE") String status) {
        Map<String, Object> response = new HashMap<>();
        try {
            response.put("success", true);
            response.put("data", artifactService.getRecentArtifacts(sessionId, groupId, limit, status));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get recent artifacts: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getArtifactDetail(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            response.put("success", true);
            response.put("data", artifactService.getArtifactDetail(id));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get artifact detail: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PatchMapping("/{id}/archive")
    public ResponseEntity<Map<String, Object>> archiveArtifact(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            response.put("success", true);
            response.put("data", artifactService.archiveArtifact(id));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to archive artifact: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PatchMapping("/{id}/restore")
    public ResponseEntity<Map<String, Object>> restoreArtifact(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            response.put("success", true);
            response.put("data", artifactService.restoreArtifact(id));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to restore artifact: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteArtifact(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            response.put("success", true);
            response.put("data", artifactService.deleteArtifact(id));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to delete artifact: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
