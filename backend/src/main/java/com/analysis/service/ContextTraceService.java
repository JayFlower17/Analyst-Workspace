package com.analysis.service;

import java.sql.SQLException;
import java.util.List;

import org.springframework.stereotype.Service;

import com.analysis.model.entity.ContextTrace;
import com.analysis.persistence.AnalysisArtifactStore;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ContextTraceService {

    private final AnalysisArtifactStore analysisArtifactStore;

    public ContextTrace getContextTrace(Long id) throws SQLException {
        ContextTrace trace = analysisArtifactStore.findContextTraceById(id);
        if (trace == null) {
            throw new IllegalArgumentException("Context trace not found: " + id);
        }
        return trace;
    }

    public List<ContextTrace> getRecentContextTraces(Long groupId, int limit) throws SQLException {
        if (groupId == null) {
            throw new IllegalArgumentException("请提供 groupId");
        }
        int safeLimit = Math.max(1, Math.min(limit, 20));
        return analysisArtifactStore.findRecentContextTracesByGroupId(groupId, safeLimit);
    }
}
