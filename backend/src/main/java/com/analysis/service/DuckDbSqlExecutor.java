package com.analysis.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.analysis.model.execution.SqlExecutionResult;
import com.analysis.repository.DuckDBRepository;
import com.analysis.security.SqlWhitelist;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DuckDbSqlExecutor implements SqlExecutor {

    private final SqlWhitelist sqlWhitelist;
    private final DuckDBRepository duckDBRepository;

    @Override
    public SqlExecutionResult execute(String sql) {
        long startedAt = System.currentTimeMillis();
        if (!sqlWhitelist.validate(sql)) {
            log.warn("[SqlExecutor] SQL failed whitelist validation: {}", sql);
            return SqlExecutionResult.failure(
                    sql,
                    "生成的 SQL 未通过安全白名单校验，拒绝执行。",
                    elapsed(startedAt));
        }

        try {
            List<Map<String, Object>> rows = duckDBRepository.executeQuery(sql);
            SqlExecutionResult result = SqlExecutionResult.success(sql, rows, elapsed(startedAt));
            log.info("[SqlExecutor] SQL executed successfully, rows={}, durationMs={}",
                    result.rowCount(), result.durationMs());
            return result;
        } catch (Exception exc) {
            log.warn("[SqlExecutor] SQL execution failed: {}", exc.getMessage());
            return SqlExecutionResult.failure(
                    sql,
                    "SQL execution failed: " + exc.getMessage(),
                    elapsed(startedAt));
        }
    }

    private long elapsed(long startedAt) {
        return System.currentTimeMillis() - startedAt;
    }
}
