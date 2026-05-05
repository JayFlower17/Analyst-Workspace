package com.analysis.model.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class ContextTrace {
    private Long id;
    private Long groupId;
    private Long sessionId;
    private String query;
    private String selectedSchemaIdsJson;
    private String selectedDocumentChunkIdsJson;
    private String selectedMemoryIdsJson;
    private String filteredItemsJson;
    private String packedContext;
    private LocalDateTime createdAt;
}
