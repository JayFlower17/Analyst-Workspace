package com.analysis.model.dto;

import java.util.List;
import java.util.Map;

import com.analysis.model.context.UnifiedContextSummary;
import com.analysis.model.enums.ChartType;
import com.analysis.model.execution.ToolExecutionLog;
import com.analysis.model.plan.AnalysisPlan;
import com.analysis.model.report.AnalysisReport;
import com.analysis.model.route.AnalysisRouteDecision;
import com.analysis.model.validation.AnalysisValidationReport;
import com.analysis.model.validation.RiskNotice;

import lombok.Data;

@Data
public class AnalysisResponse {
    private boolean success;
    private String message;
    private List<Map<String, Object>> data;
    private ChartType recommendedChart;
    private String summary;
    private String generatedSql;
    private Long executionTime;
    private Long artifactId;
    private Long contextTraceId;
    private UnifiedContextSummary contextSummary;
    private AnalysisRouteDecision routeDecision;
    private AnalysisPlan analysisPlan;
    private List<ToolExecutionLog> executionLogs;
    private AnalysisValidationReport validationReport;
    private List<RiskNotice> riskNotices;
    private AnalysisReport analysisReport;
}
