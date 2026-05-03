package com.analysis.service;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.analysis.model.dto.ArtifactDetailResponse;
import com.analysis.model.entity.AnalysisArtifact;
import com.analysis.model.execution.ToolExecutionLog;
import com.analysis.model.report.AnalysisEvidenceSummary;
import com.analysis.model.report.AnalysisReport;
import com.analysis.model.validation.AnalysisValidationReport;
import com.analysis.model.validation.RiskNotice;
import com.analysis.repository.DuckDBRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class ArtifactService {

    private final DuckDBRepository duckDBRepository;
    private final ObjectMapper objectMapper;

    public List<AnalysisArtifact> getRecentArtifacts(Long sessionId, Long groupId, int limit, String status)
            throws SQLException {
        int safeLimit = Math.max(1, Math.min(limit, 20));
        String safeStatus = normalizeStatus(status);
        if (sessionId != null) {
            return duckDBRepository.findRecentArtifactsBySessionId(sessionId, safeLimit, safeStatus);
        }
        if (groupId != null) {
            return duckDBRepository.findRecentArtifactsByGroupId(groupId, safeLimit, safeStatus);
        }
        throw new IllegalArgumentException("请提供 sessionId 或 groupId");
    }

    public List<AnalysisArtifact> getRecentArtifacts(Long sessionId, Long groupId, int limit) throws SQLException {
        return getRecentArtifacts(sessionId, groupId, limit, "ACTIVE");
    }

    public ArtifactDetailResponse getArtifactDetail(Long id) throws SQLException {
        AnalysisArtifact artifact = duckDBRepository.findAnalysisArtifactById(id);
        if (artifact == null) {
            throw new IllegalArgumentException("Artifact not found: " + id);
        }
        return toDetailResponse(artifact);
    }

    public ArtifactDetailResponse archiveArtifact(Long id) throws SQLException {
        return updateArtifactStatus(id, "ARCHIVED");
    }

    public ArtifactDetailResponse restoreArtifact(Long id) throws SQLException {
        return updateArtifactStatus(id, "ACTIVE");
    }

    public ArtifactDetailResponse deleteArtifact(Long id) throws SQLException {
        return updateArtifactStatus(id, "DELETED");
    }

    private ArtifactDetailResponse updateArtifactStatus(Long id, String status) throws SQLException {
        boolean updated = duckDBRepository.updateAnalysisArtifactStatus(id, normalizeStatus(status));
        if (!updated) {
            throw new IllegalArgumentException("Artifact not found: " + id);
        }
        return getArtifactDetail(id);
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "ACTIVE";
        }
        String normalizedStatus = status.trim().toUpperCase();
        return switch (normalizedStatus) {
            case "ACTIVE", "ARCHIVED", "DELETED" -> normalizedStatus;
            default -> throw new IllegalArgumentException("Unsupported artifact status: " + status);
        };
    }

    private ArtifactDetailResponse toDetailResponse(AnalysisArtifact artifact) {
        ArtifactDetailResponse detail = new ArtifactDetailResponse();
        detail.setId(artifact.getId());
        detail.setMode(artifact.getMode());
        detail.setSessionId(artifact.getSessionId());
        detail.setGroupId(artifact.getGroupId());
        detail.setDatasetId(artifact.getDatasetId());
        detail.setUserQuery(artifact.getUserQuery());
        detail.setGeneratedCodeOrSql(artifact.getGeneratedCodeOrSql());
        detail.setSummary(artifact.getSummary());
        detail.setChartType(artifact.getChartType());
        detail.setResultPreviewJson(artifact.getResultPreviewJson());
        detail.setResultPreview(parseResultPreview(artifact.getResultPreviewJson()));
        detail.setArtifactSchemaVersion(artifact.getArtifactSchemaVersion());
        detail.setCreatedAt(artifact.getCreatedAt());

        AnalysisReport report = parseAnalysisReport(artifact.getAnalysisReportJson());
        detail.setAnalysisReport(report);
        detail.setEvidence(parseEvidenceSummary(artifact.getEvidenceSummaryJson(), report));
        detail.setExecutionLogs(parseExecutionLogs(artifact.getExecutionLogsJson(), report));
        detail.setValidationReport(parseValidationReport(artifact.getValidationReportJson(), report));
        detail.setRiskNotices(parseRiskNotices(artifact.getRiskNoticesJson(), report));
        detail.setReportAvailable(report != null);
        detail.setArtifactStatus(artifact.getArtifactStatus());
        detail.setArchivedAt(artifact.getArchivedAt());
        detail.setDeletedAt(artifact.getDeletedAt());
        detail.setUpdatedAt(artifact.getUpdatedAt());
        return detail;
    }

    private List<Map<String, Object>> parseResultPreview(String resultPreviewJson) {
        if (resultPreviewJson == null || resultPreviewJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(resultPreviewJson, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (Exception e) {
            log.warn("[Artifact] failed to parse result preview json: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private AnalysisReport parseAnalysisReport(String analysisReportJson) {
        if (analysisReportJson == null || analysisReportJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(analysisReportJson, AnalysisReport.class);
        } catch (Exception e) {
            log.warn("[Artifact] failed to parse analysis report json: {}", e.getMessage());
            return null;
        }
    }

    private AnalysisEvidenceSummary parseEvidenceSummary(String evidenceSummaryJson, AnalysisReport fallbackReport) {
        if (evidenceSummaryJson != null && !evidenceSummaryJson.isBlank()) {
            try {
                return objectMapper.readValue(evidenceSummaryJson, AnalysisEvidenceSummary.class);
            } catch (Exception e) {
                log.warn("[Artifact] failed to parse evidence summary json: {}", e.getMessage());
            }
        }
        return fallbackReport != null ? fallbackReport.evidence() : null;
    }

    private List<ToolExecutionLog> parseExecutionLogs(String executionLogsJson, AnalysisReport fallbackReport) {
        if (executionLogsJson != null && !executionLogsJson.isBlank()) {
            try {
                return objectMapper.readValue(executionLogsJson, new TypeReference<List<ToolExecutionLog>>() {
                });
            } catch (Exception e) {
                log.warn("[Artifact] failed to parse execution logs json: {}", e.getMessage());
            }
        }
        if (fallbackReport != null && fallbackReport.executionLogs() != null) {
            return fallbackReport.executionLogs();
        }
        return Collections.emptyList();
    }

    private AnalysisValidationReport parseValidationReport(
            String validationReportJson,
            AnalysisReport fallbackReport) {
        if (validationReportJson != null && !validationReportJson.isBlank()) {
            try {
                return objectMapper.readValue(validationReportJson, AnalysisValidationReport.class);
            } catch (Exception e) {
                log.warn("[Artifact] failed to parse validation report json: {}", e.getMessage());
            }
        }
        return fallbackReport != null ? fallbackReport.validationReport() : null;
    }

    private List<RiskNotice> parseRiskNotices(String riskNoticesJson, AnalysisReport fallbackReport) {
        if (riskNoticesJson != null && !riskNoticesJson.isBlank()) {
            try {
                return objectMapper.readValue(riskNoticesJson, new TypeReference<List<RiskNotice>>() {
                });
            } catch (Exception e) {
                log.warn("[Artifact] failed to parse risk notices json: {}", e.getMessage());
            }
        }
        if (fallbackReport != null && fallbackReport.riskNotices() != null) {
            return fallbackReport.riskNotices();
        }
        return Collections.emptyList();
    }
}
