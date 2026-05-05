package com.analysis.model.context;

public record UnifiedContextSummary(
        long groupId,
        String structuredSource,
        String documentSource,
        String semanticSource,
        int datasetCount,
        int relationCount,
        boolean hasSemanticContext,
        String documentStrategy,
        int documentTopK,
        int documentCharBudget,
        int documentChunkCount,
        String memorySource,
        String memoryStrategy,
        int memoryTopK,
        int memoryCharBudget,
        int memoryCount,
        int structuredPromptChars,
        int relationPromptChars,
        int semanticPromptChars,
        int documentPromptChars,
        int memoryPromptChars) {
}
