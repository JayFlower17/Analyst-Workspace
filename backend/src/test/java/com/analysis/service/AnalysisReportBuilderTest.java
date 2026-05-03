package com.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.analysis.model.context.DocumentContext;
import com.analysis.model.context.SemanticContext;
import com.analysis.model.context.StructuredContext;
import com.analysis.model.context.UnifiedAnalysisContext;
import com.analysis.model.dto.DocumentChunkSearchResult;
import com.analysis.model.entity.Dataset;
import com.analysis.model.enums.ChartType;
import com.analysis.model.report.AnalysisReport;
import com.analysis.model.validation.AnalysisValidationReport;

class AnalysisReportBuilderTest {

    private final AnalysisReportBuilder builder = new AnalysisReportBuilder();

    @Test
    void buildsUnifiedReportWithDataCodeEvidenceAndRiskSections() {
        Dataset orders = new Dataset();
        orders.setName("orders.csv");
        orders.setTableName("orders");

        DocumentChunkSearchResult chunk = new DocumentChunkSearchResult();
        chunk.setDocumentName("Revenue Policy");
        chunk.setChunkText("Refunded orders are excluded.");

        UnifiedAnalysisContext context = new UnifiedAnalysisContext(
                new StructuredContext(1L, List.of(orders), List.of(), "schema", "relations"),
                new DocumentContext("HEAVY", 4, 2600, List.of(chunk), "doc prompt"),
                new SemanticContext("business context"));

        AnalysisReport report = builder.build(
                "summary",
                List.of(Map.of("category", "mobile")),
                ChartType.BAR,
                "select * from orders",
                context,
                AnalysisValidationReport.ok(),
                List.of(),
                List.of());

        assertThat(report.summary()).isEqualTo("summary");
        assertThat(report.rowCount()).isEqualTo(1);
        assertThat(report.generatedCodeOrSqlPresent()).isTrue();
        assertThat(report.evidence().datasetNames()).containsExactly("orders.csv");
        assertThat(report.evidence().documentNames()).containsExactly("Revenue Policy");
        assertThat(report.evidence().documentChunkCount()).isEqualTo(1);
        assertThat(report.riskNotices()).isEmpty();
    }

    @Test
    void buildsReportWithoutContext() {
        AnalysisReport report = builder.build(
                "summary",
                null,
                ChartType.TABLE,
                null,
                null,
                null,
                null,
                null);

        assertThat(report.rowCount()).isZero();
        assertThat(report.generatedCodeOrSqlPresent()).isFalse();
        assertThat(report.evidence().datasetCount()).isZero();
        assertThat(report.validationReport().passed()).isTrue();
    }
}
