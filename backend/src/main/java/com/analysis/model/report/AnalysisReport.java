package com.analysis.model.report;

import java.util.List;
import java.util.Map;

import com.analysis.model.enums.ChartType;
import com.analysis.model.execution.ToolExecutionLog;
import com.analysis.model.validation.AnalysisValidationReport;
import com.analysis.model.validation.RiskNotice;

public record AnalysisReport(
        String summary,
        List<Map<String, Object>> data,
        int rowCount,
        ChartType recommendedChart,
        boolean generatedCodeOrSqlPresent,
        String generatedCodeOrSql,
        AnalysisEvidenceSummary evidence,
        AnalysisValidationReport validationReport,
        List<RiskNotice> riskNotices,
        List<ToolExecutionLog> executionLogs) {
}
