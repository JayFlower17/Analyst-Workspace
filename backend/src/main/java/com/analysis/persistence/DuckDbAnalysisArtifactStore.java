package com.analysis.persistence;

import java.sql.SQLException;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import com.analysis.model.entity.AnalysisArtifact;
import com.analysis.model.entity.ArtifactMemory;
import com.analysis.model.entity.ContextTrace;
import com.analysis.repository.DuckDBRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.persistence",
        name = "artifact-store",
        havingValue = "duckdb",
        matchIfMissing = true)
public class DuckDbAnalysisArtifactStore implements AnalysisArtifactStore {

    private final DuckDBRepository duckDBRepository;

    @Override
    public Long saveAnalysisArtifact(AnalysisArtifact artifact) throws SQLException {
        return duckDBRepository.saveAnalysisArtifact(artifact);
    }

    @Override
    public boolean updateAnalysisArtifactStatus(Long id, String status) throws SQLException {
        return duckDBRepository.updateAnalysisArtifactStatus(id, status);
    }

    @Override
    public AnalysisArtifact findAnalysisArtifactById(Long id) throws SQLException {
        return duckDBRepository.findAnalysisArtifactById(id);
    }

    @Override
    public List<AnalysisArtifact> findRecentArtifactsBySessionId(Long sessionId, int limit, String status)
            throws SQLException {
        return duckDBRepository.findRecentArtifactsBySessionId(sessionId, limit, status);
    }

    @Override
    public List<AnalysisArtifact> findRecentArtifactsByGroupId(Long groupId, int limit, String status)
            throws SQLException {
        return duckDBRepository.findRecentArtifactsByGroupId(groupId, limit, status);
    }

    @Override
    public Long saveArtifactMemory(ArtifactMemory memory) throws SQLException {
        return duckDBRepository.saveArtifactMemory(memory);
    }

    @Override
    public ArtifactMemory findArtifactMemoryById(Long id) throws SQLException {
        return duckDBRepository.findArtifactMemoryById(id);
    }

    @Override
    public List<ArtifactMemory> findArtifactMemoriesByArtifactId(Long artifactId) throws SQLException {
        return duckDBRepository.findArtifactMemoriesByArtifactId(artifactId);
    }

    @Override
    public List<ArtifactMemory> findActiveArtifactMemoriesByGroupId(Long groupId, int limit) throws SQLException {
        return duckDBRepository.findActiveArtifactMemoriesByGroupId(groupId, limit);
    }

    @Override
    public List<ArtifactMemory> findRecentArtifactMemoriesByGroupId(Long groupId, int limit, String status)
            throws SQLException {
        return duckDBRepository.findRecentArtifactMemoriesByGroupId(groupId, limit, status);
    }

    @Override
    public int recordArtifactMemoryUsage(List<Long> memoryIds) throws SQLException {
        return duckDBRepository.recordArtifactMemoryUsage(memoryIds);
    }

    @Override
    public boolean updateArtifactMemoryStatus(Long id, String status) throws SQLException {
        return duckDBRepository.updateArtifactMemoryStatus(id, status);
    }

    @Override
    public boolean updateArtifactMemoryImportance(Long id, Double importance) throws SQLException {
        return duckDBRepository.updateArtifactMemoryImportance(id, importance);
    }

    @Override
    public Long saveContextTrace(ContextTrace trace) throws SQLException {
        return duckDBRepository.saveContextTrace(trace);
    }

    @Override
    public ContextTrace findContextTraceById(Long id) throws SQLException {
        return duckDBRepository.findContextTraceById(id);
    }

    @Override
    public List<ContextTrace> findRecentContextTracesByGroupId(Long groupId, int limit) throws SQLException {
        return duckDBRepository.findRecentContextTracesByGroupId(groupId, limit);
    }
}
