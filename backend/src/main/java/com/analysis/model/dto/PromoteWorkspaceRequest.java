package com.analysis.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PromoteWorkspaceRequest {
    @NotBlank(message = "Workplace 名称不能为空")
    private String workspaceName;

    private String description;
}

