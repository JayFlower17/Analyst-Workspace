package com.analysis.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.analysis.model.context.UnifiedAnalysisContext;
import com.analysis.model.dto.DocumentChunkSearchResult;
import com.analysis.model.entity.Dataset;
import com.analysis.model.enums.ChartType;
import com.analysis.model.execution.ToolExecutionLog;
import com.analysis.model.report.AnalysisEvidenceSummary;
import com.analysis.model.report.AnalysisReport;
import com.analysis.model.validation.AnalysisValidationReport;
import com.analysis.model.validation.RiskNotice;

@Service
public class AnalysisReportBuilder {

    public AnalysisReport build(
            String summary,
            List<Map<String, Object>> data,
            ChartType recommendedChart,
            String generatedCodeOrSql,
            UnifiedAnalysisContext context,
            AnalysisValidationReport validationReport,
            List<RiskNotice> riskNotices,
            List<ToolExecutionLog> executionLogs) {
        List<Map<String, Object>> safeData = data == null ? List.of() : data;
        return new AnalysisReport(
                summary,
                safeData,
                safeData.size(),
                recommendedChart,
                generatedCodeOrSql != null && !generatedCodeOrSql.isBlank(),
                generatedCodeOrSql,
                evidenceSummary(context),
                validationReport != null ? validationReport : AnalysisValidationReport.ok(),
                riskNotices != null ? riskNotices : List.of(),
                executionLogs != null ? executionLogs : List.of());
    }

    private AnalysisEvidenceSummary evidenceSummary(UnifiedAnalysisContext context) {
        if (context == null) {
            return new AnalysisEvidenceSummary(0L, 0, 0, List.of(), "NONE", 0, List.of(), false);
        }

        List<String> datasetNames = context.structured() == null || context.structured().datasets() == null
                ? List.of()
                : context.structured().datasets().stream()
                        .map(this::datasetName)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();

        List<String> documentNames = context.document() == null || context.document().chunks() == null
                ? List.of()
                : context.document().chunks().stream()
                        .map(DocumentChunkSearchResult::getDocumentName)
                        .filter(Objects::nonNull)
                        .filter(name -> !name.isBlank())
                        .distinct()
                        .toList();

        return new AnalysisEvidenceSummary(
                context.structured() != null ? context.structured().groupId() : 0L,
                context.structured() != null ? context.structured().datasetCount() : 0,
                context.structured() != null ? context.structured().relationCount() : 0,
                datasetNames,
                context.document() != null ? context.document().strategy() : "NONE",
                context.document() != null ? context.document().chunkCount() : 0,
                documentNames,
                context.semantic() != null && context.semantic().hasBusinessContext());
    }

    private String datasetName(Dataset dataset) {
        if (dataset == null) {
            return null;
        }
        if (dataset.getName() != null && !dataset.getName().isBlank()) {
            return dataset.getName();
        }
        return dataset.getTableName();
    }
}
