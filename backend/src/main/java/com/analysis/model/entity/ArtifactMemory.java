package com.analysis.model.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class ArtifactMemory {
    private Long id;
    private Long artifactId;
    private Long groupId;
    private Long datasetId;
    private String memoryType;
    private String scope;
    private String content;
    private String summary;
    private Double importance;
    private Double confidence;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastUsedAt;
    private Long useCount;
    private String retrievalMode;
    private Double retrievalScore;
    private String retrievalReason;
}
