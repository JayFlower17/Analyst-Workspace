package com.analysis.service;

import com.analysis.model.execution.PythonExecutionResult;

public interface PythonExecutor {

    PythonExecutionResult execute(String pythonCode, String duckdbPath, String tableName);
}
