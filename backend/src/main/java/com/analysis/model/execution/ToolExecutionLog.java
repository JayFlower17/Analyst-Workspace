package com.analysis.model.execution;

import java.util.LinkedHashMap;
import java.util.Map;

public record ToolExecutionLog(
        ToolExecutionType toolType,
        String toolName,
        String stepType,
        boolean success,
        String message,
        long durationMs,
        Map<String, Object> input,
        Map<String, Object> output) {

    public static ToolExecutionLog retrieval(RetrievalExecutionResult result) {
        return new ToolExecutionLog(
                ToolExecutionType.DOCUMENT_RETRIEVAL,
                "RetrievalExecutor",
                "RETRIEVE_DOCUMENTS",
                result.success(),
                result.message(),
                result.durationMs(),
                orderedMap(
                        "groupId", result.groupId(),
                        "queryChars", lengthOf(result.query()),
                        "topK", result.topK()),
                orderedMap(
                        "chunkCount", result.chunkCount(),
                        "retrievalMode", result.retrievalMode()));
    }

    public static ToolExecutionLog sql(SqlExecutionResult result) {
        return new ToolExecutionLog(
                ToolExecutionType.SQL_EXECUTION,
                "SqlExecutor",
                "EXECUTE_SQL",
                result.success(),
                result.message(),
                result.durationMs(),
                orderedMap(
                        "sqlChars", lengthOf(result.sql()),
                        "sqlPresent", result.sql() != null && !result.sql().isBlank()),
                orderedMap("rowCount", result.rowCount()));
    }

    public static ToolExecutionLog python(PythonExecutionResult result) {
        return new ToolExecutionLog(
                ToolExecutionType.PYTHON_EXECUTION,
                "PythonExecutor",
                "EXECUTE_PYTHON",
                result.success(),
                result.message(),
                result.durationMs(),
                orderedMap(
                        "pythonCodeChars", lengthOf(result.pythonCode()),
                        "pythonCodePresent", result.pythonCode() != null && !result.pythonCode().isBlank()),
                orderedMap(
                        "rowCount", result.rowCount(),
                        "recommendedChart", result.recommendedChart() != null ? result.recommendedChart().name() : null));
    }

    private static int lengthOf(String value) {
        return value != null ? value.length() : 0;
    }

    private static Map<String, Object> orderedMap(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            result.put((String) values[i], values[i + 1]);
        }
        return result;
    }
}
