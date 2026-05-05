package com.analysis.model.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.analysis.model.execution.ToolExecutionLog;
import com.analysis.model.entity.ArtifactMemory;
import com.analysis.model.entity.ContextTrace;
import com.analysis.model.report.AnalysisEvidenceSummary;
import com.analysis.model.report.AnalysisReport;
import com.analysis.model.validation.AnalysisValidationReport;
import com.analysis.model.validation.RiskNotice;

import lombok.Data;

@Data
public class ArtifactDetailResponse {
    private Long id;
    private String mode;
    private Long sessionId;
    private Long groupId;
    private Long datasetId;
    private Long contextTraceId;
    private String userQuery;
    private String generatedCodeOrSql;
    private String summary;
    private String chartType;
    private String resultPreviewJson;
    private List<Map<String, Object>> resultPreview;
    private Integer artifactSchemaVersion;
    private boolean reportAvailable;
    private AnalysisReport analysisReport;
    private AnalysisEvidenceSummary evidence;
    private List<ToolExecutionLog> executionLogs;
    private AnalysisValidationReport validationReport;
    private List<RiskNotice> riskNotices;
    private List<ArtifactMemory> memories;
    private ContextTrace contextTrace;
    private String artifactStatus;
    private LocalDateTime archivedAt;
    private LocalDateTime deletedAt;
    private LocalDateTime updatedAt;
    private LocalDateTime createdAt;
}
