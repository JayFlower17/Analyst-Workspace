package com.analysis.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.analysis.model.dto.AnalysisRequest;
import com.analysis.model.dto.AnalysisResponse;
import com.analysis.service.AnalysisService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;

    @PostMapping("/query")
    public ResponseEntity<AnalysisResponse> analyze(@Valid @RequestBody AnalysisRequest request) {
        log.info("Received analysis request: {}", request.getQuery());
        AnalysisResponse response = analysisService.analyze(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/preview/{datasetId}")
    public ResponseEntity<Map<String, Object>> previewData(
            @PathVariable("datasetId") Long datasetId,
            @RequestParam(name = "limit", defaultValue = "100") int limit) {

        Map<String, Object> response = new HashMap<>();

        try {
            List<Map<String, Object>> data = analysisService.previewData(datasetId, limit);
            response.put("success", true);
            response.put("data", data);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to preview data: {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
