package com.analysis.model.entity;

import java.time.LocalDateTime;

import com.analysis.model.enums.ChartType;

import lombok.Data;

@Data
public class AnalysisHistory {
    private Long id;
    private Long datasetId;
    private String userQuery;
    private String generatedSql;
    private String generatedCode;
    private String resultData;
    private ChartType chartType;
    private String summary;
    private LocalDateTime createdAt;
}
