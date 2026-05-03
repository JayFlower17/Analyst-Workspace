package com.analysis.model.context;

public record SemanticContext(
        String businessContextPrompt) {

    public String source() {
        return "WORKSPACE_METADATA";
    }

    public boolean hasBusinessContext() {
        return businessContextPrompt != null && !businessContextPrompt.isBlank();
    }

    public int businessContextPromptChars() {
        return businessContextPrompt != null ? businessContextPrompt.length() : 0;
    }
}
