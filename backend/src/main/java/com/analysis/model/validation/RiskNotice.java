package com.analysis.model.validation;

public record RiskNotice(
        String code,
        ValidationSeverity severity,
        String message,
        String source) {

    public static RiskNotice fromFinding(AnalysisValidationFinding finding) {
        return new RiskNotice(
                finding.code(),
                finding.severity(),
                finding.message(),
                "VALIDATION");
    }
}
