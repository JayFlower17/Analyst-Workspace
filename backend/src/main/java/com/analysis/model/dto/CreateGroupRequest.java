package com.analysis.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateGroupRequest {
    @NotBlank(message = "工作区名称不能为空")
    @Size(max = 100, message = "工作区名称长度不能超过100")
    private String name;

    @Size(max = 500, message = "工作区描述长度不能超过500")
    private String description;
}
