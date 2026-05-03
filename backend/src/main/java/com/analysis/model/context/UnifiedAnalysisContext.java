package com.analysis.model.context;

public record UnifiedAnalysisContext(
        StructuredContext structured,
        DocumentContext document,
        SemanticContext semantic) {

    public String schemaPrompt() {
        return structured != null ? structured.schemaPrompt() : "";
    }

    public String relationPrompt() {
        return structured != null ? structured.relationPrompt() : "";
    }

    public String documentPrompt() {
        return document != null ? document.prompt() : "";
    }

    public String businessContextPrompt() {
        return semantic != null ? semantic.businessContextPrompt() : "";
    }

    public UnifiedContextSummary summary() {
        return new UnifiedContextSummary(
                structured != null && structured.groupId() != null ? structured.groupId() : 0,
                structured != null ? structured.source() : "NONE",
                document != null ? document.source() : "NONE",
                semantic != null ? semantic.source() : "NONE",
                structured != null ? structured.datasetCount() : 0,
                structured != null ? structured.relationCount() : 0,
                semantic != null && semantic.hasBusinessContext(),
                document != null ? document.strategy() : "NONE",
                document != null ? document.topK() : 0,
                document != null ? document.charBudget() : 0,
                document != null ? document.chunkCount() : 0,
                structured != null ? structured.schemaPromptChars() : 0,
                structured != null ? structured.relationPromptChars() : 0,
                semantic != null ? semantic.businessContextPromptChars() : 0,
                document != null ? document.promptChars() : 0);
    }
}
