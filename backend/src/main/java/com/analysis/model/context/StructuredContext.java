package com.analysis.model.context;

import java.util.List;

import com.analysis.model.entity.Dataset;
import com.analysis.model.entity.DatasetRelation;

public record StructuredContext(
        Long groupId,
        List<Dataset> datasets,
        List<DatasetRelation> relations,
        String schemaPrompt,
        String relationPrompt) {

    public String source() {
        return "WORKSPACE_SCHEMA";
    }

    public int datasetCount() {
        return datasets != null ? datasets.size() : 0;
    }

    public int relationCount() {
        return relations != null ? relations.size() : 0;
    }

    public int schemaPromptChars() {
        return schemaPrompt != null ? schemaPrompt.length() : 0;
    }

    public int relationPromptChars() {
        return relationPrompt != null ? relationPrompt.length() : 0;
    }
}
