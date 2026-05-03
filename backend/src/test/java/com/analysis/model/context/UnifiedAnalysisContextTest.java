package com.analysis.model.context;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.analysis.model.dto.DocumentChunkSearchResult;
import com.analysis.model.entity.Dataset;
import com.analysis.model.entity.DatasetRelation;

class UnifiedAnalysisContextTest {

    @Test
    void summaryCountsStructuredSemanticAndDocumentContext() {
        Dataset orders = new Dataset();
        orders.setId(1L);
        orders.setTableName("orders");

        Dataset users = new Dataset();
        users.setId(2L);
        users.setTableName("users");

        DatasetRelation relation = new DatasetRelation();
        relation.setSourceDatasetId(1L);
        relation.setTargetDatasetId(2L);

        DocumentChunkSearchResult chunk = new DocumentChunkSearchResult();
        chunk.setDocumentId(10L);
        chunk.setDocumentName("workspace-note.md");

        StructuredContext structured = new StructuredContext(
                7L,
                List.of(orders, users),
                List.of(relation),
                "schema prompt",
                "relation prompt");
        DocumentContext document = new DocumentContext(
                "HEAVY",
                4,
                2600,
                List.of(chunk),
                "document prompt");
        SemanticContext semantic = new SemanticContext("business prompt");

        UnifiedContextSummary summary = new UnifiedAnalysisContext(structured, document, semantic).summary();

        assertEquals(7L, summary.groupId());
        assertEquals("WORKSPACE_SCHEMA", summary.structuredSource());
        assertEquals("DOCUMENT_RETRIEVAL", summary.documentSource());
        assertEquals("WORKSPACE_METADATA", summary.semanticSource());
        assertEquals(2, summary.datasetCount());
        assertEquals(1, summary.relationCount());
        assertEquals(true, summary.hasSemanticContext());
        assertEquals("HEAVY", summary.documentStrategy());
        assertEquals(4, summary.documentTopK());
        assertEquals(2600, summary.documentCharBudget());
        assertEquals(1, summary.documentChunkCount());
        assertEquals("schema prompt".length(), summary.structuredPromptChars());
        assertEquals("relation prompt".length(), summary.relationPromptChars());
        assertEquals("business prompt".length(), summary.semanticPromptChars());
        assertEquals("document prompt".length(), summary.documentPromptChars());
    }

    @Test
    void summaryDefaultsWhenContextPartsAreMissing() {
        UnifiedContextSummary summary = new UnifiedAnalysisContext(null, null, null).summary();

        assertEquals(0L, summary.groupId());
        assertEquals("NONE", summary.structuredSource());
        assertEquals("NONE", summary.documentSource());
        assertEquals("NONE", summary.semanticSource());
        assertEquals(0, summary.datasetCount());
        assertEquals(0, summary.relationCount());
        assertEquals(false, summary.hasSemanticContext());
        assertEquals("NONE", summary.documentStrategy());
        assertEquals(0, summary.documentTopK());
        assertEquals(0, summary.documentCharBudget());
        assertEquals(0, summary.documentChunkCount());
        assertEquals(0, summary.structuredPromptChars());
        assertEquals(0, summary.relationPromptChars());
        assertEquals(0, summary.semanticPromptChars());
        assertEquals(0, summary.documentPromptChars());
    }
}
