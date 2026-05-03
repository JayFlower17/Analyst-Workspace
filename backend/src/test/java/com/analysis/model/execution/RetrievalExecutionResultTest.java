package com.analysis.model.execution;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.analysis.model.dto.DocumentChunkSearchResult;

class RetrievalExecutionResultTest {

    @Test
    void buildsSuccessfulResultWithSingleRetrievalMode() {
        DocumentChunkSearchResult chunk = chunk("vector");

        RetrievalExecutionResult result = RetrievalExecutionResult.success(8L, "policy", 3, List.of(chunk), 42);

        assertThat(result.success()).isTrue();
        assertThat(result.groupId()).isEqualTo(8L);
        assertThat(result.query()).isEqualTo("policy");
        assertThat(result.topK()).isEqualTo(3);
        assertThat(result.chunks()).hasSize(1);
        assertThat(result.chunkCount()).isEqualTo(1);
        assertThat(result.durationMs()).isEqualTo(42);
        assertThat(result.retrievalMode()).isEqualTo("vector");
    }

    @Test
    void marksMixedModeWhenChunksComeFromDifferentRetrievalModes() {
        RetrievalExecutionResult result = RetrievalExecutionResult.success(
                8L,
                "policy",
                4,
                List.of(chunk("vector"), chunk("lexical")),
                12);

        assertThat(result.retrievalMode()).isEqualTo("mixed");
    }

    @Test
    void buildsFailureResultWithEmptyChunks() {
        RetrievalExecutionResult result = RetrievalExecutionResult.failure(
                8L,
                "policy",
                4,
                "Document retrieval failed: timeout",
                9);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("Document retrieval failed: timeout");
        assertThat(result.chunks()).isEmpty();
        assertThat(result.chunkCount()).isZero();
        assertThat(result.retrievalMode()).isEqualTo("none");
    }

    private DocumentChunkSearchResult chunk(String retrievalMode) {
        DocumentChunkSearchResult chunk = new DocumentChunkSearchResult();
        chunk.setDocumentName("policy.md");
        chunk.setChunkIndex(0);
        chunk.setChunkText("Policy text");
        chunk.setRetrievalMode(retrievalMode);
        return chunk;
    }
}
