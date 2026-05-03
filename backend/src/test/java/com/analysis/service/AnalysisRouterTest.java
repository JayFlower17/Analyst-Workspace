package com.analysis.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.analysis.model.dto.AnalysisRequest;
import com.analysis.model.route.AnalysisRouteDecision;
import com.analysis.model.route.AnalysisRouteType;

class AnalysisRouterTest {

    private final AnalysisRouter router = new AnalysisRouter();

    @Test
    void routesWorkspaceAggregationToStructuredQuery() {
        AnalysisRequest request = workspaceRequest(
                "Using SQL only, summarize paid revenue by traffic channel.");

        AnalysisRouteDecision decision = router.decide(request);

        assertEquals(AnalysisRouteType.STRUCTURED_QUERY, decision.route());
        assertTrue(decision.requiresWorkspaceContext());
        assertFalse(decision.requiresDocumentContext());
        assertTrue(decision.allowsSqlGeneration());
    }

    @Test
    void routesPolicyQuestionToHybridAnalysis() {
        AnalysisRequest request = workspaceRequest(
                "Join orders and products according to the revenue recognition policy.");

        AnalysisRouteDecision decision = router.decide(request);

        assertEquals(AnalysisRouteType.HYBRID_ANALYSIS, decision.route());
        assertTrue(decision.requiresDocumentContext());
        assertTrue(decision.allowsSqlGeneration());
    }

    @Test
    void routesExplicitNoSqlQuestionToDocumentExplanation() {
        AnalysisRequest request = workspaceRequest(
                "According to the revenue recognition policy, how are REFUNDED orders treated? Answer from the document only; no SQL is needed.");

        AnalysisRouteDecision decision = router.decide(request);

        assertEquals(AnalysisRouteType.DOCUMENT_EXPLANATION, decision.route());
        assertTrue(decision.requiresDocumentContext());
        assertFalse(decision.allowsSqlGeneration());
    }

    @Test
    void routesDatasetOnlyRequestToLegacyDataset() {
        AnalysisRequest request = new AnalysisRequest();
        request.setDatasetId(1L);
        request.setQuery("Summarize sales by month.");

        AnalysisRouteDecision decision = router.decide(request);

        assertEquals(AnalysisRouteType.LEGACY_DATASET, decision.route());
        assertFalse(decision.requiresWorkspaceContext());
    }

    private AnalysisRequest workspaceRequest(String query) {
        AnalysisRequest request = new AnalysisRequest();
        request.setGroupId(1L);
        request.setQuery(query);
        return request;
    }
}
