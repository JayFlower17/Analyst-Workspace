package com.analysis.service;

import org.springframework.stereotype.Service;

import com.analysis.model.dto.ExecutorResponse;
import com.analysis.model.execution.PythonExecutionResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class HttpPythonExecutor implements PythonExecutor {

    private final PythonExecutorClient pythonExecutorClient;

    @Override
    public PythonExecutionResult execute(String pythonCode, String duckdbPath, String tableName) {
        long startTime = System.currentTimeMillis();
        try {
            ExecutorResponse response = pythonExecutorClient.executePython(pythonCode, duckdbPath, tableName);
            PythonExecutionResult result = PythonExecutionResult.fromExecutorResponse(
                    pythonCode,
                    response,
                    System.currentTimeMillis() - startTime);
            if (result.success()) {
                log.info("[PythonExecutor] rows={} durationMs={}", result.rowCount(), result.durationMs());
            } else {
                log.warn("[PythonExecutor] failed durationMs={} message={}", result.durationMs(), result.message());
            }
            return result;
        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;
            log.warn("[PythonExecutor] error durationMs={} message={}", durationMs, e.getMessage());
            return PythonExecutionResult.failure(
                    pythonCode,
                    "Python executor failed: " + e.getMessage(),
                    durationMs);
        }
    }
}
