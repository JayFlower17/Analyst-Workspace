package com.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.analysis.model.context.DocumentContext;
import com.analysis.model.dto.DocumentChunkSearchResult;
import com.analysis.model.execution.SqlExecutionResult;
import com.analysis.model.validation.AnalysisValidationReport;
import com.analysis.model.validation.ValidationSeverity;

class AnalysisResultValidatorTest {

    private final AnalysisResultValidator validator = new AnalysisResultValidator();

    @Test
    void warnsWhenSqlResultIsEmpty() {
        SqlExecutionResult sqlResult = SqlExecutionResult.success("select * from orders where 1 = 0", List.of(), 5);

        AnalysisValidationReport report = validator.validateSqlResult(sqlResult);

        assertThat(report.passed()).isTrue();
        assertThat(report.findings()).hasSize(1);
        assertThat(report.findings().get(0).code()).isEqualTo(AnalysisResultValidator.SQL_EMPTY_RESULT);
        assertThat(report.findings().get(0).severity()).isEqualTo(ValidationSeverity.WARNING);
    }

    @Test
    void passesWhenSqlResultHasRows() {
        SqlExecutionResult sqlResult = SqlExecutionResult.success("select 1", List.of(Map.of("value", 1)), 5);

        AnalysisValidationReport report = validator.validateSqlResult(sqlResult);

        assertThat(report.passed()).isTrue();
        assertThat(report.findings()).isEmpty();
    }

    @Test
    void warnsWhenSummaryClaimsNoDataButRowsExist() {
        AnalysisValidationReport report = validator.validateSummaryConsistency(
                "No data was found for this request.",
                List.of(Map.of("channel", "Ads", "revenue", 120)));

        assertThat(report.passed()).isTrue();
        assertThat(report.findings()).hasSize(1);
        assertThat(report.findings().get(0).code()).isEqualTo(AnalysisResultValidator.SUMMARY_RESULT_CONTRADICTION);
        assertThat(report.findings().get(0).severity()).isEqualTo(ValidationSeverity.WARNING);
    }

    @Test
    void passesWhenSummaryAndRowsDoNotObviouslyContradict() {
        AnalysisValidationReport report = validator.validateSummaryConsistency(
                "Ads generated 120 in recognized revenue.",
                List.of(Map.of("channel", "Ads", "revenue", 120)));

        assertThat(report.passed()).isTrue();
        assertThat(report.findings()).isEmpty();
    }

    @Test
    void mergesValidationFindings() {
        AnalysisValidationReport left = validator.validateSqlResult(
                SqlExecutionResult.success("select * from orders where 1 = 0", List.of(), 5));
        AnalysisValidationReport right = validator.validateSummaryConsistency(
                "No rows were returned.",
                List.of(Map.of("channel", "Ads")));

        AnalysisValidationReport merged = left.merge(right);

        assertThat(merged.passed()).isTrue();
        assertThat(merged.findings()).extracting("code")
                .containsExactly(
                        AnalysisResultValidator.SQL_EMPTY_RESULT,
                        AnalysisResultValidator.SUMMARY_RESULT_CONTRADICTION);
    }

    @Test
    void warnsWhenDocumentChunksAreNotReferencedBySummary() {
        AnalysisValidationReport report = validator.validateDocumentEvidenceReference(
                "Paid revenue was highest for mobile phones.",
                documentContext("Revenue Recognition Policy"));

        assertThat(report.passed()).isTrue();
        assertThat(report.findings()).hasSize(1);
        assertThat(report.findings().get(0).code()).isEqualTo(AnalysisResultValidator.DOCUMENT_EVIDENCE_NOT_REFERENCED);
        assertThat(report.findings().get(0).severity()).isEqualTo(ValidationSeverity.WARNING);
    }

    @Test
    void passesWhenSummaryMentionsDocumentName() {
        AnalysisValidationReport report = validator.validateDocumentEvidenceReference(
                "According to the Revenue Recognition Policy, refunded orders are excluded.",
                documentContext("Revenue Recognition Policy"));

        assertThat(report.passed()).isTrue();
        assertThat(report.findings()).isEmpty();
    }

    @Test
    void passesWhenNoDocumentChunksWereRetrieved() {
        AnalysisValidationReport report = validator.validateDocumentEvidenceReference(
                "Paid revenue was highest for mobile phones.",
                new DocumentContext("HEAVY", 4, 2600, List.of(), "No relevant document context found."));

        assertThat(report.passed()).isTrue();
        assertThat(report.findings()).isEmpty();
    }

    private DocumentContext documentContext(String documentName) {
        DocumentChunkSearchResult chunk = new DocumentChunkSearchResult();
        chunk.setDocumentName(documentName);
        chunk.setChunkText("Recognized revenue excludes refunded orders.");
        chunk.setChunkIndex(0);
        chunk.setRetrievalMode("lexical");
        return new DocumentContext("HEAVY", 4, 2600, List.of(chunk), chunk.getChunkText());
    }
}
