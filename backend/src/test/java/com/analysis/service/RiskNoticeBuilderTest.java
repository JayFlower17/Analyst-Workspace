package com.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.analysis.model.validation.AnalysisValidationFinding;
import com.analysis.model.validation.AnalysisValidationReport;
import com.analysis.model.validation.RiskNotice;
import com.analysis.model.validation.ValidationSeverity;

class RiskNoticeBuilderTest {

    private final RiskNoticeBuilder builder = new RiskNoticeBuilder();

    @Test
    void returnsEmptyListWhenThereAreNoFindings() {
        assertThat(builder.build(AnalysisValidationReport.ok())).isEmpty();
    }

    @Test
    void mapsValidationFindingsToRiskNotices() {
        AnalysisValidationReport report = AnalysisValidationReport.withFindings(List.of(
                new AnalysisValidationFinding(
                        AnalysisResultValidator.SQL_EMPTY_RESULT,
                        ValidationSeverity.WARNING,
                        "SQL returned no rows.")));

        List<RiskNotice> notices = builder.build(report);

        assertThat(notices).hasSize(1);
        assertThat(notices.get(0).code()).isEqualTo(AnalysisResultValidator.SQL_EMPTY_RESULT);
        assertThat(notices.get(0).severity()).isEqualTo(ValidationSeverity.WARNING);
        assertThat(notices.get(0).source()).isEqualTo("VALIDATION");
    }
}
