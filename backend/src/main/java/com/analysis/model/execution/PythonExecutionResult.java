package com.analysis.model.execution;

import java.util.List;
import java.util.Map;

import com.analysis.model.dto.ExecutorResponse;
import com.analysis.model.enums.ChartType;

public record PythonExecutionResult(
        boolean success,
        String message,
        String pythonCode,
        List<Map<String, Object>> rows,
        int rowCount,
        long durationMs,
        ChartType recommendedChart,
        String summary,
        ToolExecutionLog executionLog) {

    public static PythonExecutionResult success(
            String pythonCode,
            List<Map<String, Object>> rows,
            long durationMs,
            ChartType recommendedChart,
            String summary) {
        List<Map<String, Object>> safeRows = rows == null ? List.of() : rows;
        PythonExecutionResult result = new PythonExecutionResult(
                true,
                "Python execution completed.",
                pythonCode,
                safeRows,
                safeRows.size(),
                durationMs,
                recommendedChart,
                summary,
                null);
        return result.withExecutionLog(ToolExecutionLog.python(result));
    }

    public static PythonExecutionResult failure(String pythonCode, String message, long durationMs) {
        PythonExecutionResult result = new PythonExecutionResult(
                false,
                message,
                pythonCode,
                List.of(),
                0,
                durationMs,
                null,
                null,
                null);
        return result.withExecutionLog(ToolExecutionLog.python(result));
    }

    public static PythonExecutionResult fromExecutorResponse(
            String pythonCode,
            ExecutorResponse response,
            long durationMs) {
        if (response == null) {
            return failure(pythonCode, "Python executor returned an empty response.", durationMs);
        }
        if (!response.isSuccess()) {
            String message = response.getError() == null || response.getError().isBlank()
                    ? "Python execution failed."
                    : response.getError();
            return failure(pythonCode, message, durationMs);
        }
        return success(
                pythonCode,
                response.getData(),
                durationMs,
                response.getRecommendedChart(),
                response.getSummary());
    }

    private PythonExecutionResult withExecutionLog(ToolExecutionLog executionLog) {
        return new PythonExecutionResult(
                success,
                message,
                pythonCode,
                rows,
                rowCount,
                durationMs,
                recommendedChart,
                summary,
                executionLog);
    }
}
