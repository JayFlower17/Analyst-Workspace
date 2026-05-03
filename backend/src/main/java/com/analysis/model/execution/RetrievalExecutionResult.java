package com.analysis.model.execution;

import java.util.List;
import java.util.Objects;

import com.analysis.model.dto.DocumentChunkSearchResult;

public record RetrievalExecutionResult(
        boolean success,
        String message,
        Long groupId,
        String query,
        int topK,
        List<DocumentChunkSearchResult> chunks,
        int chunkCount,
        long durationMs,
        String retrievalMode,
        ToolExecutionLog executionLog) {

    public static RetrievalExecutionResult success(
            Long groupId,
            String query,
            int topK,
            List<DocumentChunkSearchResult> chunks,
            long durationMs) {
        List<DocumentChunkSearchResult> safeChunks = chunks == null ? List.of() : chunks;
        RetrievalExecutionResult result = new RetrievalExecutionResult(
                true,
                "Document retrieval completed.",
                groupId,
                query,
                topK,
                safeChunks,
                safeChunks.size(),
                durationMs,
                resolveRetrievalMode(safeChunks),
                null);
        return result.withExecutionLog(ToolExecutionLog.retrieval(result));
    }

    public static RetrievalExecutionResult failure(
            Long groupId,
            String query,
            int topK,
            String message,
            long durationMs) {
        RetrievalExecutionResult result = new RetrievalExecutionResult(
                false,
                message,
                groupId,
                query,
                topK,
                List.of(),
                0,
                durationMs,
                "none",
                null);
        return result.withExecutionLog(ToolExecutionLog.retrieval(result));
    }

    private static String resolveRetrievalMode(List<DocumentChunkSearchResult> chunks) {
        List<String> modes = chunks.stream()
                .map(DocumentChunkSearchResult::getRetrievalMode)
                .filter(Objects::nonNull)
                .filter(mode -> !mode.isBlank())
                .distinct()
                .toList();
        if (modes.isEmpty()) {
            return "none";
        }
        if (modes.size() == 1) {
            return modes.get(0);
        }
        return "mixed";
    }

    private RetrievalExecutionResult withExecutionLog(ToolExecutionLog executionLog) {
        return new RetrievalExecutionResult(
                success,
                message,
                groupId,
                query,
                topK,
                chunks,
                chunkCount,
                durationMs,
                retrievalMode,
                executionLog);
    }
}
