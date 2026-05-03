package com.analysis.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.analysis.model.context.DocumentContext;
import com.analysis.model.dto.DocumentChunkSearchResult;
import com.analysis.model.execution.SqlExecutionResult;
import com.analysis.model.validation.AnalysisValidationFinding;
import com.analysis.model.validation.AnalysisValidationReport;
import com.analysis.model.validation.ValidationSeverity;

@Service
public class AnalysisResultValidator {

    public static final String SQL_EMPTY_RESULT = "SQL_EMPTY_RESULT";
    public static final String SUMMARY_RESULT_CONTRADICTION = "SUMMARY_RESULT_CONTRADICTION";
    public static final String DOCUMENT_EVIDENCE_NOT_REFERENCED = "DOCUMENT_EVIDENCE_NOT_REFERENCED";

    public AnalysisValidationReport validateSqlResult(SqlExecutionResult sqlResult) {
        if (sqlResult == null) {
            return AnalysisValidationReport.ok();
        }

        List<AnalysisValidationFinding> findings = new ArrayList<>();
        if (sqlResult.success() && sqlResult.rowCount() == 0) {
            findings.add(new AnalysisValidationFinding(
                    SQL_EMPTY_RESULT,
                    ValidationSeverity.WARNING,
                    "SQL executed successfully but returned no rows; treat the answer as an empty-result finding, not a confirmed business conclusion."));
        }
        return AnalysisValidationReport.withFindings(findings);
    }

    public AnalysisValidationReport validateSummaryConsistency(String summary, List<Map<String, Object>> rows) {
        List<AnalysisValidationFinding> findings = new ArrayList<>();
        int rowCount = rows == null ? 0 : rows.size();
        if (rowCount > 0 && claimsNoData(summary)) {
            findings.add(new AnalysisValidationFinding(
                    SUMMARY_RESULT_CONTRADICTION,
                    ValidationSeverity.WARNING,
                    "Summary appears to claim that no data or no results were found, but the execution result contains rows."));
        }
        return AnalysisValidationReport.withFindings(findings);
    }

    public AnalysisValidationReport validateDocumentEvidenceReference(String summary, DocumentContext documentContext) {
        List<AnalysisValidationFinding> findings = new ArrayList<>();
        if (documentContext == null || documentContext.chunks() == null || documentContext.chunks().isEmpty()) {
            return AnalysisValidationReport.withFindings(findings);
        }

        if (!referencesDocumentEvidence(summary, documentContext.chunks())) {
            findings.add(new AnalysisValidationFinding(
                    DOCUMENT_EVIDENCE_NOT_REFERENCED,
                    ValidationSeverity.WARNING,
                    "Relevant document chunks were retrieved, but the summary does not clearly reference the document evidence."));
        }
        return AnalysisValidationReport.withFindings(findings);
    }

    private boolean claimsNoData(String summary) {
        if (summary == null || summary.isBlank()) {
            return false;
        }
        String normalized = summary.toLowerCase(Locale.ROOT);
        return containsAny(normalized,
                "no data", "no rows", "no result", "no results", "empty result", "returned no rows",
                "没有数据", "无数据", "没有结果", "无结果", "空结果", "未找到结果");
    }

    private boolean referencesDocumentEvidence(String summary, List<DocumentChunkSearchResult> chunks) {
        if (summary == null || summary.isBlank()) {
            return false;
        }

        String normalized = summary.toLowerCase(Locale.ROOT);
        if (containsAny(normalized,
                "according to", "based on", "per the", "document", "policy", "note", "rule",
                "根据", "按照", "文档", "政策", "规则", "备注", "说明")) {
            return true;
        }

        for (DocumentChunkSearchResult chunk : chunks) {
            if (matchesDocumentName(normalized, chunk.getDocumentName())) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesDocumentName(String normalizedSummary, String documentName) {
        if (documentName == null || documentName.isBlank()) {
            return false;
        }

        String normalizedName = documentName.toLowerCase(Locale.ROOT);
        if (normalizedSummary.contains(normalizedName)) {
            return true;
        }

        List<String> significantTokens = new ArrayList<>();
        for (String token : normalizedName.split("[^\\p{IsAlphabetic}\\p{IsDigit}]+")) {
            if (token.length() >= 4 && !isCommonDocumentToken(token)) {
                significantTokens.add(token);
            }
        }

        int matches = 0;
        for (String token : significantTokens) {
            if (normalizedSummary.contains(token)) {
                matches++;
            }
        }
        return !significantTokens.isEmpty() && matches >= Math.min(2, significantTokens.size());
    }

    private boolean isCommonDocumentToken(String token) {
        return List.of("document", "workspace", "analysis", "context", "note", "policy").contains(token);
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
