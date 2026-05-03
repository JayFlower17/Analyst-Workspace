package com.analysis.model.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class SqlExecutionResultTest {

    @Test
    void successIncludesRowsAndRowCount() {
        SqlExecutionResult result = SqlExecutionResult.success(
                "SELECT 1",
                List.of(Map.of("value", 1)),
                12);

        assertTrue(result.success());
        assertEquals("ok", result.message());
        assertEquals(1, result.rowCount());
        assertEquals(1, result.rows().size());
        assertEquals(12, result.durationMs());
    }

    @Test
    void failureUsesEmptyRows() {
        SqlExecutionResult result = SqlExecutionResult.failure(
                "DROP TABLE x",
                "rejected",
                4);

        assertFalse(result.success());
        assertEquals("rejected", result.message());
        assertEquals(0, result.rowCount());
        assertTrue(result.rows().isEmpty());
    }
}
