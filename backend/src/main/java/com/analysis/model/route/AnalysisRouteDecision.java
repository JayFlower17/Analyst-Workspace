package com.analysis.model.route;

public record AnalysisRouteDecision(
        AnalysisRouteType route,
        String reason,
        boolean requiresWorkspaceContext,
        boolean requiresDocumentContext,
        boolean allowsSqlGeneration) {
}
