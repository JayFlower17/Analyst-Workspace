package com.analysis.model.context;

public record UnifiedAnalysisContext(
        StructuredContext structured,
        DocumentContext document,
        SemanticContext semantic,
        MemoryContext memory) {

    public UnifiedAnalysisContext(StructuredContext structured, DocumentContext document, SemanticContext semantic) {
        this(structured, document, semantic, MemoryContext.empty("No artifact memory context available."));
    }

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
        String semanticPrompt = semantic != null ? semantic.businessContextPrompt() : "";
        String memoryPrompt = memoryPrompt();
        if (memoryPrompt.isBlank()) {
            return semanticPrompt;
        }
        if (semanticPrompt.isBlank()) {
            return memoryPrompt;
        }
        return semanticPrompt + "\n\n[Retrieved Artifact Memories]\n" + memoryPrompt;
    }

    public String memoryPrompt() {
        return memory != null ? memory.prompt() : "";
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
                memory != null ? memory.source() : "NONE",
                memory != null ? memory.strategy() : "NONE",
                memory != null ? memory.topK() : 0,
                memory != null ? memory.charBudget() : 0,
                memory != null ? memory.memoryCount() : 0,
                structured != null ? structured.schemaPromptChars() : 0,
                structured != null ? structured.relationPromptChars() : 0,
                semantic != null ? semantic.businessContextPromptChars() : 0,
                document != null ? document.promptChars() : 0,
                memory != null ? memory.promptChars() : 0);
    }
}
