package com.analysis.model.execution;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.analysis.model.dto.ExecutorResponse;
import com.analysis.model.enums.ChartType;

class PythonExecutionResultTest {

    @Test
    void mapsSuccessfulExecutorResponse() {
        ExecutorResponse response = new ExecutorResponse();
        response.setSuccess(true);
        response.setData(List.of(Map.of("segment", "priority", "orders", 12)));
        response.setRecommendedChart(ChartType.BAR);
        response.setSummary("Priority orders by segment.");

        PythonExecutionResult result = PythonExecutionResult.fromExecutorResponse("print(df)", response, 25);

        assertThat(result.success()).isTrue();
        assertThat(result.rows()).hasSize(1);
        assertThat(result.rowCount()).isEqualTo(1);
        assertThat(result.durationMs()).isEqualTo(25);
        assertThat(result.recommendedChart()).isEqualTo(ChartType.BAR);
        assertThat(result.summary()).isEqualTo("Priority orders by segment.");
    }

    @Test
    void mapsFailedExecutorResponse() {
        ExecutorResponse response = new ExecutorResponse();
        response.setSuccess(false);
        response.setError("NameError: df is not defined");

        PythonExecutionResult result = PythonExecutionResult.fromExecutorResponse("print(df)", response, 7);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("NameError: df is not defined");
        assertThat(result.rows()).isEmpty();
        assertThat(result.rowCount()).isZero();
        assertThat(result.durationMs()).isEqualTo(7);
    }

    @Test
    void treatsNullExecutorResponseAsFailure() {
        PythonExecutionResult result = PythonExecutionResult.fromExecutorResponse("print(df)", null, 3);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("Python executor returned an empty response.");
        assertThat(result.rows()).isEmpty();
    }
}
