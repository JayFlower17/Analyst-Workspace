package com.analysis.ai.tools;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.analysis.model.dto.ExecutorResponse;
import com.analysis.service.PythonExecutorClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PythonTools {

    private final PythonExecutorClient pythonExecutorClient;

    @Value("${duckdb.path:./data/analysis.duckdb}")
    private String duckdbPath;

    /** Python 执行比 SQL 更重，限制更严 */
    private static final int MAX_PYTHON_CALLS = 3;

    private final AtomicInteger pythonCallCount = new AtomicInteger(0);

    /**
     * 重置调用计数器
     */
    public void resetCallCounter() {
        pythonCallCount.set(0);
        log.info("[AI Tool] Python call counter reset for new analysis session");
    }

    public record ExecutePythonRequest(
            @JsonProperty(required = true) @JsonPropertyDescription("The Python code to execute. Pandas 'df' is already loaded with the dataset.") String pythonCode,
            @JsonProperty(required = true) @JsonPropertyDescription("The exact name of the table to execute analysis on.") String tableName) {
    }

    /**
     * Tool for executing complex Python code
     */
    @Bean
    @Description("Execute Python code (pandas/numpy) to analyze data when SQL is not sufficient. The variable 'df' is pre-loaded with the table data.")
    public Function<ExecutePythonRequest, String> executePython() {
        return request -> {
            int callNum = pythonCallCount.incrementAndGet();
            log.info("[AI Tool] executePython call #{} for table: {}", callNum, request.tableName());

            // 调用次数限制
            if (callNum > MAX_PYTHON_CALLS) {
                log.warn("[AI Tool] executePython exceeded max call limit ({})", MAX_PYTHON_CALLS);
                return "Error: You have exceeded the maximum number of Python execution attempts (" + MAX_PYTHON_CALLS
                        + "). Please finalize your analysis with results obtained so far.";
            }

            try {
                ExecutorResponse response = pythonExecutorClient.executePython(
                        request.pythonCode(), duckdbPath, request.tableName());
                
                if (response.isSuccess()) {
                    int remaining = MAX_PYTHON_CALLS - callNum;
                    return "Success. Analysis complete. Summary: " + response.getSummary() + 
                           "\nResult data previews are saved. Please recommend the appropriate chart type! (" + remaining
                           + " Python attempts remaining)";
                } else {
                    int remaining = MAX_PYTHON_CALLS - callNum;
                    return "Error executing Python code: " + response.getError() + 
                           "\nPlease fix the syntax or logic error and execute again. (" + remaining
                           + " Python attempts remaining)";
                }
            } catch (Exception e) {
                log.warn("[AI Tool] executePython failed: {}", e.getMessage());
                return "Error in python executor: " + e.getMessage();
            }
        };
    }
}
