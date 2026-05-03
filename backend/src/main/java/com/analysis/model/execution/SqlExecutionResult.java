package com.analysis.model.execution;

import java.util.List;
import java.util.Map;

public record SqlExecutionResult(
        boolean success,
        String message,
        String sql,
        List<Map<String, Object>> rows,
        int rowCount,
        long durationMs,
        ToolExecutionLog executionLog) {

    public static SqlExecutionResult success(String sql, List<Map<String, Object>> rows, long durationMs) {
        List<Map<String, Object>> safeRows = rows != null ? rows : List.of();
        SqlExecutionResult result = new SqlExecutionResult(
                true,
                "ok",
                sql,
                safeRows,
                safeRows.size(),
                durationMs,
                null);
        return result.withExecutionLog(ToolExecutionLog.sql(result));
    }

    public static SqlExecutionResult failure(String sql, String message, long durationMs) {
        SqlExecutionResult result = new SqlExecutionResult(
                false,
                message,
                sql,
                List.of(),
                0,
                durationMs,
                null);
        return result.withExecutionLog(ToolExecutionLog.sql(result));
    }

    private SqlExecutionResult withExecutionLog(ToolExecutionLog executionLog) {
        return new SqlExecutionResult(success, message, sql, rows, rowCount, durationMs, executionLog);
    }
}
