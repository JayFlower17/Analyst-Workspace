package com.analysis.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.analysis.model.dto.AnalysisRequest;
import com.analysis.model.plan.AnalysisPlan;
import com.analysis.model.plan.AnalysisPlanStep;
import com.analysis.model.plan.AnalysisPlanStepType;
import com.analysis.model.route.AnalysisRouteDecision;
import com.analysis.model.route.AnalysisRouteType;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class RuleBasedAnalysisPlanner implements AnalysisPlanner {

    @Override
    public AnalysisPlan plan(AnalysisRequest request, AnalysisRouteDecision routeDecision) {
        AnalysisRouteType route = routeDecision.route();
        AnalysisPlan plan = switch (route) {
            case STRUCTURED_QUERY -> structuredPlan(route);
            case HYBRID_ANALYSIS -> hybridPlan(route);
            case DOCUMENT_EXPLANATION -> documentOnlyPlan(route);
            case LEGACY_DATASET -> legacyDatasetPlan(route);
        };
        log.info("[AnalysisPlanner] route={} steps={}", plan.route(), plan.steps().size());
        return plan;
    }

    private AnalysisPlan structuredPlan(AnalysisRouteType route) {
        return new AnalysisPlan(
                route,
                "Answer the workspace question using structured table context and SQL.",
                List.of(
                        step(1, AnalysisPlanStepType.ROUTE_DECISION, "Classify the query as structured workspace analysis."),
                        step(2, AnalysisPlanStepType.BUILD_CONTEXT, "Build workspace schema, relation, and semantic context."),
                        step(3, AnalysisPlanStepType.GENERATE_SQL, "Generate one SQL query for the requested aggregation or join."),
                        step(4, AnalysisPlanStepType.EXECUTE_SQL, "Execute SQL and collect tabular results."),
                        step(5, AnalysisPlanStepType.SUMMARIZE_RESULT, "Summarize the result with chart recommendation.")));
    }

    private AnalysisPlan hybridPlan(AnalysisRouteType route) {
        return new AnalysisPlan(
                route,
                "Answer the workspace question by combining table analysis with retrieved document context.",
                List.of(
                        step(1, AnalysisPlanStepType.ROUTE_DECISION, "Classify the query as hybrid table-and-document analysis."),
                        step(2, AnalysisPlanStepType.BUILD_CONTEXT, "Build structured, semantic, and document context."),
                        step(3, AnalysisPlanStepType.RETRIEVE_DOCUMENTS, "Retrieve policy, rule, note, or explanatory document chunks."),
                        step(4, AnalysisPlanStepType.GENERATE_SQL, "Generate SQL using document rules as analysis constraints."),
                        step(5, AnalysisPlanStepType.EXECUTE_SQL, "Execute SQL and collect tabular results."),
                        step(6, AnalysisPlanStepType.SUMMARIZE_RESULT, "Summarize table results together with document evidence.")));
    }

    private AnalysisPlan documentOnlyPlan(AnalysisRouteType route) {
        return new AnalysisPlan(
                route,
                "Answer directly from retrieved document context without generating SQL.",
                List.of(
                        step(1, AnalysisPlanStepType.ROUTE_DECISION, "Classify the query as document-only explanation."),
                        step(2, AnalysisPlanStepType.BUILD_CONTEXT, "Build workspace context needed for document retrieval."),
                        step(3, AnalysisPlanStepType.RETRIEVE_DOCUMENTS, "Retrieve relevant document chunks."),
                        step(4, AnalysisPlanStepType.ANSWER_FROM_DOCUMENTS, "Answer from document chunks without SQL generation."),
                        step(5, AnalysisPlanStepType.SUMMARIZE_RESULT, "Return a concise document-grounded answer.")));
    }

    private AnalysisPlan legacyDatasetPlan(AnalysisRouteType route) {
        return new AnalysisPlan(
                route,
                "Answer a legacy single-dataset analysis request.",
                List.of(
                        step(1, AnalysisPlanStepType.ROUTE_DECISION, "Classify the request as legacy dataset analysis."),
                        step(2, AnalysisPlanStepType.GENERATE_SQL, "Generate SQL or Python for the selected dataset."),
                        step(3, AnalysisPlanStepType.EXECUTE_SQL, "Execute generated code or SQL when applicable."),
                        step(4, AnalysisPlanStepType.SUMMARIZE_RESULT, "Summarize the result.")));
    }

    private AnalysisPlanStep step(int order, AnalysisPlanStepType type, String description) {
        return new AnalysisPlanStep(order, type, description);
    }
}
