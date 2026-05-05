package com.analysis.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.analysis.model.entity.ArtifactMemory;
import com.analysis.model.report.AnalysisEvidenceSummary;
import com.analysis.model.report.AnalysisReport;
import com.analysis.model.validation.RiskNotice;

@Service
public class ArtifactMemoryExtractor {

    public List<ArtifactMemory> extract(
            Long artifactId,
            Long groupId,
            Long datasetId,
            String generatedCodeOrSql,
            String summary,
            AnalysisReport analysisReport) {
        List<ArtifactMemory> memories = new ArrayList<>();

        if (hasText(summary)) {
            memories.add(memory(
                    artifactId,
                    groupId,
                    datasetId,
                    "ANALYSIS_FINDING",
                    groupId != null ? "WORKSPACE_LOCAL" : "DATASET_LOCAL",
                    summary,
                    clip(summary, 360),
                    0.65,
                    0.70));
        }

        if (hasText(generatedCodeOrSql)) {
            memories.add(memory(
                    artifactId,
                    groupId,
                    datasetId,
                    "QUERY_PATTERN",
                    groupId != null ? "WORKSPACE_LOCAL" : "DATASET_LOCAL",
                    generatedCodeOrSql,
                    clip(generatedCodeOrSql, 360),
                    0.55,
                    0.65));
        }

        AnalysisEvidenceSummary evidence = analysisReport != null ? analysisReport.evidence() : null;
        if (evidence != null && evidence.datasetNames() != null && !evidence.datasetNames().isEmpty()) {
            String content = "Datasets used: " + String.join(", ", evidence.datasetNames())
                    + "; relationCount=" + evidence.relationCount()
                    + "; documentStrategy=" + evidence.documentStrategy()
                    + "; documentChunkCount=" + evidence.documentChunkCount();
            memories.add(memory(
                    artifactId,
                    groupId != null ? groupId : evidence.groupId(),
                    datasetId,
                    "SCHEMA_FINDING",
                    groupId != null ? "WORKSPACE_LOCAL" : "DATASET_LOCAL",
                    content,
                    clip(content, 360),
                    0.50,
                    0.60));
        }

        if (analysisReport != null && analysisReport.riskNotices() != null) {
            for (RiskNotice notice : analysisReport.riskNotices()) {
                if (notice == null || !hasText(notice.message())) {
                    continue;
                }
                memories.add(memory(
                        artifactId,
                        groupId,
                        datasetId,
                        "RISK_NOTICE",
                        groupId != null ? "WORKSPACE_LOCAL" : "DATASET_LOCAL",
                        notice.message(),
                        clip(notice.message(), 360),
                        0.75,
                        0.80));
            }
        }

        return memories;
    }

    private ArtifactMemory memory(
            Long artifactId,
            Long groupId,
            Long datasetId,
            String memoryType,
            String scope,
            String content,
            String summary,
            double importance,
            double confidence) {
        ArtifactMemory memory = new ArtifactMemory();
        memory.setArtifactId(artifactId);
        memory.setGroupId(groupId);
        memory.setDatasetId(datasetId);
        memory.setMemoryType(memoryType);
        memory.setScope(scope);
        memory.setContent(content);
        memory.setSummary(summary);
        memory.setImportance(importance);
        memory.setConfidence(confidence);
        memory.setStatus("ACTIVE");
        memory.setUseCount(0L);
        return memory;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String clip(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, Math.max(0, maxChars - 3)).trim() + "...";
    }
}
