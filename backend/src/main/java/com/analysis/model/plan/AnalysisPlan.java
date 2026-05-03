package com.analysis.model.plan;

import java.util.List;

import com.analysis.model.route.AnalysisRouteType;

public record AnalysisPlan(
        AnalysisRouteType route,
        String objective,
        List<AnalysisPlanStep> steps) {
}
