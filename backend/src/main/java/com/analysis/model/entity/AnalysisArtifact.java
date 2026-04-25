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
    private String userQuery;
    private String generatedCodeOrSql;
    private String summary;
    private String chartType;
    private String resultPreviewJson;
    private LocalDateTime createdAt;
}

