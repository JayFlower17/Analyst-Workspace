package com.analysis.model.context;

import java.util.List;

import com.analysis.model.entity.ArtifactMemory;

public record MemoryContext(
        String strategy,
        int topK,
        int charBudget,
        List<ArtifactMemory> memories,
        String prompt) {

    public static MemoryContext empty(String prompt) {
        return new MemoryContext("SKIP", 0, 0, List.of(), prompt);
    }

    public String source() {
        return memoryCount() > 0 ? "ARTIFACT_MEMORY" : "NONE";
    }

    public int memoryCount() {
        return memories != null ? memories.size() : 0;
    }

    public int promptChars() {
        return prompt != null ? prompt.length() : 0;
    }
}
