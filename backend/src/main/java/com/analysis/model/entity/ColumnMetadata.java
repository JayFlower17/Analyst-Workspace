package com.analysis.model.entity;

import lombok.Data;

@Data
public class ColumnMetadata {
    private Long id;
    private Long datasetId;
    private String columnName;
    private String dataType;
    private Boolean nullable;
    private Long distinctCount;
    private String minValue;
    private String maxValue;
    private String sampleValues;
}
