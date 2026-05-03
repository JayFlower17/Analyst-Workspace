package com.analysis.model.report;

import java.util.List;

public record AnalysisEvidenceSummary(
        Long groupId,
        int datasetCount,
        int relationCount,
        List<String> datasetNames,
        String documentStrategy,
        int documentChunkCount,
        List<String> documentNames,
        boolean hasSemanticContext) {
}
