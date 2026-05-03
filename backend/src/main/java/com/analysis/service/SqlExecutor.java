package com.analysis.service;

import com.analysis.model.execution.SqlExecutionResult;

public interface SqlExecutor {

    SqlExecutionResult execute(String sql);
}
