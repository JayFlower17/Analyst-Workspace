package com.analysis.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.analysis.model.dto.DocumentChunkSearchResult;
import com.analysis.model.entity.DocumentAsset;
import com.analysis.model.entity.DocumentChunk;
import com.analysis.repository.DuckDBRepository;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".pdf", ".docx", ".txt", ".md");
    private static final int MAX_MERGED_ADJACENT_CHUNKS = 2;

    private final DuckDBRepository duckDBRepository;
    private final ObjectProvider<VectorStore> vectorStoreProvider;

    @Value("${upload.path:./uploads}")
    private String uploadPath;

    @Value("${app.vector-store.enabled:true}")
    private boolean vectorStoreEnabled;

    @PostConstruct
    public void init() {
        Path documentsDir = Paths.get(uploadPath, "documents");
        if (!Files.exists(documentsDir)) {
            try {
                Files.createDirectories(documentsDir);
            } catch (IOException e) {
                log.error("Failed to create document upload directory", e);
            }
        }
    }

    public DocumentAsset uploadDocument(MultipartFile file, String documentName, Long groupId) throws Exception {
        if (groupId == null) {
            throw new IllegalArgumentException("文档必须绑定到工作区");
        }
        if (duckDBRepository.findDatasetGroupById(groupId) == null) {
            throw new IllegalArgumentException("工作区不存在: " + groupId);
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        String extension = resolveExtension(originalFilename);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("不支持的文档格式，仅支持 PDF / DOCX / TXT / MD");
        }

        String resolvedName = (documentName == null || documentName.isBlank()) ? originalFilename : documentName.trim();
        Path targetPath = Paths.get(uploadPath, "documents", UUID.randomUUID() + "_" + originalFilename);
        Files.copy(file.getInputStream(), targetPath);

        DocumentAsset asset = new DocumentAsset();
        asset.setGroupId(groupId);
        asset.setName(resolvedName);
        asset.setOriginalFileName(originalFilename);
        asset.setStoredPath(targetPath.toString());
        asset.setFileType(extension.substring(1).toUpperCase());
        asset.setMimeType(file.getContentType());
        asset.setSizeBytes(file.getSize());
        asset.setProcessingStatus("UPLOADED");
        asset.setProcessingError(null);
        asset.setChunkCount(0);

        Long assetId = duckDBRepository.saveDocumentAsset(asset);
        DocumentAsset saved = duckDBRepository.findDocumentAssetById(assetId);
        processDocument(saved);
        return duckDBRepository.findDocumentAssetById(assetId);
    }

    public List<DocumentAsset> getDocumentsByGroupId(Long groupId) throws SQLException {
        ensureGroupExists(groupId);
        return duckDBRepository.findDocumentAssetsByGroupId(groupId);
    }

    public DocumentAsset getDocumentById(Long documentId) throws SQLException {
        return duckDBRepository.findDocumentAssetById(documentId);
    }

    public List<DocumentChunk> getDocumentChunks(Long documentId) throws SQLException {
        DocumentAsset asset = duckDBRepository.findDocumentAssetById(documentId);
        if (asset == null) {
            throw new IllegalArgumentException("文档不存在");
        }
        return duckDBRepository.findDocumentChunksByDocumentId(documentId);
    }

    public List<DocumentChunkSearchResult> searchDocumentChunks(Long groupId, String query, Integer topK)
            throws SQLException {
        ensureGroupExists(groupId);
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("检索问题不能为空");
        }

        int resolvedTopK = topK == null ? 5 : Math.max(1, Math.min(topK, 20));
        VectorStore vectorStore = vectorStoreEnabled ? vectorStoreProvider.getIfAvailable() : null;
        if (vectorStore != null) {
            try {
                List<DocumentChunkSearchResult> vectorResults = searchWithVectorStore(groupId, query, resolvedTopK,
                        vectorStore);
                if (!vectorResults.isEmpty()) {
                    return postProcessSearchResults(vectorResults, query, resolvedTopK);
                }
                log.info("Vector retrieval returned no results for group {}. Falling back to lexical search.", groupId);
            } catch (Exception e) {
                log.warn("Vector retrieval failed for group {}: {}. Falling back to lexical search.", groupId,
                        e.getMessage());
            }
        }

        return postProcessSearchResults(searchLexically(groupId, query, resolvedTopK), query, resolvedTopK);
    }

    public void deleteDocument(Long documentId) throws Exception {
        DocumentAsset asset = duckDBRepository.findDocumentAssetById(documentId);
        if (asset == null) {
            throw new IllegalArgumentException("文档不存在");
        }
        duckDBRepository.deleteDocumentChunksByDocumentId(documentId);
        duckDBRepository.deleteDocumentAssetById(documentId);
        if (asset.getStoredPath() != null && !asset.getStoredPath().isBlank()) {
            Files.deleteIfExists(Paths.get(asset.getStoredPath()));
        }
    }

    private void ensureGroupExists(Long groupId) throws SQLException {
        if (groupId == null) {
            throw new IllegalArgumentException("工作区 ID 不能为空");
        }
        if (duckDBRepository.findDatasetGroupById(groupId) == null) {
            throw new IllegalArgumentException("工作区不存在: " + groupId);
        }
    }

    private String resolveExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex).toLowerCase();
    }

    private void processDocument(DocumentAsset asset) {
        if (asset == null) {
            return;
        }
        try {
            duckDBRepository.updateDocumentAssetProcessing(asset.getId(), "PARSING", null, 0);
            String extractedText = extractDocumentText(asset);
            List<String> chunks = chunkText(extractedText);
            duckDBRepository.deleteDocumentChunksByDocumentId(asset.getId());
            List<DocumentChunk> persistedChunks = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                String metadataJson = "{\"source\":\"document\",\"chunkIndex\":" + i + "}";
                DocumentChunk chunk = buildChunk(asset.getId(), i, chunks.get(i), metadataJson);
                duckDBRepository.saveDocumentChunk(chunk);
                persistedChunks.add(chunk);
            }
            duckDBRepository.updateDocumentAssetProcessing(asset.getId(), "PARSED", null, chunks.size());
            indexDocumentChunksIfAvailable(asset, persistedChunks);
        } catch (Exception e) {
            log.warn("Document processing failed for {}: {}", asset.getId(), e.getMessage());
            try {
                duckDBRepository.updateDocumentAssetProcessing(asset.getId(), "FAILED", e.getMessage(), 0);
            } catch (SQLException sqlException) {
                log.error("Failed to persist document processing error for {}: {}", asset.getId(), sqlException.getMessage());
            }
        }
    }

    private String extractDocumentText(DocumentAsset asset) throws Exception {
        String fileType = asset.getFileType() != null ? asset.getFileType().toUpperCase() : "";
        return switch (fileType) {
            case "TXT", "MD" -> Files.readString(Paths.get(asset.getStoredPath()), StandardCharsets.UTF_8);
            case "DOCX" -> extractDocxText(Paths.get(asset.getStoredPath()));
            case "PDF" -> extractPdfText(Paths.get(asset.getStoredPath()));
            default -> throw new UnsupportedOperationException("当前版本暂不支持 " + fileType + " 解析");
        };
    }

    private List<String> chunkText(String text) {
        String normalized = text == null ? "" : text.replace("\r\n", "\n").trim();
        List<String> chunks = new ArrayList<>();
        if (normalized.isBlank()) {
            return chunks;
        }

        final int maxChunkLength = 1200;
        String[] paragraphs = normalized.split("\\n\\s*\\n");
        StringBuilder current = new StringBuilder();

        for (String rawParagraph : paragraphs) {
            String paragraph = rawParagraph.trim();
            if (paragraph.isBlank()) {
                continue;
            }

            if (paragraph.length() > maxChunkLength) {
                if (current.length() > 0) {
                    chunks.add(current.toString().trim());
                    current.setLength(0);
                }
                chunks.addAll(splitOversizedParagraph(paragraph, maxChunkLength));
                continue;
            }

            if (current.length() == 0) {
                current.append(paragraph);
                continue;
            }

            if (current.length() + 2 + paragraph.length() <= maxChunkLength) {
                current.append("\n\n").append(paragraph);
            } else {
                chunks.add(current.toString().trim());
                current.setLength(0);
                current.append(paragraph);
            }
        }

        if (current.length() > 0) {
            chunks.add(current.toString().trim());
        }

        return chunks;
    }

    private List<String> splitOversizedParagraph(String paragraph, int maxChunkLength) {
        List<String> pieces = new ArrayList<>();
        int start = 0;
        while (start < paragraph.length()) {
            int end = Math.min(start + maxChunkLength, paragraph.length());
            if (end < paragraph.length()) {
                int breakAt = paragraph.lastIndexOf(' ', end);
                if (breakAt > start + 100) {
                    end = breakAt;
                }
            }
            String piece = paragraph.substring(start, end).trim();
            if (!piece.isBlank()) {
                pieces.add(piece);
            }
            start = end;
        }
        return pieces;
    }

    private DocumentChunk buildChunk(Long documentId, int chunkIndex, String chunkText,
            String metadataJson) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setDocumentId(documentId);
        chunk.setChunkIndex(chunkIndex);
        chunk.setChunkText(chunkText);
        chunk.setMetadataJson(metadataJson);
        return chunk;
    }

    private void indexDocumentChunksIfAvailable(DocumentAsset asset, List<DocumentChunk> chunks) {
        if (!vectorStoreEnabled) {
            return;
        }
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null || chunks.isEmpty()) {
            return;
        }

        List<Document> documents = new ArrayList<>();
        for (DocumentChunk chunk : chunks) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("type", "document_chunk");
            metadata.put("groupId", asset.getGroupId());
            metadata.put("documentId", asset.getId());
            metadata.put("documentName", asset.getName());
            metadata.put("fileType", asset.getFileType());
            metadata.put("chunkIndex", chunk.getChunkIndex());
            documents.add(new Document(chunk.getChunkText(), metadata));
        }

        try {
            vectorStore.add(documents);
            log.info("Indexed {} document chunks into Vector Store for document {}", documents.size(), asset.getId());
        } catch (Exception e) {
            log.warn("Failed to index document {} into Vector Store: {}", asset.getId(), e.getMessage());
        }
    }

    private List<DocumentChunkSearchResult> searchWithVectorStore(Long groupId, String query, int topK,
            VectorStore vectorStore) {
        SearchRequest searchRequest = SearchRequest.query(query)
                .withTopK(Math.min(topK * 4, 20))
                .withFilterExpression("groupId == " + groupId);

        List<Document> documents = vectorStore.similaritySearch(searchRequest);
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }

        List<DocumentChunkSearchResult> results = new ArrayList<>();
        for (Document document : documents) {
            Map<String, Object> metadata = document.getMetadata();
            DocumentChunkSearchResult result = new DocumentChunkSearchResult();
            result.setDocumentId(asLong(metadata.get("documentId")));
            result.setDocumentName(asString(metadata.get("documentName")));
            result.setFileType(asString(metadata.get("fileType")));
            result.setChunkIndex(asInteger(metadata.get("chunkIndex")));
            result.setChunkText(document.getContent());
            result.setScore(asDouble(metadata.get("distance")));
            result.setRetrievalMode("vector");
            results.add(result);
        }
        return results;
    }

    private List<DocumentChunkSearchResult> searchLexically(Long groupId, String query, int topK) throws SQLException {
        List<DocumentAsset> assets = duckDBRepository.findDocumentAssetsByGroupId(groupId);
        if (assets.isEmpty()) {
            return List.of();
        }

        Map<Long, DocumentAsset> assetsById = assets.stream()
                .collect(Collectors.toMap(DocumentAsset::getId, asset -> asset));
        Set<String> terms = tokenizeQuery(query);
        List<ScoredChunk> scoredChunks = new ArrayList<>();

        for (DocumentAsset asset : assets) {
            List<DocumentChunk> chunks = duckDBRepository.findDocumentChunksByDocumentId(asset.getId());
            for (DocumentChunk chunk : chunks) {
                double score = scoreChunk(query, terms, chunk.getChunkText());
                if (score > 0) {
                    scoredChunks.add(new ScoredChunk(asset.getId(), chunk, score));
                }
            }
        }

        return scoredChunks.stream()
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed()
                        .thenComparing(scored -> scored.chunk().getDocumentId())
                        .thenComparing(scored -> scored.chunk().getChunkIndex()))
                .limit(Math.min(topK * 4L, 40L))
                .map(scored -> toSearchResult(assetsById.get(scored.documentId()), scored.chunk(), scored.score(),
                        "lexical"))
                .toList();
    }

    private DocumentChunkSearchResult toSearchResult(DocumentAsset asset, DocumentChunk chunk, double score,
            String retrievalMode) {
        DocumentChunkSearchResult result = new DocumentChunkSearchResult();
        result.setDocumentId(asset.getId());
        result.setDocumentName(asset.getName());
        result.setFileType(asset.getFileType());
        result.setChunkIndex(chunk.getChunkIndex());
        result.setEndChunkIndex(chunk.getChunkIndex());
        result.setSourceChunkCount(1);
        result.setChunkText(chunk.getChunkText());
        result.setScore(score);
        result.setRetrievalMode(retrievalMode);
        return result;
    }

    private List<DocumentChunkSearchResult> postProcessSearchResults(List<DocumentChunkSearchResult> rawResults,
            String query, int topK) {
        if (rawResults == null || rawResults.isEmpty()) {
            return List.of();
        }

        List<DocumentChunkSearchResult> deduped = deduplicateResults(rawResults);
        List<DocumentChunkSearchResult> merged = mergeAdjacentResults(deduped);
        rerankResults(merged, query);

        Map<Long, Integer> perDocumentCount = new HashMap<>();
        List<DocumentChunkSearchResult> finalResults = new ArrayList<>();
        for (DocumentChunkSearchResult result : merged) {
            Long documentId = result.getDocumentId();
            int used = perDocumentCount.getOrDefault(documentId, 0);
            if (used >= 2) {
                continue;
            }
            finalResults.add(result);
            perDocumentCount.put(documentId, used + 1);
            if (finalResults.size() >= topK) {
                break;
            }
        }
        return finalResults;
    }

    private List<DocumentChunkSearchResult> deduplicateResults(List<DocumentChunkSearchResult> rawResults) {
        Map<String, DocumentChunkSearchResult> deduped = new LinkedHashMap<>();
        for (DocumentChunkSearchResult result : rawResults) {
            String key = buildResultKey(result);
            DocumentChunkSearchResult existing = deduped.get(key);
            if (existing == null || compareScore(result.getScore(), existing.getScore()) > 0) {
                deduped.put(key, copyResult(result));
            }
        }

        return deduped.values().stream()
                .sorted(Comparator
                        .comparing(DocumentChunkSearchResult::getScore, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(DocumentChunkSearchResult::getDocumentId, Comparator.nullsLast(Long::compareTo))
                        .thenComparing(DocumentChunkSearchResult::getChunkIndex,
                                Comparator.nullsLast(Integer::compareTo)))
                .toList();
    }

    private List<DocumentChunkSearchResult> mergeAdjacentResults(List<DocumentChunkSearchResult> results) {
        Map<Long, List<DocumentChunkSearchResult>> groupedByDocument = results.stream()
                .collect(Collectors.groupingBy(DocumentChunkSearchResult::getDocumentId));

        List<DocumentChunkSearchResult> merged = new ArrayList<>();
        for (List<DocumentChunkSearchResult> docResults : groupedByDocument.values()) {
            List<DocumentChunkSearchResult> sorted = docResults.stream()
                    .sorted(Comparator.comparing(DocumentChunkSearchResult::getChunkIndex,
                            Comparator.nullsLast(Integer::compareTo)))
                    .toList();

            DocumentChunkSearchResult current = null;
            for (DocumentChunkSearchResult result : sorted) {
                if (current == null) {
                    current = copyResult(result);
                    continue;
                }

                Integer currentEnd = current.getEndChunkIndex() != null ? current.getEndChunkIndex() : current.getChunkIndex();
                Integer nextIndex = result.getChunkIndex();
                int currentChunkCount = current.getSourceChunkCount() != null ? current.getSourceChunkCount() : 1;
                if (currentEnd != null
                        && nextIndex != null
                        && nextIndex == currentEnd + 1
                        && currentChunkCount < MAX_MERGED_ADJACENT_CHUNKS) {
                    current.setEndChunkIndex(nextIndex);
                    current.setSourceChunkCount(currentChunkCount
                            + (result.getSourceChunkCount() != null ? result.getSourceChunkCount() : 1));
                    current.setChunkText(current.getChunkText() + "\n\n" + result.getChunkText());
                    if (compareScore(result.getScore(), current.getScore()) > 0) {
                        current.setScore(result.getScore());
                    }
                } else {
                    merged.add(current);
                    current = copyResult(result);
                }
            }

            if (current != null) {
                merged.add(current);
            }
        }

        return merged.stream()
                .sorted(Comparator
                        .comparing(DocumentChunkSearchResult::getRerankScore,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(DocumentChunkSearchResult::getScore, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(DocumentChunkSearchResult::getSourceChunkCount,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(DocumentChunkSearchResult::getDocumentId, Comparator.nullsLast(Long::compareTo))
                        .thenComparing(DocumentChunkSearchResult::getChunkIndex,
                                Comparator.nullsLast(Integer::compareTo)))
                .toList();
    }

    private String buildResultKey(DocumentChunkSearchResult result) {
        Long documentId = result.getDocumentId() != null ? result.getDocumentId() : -1L;
        Integer chunkIndex = result.getChunkIndex() != null ? result.getChunkIndex() : -1;
        return documentId + ":" + chunkIndex;
    }

    private DocumentChunkSearchResult copyResult(DocumentChunkSearchResult source) {
        DocumentChunkSearchResult copy = new DocumentChunkSearchResult();
        copy.setDocumentId(source.getDocumentId());
        copy.setDocumentName(source.getDocumentName());
        copy.setFileType(source.getFileType());
        copy.setChunkIndex(source.getChunkIndex());
        copy.setEndChunkIndex(source.getEndChunkIndex());
        copy.setSourceChunkCount(source.getSourceChunkCount());
        copy.setChunkText(source.getChunkText());
        copy.setScore(source.getScore());
        copy.setRerankScore(source.getRerankScore());
        copy.setRerankNotes(source.getRerankNotes());
        copy.setQueryIntent(source.getQueryIntent());
        copy.setDocumentRole(source.getDocumentRole());
        copy.setRetrievalMode(source.getRetrievalMode());
        return copy;
    }

    private void rerankResults(List<DocumentChunkSearchResult> results, String query) {
        Set<String> terms = tokenizeQuery(query);
        String normalizedQuery = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        QueryIntent queryIntent = classifyQueryIntent(normalizedQuery);

        for (DocumentChunkSearchResult result : results) {
            String chunkText = result.getChunkText() != null ? result.getChunkText() : "";
            String normalizedChunk = chunkText.toLowerCase(Locale.ROOT);
            String documentName = result.getDocumentName() != null ? result.getDocumentName().toLowerCase(Locale.ROOT) : "";
            String heading = extractHeading(chunkText).toLowerCase(Locale.ROOT);
            DocumentRole documentRole = inferDocumentRole(documentName, normalizedChunk, heading);

            double rerankScore = result.getScore() != null ? result.getScore() : 0.0;
            StringJoiner notes = new StringJoiner(", ");
            int matchedTerms = 0;
            for (String term : terms) {
                if (normalizedChunk.contains(term)) {
                    matchedTerms++;
                    rerankScore += 0.8;
                }
                if (!documentName.isBlank() && documentName.contains(term)) {
                    rerankScore += 0.35;
                }
                if (!heading.isBlank() && heading.contains(term)) {
                    rerankScore += 0.55;
                }
            }

            if (!normalizedQuery.isBlank() && normalizedChunk.contains(normalizedQuery)) {
                rerankScore += 2.0;
                notes.add("full-query-match");
            }

            if (matchedTerms > 0 && !terms.isEmpty()) {
                double coverage = matchedTerms * 1.0 / terms.size();
                rerankScore += coverage * 1.5;
                notes.add("coverage=" + String.format("%.2f", coverage));
            }

            if (chunkText.startsWith("#") || chunkText.startsWith("##")) {
                rerankScore += 0.4;
                notes.add("heading-block");
            }

            int sourceChunkCount = result.getSourceChunkCount() != null ? result.getSourceChunkCount() : 1;
            if (sourceChunkCount > 1) {
                rerankScore += Math.min(0.6, sourceChunkCount * 0.2);
                notes.add("merged=" + sourceChunkCount);
            }

            int length = chunkText.length();
            if (length > 2200) {
                rerankScore -= 0.6;
                notes.add("too-long");
            } else if (length < 120) {
                rerankScore -= 0.25;
                notes.add("too-short");
            }

            double intentBoost = scoreByIntent(queryIntent, documentRole, normalizedChunk, heading);
            if (intentBoost != 0) {
                rerankScore += intentBoost;
                notes.add("intent=" + queryIntent.name());
                notes.add("role=" + documentRole.name());
            }

            result.setRerankScore(rerankScore);
            result.setRerankNotes(notes.length() == 0 ? "base-score" : notes.toString());
            result.setQueryIntent(queryIntent.name());
            result.setDocumentRole(documentRole.name());
        }
    }

    private double scoreByIntent(QueryIntent queryIntent, DocumentRole documentRole, String normalizedChunk, String heading) {
        return switch (queryIntent) {
            case RULE_HEAVY -> switch (documentRole) {
                case RULE_NOTE -> 1.4;
                case GUIDE -> 0.8;
                case REFERENCE -> 0.25;
                case GENERIC -> 0.0;
            };
            case DEFINITION_HEAVY -> switch (documentRole) {
                case REFERENCE -> 1.2;
                case GUIDE -> 0.6;
                case RULE_NOTE -> 0.4;
                case GENERIC -> 0.0;
            };
            case STRUCTURED -> {
                double boost = 0.0;
                if (heading.contains("接口") || heading.contains("schema") || heading.contains("字段") || heading.contains("验证")) {
                    boost += 0.45;
                }
                if (documentRole == DocumentRole.REFERENCE) {
                    boost += 0.35;
                }
                yield boost;
            }
            case GENERAL -> 0.0;
        };
    }

    private QueryIntent classifyQueryIntent(String normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            return QueryIntent.GENERAL;
        }
        if (containsAny(normalizedQuery, "according to", "workspace note", "rule", "policy", "priority",
                "according", "文档", "规则", "根据", "优先", "说明", "note")) {
            return QueryIntent.RULE_HEAVY;
        }
        if (containsAny(normalizedQuery, "definition", "meaning", "what is", "解释", "含义", "定义", "口径")) {
            return QueryIntent.DEFINITION_HEAVY;
        }
        if (containsAny(normalizedQuery, "count", "sum", "avg", "trend", "统计", "汇总", "求和", "平均", "趋势")) {
            return QueryIntent.STRUCTURED;
        }
        return QueryIntent.GENERAL;
    }

    private DocumentRole inferDocumentRole(String documentName, String normalizedChunk, String heading) {
        String combined = documentName + "\n" + heading + "\n" + normalizedChunk;
        if (containsAny(combined, "note", "rule", "policy", "priority", "注意", "规则", "说明", "优先", "备注")) {
            return DocumentRole.RULE_NOTE;
        }
        if (containsAny(combined, "guide", "how to", "步骤", "验证", "runbook", "操作")) {
            return DocumentRole.GUIDE;
        }
        if (containsAny(combined, "schema", "接口", "字段", "reference", "定义", "说明书")) {
            return DocumentRole.REFERENCE;
        }
        return DocumentRole.GENERIC;
    }

    private String extractHeading(String chunkText) {
        if (chunkText == null || chunkText.isBlank()) {
            return "";
        }
        String[] lines = chunkText.split("\\R");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                return trimmed.replaceFirst("^#+\\s*", "");
            }
        }
        return lines[0].trim();
    }

    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private int compareScore(Double left, Double right) {
        double normalizedLeft = left != null ? left : Double.NEGATIVE_INFINITY;
        double normalizedRight = right != null ? right : Double.NEGATIVE_INFINITY;
        return Double.compare(normalizedLeft, normalizedRight);
    }

    private Set<String> tokenizeQuery(String query) {
        return java.util.Arrays.stream(query.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(token -> !token.isBlank())
                .filter(token -> token.length() > 1)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private double scoreChunk(String query, Set<String> terms, String chunkText) {
        if (chunkText == null || chunkText.isBlank()) {
            return 0;
        }

        String normalizedChunk = chunkText.toLowerCase(Locale.ROOT);
        String normalizedQuery = query.toLowerCase(Locale.ROOT).trim();
        double score = 0;

        if (!normalizedQuery.isBlank() && normalizedChunk.contains(normalizedQuery)) {
            score += 5.0;
        }

        for (String term : terms) {
            if (normalizedChunk.contains(term)) {
                score += 1.0;
            }
        }

        return score;
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            return Long.parseLong(string);
        }
        return null;
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            return Integer.parseInt(string);
        }
        return null;
    }

    private Double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            return Double.parseDouble(string);
        }
        return null;
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private record ScoredChunk(Long documentId, DocumentChunk chunk, double score) {
    }

    private enum QueryIntent {
        RULE_HEAVY,
        DEFINITION_HEAVY,
        STRUCTURED,
        GENERAL
    }

    private enum DocumentRole {
        RULE_NOTE,
        GUIDE,
        REFERENCE,
        GENERIC
    }

    private String extractDocxText(Path path) throws Exception {
        try (var inputStream = Files.newInputStream(path);
                XWPFDocument document = new XWPFDocument(inputStream)) {
            StringBuilder builder = new StringBuilder();
            document.getParagraphs().forEach(paragraph -> {
                String text = paragraph.getText();
                if (text != null && !text.isBlank()) {
                    if (builder.length() > 0) {
                        builder.append("\n\n");
                    }
                    builder.append(text.trim());
                }
            });
            return builder.toString();
        }
    }

    private String extractPdfText(Path path) throws Exception {
        try (PDDocument document = Loader.loadPDF(Files.readAllBytes(path))) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }
}
