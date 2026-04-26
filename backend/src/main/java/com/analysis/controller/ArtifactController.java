package com.analysis.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
            @RequestParam(value = "limit", defaultValue = "5") int limit) {
        Map<String, Object> response = new HashMap<>();
        try {
            response.put("success", true);
            response.put("data", artifactService.getRecentArtifacts(sessionId, groupId, limit));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get recent artifacts: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
