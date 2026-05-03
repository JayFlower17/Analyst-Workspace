package com.analysis.model.plan;

public record AnalysisPlanStep(
        int order,
        AnalysisPlanStepType type,
        String description) {
}
