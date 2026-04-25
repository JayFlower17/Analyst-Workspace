package com.analysis.model.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MarkdownDescriptionRequest {
    @Size(max = 20000, message = "描述内容长度不能超过20000字符")
    private String descriptionMd;
}
