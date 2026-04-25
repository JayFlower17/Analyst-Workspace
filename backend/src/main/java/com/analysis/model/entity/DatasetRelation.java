package com.analysis.model.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class DatasetRelation {
    private Long id;
    private Long groupId;
    private Long sourceDatasetId;
    private String sourceTableName;
    private String sourceColumnName;
    private Long targetDatasetId;
    private String targetTableName;
    private String targetColumnName;
    private String relationType;
    private Double confidence;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
