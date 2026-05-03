package com.analysis.model.execution;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.analysis.model.dto.DocumentChunkSearchResult;

class ToolExecutionLogTest {

    @Test
    void createsSqlExecutionLogFromResult() {
        SqlExecutionResult result = SqlExecutionResult.success("select 1", List.of(Map.of("value", 1)), 12);

        ToolExecutionLog log = result.executionLog();

        assertThat(log.toolType()).isEqualTo(ToolExecutionType.SQL_EXECUTION);
        assertThat(log.stepType()).isEqualTo("EXECUTE_SQL");
        assertThat(log.success()).isTrue();
        assertThat(log.durationMs()).isEqualTo(12);
        assertThat(log.output()).containsEntry("rowCount", 1);
    }

    @Test
    void createsRetrievalExecutionLogFromResult() {
        DocumentChunkSearchResult chunk = new DocumentChunkSearchResult();
        chunk.setRetrievalMode("lexical");

        RetrievalExecutionResult result = RetrievalExecutionResult.success(1L, "policy", 2, List.of(chunk), 7);

        ToolExecutionLog log = result.executionLog();

        assertThat(log.toolType()).isEqualTo(ToolExecutionType.DOCUMENT_RETRIEVAL);
        assertThat(log.stepType()).isEqualTo("RETRIEVE_DOCUMENTS");
        assertThat(log.input()).containsEntry("groupId", 1L);
        assertThat(log.output()).containsEntry("chunkCount", 1);
        assertThat(log.output()).containsEntry("retrievalMode", "lexical");
    }
}
