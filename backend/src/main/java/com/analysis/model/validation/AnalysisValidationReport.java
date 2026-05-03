package com.analysis.model.validation;

import java.util.ArrayList;
import java.util.List;

public record AnalysisValidationReport(
        boolean passed,
        List<AnalysisValidationFinding> findings) {

    public static AnalysisValidationReport ok() {
        return new AnalysisValidationReport(true, List.of());
    }

    public static AnalysisValidationReport withFindings(List<AnalysisValidationFinding> findings) {
        List<AnalysisValidationFinding> safeFindings = findings == null ? List.of() : findings;
        boolean passed = safeFindings.stream()
                .noneMatch(finding -> finding.severity() == ValidationSeverity.ERROR);
        return new AnalysisValidationReport(passed, safeFindings);
    }

    public boolean hasFindings() {
        return findings != null && !findings.isEmpty();
    }

    public AnalysisValidationReport merge(AnalysisValidationReport other) {
        if (other == null || !other.hasFindings()) {
            return this;
        }
        if (!hasFindings()) {
            return other;
        }

        List<AnalysisValidationFinding> merged = new ArrayList<>();
        merged.addAll(findings);
        merged.addAll(other.findings());
        return withFindings(merged);
    }
}
