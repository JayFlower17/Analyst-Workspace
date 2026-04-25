package com.analysis.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.analysis.model.dto.ExecutorRequest;
import com.analysis.model.dto.ExecutorResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PythonExecutorClient {

    private final RestTemplate restTemplate;

    @Value("${python-executor.url:http://localhost:8000}")
    private String executorUrl;

    public PythonExecutorClient(org.springframework.boot.web.client.RestTemplateBuilder restTemplateBuilder, 
                                @Value("${python-executor.timeout:30000}") int timeout) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(java.time.Duration.ofMillis(timeout))
                .setReadTimeout(java.time.Duration.ofMillis(timeout))
                .build();
    }


    public ExecutorResponse executePython(String pythonCode, String duckdbPath, String tableName) {
        ExecutorRequest request = new ExecutorRequest();
        request.setTaskType("python");
        request.setPythonCode(pythonCode);
        request.setDuckdbPath(duckdbPath);
        request.setTableName(tableName);
        return execute(request);
    }

    private ExecutorResponse execute(ExecutorRequest request) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<ExecutorRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<ExecutorResponse> response = restTemplate.exchange(
                    executorUrl + "/execute",
                    HttpMethod.POST,
                    entity,
                    ExecutorResponse.class);

            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to call Python Executor: {}", e.getMessage());
            ExecutorResponse errorResponse = new ExecutorResponse();
            errorResponse.setSuccess(false);
            errorResponse.setError("Python Executor调用失败: " + e.getMessage());
            return errorResponse;
        }
    }

    public boolean healthCheck() {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    executorUrl + "/health",
                    String.class);
            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            log.warn("Python Executor health check failed: {}", e.getMessage());
            return false;
        }
    }
}
