package com.analysis.model.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class DocumentChunk {
    private Long id;
    private Long documentId;
    private Integer chunkIndex;
    private String chunkText;
    private String metadataJson;
    private LocalDateTime createdAt;
}
