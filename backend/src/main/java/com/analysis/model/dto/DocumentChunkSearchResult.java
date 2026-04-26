package com.analysis.model.dto;

import lombok.Data;

@Data
public class DocumentChunkSearchResult {
    private Long documentId;
    private String documentName;
    private String fileType;
    private Integer chunkIndex;
    private Integer endChunkIndex;
    private Integer sourceChunkCount;
    private String chunkText;
    private Double score;
    private Double rerankScore;
    private String rerankNotes;
    private String queryIntent;
    private String documentRole;
    private String retrievalMode;
}
