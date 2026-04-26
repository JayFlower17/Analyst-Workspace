package com.analysis.model.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class DocumentAsset {
    private Long id;
    private Long groupId;
    private String name;
    private String originalFileName;
    private String storedPath;
    private String fileType;
    private String mimeType;
    private Long sizeBytes;
    private String processingStatus;
    private String processingError;
    private Integer chunkCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
