package com.analysis.service;

import java.sql.SQLException;
import java.util.List;

import org.springframework.stereotype.Service;

import com.analysis.model.entity.AnalysisArtifact;
import com.analysis.repository.DuckDBRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ArtifactService {

    private final DuckDBRepository duckDBRepository;

    public List<AnalysisArtifact> getRecentArtifacts(Long sessionId, Long groupId, int limit) throws SQLException {
        int safeLimit = Math.max(1, Math.min(limit, 20));
        if (sessionId != null) {
            return duckDBRepository.findRecentArtifactsBySessionId(sessionId, safeLimit);
        }
        if (groupId != null) {
            return duckDBRepository.findRecentArtifactsByGroupId(groupId, safeLimit);
        }
        throw new IllegalArgumentException("请提供 sessionId 或 groupId");
    }
}
