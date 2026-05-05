package com.analysis.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import com.analysis.model.entity.ArtifactMemory;
import com.analysis.persistence.AnalysisArtifactStore;

class ArtifactMemoryVectorServiceTest {

    @Test
    void indexesArtifactMemoryIntoVectorStoreWithMetadata() {
        AnalysisArtifactStore repository = mock(AnalysisArtifactStore.class);
        VectorStore vectorStore = mock(VectorStore.class);
        ObjectProvider<VectorStore> provider = vectorStoreProvider(vectorStore);
        ArtifactMemoryVectorService service = enabledService(repository, provider);

        ArtifactMemory memory = memory(7L);
        assertTrue(service.indexMemoryIfAvailable(memory));

        @SuppressWarnings({ "unchecked", "rawtypes" })
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(vectorStore).add(captor.capture());
        Document document = captor.getValue().get(0);

        assertTrue(document.getContent().contains("Revenue increased by category"));
        assertEquals("artifact_memory", document.getMetadata().get("type"));
        assertEquals(7L, document.getMetadata().get("memoryId"));
        assertEquals(1L, document.getMetadata().get("groupId"));
        assertEquals("ANALYSIS_FINDING", document.getMetadata().get("memoryType"));
    }

    @Test
    void searchesVectorStoreAndLoadsActiveMemoriesById() throws Exception {
        AnalysisArtifactStore repository = mock(AnalysisArtifactStore.class);
        VectorStore vectorStore = mock(VectorStore.class);
        ObjectProvider<VectorStore> provider = vectorStoreProvider(vectorStore);
        ArtifactMemoryVectorService service = enabledService(repository, provider);
        ArtifactMemory activeMemory = memory(7L);

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                new Document("document chunk", Map.of("type", "document_chunk", "groupId", 1L)),
                new Document("memory hit", Map.of(
                        "type", "artifact_memory",
                        "memoryId", 7L,
                        "groupId", 1L,
                        "distance", 0.18))));
        when(repository.findArtifactMemoryById(7L)).thenReturn(activeMemory);

        List<ArtifactMemory> results = service.searchMemories(1L, "revenue by category", 5);

        assertEquals(1, results.size());
        assertEquals(7L, results.get(0).getId());
        assertEquals("semantic_vector", results.get(0).getRetrievalMode());
        assertEquals(0.18, results.get(0).getRetrievalScore());
        verify(repository).findArtifactMemoryById(7L);
    }

    @Test
    void skipsVectorStoreWhenSemanticRetrievalIsDisabled() {
        AnalysisArtifactStore repository = mock(AnalysisArtifactStore.class);
        VectorStore vectorStore = mock(VectorStore.class);
        ObjectProvider<VectorStore> provider = vectorStoreProvider(vectorStore);
        ArtifactMemoryVectorService service = new ArtifactMemoryVectorService(repository, provider);
        ReflectionTestUtils.setField(service, "vectorStoreEnabled", true);
        ReflectionTestUtils.setField(service, "semanticRetrievalEnabled", false);

        assertEquals(List.of(), service.searchMemories(1L, "query", 5));
        verifyNoInteractions(vectorStore);
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<VectorStore> vectorStoreProvider(VectorStore vectorStore) {
        ObjectProvider<VectorStore> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(vectorStore);
        return provider;
    }

    private ArtifactMemoryVectorService enabledService(
            AnalysisArtifactStore repository,
            ObjectProvider<VectorStore> provider) {
        ArtifactMemoryVectorService service = new ArtifactMemoryVectorService(repository, provider);
        ReflectionTestUtils.setField(service, "vectorStoreEnabled", true);
        ReflectionTestUtils.setField(service, "semanticRetrievalEnabled", true);
        return service;
    }

    private ArtifactMemory memory(Long id) {
        ArtifactMemory memory = new ArtifactMemory();
        memory.setId(id);
        memory.setArtifactId(6L);
        memory.setGroupId(1L);
        memory.setDatasetId(2L);
        memory.setMemoryType("ANALYSIS_FINDING");
        memory.setScope("WORKSPACE_LOCAL");
        memory.setSummary("Revenue increased by category");
        memory.setContent("Category A produced the strongest revenue increase.");
        memory.setStatus("ACTIVE");
        return memory;
    }
}
