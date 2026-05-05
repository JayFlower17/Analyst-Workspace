package com.analysis.service;

import java.sql.SQLException;
import java.util.List;

import org.springframework.stereotype.Service;

import com.analysis.model.entity.ArtifactMemory;
import com.analysis.persistence.AnalysisArtifactStore;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ArtifactMemoryService {

    private final AnalysisArtifactStore analysisArtifactStore;

    public ArtifactMemory getArtifactMemory(Long id) throws SQLException {
        ArtifactMemory memory = analysisArtifactStore.findArtifactMemoryById(id);
        if (memory == null) {
            throw new IllegalArgumentException("Artifact memory not found: " + id);
        }
        return memory;
    }

    public List<ArtifactMemory> getRecentMemories(Long groupId, int limit, String status) throws SQLException {
        if (groupId == null) {
            throw new IllegalArgumentException("请提供 groupId");
        }
        int safeLimit = Math.max(1, Math.min(limit, 50));
        return analysisArtifactStore.findRecentArtifactMemoriesByGroupId(
                groupId,
                safeLimit,
                normalizeStatus(status));
    }

    public ArtifactMemory archiveMemory(Long id) throws SQLException {
        return updateMemoryStatus(id, "ARCHIVED");
    }

    public ArtifactMemory restoreMemory(Long id) throws SQLException {
        return updateMemoryStatus(id, "ACTIVE");
    }

    public ArtifactMemory supersedeMemory(Long id) throws SQLException {
        return updateMemoryStatus(id, "SUPERSEDED");
    }

    public ArtifactMemory deleteMemory(Long id) throws SQLException {
        return updateMemoryStatus(id, "DELETED");
    }

    public ArtifactMemory updateImportance(Long id, Double importance) throws SQLException {
        if (importance == null || importance < 0.0 || importance > 1.0) {
            throw new IllegalArgumentException("importance must be between 0.0 and 1.0");
        }
        boolean updated = analysisArtifactStore.updateArtifactMemoryImportance(id, importance);
        if (!updated) {
            throw new IllegalArgumentException("Artifact memory not found: " + id);
        }
        return getArtifactMemory(id);
    }

    private ArtifactMemory updateMemoryStatus(Long id, String status) throws SQLException {
        boolean updated = analysisArtifactStore.updateArtifactMemoryStatus(id, normalizeStatus(status));
        if (!updated) {
            throw new IllegalArgumentException("Artifact memory not found: " + id);
        }
        return getArtifactMemory(id);
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "ACTIVE";
        }
        String normalizedStatus = status.trim().toUpperCase();
        return switch (normalizedStatus) {
            case "ACTIVE", "ARCHIVED", "SUPERSEDED", "DELETED" -> normalizedStatus;
            default -> throw new IllegalArgumentException("Unsupported artifact memory status: " + status);
        };
    }
}
