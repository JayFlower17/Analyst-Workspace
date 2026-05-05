package com.analysis.service;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.analysis.ai.AnalysisPlanGenerator;
import com.analysis.ai.AnalysisPlanGenerator.AgentAnalysisResult;
import com.analysis.ai.StructuredSqlGenerator;
import com.analysis.model.dto.AnalysisRequest;
import com.analysis.model.dto.AnalysisResponse;
import com.analysis.model.dto.DatasetInfo;
import com.analysis.model.dto.DocumentChunkSearchResult;
import com.analysis.model.context.UnifiedAnalysisContext;
import com.analysis.model.context.UnifiedContextSummary;
import com.analysis.model.entity.AnalysisArtifact;
import com.analysis.model.execution.PythonExecutionResult;
import com.analysis.model.execution.SqlExecutionResult;
import com.analysis.model.execution.ToolExecutionLog;
import com.analysis.model.plan.AnalysisPlan;
import com.analysis.model.report.AnalysisReport;
import com.analysis.model.route.AnalysisRouteDecision;
import com.analysis.model.route.AnalysisRouteType;
import com.analysis.model.validation.AnalysisValidationReport;
import com.analysis.model.validation.RiskNotice;
import com.analysis.model.entity.Dataset;
import com.analysis.persistence.AnalysisArtifactStore;
import com.analysis.service.WorkspaceSchemaService.WorkspaceSchemaContext;
import com.analysis.service.WorkspaceContextAssembler.WorkspaceAnalysisContext;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final DatasetService datasetService;
    private final AnalysisPlanGenerator planGenerator;
    private final StructuredSqlGenerator structuredSqlGenerator;
    private final SqlExecutor sqlExecutor;
    private final AnalysisArtifactStore analysisArtifactStore;
    private final PythonExecutor pythonExecutor;
    private final WorkspaceSchemaService workspaceSchemaService;
    private final WorkspaceContextAssembler workspaceContextAssembler;
    private final AnalysisRouter analysisRouter;
    private final AnalysisPlanner analysisPlanner;
    private final AnalysisResultValidator analysisResultValidator;
    private final RiskNoticeBuilder riskNoticeBuilder;
    private final AnalysisReportBuilder analysisReportBuilder;
    private final ArtifactMemoryExtractor artifactMemoryExtractor;
    private final ArtifactMemoryVectorService artifactMemoryVectorService;
    private final ObjectMapper objectMapper;

    @Value("${duckdb.path:./data/analysis.duckdb}")
    private String duckdbPath;

    /**
     * 执行数据分析（基于 Function Calling 的全新 Agent 架构）
     */
    public AnalysisResponse analyze(AnalysisRequest request) {
        long startTime = System.currentTimeMillis();
        AnalysisResponse response = new AnalysisResponse();

        try {
            AgentAnalysisResult agentResult;
            Dataset executionDataset;
            UnifiedAnalysisContext unifiedContext = null;
            UnifiedContextSummary contextSummary = null;
            Long contextTraceId = null;
            List<ToolExecutionLog> executionLogs = new ArrayList<>();
            AnalysisValidationReport validationReport = AnalysisValidationReport.ok();
            List<RiskNotice> riskNotices = List.of();
            AnalysisRouteDecision routeDecision = analysisRouter.decide(request);
            AnalysisPlan analysisPlan = analysisPlanner.plan(request, routeDecision);
            response.setRouteDecision(routeDecision);
            response.setAnalysisPlan(analysisPlan);
            response.setExecutionLogs(executionLogs);
            response.setValidationReport(validationReport);
            response.setRiskNotices(riskNotices);

            if (request.getGroupId() != null) {
                WorkspaceSchemaContext context = workspaceSchemaService.buildContext(
                        request.getGroupId(),
                        request.getFocusDatasetIds());
                executionDataset = context.datasets().get(0);
                DatasetInfo anchorInfo = datasetService.getDatasetInfo(executionDataset.getId());
                log.info("[Analysis] Workspace mode | Query: {} | Group: {} | Tables: {}",
                        request.getQuery(), request.getGroupId(), context.datasets().size());
                WorkspaceAnalysisContext analysisContext = workspaceContextAssembler.assemble(request, context);
                executionLogs.addAll(analysisContext.executionLogs());
                contextTraceId = analysisContext.contextTraceId();
                unifiedContext = analysisContext.unifiedContext();
                contextSummary = unifiedContext.summary();
                response.setContextSummary(contextSummary);
                response.setContextTraceId(contextTraceId);
                if (routeDecision.route() == AnalysisRouteType.DOCUMENT_EXPLANATION) {
                    String summary = buildDocumentOnlySummary(unifiedContext, request.getQuery());
                    validationReport = validationReport.merge(
                            analysisResultValidator.validateDocumentEvidenceReference(summary, unifiedContext.document()));
                    summary = appendValidationWarnings(summary, validationReport);
                    riskNotices = riskNoticeBuilder.build(validationReport);
                    response.setSuccess(true);
                    response.setData(List.of());
                    response.setGeneratedSql(null);
                    response.setRecommendedChart(com.analysis.model.enums.ChartType.TABLE);
                    response.setSummary(summary);
                    response.setExecutionTime(System.currentTimeMillis() - startTime);
                    response.setContextSummary(contextSummary);
                    response.setContextTraceId(contextTraceId);
                    response.setRouteDecision(routeDecision);
                    response.setAnalysisPlan(analysisPlan);
                    response.setExecutionLogs(executionLogs);
                    response.setValidationReport(validationReport);
                    response.setRiskNotices(riskNotices);
                    response.setAnalysisReport(analysisReportBuilder.build(
                            summary,
                            List.of(),
                            response.getRecommendedChart(),
                            null,
                            unifiedContext,
                            validationReport,
                            riskNotices,
                            executionLogs));
                    Long artifactId = saveAnalysisArtifactSafely(
                            request,
                            executionDataset,
                            null,
                            summary,
                            response.getRecommendedChart(),
                            List.of(),
                            response.getAnalysisReport(),
                            contextTraceId);
                    response.setArtifactId(artifactId);
                    return response;
                }
                agentResult = planGenerator.runWorkspaceAnalysis(
                        request.getQuery(),
                        unifiedContext.businessContextPrompt(),
                        unifiedContext.schemaPrompt(),
                        unifiedContext.relationPrompt(),
                        unifiedContext.documentPrompt(),
                        anchorInfo);
            } else {
                if (request.getDatasetId() == null) {
                    response.setSuccess(false);
                    response.setMessage("缺少分析目标：请提供 groupId 或 datasetId");
                    return response;
                }
                executionDataset = datasetService.getDatasetById(request.getDatasetId());
                if (executionDataset == null) {
                    response.setSuccess(false);
                    response.setMessage("Dataset not found");
                    return response;
                }
                DatasetInfo datasetInfo = datasetService.getDatasetInfo(request.getDatasetId());
                log.info("[Analysis] Legacy dataset mode | Query: {} | Table: {}",
                        request.getQuery(), datasetInfo.getTableName());
                agentResult = planGenerator.runAgentAnalysis(request.getQuery(), datasetInfo);
            }

            if (agentResult == null) {
                response.setSuccess(false);
                response.setMessage("AI 代理未能返回分析结果，请重新尝试。");
                return response;
            }

            log.info("[Analysis] Agent Result ChartType: {}", agentResult.chartType());
            
            String codeOrSql = agentResult.generatedCodeOrSql();
            List<Map<String, Object>> displayData = new ArrayList<>();
            
            if (codeOrSql != null && isSql(codeOrSql)) {
                SqlExecutionResult sqlResult = sqlExecutor.execute(codeOrSql);
                executionLogs.add(sqlResult.executionLog());
                if (!sqlResult.success()) {
                    response.setSuccess(false);
                    response.setMessage(sqlResult.message());
                    response.setExecutionLogs(executionLogs);
                    return response;
                }
                displayData = sqlResult.rows();
                validationReport = analysisResultValidator.validateSqlResult(sqlResult);
            } else if (codeOrSql != null && !codeOrSql.isBlank()) {
                // Python 路径：重新执行 Python 代码获取结果数据
                log.info("[Agent] Analysis used Python code. Re-executing to fetch display data...");
                PythonExecutionResult pyResult = pythonExecutor.execute(
                        codeOrSql,
                        duckdbPath,
                        executionDataset.getTableName());
                executionLogs.add(pyResult.executionLog());
                if (pyResult.success()) {
                    displayData = pyResult.rows();
                    log.info("[Agent] Python re-execution returned {} rows", pyResult.rowCount());
                } else {
                    log.warn("[Agent] Python re-execution failed: {}", pyResult.message());
                }
            } else {
                log.info("[Agent] No code/SQL returned by agent. Showing summary only.");
            }

            response.setSuccess(true);
            response.setData(displayData);
            response.setGeneratedSql(codeOrSql);
            
            try {
                response.setRecommendedChart(com.analysis.model.enums.ChartType.valueOf(agentResult.chartType().toUpperCase()));
            } catch (Exception e) {
                response.setRecommendedChart(com.analysis.model.enums.ChartType.TABLE);
            }
            
            String summary = agentResult.summaryText();
            if (request.getGroupId() != null && codeOrSql != null && !isSql(codeOrSql)) {
                summary = (summary != null ? summary + "\n" : "")
                        + "提示：当前 Python 执行器以单表 DataFrame 方式运行，工作区多表 Python 联合执行将在后续阶段增强。";
            }
            validationReport = validationReport.merge(
                    analysisResultValidator.validateSummaryConsistency(summary, displayData));
            validationReport = validationReport.merge(
                    analysisResultValidator.validateDocumentEvidenceReference(
                            summary,
                            unifiedContext != null ? unifiedContext.document() : null));
            summary = appendValidationWarnings(summary, validationReport);
            riskNotices = riskNoticeBuilder.build(validationReport);
            response.setSummary(summary);
            response.setExecutionTime(System.currentTimeMillis() - startTime);
            response.setContextSummary(contextSummary);
            response.setContextTraceId(contextTraceId);
            response.setRouteDecision(routeDecision);
            response.setAnalysisPlan(analysisPlan);
            response.setExecutionLogs(executionLogs);
            response.setValidationReport(validationReport);
            response.setRiskNotices(riskNotices);
            response.setAnalysisReport(analysisReportBuilder.build(
                    summary,
                    displayData,
                    response.getRecommendedChart(),
                    codeOrSql,
                    unifiedContext,
                    validationReport,
                    riskNotices,
                    executionLogs));

            Long artifactId = saveAnalysisArtifactSafely(
                    request,
                    executionDataset,
                    codeOrSql,
                    summary,
                    response.getRecommendedChart(),
                    displayData,
                    response.getAnalysisReport(),
                    contextTraceId);
            response.setArtifactId(artifactId);

            return response;

        } catch (Exception e) {
            log.error("[Analysis Failed] Error: {}", e.getMessage(), e);
            response.setSuccess(false);
            response.setMessage("Analysis failed: " + e.getMessage());
            response.setExecutionTime(System.currentTimeMillis() - startTime);
            return response;
        }
    }

    public List<Map<String, Object>> previewData(Long datasetId, int limit) throws SQLException {
        Dataset dataset = datasetService.getDatasetById(datasetId);
        if (dataset == null) {
            throw new IllegalArgumentException("数据集不存在");
        }
        String sql = structuredSqlGenerator.generatePreviewSql(dataset.getTableName(), limit);
        SqlExecutionResult result = sqlExecutor.execute(sql);
        if (!result.success()) {
            throw new SQLException(result.message());
        }
        return result.rows();
    }


    /**
     * 判断字符串是 SQL 语句（兼容 SELECT 和 CTE WITH 开头的查询）
     */
    private boolean isSql(String codeOrSql) {
        String upper = codeOrSql.trim().toUpperCase();
        return upper.startsWith("SELECT") || upper.startsWith("WITH");
    }

    private String buildDocumentOnlySummary(UnifiedAnalysisContext unifiedContext, String query) {
        if (unifiedContext == null || unifiedContext.document() == null
                || unifiedContext.document().chunks() == null
                || unifiedContext.document().chunks().isEmpty()) {
            return "No relevant document context was found for this question.";
        }

        List<DocumentChunkSearchResult> chunks = selectDocumentOnlyChunks(unifiedContext.document().chunks(), query);
        StringBuilder summary = new StringBuilder();
        summary.append("Based on the retrieved workspace document context:\n");
        for (DocumentChunkSearchResult chunk : chunks) {
            if (chunk.getChunkText() == null || chunk.getChunkText().isBlank()) {
                continue;
            }
            summary.append("- ");
            if (chunk.getDocumentName() != null && !chunk.getDocumentName().isBlank()) {
                summary.append(chunk.getDocumentName()).append(": ");
            }
            summary.append(clipForSummary(chunk.getChunkText().trim(), 520)).append("\n");
        }
        summary.append("No SQL was generated because the request was classified as document-only.");
        return summary.toString().trim();
    }

    private List<DocumentChunkSearchResult> selectDocumentOnlyChunks(List<DocumentChunkSearchResult> chunks, String query) {
        List<String> terms = documentSpecificTerms(query);
        if (terms.isEmpty()) {
            return chunks;
        }

        List<DocumentChunkSearchResult> selected = chunks.stream()
                .filter(chunk -> matchesDocumentSpecificTerms(chunk, terms))
                .toList();
        return selected.isEmpty() ? chunks : selected;
    }

    private List<String> documentSpecificTerms(String query) {
        String normalized = query == null ? "" : query.toLowerCase(Locale.ROOT);
        List<String> ignored = List.of(
                "according", "answer", "document", "documents", "only", "needed", "treated", "orders",
                "order", "from", "with", "without", "what", "does", "policy");
        List<String> terms = new ArrayList<>();
        for (String token : normalized.split("[^a-z0-9_]+")) {
            if (token.length() >= 4 && !ignored.contains(token) && !terms.contains(token)) {
                terms.add(token);
            }
        }
        return terms;
    }

    private boolean matchesDocumentSpecificTerms(DocumentChunkSearchResult chunk, List<String> terms) {
        String combined = ((chunk.getDocumentName() != null ? chunk.getDocumentName() : "") + "\n"
                + (chunk.getChunkText() != null ? chunk.getChunkText() : "")).toLowerCase(Locale.ROOT);
        int matches = 0;
        for (String term : terms) {
            if (combined.contains(term)) {
                matches++;
            }
        }
        return matches >= Math.min(2, terms.size());
    }

    private String clipForSummary(String text, int maxChars) {
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, Math.max(0, maxChars - 3)).trim() + "...";
    }

    private String appendValidationWarnings(String summary, AnalysisValidationReport validationReport) {
        if (validationReport == null || !validationReport.hasFindings()) {
            return summary;
        }

        StringBuilder result = new StringBuilder(summary != null ? summary : "");
        for (var finding : validationReport.findings()) {
            if (finding.severity() == com.analysis.model.validation.ValidationSeverity.WARNING) {
                if (!result.isEmpty()) {
                    result.append("\n");
                }
                result.append("Validation warning: ").append(finding.message());
            }
        }
        return result.toString();
    }

    private Long saveAnalysisArtifactSafely(
            AnalysisRequest request,
            Dataset executionDataset,
            String codeOrSql,
            String summary,
            com.analysis.model.enums.ChartType chartType,
            List<Map<String, Object>> displayData,
            AnalysisReport analysisReport,
            Long contextTraceId) {
        try {
            AnalysisArtifact artifact = new AnalysisArtifact();
            artifact.setMode("workplace");
            artifact.setGroupId(request.getGroupId());
            artifact.setDatasetId(executionDataset != null ? executionDataset.getId() : null);
            artifact.setContextTraceId(contextTraceId);
            artifact.setUserQuery(request.getQuery());
            artifact.setGeneratedCodeOrSql(codeOrSql);
            artifact.setSummary(summary);
            artifact.setChartType(chartType != null ? chartType.name() : com.analysis.model.enums.ChartType.TABLE.name());
            int maxRows = Math.min(displayData.size(), 20);
            artifact.setResultPreviewJson(objectMapper.writeValueAsString(displayData.subList(0, maxRows)));
            if (analysisReport != null) {
                artifact.setArtifactSchemaVersion(2);
                artifact.setAnalysisReportJson(objectMapper.writeValueAsString(trimAnalysisReport(analysisReport)));
                if (analysisReport.evidence() != null) {
                    artifact.setEvidenceSummaryJson(objectMapper.writeValueAsString(analysisReport.evidence()));
                }
                artifact.setExecutionLogsJson(objectMapper.writeValueAsString(
                        analysisReport.executionLogs() != null ? analysisReport.executionLogs() : List.of()));
                if (analysisReport.validationReport() != null) {
                    artifact.setValidationReportJson(objectMapper.writeValueAsString(analysisReport.validationReport()));
                }
                artifact.setRiskNoticesJson(objectMapper.writeValueAsString(
                        analysisReport.riskNotices() != null ? analysisReport.riskNotices() : List.of()));
            } else {
                artifact.setArtifactSchemaVersion(1);
            }
            Long artifactId = analysisArtifactStore.saveAnalysisArtifact(artifact);
            saveArtifactMemoriesSafely(
                    artifactId,
                    request,
                    executionDataset,
                    codeOrSql,
                    summary,
                    analysisReport);
            return artifactId;
        } catch (Exception e) {
            log.warn("[Analysis] save artifact failed: {}", e.getMessage());
            return null;
        }
    }

    private void saveArtifactMemoriesSafely(
            Long artifactId,
            AnalysisRequest request,
            Dataset executionDataset,
            String codeOrSql,
            String summary,
            AnalysisReport analysisReport) {
        if (artifactId == null) {
            return;
        }
        try {
            var memories = artifactMemoryExtractor.extract(
                    artifactId,
                    request.getGroupId(),
                    executionDataset != null ? executionDataset.getId() : null,
                    codeOrSql,
                    summary,
                    analysisReport);
            for (var memory : memories) {
                Long memoryId = analysisArtifactStore.saveArtifactMemory(memory);
                memory.setId(memoryId);
                artifactMemoryVectorService.indexMemoryIfAvailable(memory);
            }
        } catch (Exception e) {
            log.warn("[Analysis] save artifact memories failed: {}", e.getMessage());
        }
    }

    private AnalysisReport trimAnalysisReport(AnalysisReport report) {
        return new AnalysisReport(
                report.summary(),
                List.of(),
                report.rowCount(),
                report.recommendedChart(),
                report.generatedCodeOrSqlPresent(),
                report.generatedCodeOrSql(),
                report.evidence(),
                report.validationReport(),
                report.riskNotices(),
                report.executionLogs());
    }
}
