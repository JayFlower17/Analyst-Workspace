package com.analysis.service;

import org.springframework.stereotype.Service;

import com.analysis.model.dto.DocumentChunkSearchResult;
import com.analysis.model.execution.RetrievalExecutionResult;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentRetrievalExecutor implements RetrievalExecutor {

    private final DocumentService documentService;

    @Override
    public RetrievalExecutionResult retrieve(Long groupId, String query, int topK) {
        long startTime = System.currentTimeMillis();
        try {
            List<DocumentChunkSearchResult> chunks = documentService.searchDocumentChunks(groupId, query, topK);
            RetrievalExecutionResult result = RetrievalExecutionResult.success(
                    groupId,
                    query,
                    topK,
                    chunks,
                    System.currentTimeMillis() - startTime);
            log.info("[RetrievalExecutor] groupId={} topK={} chunks={} mode={} durationMs={}",
                    groupId, topK, result.chunkCount(), result.retrievalMode(), result.durationMs());
            return result;
        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;
            log.warn("[RetrievalExecutor] failed groupId={} topK={} durationMs={} message={}",
                    groupId, topK, durationMs, e.getMessage());
            return RetrievalExecutionResult.failure(
                    groupId,
                    query,
                    topK,
                    "Document retrieval failed: " + e.getMessage(),
                    durationMs);
        }
    }
}
