package com.analysis.ai;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import com.analysis.ai.tools.DatabaseTools;
import com.analysis.ai.tools.PythonTools;
import com.analysis.model.dto.DatasetInfo;
import com.analysis.service.MetadataService;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 分析生成器 - 基于 Spring AI M3 智能体架构实现
 * 
 * 稳定性增强：
 * - 每次分析前重置工具调用计数器
 * - System Prompt 中告知 AI 工具调用限制
 * - 多级 JSON 解析策略，增强输出稳定性
 */
@Slf4j
@Component
@RequiredArgsConstructor
// 接受到用户的问题->联动RAG + LLM + 工具执行->生成最终的分析结果
public class AnalysisPlanGenerator {

    private final ChatClient.Builder chatClientBuilder;// AI对话客户端
    private final ObjectMapper objectMapper;// JSON序列化工具
    private final DatabaseTools databaseTools;// 数据库工具
    private final PythonTools pythonTools;// python工具
    private final VectorStore vectorStore;// 向量数据库
    private final MetadataService metadataService;// 元数据服务，用于获取完整表结构

    /** 用于提取 markdown 代码块中的 JSON */
    private static final Pattern JSON_CODE_BLOCK = Pattern.compile(
            "```(?:json)?\\s*\\n?(\\{.*?})\\s*\\n?```", Pattern.DOTALL);

    // Define the expected output format wrapper
    public record AgentAnalysisResult(
            String chartType,
            String summaryText,
            String generatedCodeOrSql) {
    }

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            You are a professional data analysis Agent. Your task is to analyze user queries, autonomously explore data using the provided tools, and output final actionable results.

            IMPORTANT RESOURCE LIMITS:
            - You have at most 10 SQL query attempts and 5 schema lookups per session.
            - You have at most 3 Python execution attempts per session.
            - Be efficient: plan your queries carefully before executing them.

            You MUST follow these steps:
            1. Check if a [Complete Table Schema] section is provided in the user message.
               - If YES, use it directly as the table structure. Do NOT call 'getSchema' unless you suspect the schema is outdated or incomplete.
               - If NO, use the 'getSchema' tool to fetch the exact table structure.
            2. To answer the query, prioritize writing ONE single, unified SQL statement.
               - Use standard SQL techniques like GROUP BY, CASE WHEN, or aggregations within a single query instead of splitting it into multiple queries.
               - DO NOT use UNION ALL unless absolutely necessary.
               - Test it using the 'executeQuery' tool. If it passes, stop exploring.
               - If the SQL fails, READ the error message, fix the syntax, and test again.
            3. ONLY IF the question requires complex data science (like correlation matrices, model fitting) that SQL cannot do WELL, use the 'executePython' tool.
            4. Do not over-explain or do unnecessary steps. Be fast and efficient.

            Once you completely finish the process and verify the results, your final output MUST be a strict JSON object that exactly matches this structure WITHOUT any markdown wrappers:
            {
              "chartType": "recommended visualization type (e.g. LINE, BAR, PIE, SCATTER, TABLE)",
              "summaryText": "a detailed but concise summary of your analysis findings for the user based on the tool execution results",
              "generatedCodeOrSql": "the final single successful SQL query or Python code you settled on (do NOT include multiple SQL statements separated by semicolons)"
            }

            Chart type guide:
            - Time series trends → LINE
            - Category comparison → BAR
            - Proportion analysis → PIE
            - Variable relationships → SCATTER
            - Multi-dimensional → HEATMAP
            - Value distribution → HISTOGRAM
            - Statistical summary → BOX
            - Default → TABLE
            """;

    /**
     * 作为 Agent 自主运行分析
     */
    public AgentAnalysisResult runAgentAnalysis(String userQuery, DatasetInfo datasetInfo) {
        try {
            // 重置工具调用计数器
            databaseTools.resetCallCounters();
            pythonTools.resetCallCounter();

            // 🌟 RAG 检索：拿着用户的问题去 Milvus 里搜相关的列解释或业务知识
            String ragContext = "";
            try {
                log.info("[RAG] 正在根据用户问题从向量库检索上下文: {}", userQuery);
                // 按 datasetId 做隔离过滤，查出最相关的 5 条记录。注意：如果有类型转换问题，可尝试去掉引号
                SearchRequest searchRequest = SearchRequest.query(userQuery)
                        .withTopK(5)
                        .withFilterExpression("datasetId == '" + datasetInfo.getId() + "'");

                List<Document> documents = vectorStore.similaritySearch(searchRequest);
                if (documents != null && !documents.isEmpty()) {
                    ragContext = documents.stream()
                            .map(Document::getContent)
                            .collect(Collectors.joining("\n"));
                    log.info("[RAG] 成功检索到 {} 条业务知识", documents.size());
                }
            } catch (Exception e) {
                log.warn("[RAG] 向量检索失败，降级为无 RAG 模式: {}", e.getMessage());
            }

            // 📋 获取完整表结构，直接注入 prompt，避免 AI 再调用 getSchema
            String fullSchema = "";
            try {
                fullSchema = metadataService.generateMetadataPrompt(datasetInfo.getId());
                log.info("[Agent] 已加载完整表结构，将直接注入 prompt");
            } catch (Exception e) {
                log.warn("[Agent] 加载表结构失败，AI 将通过 getSchema 工具获取: {}", e.getMessage());
            }

            // 构建prompt:用户问题 + RAG检索到的业务知识 + 完整表结构 + 目标表名
            String userContent = String.format("""
                    [System RAG Context (IMPORTANT BUSINESS KNOWLEDGE)]
                    %s

                    [Complete Table Schema]
                    %s

                    User query: %s
                    Target Table: %s
                    """, 
                    ragContext.isEmpty() ? "No specific business context found." : ragContext,
                    fullSchema.isEmpty() ? "Schema not available. Please use the getSchema tool." : fullSchema,
                    userQuery,
                    datasetInfo.getTableName());

            ChatClient chatClient = chatClientBuilder
                    .defaultSystem(SYSTEM_PROMPT_TEMPLATE)
                    .build();

            long apiStartTime = System.currentTimeMillis();

            log.info("[Agent] Starting analysis loop for table: {}", datasetInfo.getTableName());
            // AI不会直接拿到数据，必须通过函数调用来获取数据
            String response = chatClient.prompt()
                    .user(userContent)
                    .functions("executeQuery", "getSchema", "executePython")
                    .call()
                    .content();

            long apiDuration = System.currentTimeMillis() - apiStartTime;
            log.info("[Agent] Agent loop finished in {}ms. Parsing result...", apiDuration);

            return parseAgentResponse(response);

        } catch (Exception e) {
            log.error("[Agent] Analysis loop failed: {}", e.getMessage(), e);
            return new AgentAnalysisResult("TABLE", "代理分析失败: " + e.getMessage(), "");
        }
    }

    /**
     * 工作区模式分析：注入多表 schema 与显式关系信息
     */
    public AgentAnalysisResult runWorkspaceAnalysis(
            String userQuery,
            String workspaceBusinessContext,
            String workspaceSchema,
            String workspaceRelations,
            DatasetInfo anchorDatasetInfo) {
        try {
            databaseTools.resetCallCounters();
            pythonTools.resetCallCounter();

            String ragContext = "";
            try {
                if (anchorDatasetInfo != null && anchorDatasetInfo.getId() != null) {
                    SearchRequest searchRequest = SearchRequest.query(userQuery)
                            .withTopK(5)
                            .withFilterExpression("datasetId == '" + anchorDatasetInfo.getId() + "'");
                    List<Document> documents = vectorStore.similaritySearch(searchRequest);
                    if (documents != null && !documents.isEmpty()) {
                        ragContext = documents.stream().map(Document::getContent).collect(Collectors.joining("\n"));
                    }
                }
            } catch (Exception e) {
                log.warn("[Workspace Agent] RAG retrieval failed: {}", e.getMessage());
            }

            String userContent = String.format("""
                    [System RAG Context (IMPORTANT BUSINESS KNOWLEDGE)]
                    %s

                    [Workspace Business Context]
                    %s

                    [Workspace Schema]
                    %s

                    [Workspace Relationships]
                    %s

                    User query: %s
                    Primary Table (for fallback exploration): %s

                    IMPORTANT:
                    - Prefer JOINs based on declared relationships.
                    - Do not reference tables outside Workspace Schema.
                    - If one table is enough, single-table SQL is allowed.
                    """,
                    ragContext.isEmpty() ? "No specific business context found." : ragContext,
                    workspaceBusinessContext,
                    workspaceSchema,
                    workspaceRelations,
                    userQuery,
                    anchorDatasetInfo != null ? anchorDatasetInfo.getTableName() : "N/A");

            ChatClient chatClient = chatClientBuilder
                    .defaultSystem(SYSTEM_PROMPT_TEMPLATE)
                    .build();

            String response = chatClient.prompt()
                    .user(userContent)
                    .functions("executeQuery", "getSchema", "executePython")
                    .call()
                    .content();

            return parseAgentResponse(response);
        } catch (Exception e) {
            log.error("[Workspace Agent] Analysis failed: {}", e.getMessage(), e);
            return new AgentAnalysisResult("TABLE", "工作区分析失败: " + e.getMessage(), "");
        }
    }

    /**
     * 多级策略解析 AI 返回的 JSON
     * 1. 直接反序列化整个 response
     * 2. 提取 ```json 代码块
     * 3. 回退到 indexOf('{') 方式
     * 4. 全部失败则返回安全默认值
     */
    private AgentAnalysisResult parseAgentResponse(String response) {
        if (response == null || response.isBlank()) {
            log.warn("[Agent] Response is null or empty");
            return new AgentAnalysisResult("TABLE", "代理未返回分析结果", "");
        }

        // 策略 1：直接解析
        try {
            AgentAnalysisResult result = objectMapper.readValue(response.trim(), AgentAnalysisResult.class);
            log.info("[Agent] Parsed response using strategy 1 (direct parse)");
            return result;
        } catch (Exception e) {
            log.debug("[Agent] Strategy 1 failed: {}", e.getMessage());
        }

        // 策略 2：提取 ```json 代码块
        Matcher matcher = JSON_CODE_BLOCK.matcher(response);
        if (matcher.find()) {
            try {
                AgentAnalysisResult result = objectMapper.readValue(matcher.group(1), AgentAnalysisResult.class);
                log.info("[Agent] Parsed response using strategy 2 (json code block)");
                return result;
            } catch (Exception e) {
                log.debug("[Agent] Strategy 2 failed: {}", e.getMessage());
            }
        }

        // 策略 3：indexOf 花括号（旧方式）
        int startIndex = response.indexOf('{');
        int endIndex = response.lastIndexOf('}');
        if (startIndex >= 0 && endIndex > startIndex) {
            try {
                String jsonStr = response.substring(startIndex, endIndex + 1);
                AgentAnalysisResult result = objectMapper.readValue(jsonStr, AgentAnalysisResult.class);
                log.info("[Agent] Parsed response using strategy 3 (indexOf fallback)");
                return result;
            } catch (Exception e) {
                log.debug("[Agent] Strategy 3 failed: {}", e.getMessage());
            }
        }

        // 策略 4：全部失败，返回安全默认值（将 AI 的文本作为 summary）
        log.warn("[Agent] All parsing strategies failed. Response: {}",
                response.length() > 500 ? response.substring(0, 500) + "..." : response);
        String truncatedResponse = response.length() > 300 ? response.substring(0, 300) + "..." : response;
        return new AgentAnalysisResult("TABLE", truncatedResponse, "");
    }
}
