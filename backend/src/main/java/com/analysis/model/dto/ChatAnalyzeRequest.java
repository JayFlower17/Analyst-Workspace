package com.analysis.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatAnalyzeRequest {
    @NotBlank(message = "查询内容不能为空")
    private String query;

    /**
     * 可选：指定当前聊天会话中的数据集进行分析
     */
    private Long datasetId;
}

