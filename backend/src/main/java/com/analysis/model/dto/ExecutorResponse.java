package com.analysis.model.dto;

import java.util.List;
import java.util.Map;

import com.analysis.model.enums.ChartType;

import lombok.Data;

@Data
public class ExecutorResponse {
    private boolean success;
    private String error;
    private List<Map<String, Object>> data;
    private ChartType recommendedChart;
    private String summary;
}
