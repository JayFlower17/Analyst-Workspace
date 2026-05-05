package com.analysis.service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.analysis.model.entity.ArtifactMemory;
import com.analysis.persistence.AnalysisArtifactStore;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArtifactMemoryVectorService {

    private static final String MEMORY_VECTOR_TYPE = "artifact_memory";

    private final AnalysisArtifactStore analysisArtifactStore;
    private final ObjectProvider<VectorStore> vectorStoreProvider;

    @Value("${app.vector-store.enabled:false}")
    private boolean vectorStoreEnabled;

    @Value("${app.memory.semantic-retrieval.enabled:true}")
    private boolean semanticRetrievalEnabled;

    public boolean indexMemoryIfAvailable(ArtifactMemory memory) {
        if (!vectorStoreEnabled || !semanticRetrievalEnabled || memory == null || memory.getId() == null) {
            return false;
        }
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        String content = buildMemoryDocumentContent(memory);
        if (vectorStore == null || content.isBlank()) {
            return false;
        }

        try {
            vectorStore.add(List.of(new Document(content, buildMetadata(memory))));
            log.info("[MemoryVector] Indexed artifact memory {} into Vector Store", memory.getId());
            return true;
        } catch (Exception e) {
            log.warn("[MemoryVector] Failed to index artifact memory {}: {}", memory.getId(), e.getMessage());
            return false;
        }
    }

    public List<ArtifactMemory> searchMemories(Long groupId, String query, int topK) {
        if (!vectorStoreEnabled || !semanticRetrievalEnabled || groupId == null || query == null || query.isBlank()) {
            return List.of();
        }
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            return List.of();
        }

        try {
            SearchRequest searchRequest = SearchRequest.query(query)
                    .withTopK(Math.min(Math.max(topK, 1) * 8, 40))
                    .withFilterExpression("groupId == " + groupId);
            List<Document> documents = vectorStore.similaritySearch(searchRequest);
            if (documents == null || documents.isEmpty()) {
                return List.of();
            }
            return loadActiveMemoryMatches(documents, topK);
        } catch (Exception e) {
            log.warn("[MemoryVector] Semantic memory search failed for group {}: {}", groupId, e.getMessage());
            return List.of();
        }
    }

    private List<ArtifactMemory> loadActiveMemoryMatches(List<Document> documents, int topK) throws SQLException {
        Map<Long, ArtifactMemory> deduped = new LinkedHashMap<>();
        for (Document document : documents) {
            Map<String, Object> metadata = document.getMetadata();
            if (!MEMORY_VECTOR_TYPE.equals(asString(metadata.get("type")))) {
                continue;
            }
            Long memoryId = asLong(metadata.get("memoryId"));
            if (memoryId == null || deduped.containsKey(memoryId)) {
                continue;
            }
            ArtifactMemory memory = analysisArtifactStore.findArtifactMemoryById(memoryId);
            if (memory != null && "ACTIVE".equalsIgnoreCase(memory.getStatus())) {
                memory.setRetrievalMode("semantic_vector");
                memory.setRetrievalScore(asDouble(metadata.get("distance")));
                memory.setRetrievalReason("Matched by VectorStore semantic similarity.");
                deduped.put(memoryId, memory);
            }
            if (deduped.size() >= Math.max(1, topK)) {
                break;
            }
        }
        return new ArrayList<>(deduped.values());
    }

    private String buildMemoryDocumentContent(ArtifactMemory memory) {
        StringBuilder content = new StringBuilder();
        appendLine(content, "Memory type", memory.getMemoryType());
        appendLine(content, "Scope", memory.getScope());
        appendLine(content, "Summary", memory.getSummary());
        appendLine(content, "Content", memory.getContent());
        return content.toString().trim();
    }

    private Map<String, Object> buildMetadata(ArtifactMemory memory) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("type", MEMORY_VECTOR_TYPE);
        metadata.put("memoryId", memory.getId());
        putIfPresent(metadata, "artifactId", memory.getArtifactId());
        putIfPresent(metadata, "groupId", memory.getGroupId());
        putIfPresent(metadata, "datasetId", memory.getDatasetId());
        putIfPresent(metadata, "memoryType", memory.getMemoryType());
        putIfPresent(metadata, "scope", memory.getScope());
        metadata.put("status", memory.getStatus() != null ? memory.getStatus() : "ACTIVE");
        return metadata;
    }

    private void appendLine(StringBuilder builder, String label, String value) {
        if (value != null && !value.isBlank()) {
            builder.append(label).append(": ").append(value.trim()).append('\n');
        }
    }

    private void putIfPresent(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }

    private String asString(Object value) {
        return value != null ? value.toString() : null;
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double asDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
