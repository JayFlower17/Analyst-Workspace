package com.analysis.ai.tools;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;
import java.util.function.Function;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.analysis.repository.DuckDBRepository;
import com.analysis.security.SqlWhitelist;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseTools {

    private final DuckDBRepository duckDBRepository;
    private final SqlWhitelist sqlWhitelist;

    /** 单次分析中 executeQuery 的最大调用次数 */
    private static final int MAX_QUERY_CALLS = 10;
    /** 单次分析中 getSchema 的最大调用次数 */
    private static final int MAX_SCHEMA_CALLS = 5;
    /** SQL 自动注入的最大行数保护 */
    private static final int AUTO_LIMIT = 10000;
    /** 单条查询超时秒数 */
    private static final int QUERY_TIMEOUT_SECONDS = 30;

    private static final Pattern LIMIT_PATTERN = Pattern.compile(
            "\\bLIMIT\\s+\\d+", Pattern.CASE_INSENSITIVE);

    private final AtomicInteger queryCallCount = new AtomicInteger(0);
    private final AtomicInteger schemaCallCount = new AtomicInteger(0);

    /**
     * 重置调用计数器，在每次新的分析请求开始时调用
     */
    public void resetCallCounters() {
        queryCallCount.set(0);
        schemaCallCount.set(0);
        log.info("[AI Tool] Call counters reset for new analysis session");
    }

    public record ExecuteQueryRequest(
            @JsonProperty(required = true) @JsonPropertyDescription("The SQL query to execute against the DuckDB database") String sql) {
    }

    public record GetSchemaRequest(
            @JsonProperty(required = true) @JsonPropertyDescription("The name of the table to get the schema for") String tableName) {
    }

    /**
     * 为没有 LIMIT 的 SQL 自动注入 LIMIT 保护
     */
    private String injectLimitIfNeeded(String sql) {
        if (!LIMIT_PATTERN.matcher(sql).find()) {
            String trimmed = sql.trim();
            if (trimmed.endsWith(";")) {
                trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
            }
            String safeSql = trimmed + " LIMIT " + AUTO_LIMIT;
            log.info("[AI Tool] Auto-injected LIMIT {} into SQL", AUTO_LIMIT);
            return safeSql;
        }
        return sql;
    }

    /**
     * Tool for executing SQL queries
     */
    @Bean
    @Description("Execute a read-only SQL query against the database to fetch data or preview analysis results")
    public Function<ExecuteQueryRequest, String> executeQuery() {
        return request -> {
            int callNum = queryCallCount.incrementAndGet();
            log.info("[AI Tool] executeQuery call #{} with SQL: {}", callNum, request.sql());

            // 调用次数限制
            if (callNum > MAX_QUERY_CALLS) {
                log.warn("[AI Tool] executeQuery exceeded max call limit ({})", MAX_QUERY_CALLS);
                return "Error: You have exceeded the maximum number of query attempts (" + MAX_QUERY_CALLS
                        + "). Please finalize your analysis with results obtained so far.";
            }

            try {
                if (!sqlWhitelist.validate(request.sql())) {
                    return "Error: SQL validation failed. Only read-only SELECT queries are allowed.";
                }

                // 自动注入 LIMIT 保护
                String safeSql = injectLimitIfNeeded(request.sql());

                // 带超时的查询执行
                List<Map<String, Object>> results = duckDBRepository.executeQueryWithTimeout(
                        safeSql, QUERY_TIMEOUT_SECONDS);
                if (results.isEmpty()) {
                    return "Success: The query executed but returned 0 rows.";
                }
                
                // Convert result up to a reasonable limit to prevent context overflow (e.g. 100 rows max)
                int limit = Math.min(results.size(), 100);
                List<Map<String, Object>> limitedResults = results.subList(0, limit);
                
                String resultStr = limitedResults.stream()
                        .map(Object::toString)
                        .collect(Collectors.joining("\n"));
                
                if (results.size() > 100) {
                    resultStr += "\n... (omitted " + (results.size() - 100) + " more rows)";
                }

                int remaining = MAX_QUERY_CALLS - callNum;
                return "Success. Query returned " + results.size() + " rows. (" + remaining
                        + " query attempts remaining)\nData:\n" + resultStr;
            } catch (SQLException e) {
                log.warn("[AI Tool] executeQuery failed: {}", e.getMessage());
                int remaining = MAX_QUERY_CALLS - callNum;
                return "Error executing SQL: " + e.getMessage()
                        + ". Please fix the SQL syntax or logic and try again. (" + remaining
                        + " query attempts remaining)";
            }
        };
    }

    /**
     * Tool for getting table schema
     */
    @Bean
    @Description("Get the schema (columns, data types, and sample data) of a specific table")
    public Function<GetSchemaRequest, String> getSchema() {
        return request -> {
            int callNum = schemaCallCount.incrementAndGet();
            log.info("[AI Tool] getSchema call #{} for table: {}", callNum, request.tableName());

            // 调用次数限制
            if (callNum > MAX_SCHEMA_CALLS) {
                log.warn("[AI Tool] getSchema exceeded max call limit ({})", MAX_SCHEMA_CALLS);
                return "Error: You have exceeded the maximum number of schema lookups (" + MAX_SCHEMA_CALLS
                        + "). Please use the schema information you already have.";
            }

            try {
                String schemaSql = "PRAGMA table_info('" + request.tableName() + "')";
                List<Map<String, Object>> schema = duckDBRepository.executeQuery(schemaSql);
                
                if (schema.isEmpty()) {
                    return "Error: Table '" + request.tableName() + "' not found.";
                }
                
                String schemaStr = schema.stream()
                        .map(row -> String.format("- %s (%s)", row.get("name"), row.get("type")))
                        .collect(Collectors.joining("\n"));
                
                // Fetch sample data
                String sampleSql = "SELECT * FROM " + request.tableName() + " LIMIT 3";
                List<Map<String, Object>> sample = duckDBRepository.executeQuery(sampleSql);
                String sampleStr = sample.stream().map(Object::toString).collect(Collectors.joining("\n"));
                
                return String.format("Schema for table '%s':\n%s\n\nSample Data (3 rows):\n%s", 
                        request.tableName(), schemaStr, sampleStr);
            } catch (SQLException e) {
                log.warn("[AI Tool] getSchema failed: {}", e.getMessage());
                return "Error retrieving schema: " + e.getMessage();
            }
        };
    }
}
