package com.analysis.model.dto;

import java.util.List;
import java.util.Map;

import com.analysis.model.enums.ChartType;

import lombok.Data;

@Data
public class ChatAnalyzeResponse {
    private boolean success;
    private String message;
    private String mode;
    private String summary;
    private String generatedCodeOrSql;
    private ChartType recommendedChart;
    private List<Map<String, Object>> data;
    private Long artifactId;
    private Long executionTime;
}

