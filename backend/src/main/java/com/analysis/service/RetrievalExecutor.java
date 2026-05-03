package com.analysis.service;

import com.analysis.model.execution.RetrievalExecutionResult;

public interface RetrievalExecutor {

    RetrievalExecutionResult retrieve(Long groupId, String query, int topK);
}
