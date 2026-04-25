package com.analysis.model.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class DatasetGroup {
    private Long id;
    private String name;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
