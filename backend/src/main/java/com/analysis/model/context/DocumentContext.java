package com.analysis.model.context;

import java.util.List;

import com.analysis.model.dto.DocumentChunkSearchResult;

public record DocumentContext(
        String strategy,
        int topK,
        int charBudget,
        List<DocumentChunkSearchResult> chunks,
        String prompt) {

    public String source() {
        return "DOCUMENT_RETRIEVAL";
    }

    public int chunkCount() {
        return chunks != null ? chunks.size() : 0;
    }

    public int promptChars() {
        return prompt != null ? prompt.length() : 0;
    }
}
