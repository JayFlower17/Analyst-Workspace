package com.analysis.model.validation;

public record AnalysisValidationFinding(
        String code,
        ValidationSeverity severity,
        String message) {
}
