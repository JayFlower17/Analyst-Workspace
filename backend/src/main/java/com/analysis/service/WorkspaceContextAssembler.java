package com.analysis.service;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.analysis.model.dto.AnalysisRequest;
import com.analysis.model.dto.DocumentChunkSearchResult;
import com.analysis.model.context.DocumentContext;
import com.analysis.model.context.SemanticContext;
import com.analysis.model.context.StructuredContext;
import com.analysis.model.context.UnifiedAnalysisContext;
import com.analysis.model.execution.RetrievalExecutionResult;
import com.analysis.model.execution.ToolExecutionLog;
import com.analysis.service.WorkspaceSchemaService.WorkspaceSchemaContext;

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

    private final RetrievalExecutor retrievalExecutor;

    public record WorkspaceAnalysisContext(
            String businessContextPrompt,
            String schemaPrompt,
            String relationPrompt,
            String documentContextPrompt,
            String documentStrategy,
            List<ToolExecutionLog> executionLogs,
            UnifiedAnalysisContext unifiedContext) {
    }

    public WorkspaceAnalysisContext assemble(AnalysisRequest request, WorkspaceSchemaContext schemaContext)
            throws SQLException {
        DocumentContextBuildResult documentBuildResult = buildDocumentContext(request);
        DocumentContext documentContext = documentBuildResult.documentContext();
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
                semanticContext);
        log.info("[ContextAssembler] Unified context summary: {}", unifiedContext.summary());

        return new WorkspaceAnalysisContext(
                unifiedContext.businessContextPrompt(),
                unifiedContext.schemaPrompt(),
                unifiedContext.relationPrompt(),
                unifiedContext.documentPrompt(),
                documentContext.strategy(),
                documentBuildResult.executionLogs(),
                unifiedContext);
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
