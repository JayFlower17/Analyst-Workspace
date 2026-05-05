package com.analysis.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.analysis.model.enums.ChartType;
import com.analysis.model.report.AnalysisEvidenceSummary;
import com.analysis.model.report.AnalysisReport;
import com.analysis.model.validation.RiskNotice;
import com.analysis.model.validation.ValidationSeverity;

class ArtifactMemoryExtractorTest {

    @Test
    void extractsRuleBasedMemoriesFromArtifactReport() {
        ArtifactMemoryExtractor extractor = new ArtifactMemoryExtractor();
        AnalysisReport report = new AnalysisReport(
                "Revenue increased by category.",
                List.of(),
                3,
                ChartType.BAR,
                true,
                "SELECT category, SUM(revenue) FROM orders GROUP BY category",
                new AnalysisEvidenceSummary(1L, 2, 1, List.of("orders", "products"), "HEAVY", 2,
                        List.of("Revenue Policy"), true),
                null,
                List.of(new RiskNotice("POLICY_WARNING", ValidationSeverity.WARNING, "Policy conflict detected.",
                        "VALIDATION")),
                List.of());

        var memories = extractor.extract(
                9L,
                1L,
                2L,
                report.generatedCodeOrSql(),
                report.summary(),
                report);

        assertEquals(4, memories.size());
        assertTrue(memories.stream().anyMatch(memory -> "ANALYSIS_FINDING".equals(memory.getMemoryType())));
        assertTrue(memories.stream().anyMatch(memory -> "QUERY_PATTERN".equals(memory.getMemoryType())));
        assertTrue(memories.stream().anyMatch(memory -> "SCHEMA_FINDING".equals(memory.getMemoryType())));
        assertTrue(memories.stream().anyMatch(memory -> "RISK_NOTICE".equals(memory.getMemoryType())));
    }
}
