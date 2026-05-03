package com.analysis.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.analysis.model.validation.AnalysisValidationReport;
import com.analysis.model.validation.RiskNotice;

@Service
public class RiskNoticeBuilder {

    public List<RiskNotice> build(AnalysisValidationReport validationReport) {
        if (validationReport == null || !validationReport.hasFindings()) {
            return List.of();
        }

        return validationReport.findings().stream()
                .map(RiskNotice::fromFinding)
                .toList();
    }
}
