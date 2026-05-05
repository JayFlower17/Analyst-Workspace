package com.analysis.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.analysis.service.ContextTraceService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/context-traces")
@RequiredArgsConstructor
public class ContextTraceController {

    private final ContextTraceService contextTraceService;

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getContextTrace(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            response.put("success", true);
            response.put("data", contextTraceService.getContextTrace(id));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get context trace {}: {}", id, e.getMessage(), e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/recent")
    public ResponseEntity<Map<String, Object>> getRecentContextTraces(
            @RequestParam(value = "groupId") Long groupId,
            @RequestParam(value = "limit", defaultValue = "5") int limit) {
        Map<String, Object> response = new HashMap<>();
        try {
            response.put("success", true);
            response.put("data", contextTraceService.getRecentContextTraces(groupId, limit));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get recent context traces for group {}: {}", groupId, e.getMessage(), e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
