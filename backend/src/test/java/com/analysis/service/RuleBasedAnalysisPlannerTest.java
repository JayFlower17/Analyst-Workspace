package com.analysis.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.analysis.model.dto.AnalysisRequest;
import com.analysis.model.plan.AnalysisPlan;
import com.analysis.model.plan.AnalysisPlanStepType;
import com.analysis.model.route.AnalysisRouteDecision;
import com.analysis.model.route.AnalysisRouteType;

class RuleBasedAnalysisPlannerTest {

    private final RuleBasedAnalysisPlanner planner = new RuleBasedAnalysisPlanner();

    @Test
    void structuredPlanIncludesSqlExecutionFlow() {
        AnalysisPlan plan = planner.plan(workspaceRequest(), decision(AnalysisRouteType.STRUCTURED_QUERY, true, false));

        assertEquals(AnalysisRouteType.STRUCTURED_QUERY, plan.route());
        assertStepTypes(plan, List.of(
                AnalysisPlanStepType.ROUTE_DECISION,
                AnalysisPlanStepType.BUILD_CONTEXT,
                AnalysisPlanStepType.GENERATE_SQL,
                AnalysisPlanStepType.EXECUTE_SQL,
                AnalysisPlanStepType.SUMMARIZE_RESULT));
    }

    @Test
    void hybridPlanIncludesDocumentRetrievalBeforeSql() {
        AnalysisPlan plan = planner.plan(workspaceRequest(), decision(AnalysisRouteType.HYBRID_ANALYSIS, true, true));

        assertEquals(AnalysisRouteType.HYBRID_ANALYSIS, plan.route());
        assertStepTypes(plan, List.of(
                AnalysisPlanStepType.ROUTE_DECISION,
                AnalysisPlanStepType.BUILD_CONTEXT,
                AnalysisPlanStepType.RETRIEVE_DOCUMENTS,
                AnalysisPlanStepType.GENERATE_SQL,
                AnalysisPlanStepType.EXECUTE_SQL,
                AnalysisPlanStepType.SUMMARIZE_RESULT));
    }

    @Test
    void documentOnlyPlanDoesNotGenerateSql() {
        AnalysisPlan plan = planner.plan(workspaceRequest(), decision(AnalysisRouteType.DOCUMENT_EXPLANATION, false, true));

        assertEquals(AnalysisRouteType.DOCUMENT_EXPLANATION, plan.route());
        assertTrue(plan.steps().stream().noneMatch(step -> step.type() == AnalysisPlanStepType.GENERATE_SQL));
        assertTrue(plan.steps().stream().anyMatch(step -> step.type() == AnalysisPlanStepType.ANSWER_FROM_DOCUMENTS));
    }

    private AnalysisRouteDecision decision(AnalysisRouteType route, boolean allowsSql, boolean requiresDocument) {
        return new AnalysisRouteDecision(route, "test", true, requiresDocument, allowsSql);
    }

    private AnalysisRequest workspaceRequest() {
        AnalysisRequest request = new AnalysisRequest();
        request.setGroupId(1L);
        request.setQuery("test");
        return request;
    }

    private void assertStepTypes(AnalysisPlan plan, List<AnalysisPlanStepType> expectedTypes) {
        assertEquals(expectedTypes, plan.steps().stream().map(step -> step.type()).toList());
    }
}
