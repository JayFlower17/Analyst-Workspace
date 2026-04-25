package com.analysis.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class ExecutorRequest {
    @JsonProperty("task_type")
    private String taskType; // "sql" or "python"
    
    private String sql;
    
    @JsonProperty("python_code")
    private String pythonCode;
    
    @JsonProperty("duckdb_path")
    private String duckdbPath;
    
    @JsonProperty("table_name")
    private String tableName;
}
