package com.analysis.service;

import com.analysis.model.dto.AnalysisRequest;
import com.analysis.model.plan.AnalysisPlan;
import com.analysis.model.route.AnalysisRouteDecision;

public interface AnalysisPlanner {

    AnalysisPlan plan(AnalysisRequest request, AnalysisRouteDecision routeDecision);
}
