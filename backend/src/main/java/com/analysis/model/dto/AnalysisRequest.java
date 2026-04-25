package com.analysis.model.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AnalysisRequest {
    @NotBlank(message = "查询内容不能为空")
    private String query;

    /**
     * 阶段二主入口：按工作区进行联合分析
     */
    private Long groupId;

    /**
     * 可选：在工作区内指定关注的数据集
     */
    private List<Long> focusDatasetIds;

    /**
     * 兼容旧版单数据集分析入口（逐步废弃）
     */
    private Long datasetId;
}
