package com.analysis.service;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.analysis.model.dto.AnalysisRequest;
import com.analysis.model.dto.DocumentChunkSearchResult;
import com.analysis.model.context.DocumentContext;
import com.analysis.model.context.MemoryContext;
import com.analysis.model.context.SemanticContext;
import com.analysis.model.context.StructuredContext;
import com.analysis.model.context.UnifiedAnalysisContext;
import com.analysis.model.entity.ArtifactMemory;
import com.analysis.model.entity.ContextTrace;
import com.analysis.model.execution.RetrievalExecutionResult;
import com.analysis.model.execution.ToolExecutionLog;
import com.analysis.persistence.AnalysisArtifactStore;
import com.analysis.service.WorkspaceSchemaService.WorkspaceSchemaContext;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkspaceContextAssembler {

    private static final int DEFAULT_DOC_TOP_K = 2;
    private static final int DOC_HEAVY_TOP_K = 4;
    private static final int DEFAULT_DOC_CHAR_BUDGET = 1600;
    private static final int DOC_HEAVY_CHAR_BUDGET = 2600;
    private static final int MEMORY_CANDIDATE_LIMIT = 30;
    private static final int MEMORY_TOP_K = 5;
    private static final int MEMORY_CHAR_BUDGET = 1400;

    private final RetrievalExecutor retrievalExecutor;
    private final AnalysisArtifactStore analysisArtifactStore;
    private final ObjectMapper objectMapper;
    private final ArtifactMemoryVectorService artifactMemoryVectorService;

    public record WorkspaceAnalysisContext(
            String businessContextPrompt,
            String schemaPrompt,
            String relationPrompt,
            String documentContextPrompt,
            String documentStrategy,
            List<ToolExecutionLog> executionLogs,
            Long contextTraceId,
            UnifiedAnalysisContext unifiedContext) {
    }

    public WorkspaceAnalysisContext assemble(AnalysisRequest request, WorkspaceSchemaContext schemaContext)
            throws SQLException {
        DocumentContextBuildResult documentBuildResult = buildDocumentContext(request);
        DocumentContext documentContext = documentBuildResult.documentContext();
        MemoryContext memoryContext = buildMemoryContext(request);
        StructuredContext structuredContext = new StructuredContext(
                schemaContext.groupId(),
                schemaContext.datasets(),
                schemaContext.relations(),
                schemaContext.schemaPrompt(),
                schemaContext.relationPrompt());
        SemanticContext semanticContext = new SemanticContext(schemaContext.businessContextPrompt());
        UnifiedAnalysisContext unifiedContext = new UnifiedAnalysisContext(
                structuredContext,
                documentContext,
                semanticContext,
                memoryContext);
        Long contextTraceId = saveContextTraceSafely(request, unifiedContext);
        log.info("[ContextAssembler] Unified context summary: {}", unifiedContext.summary());

        return new WorkspaceAnalysisContext(
                unifiedContext.businessContextPrompt(),
                unifiedContext.schemaPrompt(),
                unifiedContext.relationPrompt(),
                unifiedContext.documentPrompt(),
                documentContext.strategy(),
                documentBuildResult.executionLogs(),
                contextTraceId,
                unifiedContext);
    }

    private Long saveContextTraceSafely(AnalysisRequest request, UnifiedAnalysisContext context) {
        if (request.getGroupId() == null || context == null) {
            return null;
        }
        try {
            ContextTrace trace = new ContextTrace();
            trace.setGroupId(request.getGroupId());
            trace.setQuery(request.getQuery());
            trace.setSelectedSchemaIdsJson(objectMapper.writeValueAsString(selectedDatasetIds(context)));
            trace.setSelectedDocumentChunkIdsJson(objectMapper.writeValueAsString(selectedDocumentChunkRefs(context)));
            trace.setSelectedMemoryIdsJson(objectMapper.writeValueAsString(selectedMemoryIds(context)));
            trace.setFilteredItemsJson(objectMapper.writeValueAsString(Map.of(
                    "contextSummary", context.summary(),
                    "focusDatasetIds", request.getFocusDatasetIds() != null ? request.getFocusDatasetIds() : List.of(),
                    "selectedMemories", selectedMemoryDetails(context))));
            trace.setPackedContext(buildPackedContextSnapshot(context));
            return analysisArtifactStore.saveContextTrace(trace);
        } catch (Exception e) {
            log.warn("[ContextAssembler] Failed to save context trace: {}", e.getMessage());
            return null;
        }
    }

    private List<Long> selectedDatasetIds(UnifiedAnalysisContext context) {
        if (context.structured() == null || context.structured().datasets() == null) {
            return List.of();
        }
        return context.structured().datasets().stream()
                .map(dataset -> dataset.getId())
                .filter(id -> id != null)
                .toList();
    }

    private List<String> selectedDocumentChunkRefs(UnifiedAnalysisContext context) {
        if (context.document() == null || context.document().chunks() == null) {
            return List.of();
        }
        return context.document().chunks().stream()
                .map(chunk -> {
                    String documentId = chunk.getDocumentId() != null ? chunk.getDocumentId().toString() : "unknown";
                    String chunkIndex = chunk.getChunkIndex() != null ? chunk.getChunkIndex().toString() : "?";
                    String endChunkIndex = chunk.getEndChunkIndex() != null
                            && !chunk.getEndChunkIndex().equals(chunk.getChunkIndex())
                                    ? "-" + chunk.getEndChunkIndex()
                                    : "";
                    return documentId + ":" + chunkIndex + endChunkIndex;
                })
                .toList();
    }

    private List<Long> selectedMemoryIds(UnifiedAnalysisContext context) {
        if (context.memory() == null || context.memory().memories() == null) {
            return List.of();
        }
        return context.memory().memories().stream()
                .map(ArtifactMemory::getId)
                .filter(id -> id != null)
                .toList();
    }

    private List<Map<String, Object>> selectedMemoryDetails(UnifiedAnalysisContext context) {
        if (context.memory() == null || context.memory().memories() == null) {
            return List.of();
        }
        return context.memory().memories().stream()
                .map(memory -> {
                    Map<String, Object> detail = new java.util.LinkedHashMap<>();
                    putIfPresent(detail, "id", memory.getId());
                    putIfPresent(detail, "memoryType", memory.getMemoryType());
                    putIfPresent(detail, "scope", memory.getScope());
                    putIfPresent(detail, "retrievalMode", memory.getRetrievalMode());
                    putIfPresent(detail, "retrievalScore", memory.getRetrievalScore());
                    putIfPresent(detail, "retrievalReason", memory.getRetrievalReason());
                    putIfPresent(detail, "importance", memory.getImportance());
                    putIfPresent(detail, "confidence", memory.getConfidence());
                    putIfPresent(detail, "summary", selectedMemorySummary(memory));
                    return detail;
                })
                .toList();
    }

    private String selectedMemorySummary(ArtifactMemory memory) {
        if (memory == null) {
            return null;
        }
        String summary = memory.getSummary() != null && !memory.getSummary().isBlank()
                ? memory.getSummary()
                : memory.getContent();
        return summary != null && !summary.isBlank() ? clipText(summary, 240) : null;
    }

    private void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private String buildPackedContextSnapshot(UnifiedAnalysisContext context) {
        String packed = """
                [Business Context]
                %s

                [Schema]
                %s

                [Relations]
                %s

                [Documents]
                %s

                [Memories]
                %s
                """.formatted(
                context.businessContextPrompt(),
                context.schemaPrompt(),
                context.relationPrompt(),
                context.documentPrompt(),
                context.memoryPrompt());
        return clipText(packed.trim(), 8000);
    }

    private MemoryContext buildMemoryContext(AnalysisRequest request) throws SQLException {
        if (request.getGroupId() == null) {
            return MemoryContext.empty("No workspace artifact memory context available.");
        }

        List<ArtifactMemory> candidates = analysisArtifactStore.findActiveArtifactMemoriesByGroupId(
                request.getGroupId(),
                MEMORY_CANDIDATE_LIMIT);
        if (candidates.isEmpty()) {
            return MemoryContext.empty("No relevant artifact memories were found for this workspace.");
        }

        List<String> terms = memoryTerms(request.getQuery());
        List<ArtifactMemory> semanticMatches = artifactMemoryVectorService.searchMemories(
                request.getGroupId(),
                request.getQuery(),
                MEMORY_TOP_K);
        List<ArtifactMemory> selected = new java.util.ArrayList<>();
        String strategy = "LEXICAL_RECENT";

        if (!semanticMatches.isEmpty()) {
            selected.addAll(semanticMatches);
            java.util.Set<Long> selectedIds = semanticMatches.stream()
                    .map(ArtifactMemory::getId)
                    .filter(id -> id != null)
                    .collect(java.util.stream.Collectors.toSet());
            rankLexicalMemories(candidates, terms).stream()
                    .filter(memory -> memory.getId() == null || !selectedIds.contains(memory.getId()))
                    .limit(Math.max(0, MEMORY_TOP_K - selected.size()))
                    .map(memory -> withRetrievalMetadata(
                            memory,
                            "lexical_fill",
                            scoreMemory(memory, terms),
                            "Filled remaining memory budget after semantic memory retrieval."))
                    .forEach(selected::add);
            strategy = selected.size() > semanticMatches.size()
                    ? "SEMANTIC_VECTOR_LEXICAL_FILL"
                    : "SEMANTIC_VECTOR";
        } else {
            selected = rankLexicalMemories(candidates, terms).stream()
                    .map(memory -> withRetrievalMetadata(
                            memory,
                            "lexical_recent",
                            scoreMemory(memory, terms),
                            "Matched query terms and memory quality signals."))
                    .limit(MEMORY_TOP_K)
                    .toList();
        }

        if (selected.isEmpty()) {
            selected = candidates.stream()
                    .map(memory -> withRetrievalMetadata(
                            memory,
                            "recent_fallback",
                            memory.getImportance(),
                            "No query terms matched; selected recent active artifact memory."))
                    .limit(Math.min(MEMORY_TOP_K, candidates.size()))
                    .toList();
            strategy = "RECENT_FALLBACK";
        }

        StringBuilder prompt = new StringBuilder();
        int remainingBudget = MEMORY_CHAR_BUDGET;
        List<ArtifactMemory> included = new java.util.ArrayList<>();
        for (ArtifactMemory memory : selected) {
            String block = formatMemoryBlock(memory);
            if (block.isBlank()) {
                continue;
            }
            if (block.length() > remainingBudget) {
                block = clipText(block, remainingBudget);
            }
            if (block.isBlank()) {
                break;
            }
            if (prompt.length() > 0) {
                prompt.append("\n");
            }
            prompt.append(block);
            remainingBudget -= block.length() + 1;
            included.add(memory);
            if (remainingBudget <= 0) {
                break;
            }
        }

        if (included.isEmpty()) {
            return MemoryContext.empty("Artifact memories were found, but none fit within the current memory budget.");
        }
        recordMemoryUsageSafely(included);
        return new MemoryContext(strategy, MEMORY_TOP_K, MEMORY_CHAR_BUDGET, included, prompt.toString());
    }

    private List<ArtifactMemory> rankLexicalMemories(List<ArtifactMemory> candidates, List<String> terms) {
        return candidates.stream()
                .sorted(Comparator
                        .comparingDouble((ArtifactMemory memory) -> scoreMemory(memory, terms)).reversed()
                        .thenComparing(ArtifactMemory::getImportance, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ArtifactMemory::getUseCount, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ArtifactMemory::getLastUsedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ArtifactMemory::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .filter(memory -> terms.isEmpty() || scoreMemory(memory, terms) > 0)
                .toList();
    }

    private ArtifactMemory withRetrievalMetadata(
            ArtifactMemory memory,
            String mode,
            Double score,
            String reason) {
        if (memory != null) {
            memory.setRetrievalMode(mode);
            memory.setRetrievalScore(score);
            memory.setRetrievalReason(reason);
        }
        return memory;
    }

    private void recordMemoryUsageSafely(List<ArtifactMemory> memories) {
        try {
            int updated = analysisArtifactStore.recordArtifactMemoryUsage(memories.stream()
                    .map(ArtifactMemory::getId)
                    .filter(id -> id != null)
                    .toList());
            log.info("[ContextAssembler] Recorded usage for {} artifact memories", updated);
        } catch (Exception e) {
            log.warn("[ContextAssembler] Failed to record artifact memory usage: {}", e.getMessage());
        }
    }

    private List<String> memoryTerms(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(query.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}_]+"))
                .filter(token -> token.length() >= 2)
                .filter(token -> !List.of("the", "and", "for", "with", "from", "this", "that", "what", "how",
                        "统计", "分析", "根据", "使用").contains(token))
                .distinct()
                .toList();
    }

    private double scoreMemory(ArtifactMemory memory, List<String> terms) {
        if (memory == null || terms == null || terms.isEmpty()) {
            return memory != null && memory.getImportance() != null ? memory.getImportance() : 0.0;
        }
        String text = ((memory.getMemoryType() != null ? memory.getMemoryType() : "") + "\n"
                + (memory.getSummary() != null ? memory.getSummary() : "") + "\n"
                + (memory.getContent() != null ? memory.getContent() : "")).toLowerCase(Locale.ROOT);
        double score = memory.getImportance() != null ? memory.getImportance() * 0.5 : 0.0;
        for (String term : terms) {
            if (text.contains(term)) {
                score += 1.0;
            }
        }
        return score;
    }

    private String formatMemoryBlock(ArtifactMemory memory) {
        if (memory == null) {
            return "";
        }
        String body = memory.getSummary() != null && !memory.getSummary().isBlank()
                ? memory.getSummary()
                : memory.getContent();
        if (body == null || body.isBlank()) {
            return "";
        }
        String retrieval = memory.getRetrievalMode() != null
                ? ", retrieval=" + memory.getRetrievalMode()
                        + (memory.getRetrievalScore() != null ? "/" + String.format("%.2f", memory.getRetrievalScore()) : "")
                : "";
        return "- type=" + valueOrDefault(memory.getMemoryType(), "UNKNOWN")
                + ", scope=" + valueOrDefault(memory.getScope(), "UNKNOWN")
                + ", confidence=" + (memory.getConfidence() != null ? String.format("%.2f", memory.getConfidence()) : "-")
                + retrieval
                + ": " + clipText(body.trim(), 420);
    }

    private String valueOrDefault(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }

    private DocumentContextBuildResult buildDocumentContext(AnalysisRequest request) throws SQLException {
        if (request.getGroupId() == null) {
            return new DocumentContextBuildResult(emptyDocumentContext(
                    DocumentNeed.SKIP,
                    "No workspace document context available."), List.of());
        }

        DocumentNeed documentNeed = classifyDocumentNeed(request.getQuery());
        if (documentNeed == DocumentNeed.SKIP) {
            log.info("[ContextAssembler] Skipping document retrieval for query: {}", request.getQuery());
            return new DocumentContextBuildResult(emptyDocumentContext(
                    documentNeed,
                    "Document context omitted for this query because it appears to be a direct structured aggregation request."), List.of());
        }

        int topK = documentNeed == DocumentNeed.HEAVY ? DOC_HEAVY_TOP_K : DEFAULT_DOC_TOP_K;
        int charBudget = documentNeed == DocumentNeed.HEAVY ? DOC_HEAVY_CHAR_BUDGET : DEFAULT_DOC_CHAR_BUDGET;

        RetrievalExecutionResult retrievalResult = retrievalExecutor.retrieve(
                request.getGroupId(),
                request.getQuery(),
                topK);
        if (!retrievalResult.success()) {
            log.warn("[ContextAssembler] Document retrieval failed for query: {} | {}",
                    request.getQuery(), retrievalResult.message());
            return new DocumentContextBuildResult(new DocumentContext(
                    documentNeed.name(),
                    topK,
                    charBudget,
                    List.of(),
                    retrievalResult.message()), List.of(retrievalResult.executionLog()));
        }

        List<DocumentChunkSearchResult> results = retrievalResult.chunks();
        results = prioritizeForPrompt(results, documentNeed);

        if (results.isEmpty()) {
            log.info("[ContextAssembler] No document context found for query: {}", request.getQuery());
            return new DocumentContextBuildResult(new DocumentContext(
                    documentNeed.name(),
                    topK,
                    charBudget,
                    List.of(),
                    "No relevant document context found."), List.of(retrievalResult.executionLog()));
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Retrieved Document Chunks:\n");
        int remainingBudget = charBudget;
        int includedCount = 0;
        List<DocumentChunkSearchResult> includedChunks = new java.util.ArrayList<>();

        for (DocumentChunkSearchResult result : results) {
            if (remainingBudget <= 0) {
                break;
            }

            String header = buildChunkHeader(result);
            String chunkText = result.getChunkText() != null ? result.getChunkText().trim() : "";
            if (chunkText.isBlank()) {
                continue;
            }

            int allowedChunkChars = Math.max(0, remainingBudget - header.length() - 2);
            if (allowedChunkChars <= 0) {
                break;
            }

            String clipped = clipText(chunkText, allowedChunkChars);
            sb.append(header).append("\n");
            sb.append(clipped).append("\n\n");
            remainingBudget -= (header.length() + clipped.length() + 2);
            includedCount++;
            includedChunks.add(result);
        }

        log.info("[ContextAssembler] Included {} document context blocks using {} strategy with {} char budget for query: {}",
                includedCount, documentNeed.name(), charBudget, request.getQuery());

        if (includedCount == 0) {
            return new DocumentContextBuildResult(new DocumentContext(
                    documentNeed.name(),
                    topK,
                    charBudget,
                    List.of(),
                    "Relevant document chunks were found, but none fit within the current context budget."),
                    List.of(retrievalResult.executionLog()));
        }

        if (remainingBudget <= 0) {
            sb.append("(Document context truncated to fit prompt budget)");
        }

        return new DocumentContextBuildResult(new DocumentContext(
                documentNeed.name(),
                topK,
                charBudget,
                includedChunks,
                sb.toString().trim()),
                List.of(retrievalResult.executionLog()));
    }

    private DocumentContext emptyDocumentContext(DocumentNeed documentNeed, String prompt) {
        return new DocumentContext(
                documentNeed.name(),
                0,
                0,
                List.of(),
                prompt);
    }

    private String buildChunkHeader(DocumentChunkSearchResult result) {
        StringBuilder header = new StringBuilder();
        header.append("- document: ").append(result.getDocumentName())
                .append(" [").append(result.getFileType() != null ? result.getFileType() : "UNKNOWN").append("]")
                .append(", chunkRange=").append(result.getChunkIndex());
        if (result.getEndChunkIndex() != null && !result.getEndChunkIndex().equals(result.getChunkIndex())) {
            header.append("-").append(result.getEndChunkIndex());
        }
        header.append(", retrievalMode=").append(result.getRetrievalMode());
        if (result.getDocumentRole() != null) {
            header.append(", role=").append(result.getDocumentRole());
        }
        if (result.getQueryIntent() != null) {
            header.append(", intent=").append(result.getQueryIntent());
        }
        if (result.getSourceChunkCount() != null) {
            header.append(", sourceChunkCount=").append(result.getSourceChunkCount());
        }
        if (result.getScore() != null) {
            header.append(", score=").append(String.format("%.2f", result.getScore()));
        }
        return header.toString();
    }

    private List<DocumentChunkSearchResult> prioritizeForPrompt(List<DocumentChunkSearchResult> results, DocumentNeed documentNeed) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }

        return results.stream()
                .sorted(Comparator
                        .comparingInt((DocumentChunkSearchResult result) -> promptPriority(result, documentNeed))
                        .thenComparing(DocumentChunkSearchResult::getRerankScore,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(DocumentChunkSearchResult::getScore,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private int promptPriority(DocumentChunkSearchResult result, DocumentNeed documentNeed) {
        String role = result.getDocumentRole() != null ? result.getDocumentRole() : "GENERIC";
        return switch (documentNeed) {
            case HEAVY -> switch (role) {
                case "RULE_NOTE" -> 0;
                case "REFERENCE" -> 1;
                case "GUIDE" -> 2;
                default -> 3;
            };
            case LIGHT -> switch (role) {
                case "REFERENCE" -> 0;
                case "GUIDE" -> 1;
                case "RULE_NOTE" -> 2;
                default -> 3;
            };
            case SKIP -> 9;
        };
    }

    private String clipText(String text, int maxChars) {
        if (text.length() <= maxChars) {
            return text;
        }
        if (maxChars <= 3) {
            return text.substring(0, Math.max(0, maxChars));
        }
        return text.substring(0, maxChars - 3).trim() + "...";
    }

    private DocumentNeed classifyDocumentNeed(String query) {
        String normalized = query == null ? "" : query.toLowerCase(Locale.ROOT);
        boolean mentionsDocumentSignals = containsAny(normalized,
                "according to", "based on", "workspace note", "note", "document", "docs", "policy", "rule",
                "definition", "explain", "why", "meaning", "highlight", "priority", "business context",
                "文档", "说明", "规则", "口径", "根据", "解释", "为什么", "优先", "高亮", "备注");
        boolean structuredOnlySignals = containsAny(normalized,
                "count", "sum", "avg", "average", "group by", "trend", "monthly", "daily", "top", "compare",
                "统计", "汇总", "求和", "平均", "趋势", "按月", "按天", "排序", "分组");

        if (mentionsDocumentSignals) {
            return DocumentNeed.HEAVY;
        }
        if (structuredOnlySignals) {
            return DocumentNeed.SKIP;
        }
        return DocumentNeed.LIGHT;
    }

    private boolean containsAny(String query, String... needles) {
        for (String needle : needles) {
            if (query.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private enum DocumentNeed {
        SKIP,
        LIGHT,
        HEAVY
    }

    private record DocumentContextBuildResult(
            DocumentContext documentContext,
            List<ToolExecutionLog> executionLogs) {
    }
}
