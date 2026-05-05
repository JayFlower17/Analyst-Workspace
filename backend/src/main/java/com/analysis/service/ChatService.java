package com.analysis.service;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.analysis.ai.AnalysisPlanGenerator;
import com.analysis.ai.AnalysisPlanGenerator.AgentAnalysisResult;
import com.analysis.model.dto.ChatAnalyzeRequest;
import com.analysis.model.dto.ChatAnalyzeResponse;
import com.analysis.model.entity.AnalysisArtifact;
import com.analysis.model.entity.ChatMessage;
import com.analysis.model.entity.ChatSession;
import com.analysis.model.entity.Dataset;
import com.analysis.model.entity.DatasetGroup;
import com.analysis.model.enums.ChartType;
import com.analysis.repository.DuckDBRepository;
import com.analysis.security.SqlWhitelist;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final DuckDBRepository duckDBRepository;
    private final DatasetService datasetService;
    private final DatasetGroupService datasetGroupService;
    private final AnalysisPlanGenerator analysisPlanGenerator;
    private final SqlWhitelist sqlWhitelist;
    private final PythonExecutorClient pythonExecutorClient;
    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${duckdb.path:./data/analysis.duckdb}")
    private String duckdbPath;

    @Value("${chat.session.ttl-days:7}")
    private int chatSessionTtlDays;

    public ChatSession createSession(String title) throws SQLException {
        ChatSession session = new ChatSession();
        session.setTitle(title == null || title.isBlank() ? "新建聊天会话" : title.trim());
        session.setStatus("ACTIVE");
        session.setExpiresAt(LocalDateTime.now().plusDays(Math.max(1, chatSessionTtlDays)));
        Long sessionId = duckDBRepository.saveChatSession(session);
        return duckDBRepository.findChatSessionById(sessionId);
    }

    public List<ChatSession> getAllSessions() throws SQLException {
        return duckDBRepository.findAllChatSessions();
    }

    public ChatSession updateSessionTitle(Long sessionId, String title) throws SQLException {
        ensureSessionExists(sessionId);
        String nextTitle = title == null ? "" : title.trim();
        if (nextTitle.isBlank()) {
            throw new IllegalArgumentException("会话标题不能为空");
        }
        duckDBRepository.updateChatSessionTitle(sessionId, nextTitle.length() > 80 ? nextTitle.substring(0, 80) : nextTitle);
        return duckDBRepository.findChatSessionById(sessionId);
    }

    public void deleteSession(Long sessionId) throws SQLException {
        ensureSessionExists(sessionId);
        duckDBRepository.deleteChatSession(sessionId);
    }

    public List<ChatMessage> getSessionMessages(Long sessionId) throws SQLException {
        ensureSessionExists(sessionId);
        return duckDBRepository.findChatMessagesBySessionId(sessionId);
    }

    public List<Dataset> getSessionDatasets(Long sessionId) throws SQLException {
        ensureSessionExists(sessionId);
        return duckDBRepository.findDatasetsByChatSessionId(sessionId);
    }

    public Dataset uploadDatasetToSession(Long sessionId, MultipartFile file, String datasetName) throws Exception {
        ensureSessionExists(sessionId);
        String finalName = (datasetName == null || datasetName.isBlank()) ? file.getOriginalFilename() : datasetName;
        Dataset dataset = datasetService.uploadDataset(file, finalName, null);
        duckDBRepository.bindDatasetToChatSession(sessionId, dataset.getId());
        appendMessage(sessionId, "SYSTEM", "已上传数据表：" + dataset.getName() + "（" + dataset.getTableName() + "）");
        return dataset;
    }

    public ChatAnalyzeResponse analyze(Long sessionId, ChatAnalyzeRequest request) {
        long start = System.currentTimeMillis();
        ChatAnalyzeResponse response = new ChatAnalyzeResponse();
        response.setMode("chat");
        try {
            ChatSession session = ensureSessionExists(sessionId);
            appendMessage(sessionId, "USER", request.getQuery());

            Dataset dataset = resolveDataset(sessionId, request.getDatasetId());
            if (dataset == null) {
                String plainReply = runPlainChat(request.getQuery());
                appendMessage(sessionId, "ASSISTANT", plainReply);
                response.setSuccess(true);
                response.setSummary(plainReply);
                response.setRecommendedChart(ChartType.TABLE);
                response.setData(Collections.emptyList());
                response.setExecutionTime(System.currentTimeMillis() - start);

                Long artifactId = saveArtifactSafely(buildArtifact(
                        "chat",
                        session.getId(),
                        null,
                        null,
                        request.getQuery(),
                        null,
                        plainReply,
                        "TABLE",
                        "[]"));
                response.setArtifactId(artifactId);
                return response;
            }

            AgentAnalysisResult agentResult = analysisPlanGenerator.runAgentAnalysis(
                    request.getQuery(),
                    datasetService.getDatasetInfo(dataset.getId()));
            if (agentResult == null) {
                response.setSuccess(false);
                response.setMessage("Agent 未返回有效结果");
                response.setExecutionTime(System.currentTimeMillis() - start);
                return response;
            }

            String generatedCodeOrSql = agentResult.generatedCodeOrSql();
            List<Map<String, Object>> displayData = Collections.emptyList();
            if (generatedCodeOrSql != null && !generatedCodeOrSql.isBlank()) {
                if (isSql(generatedCodeOrSql)) {
                    if (!sqlWhitelist.validate(generatedCodeOrSql)) {
                        response.setSuccess(false);
                        response.setMessage("生成 SQL 未通过白名单校验");
                        response.setExecutionTime(System.currentTimeMillis() - start);
                        appendMessage(sessionId, "ASSISTANT", "本次生成的 SQL 未通过安全校验，请调整提问后重试。");
                        return response;
                    }
                    displayData = duckDBRepository.executeQuery(generatedCodeOrSql);
                } else {
                    var py = pythonExecutorClient.executePython(generatedCodeOrSql, duckdbPath, dataset.getTableName());
                    if (py.isSuccess() && py.getData() != null) {
                        displayData = py.getData();
                    } else {
                        log.warn("[Chat] Python execution failed: {}", py.getError());
                    }
                }
            }

            ChartType chartType = parseChartType(agentResult.chartType());
            String summary = agentResult.summaryText() == null || agentResult.summaryText().isBlank()
                    ? "分析完成。"
                    : agentResult.summaryText();
            appendMessage(sessionId, "ASSISTANT", summary);

            response.setSuccess(true);
            response.setSummary(summary);
            response.setGeneratedCodeOrSql(generatedCodeOrSql);
            response.setRecommendedChart(chartType);
            response.setData(displayData);
            response.setExecutionTime(System.currentTimeMillis() - start);

            String previewJson = toPreviewJson(displayData);
            Long artifactId = saveArtifactSafely(buildArtifact(
                    "chat",
                    sessionId,
                    null,
                    dataset.getId(),
                    request.getQuery(),
                    generatedCodeOrSql,
                    summary,
                    chartType.name(),
                    previewJson));
            response.setArtifactId(artifactId);

            duckDBRepository.touchChatSession(sessionId);
            return response;
        } catch (Exception e) {
            log.error("[Chat Analyze] failed: {}", e.getMessage(), e);
            response.setSuccess(false);
            response.setMessage("聊天分析失败: " + e.getMessage());
            response.setExecutionTime(System.currentTimeMillis() - start);
            return response;
        }
    }

    public DatasetGroup promoteToWorkspace(Long sessionId, String workspaceName, String description) throws SQLException {
        ensureSessionExists(sessionId);
        List<Dataset> datasets = duckDBRepository.findDatasetsByChatSessionId(sessionId);
        if (datasets.isEmpty()) {
            throw new IllegalArgumentException("当前会话没有可升级的数据集");
        }
        DatasetGroup group = datasetGroupService.createGroup(workspaceName, description);
        for (Dataset dataset : datasets) {
            duckDBRepository.updateDatasetGroupId(dataset.getId(), group.getId());
        }
        appendMessage(sessionId, "SYSTEM", "已升级为 Workplace：" + group.getName() + "（groupId=" + group.getId() + "）");
        duckDBRepository.touchChatSession(sessionId);
        return group;
    }

    private ChatSession ensureSessionExists(Long sessionId) throws SQLException {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }
        ChatSession session = duckDBRepository.findChatSessionById(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("聊天会话不存在: " + sessionId);
        }
        return session;
    }

    private void appendMessage(Long sessionId, String role, String content) throws SQLException {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        duckDBRepository.saveChatMessage(message);
        duckDBRepository.touchChatSession(sessionId);
    }

    private Dataset resolveDataset(Long sessionId, Long datasetId) throws SQLException {
        List<Dataset> datasets = duckDBRepository.findDatasetsByChatSessionId(sessionId);
        if (datasets.isEmpty()) {
            return null;
        }
        if (datasetId == null) {
            return datasets.get(0);
        }
        return datasets.stream()
                .filter(ds -> ds.getId().equals(datasetId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("指定 datasetId 不属于当前会话"));
    }

    private String runPlainChat(String query) {
        ChatClient chatClient = chatClientBuilder
                .defaultSystem("You are a helpful data assistant. Answer in concise Chinese.")
                .build();
        String content = chatClient.prompt()
                .user(query)
                .call()
                .content();
        return (content == null || content.isBlank()) ? "我已经收到你的问题，但暂时没有生成有效回复。" : content;
    }

    private boolean isSql(String codeOrSql) {
        String upper = codeOrSql.trim().toUpperCase();
        return upper.startsWith("SELECT") || upper.startsWith("WITH");
    }

    private ChartType parseChartType(String chartTypeText) {
        try {
            return ChartType.valueOf(chartTypeText.toUpperCase());
        } catch (Exception e) {
            return ChartType.TABLE;
        }
    }

    private AnalysisArtifact buildArtifact(
            String mode,
            Long sessionId,
            Long groupId,
            Long datasetId,
            String query,
            String generatedCodeOrSql,
            String summary,
            String chartType,
            String resultPreviewJson) {
        AnalysisArtifact artifact = new AnalysisArtifact();
        artifact.setMode(mode);
        artifact.setSessionId(sessionId);
        artifact.setGroupId(groupId);
        artifact.setDatasetId(datasetId);
        artifact.setUserQuery(query);
        artifact.setGeneratedCodeOrSql(generatedCodeOrSql);
        artifact.setSummary(summary);
        artifact.setChartType(chartType);
        artifact.setResultPreviewJson(resultPreviewJson);
        return artifact;
    }

    private Long saveArtifactSafely(AnalysisArtifact artifact) {
        try {
            return duckDBRepository.saveAnalysisArtifact(artifact);
        } catch (Exception e) {
            log.warn("[Chat] save artifact failed: {}", e.getMessage());
            return null;
        }
    }

    private String toPreviewJson(List<Map<String, Object>> displayData) {
        try {
            int max = Math.min(displayData.size(), 20);
            return objectMapper.writeValueAsString(displayData.subList(0, max));
        } catch (Exception e) {
            return "[]";
        }
    }
}
