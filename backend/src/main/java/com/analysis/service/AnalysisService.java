package com.analysis.service;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.analysis.ai.AnalysisPlanGenerator;
import com.analysis.ai.AnalysisPlanGenerator.AgentAnalysisResult;
import com.analysis.ai.StructuredSqlGenerator;
import com.analysis.model.dto.AnalysisRequest;
import com.analysis.model.dto.AnalysisResponse;
import com.analysis.model.dto.DatasetInfo;
import com.analysis.model.dto.ExecutorResponse;
import com.analysis.model.entity.AnalysisArtifact;
import com.analysis.model.entity.Dataset;
import com.analysis.service.WorkspaceSchemaService.WorkspaceSchemaContext;
import com.analysis.service.WorkspaceContextAssembler.WorkspaceAnalysisContext;
import com.analysis.repository.DuckDBRepository;
import com.analysis.security.SqlWhitelist;
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
    private final SqlWhitelist sqlWhitelist;
    private final DuckDBRepository duckDBRepository;
    private final PythonExecutorClient pythonExecutorClient;
    private final WorkspaceSchemaService workspaceSchemaService;
    private final WorkspaceContextAssembler workspaceContextAssembler;
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

            if (request.getGroupId() != null) {
                WorkspaceSchemaContext context = workspaceSchemaService.buildContext(
                        request.getGroupId(),
                        request.getFocusDatasetIds());
                executionDataset = context.datasets().get(0);
                DatasetInfo anchorInfo = datasetService.getDatasetInfo(executionDataset.getId());
                log.info("[Analysis] Workspace mode | Query: {} | Group: {} | Tables: {}",
                        request.getQuery(), request.getGroupId(), context.datasets().size());
                WorkspaceAnalysisContext analysisContext = workspaceContextAssembler.assemble(request, context);
                agentResult = planGenerator.runWorkspaceAnalysis(
                        request.getQuery(),
                        analysisContext.businessContextPrompt(),
                        analysisContext.schemaPrompt(),
                        analysisContext.relationPrompt(),
                        analysisContext.documentContextPrompt(),
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
                // SQL 路径：重新执行 SQL 获取前端展示数据
                if (!sqlWhitelist.validate(codeOrSql)) {
                    log.warn("[Agent] Generated SQL failed whitelist. Rejecting query: {}", codeOrSql);
                    response.setSuccess(false);
                    response.setMessage("生成的 SQL 未通过安全白名单校验，拒绝执行。");
                    return response;
                } else {
                    displayData = duckDBRepository.executeQuery(codeOrSql);
                }
            } else if (codeOrSql != null && !codeOrSql.isBlank()) {
                // Python 路径：重新执行 Python 代码获取结果数据
                log.info("[Agent] Analysis used Python code. Re-executing to fetch display data...");
                try {
                    ExecutorResponse pyResult = pythonExecutorClient.executePython(
                            codeOrSql, duckdbPath, executionDataset.getTableName());
                    if (pyResult.isSuccess() && pyResult.getData() != null) {
                        displayData = pyResult.getData();
                        log.info("[Agent] Python re-execution returned {} rows", displayData.size());
                    } else {
                        log.warn("[Agent] Python re-execution failed: {}", 
                                pyResult.getError() != null ? pyResult.getError() : "no data returned");
                    }
                } catch (Exception pyEx) {
                    log.warn("[Agent] Python re-execution error: {}", pyEx.getMessage());
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
            response.setSummary(summary);
            response.setExecutionTime(System.currentTimeMillis() - startTime);

            Long artifactId = saveAnalysisArtifactSafely(
                    request,
                    executionDataset,
                    codeOrSql,
                    summary,
                    response.getRecommendedChart(),
                    displayData);
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
        return duckDBRepository.executeQuery(sql);
    }


    /**
     * 判断字符串是 SQL 语句（兼容 SELECT 和 CTE WITH 开头的查询）
     */
    private boolean isSql(String codeOrSql) {
        String upper = codeOrSql.trim().toUpperCase();
        return upper.startsWith("SELECT") || upper.startsWith("WITH");
    }

    private Long saveAnalysisArtifactSafely(
            AnalysisRequest request,
            Dataset executionDataset,
            String codeOrSql,
            String summary,
            com.analysis.model.enums.ChartType chartType,
            List<Map<String, Object>> displayData) {
        try {
            AnalysisArtifact artifact = new AnalysisArtifact();
            artifact.setMode("workplace");
            artifact.setGroupId(request.getGroupId());
            artifact.setDatasetId(executionDataset != null ? executionDataset.getId() : null);
            artifact.setUserQuery(request.getQuery());
            artifact.setGeneratedCodeOrSql(codeOrSql);
            artifact.setSummary(summary);
            artifact.setChartType(chartType != null ? chartType.name() : com.analysis.model.enums.ChartType.TABLE.name());
            int maxRows = Math.min(displayData.size(), 20);
            artifact.setResultPreviewJson(objectMapper.writeValueAsString(displayData.subList(0, maxRows)));
            return duckDBRepository.saveAnalysisArtifact(artifact);
        } catch (Exception e) {
            log.warn("[Analysis] save artifact failed: {}", e.getMessage());
            return null;
        }
    }
}
