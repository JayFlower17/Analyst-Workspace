package com.analysis.model.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class AnalysisArtifact {
    private Long id;
    private String mode;
    private Long sessionId;
    private Long groupId;
    private Long datasetId;
    private Long contextTraceId;
    private String userQuery;
    private String generatedCodeOrSql;
    private String summary;
    private String chartType;
    private String resultPreviewJson;
    private Integer artifactSchemaVersion;
    private String analysisReportJson;
    private String evidenceSummaryJson;
    private String executionLogsJson;
    private String validationReportJson;
    private String riskNoticesJson;
    private String artifactStatus;
    private LocalDateTime archivedAt;
    private LocalDateTime deletedAt;
    private LocalDateTime updatedAt;
    private LocalDateTime createdAt;
}
