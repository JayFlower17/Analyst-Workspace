package com.analysis.model.dto;

import java.util.List;

import lombok.Data;

@Data
public class DatasetInfo {
    private Long id;
    private String name;
    private String tableName;
    private Long rowCount;
    private Integer columnCount;
    private List<ColumnInfo> columns;

    @Data
    public static class ColumnInfo {
        private String name;
        private String type;
        private Long distinctCount;
        private String minValue;
        private String maxValue;
    }
}
