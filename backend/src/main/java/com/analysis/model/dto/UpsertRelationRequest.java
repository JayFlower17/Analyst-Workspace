package com.analysis.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpsertRelationRequest {
    @NotNull(message = "sourceDatasetId 不能为空")
    private Long sourceDatasetId;

    private String sourceTableName;

    @NotBlank(message = "sourceColumnName 不能为空")
    private String sourceColumnName;

    @NotNull(message = "targetDatasetId 不能为空")
    private Long targetDatasetId;

    private String targetTableName;

    @NotBlank(message = "targetColumnName 不能为空")
    private String targetColumnName;

    private String relationType;
    private Double confidence;
}
