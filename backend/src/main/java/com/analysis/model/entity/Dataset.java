package com.analysis.model.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class Dataset {
    private Long id;
    private Long groupId;
    private String name;
    private String descriptionMd;
    private String tableName;
    private String originalFileName;
    private Long rowCount;
    private Integer columnCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
