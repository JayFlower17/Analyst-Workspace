package com.analysis.persistence;

import java.sql.SQLException;
import java.util.List;

import com.analysis.model.entity.AnalysisArtifact;
import com.analysis.model.entity.ArtifactMemory;
import com.analysis.model.entity.ContextTrace;

public interface AnalysisArtifactStore {

    Long saveAnalysisArtifact(AnalysisArtifact artifact) throws SQLException;

    boolean updateAnalysisArtifactStatus(Long id, String status) throws SQLException;

    AnalysisArtifact findAnalysisArtifactById(Long id) throws SQLException;

    List<AnalysisArtifact> findRecentArtifactsBySessionId(Long sessionId, int limit, String status) throws SQLException;

    List<AnalysisArtifact> findRecentArtifactsByGroupId(Long groupId, int limit, String status) throws SQLException;

    Long saveArtifactMemory(ArtifactMemory memory) throws SQLException;

    ArtifactMemory findArtifactMemoryById(Long id) throws SQLException;

    List<ArtifactMemory> findArtifactMemoriesByArtifactId(Long artifactId) throws SQLException;

    List<ArtifactMemory> findActiveArtifactMemoriesByGroupId(Long groupId, int limit) throws SQLException;

    List<ArtifactMemory> findRecentArtifactMemoriesByGroupId(Long groupId, int limit, String status) throws SQLException;

    int recordArtifactMemoryUsage(List<Long> memoryIds) throws SQLException;

    boolean updateArtifactMemoryStatus(Long id, String status) throws SQLException;

    boolean updateArtifactMemoryImportance(Long id, Double importance) throws SQLException;

    Long saveContextTrace(ContextTrace trace) throws SQLException;

    ContextTrace findContextTraceById(Long id) throws SQLException;

    List<ContextTrace> findRecentContextTracesByGroupId(Long groupId, int limit) throws SQLException;
}
