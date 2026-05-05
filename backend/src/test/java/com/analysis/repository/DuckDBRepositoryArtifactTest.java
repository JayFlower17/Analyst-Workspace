package com.analysis.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.analysis.model.entity.AnalysisArtifact;
import com.analysis.model.entity.ArtifactMemory;
import com.analysis.model.entity.ContextTrace;

class DuckDBRepositoryArtifactTest {

    @TempDir
    Path tempDir;

    @Test
    void savesAndReadsPhase6AnalysisReportFields() throws Exception {
        DriverManagerDataSource dataSource = dataSource();
        DuckDBRepository repository = new DuckDBRepository(dataSource);
        repository.initSchema();

        AnalysisArtifact artifact = new AnalysisArtifact();
        artifact.setMode("workplace");
        artifact.setGroupId(1L);
        artifact.setDatasetId(2L);
        artifact.setContextTraceId(3L);
        artifact.setUserQuery("Show orders");
        artifact.setGeneratedCodeOrSql("SELECT * FROM orders");
        artifact.setSummary("Orders summary");
        artifact.setChartType("BAR");
        artifact.setResultPreviewJson("[{\"channel\":\"Ads\"}]");
        artifact.setArtifactSchemaVersion(2);
        artifact.setAnalysisReportJson("{\"summary\":\"Orders summary\",\"data\":[]}");
        artifact.setEvidenceSummaryJson("{\"datasetCount\":2,\"documentChunkCount\":0}");
        artifact.setExecutionLogsJson("[{\"toolType\":\"SQL_EXECUTION\",\"success\":true}]");
        artifact.setValidationReportJson("{\"passed\":true,\"findings\":[]}");
        artifact.setRiskNoticesJson("[]");

        Long id = repository.saveAnalysisArtifact(artifact);
        AnalysisArtifact saved = repository.findAnalysisArtifactById(id);

        assertNotNull(id);
        assertNotNull(saved);
        assertEquals(2, saved.getArtifactSchemaVersion());
        assertEquals(3L, saved.getContextTraceId());
        assertEquals("{\"summary\":\"Orders summary\",\"data\":[]}", saved.getAnalysisReportJson());
        assertEquals("{\"datasetCount\":2,\"documentChunkCount\":0}", saved.getEvidenceSummaryJson());
        assertEquals("[{\"toolType\":\"SQL_EXECUTION\",\"success\":true}]", saved.getExecutionLogsJson());
        assertEquals("{\"passed\":true,\"findings\":[]}", saved.getValidationReportJson());
        assertEquals("[]", saved.getRiskNoticesJson());
        assertEquals("[{\"channel\":\"Ads\"}]", saved.getResultPreviewJson());
        assertEquals("ACTIVE", saved.getArtifactStatus());
    }

    @Test
    void migratesLegacyArtifactTableWithEvidenceSummaryColumn() throws Exception {
        DriverManagerDataSource dataSource = dataSource();
        try (Connection connection = dataSource.getConnection();
                Statement stmt = connection.createStatement()) {
            stmt.execute("""
                    CREATE TABLE analysis_artifacts (
                        id INTEGER PRIMARY KEY,
                        mode VARCHAR,
                        session_id INTEGER,
                        group_id INTEGER,
                        dataset_id INTEGER,
                        user_query VARCHAR,
                        generated_code_or_sql VARCHAR,
                        summary VARCHAR,
                        chart_type VARCHAR,
                        result_preview_json VARCHAR,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
        }

        DuckDBRepository repository = new DuckDBRepository(dataSource);
        repository.initSchema();

        AnalysisArtifact artifact = new AnalysisArtifact();
        artifact.setMode("workplace");
        artifact.setSummary("Migrated summary");
        artifact.setResultPreviewJson("[]");
        artifact.setArtifactSchemaVersion(2);
        artifact.setAnalysisReportJson("{\"summary\":\"Migrated summary\",\"data\":[]}");
        artifact.setEvidenceSummaryJson("{\"datasetCount\":1}");
        artifact.setExecutionLogsJson("[]");
        artifact.setValidationReportJson("{\"passed\":true,\"findings\":[]}");
        artifact.setRiskNoticesJson("[]");

        Long id = repository.saveAnalysisArtifact(artifact);
        AnalysisArtifact saved = repository.findAnalysisArtifactById(id);

        assertNotNull(saved);
        assertEquals("{\"datasetCount\":1}", saved.getEvidenceSummaryJson());
        assertEquals("[]", saved.getExecutionLogsJson());
        assertEquals("{\"passed\":true,\"findings\":[]}", saved.getValidationReportJson());
        assertEquals("[]", saved.getRiskNoticesJson());
        assertEquals("ACTIVE", saved.getArtifactStatus());
    }

    @Test
    void managesArtifactArchiveRestoreAndSoftDeleteStatus() throws Exception {
        DriverManagerDataSource dataSource = dataSource();
        DuckDBRepository repository = new DuckDBRepository(dataSource);
        repository.initSchema();

        AnalysisArtifact artifact = new AnalysisArtifact();
        artifact.setMode("workplace");
        artifact.setGroupId(3L);
        artifact.setSummary("Managed summary");
        artifact.setResultPreviewJson("[]");
        artifact.setArtifactSchemaVersion(2);
        artifact.setAnalysisReportJson("{\"summary\":\"Managed summary\",\"data\":[]}");

        Long id = repository.saveAnalysisArtifact(artifact);
        assertEquals(1, repository.findRecentArtifactsByGroupId(3L, 10, "ACTIVE").size());

        repository.updateAnalysisArtifactStatus(id, "ARCHIVED");
        AnalysisArtifact archived = repository.findAnalysisArtifactById(id);
        assertEquals("ARCHIVED", archived.getArtifactStatus());
        assertNotNull(archived.getArchivedAt());
        assertEquals(0, repository.findRecentArtifactsByGroupId(3L, 10, "ACTIVE").size());
        assertEquals(1, repository.findRecentArtifactsByGroupId(3L, 10, "ARCHIVED").size());

        repository.updateAnalysisArtifactStatus(id, "ACTIVE");
        AnalysisArtifact restored = repository.findAnalysisArtifactById(id);
        assertEquals("ACTIVE", restored.getArtifactStatus());
        assertEquals(1, repository.findRecentArtifactsByGroupId(3L, 10, "ACTIVE").size());

        repository.updateAnalysisArtifactStatus(id, "DELETED");
        AnalysisArtifact deleted = repository.findAnalysisArtifactById(id);
        assertEquals("DELETED", deleted.getArtifactStatus());
        assertNotNull(deleted.getDeletedAt());
        assertEquals(0, repository.findRecentArtifactsByGroupId(3L, 10, "ACTIVE").size());
        assertEquals(1, repository.findRecentArtifactsByGroupId(3L, 10, "DELETED").size());
        assertFalse(repository.updateAnalysisArtifactStatus(9999L, "ARCHIVED"));
    }

    @Test
    void savesAndReadsArtifactMemories() throws Exception {
        DriverManagerDataSource dataSource = dataSource();
        DuckDBRepository repository = new DuckDBRepository(dataSource);
        repository.initSchema();

        AnalysisArtifact artifact = new AnalysisArtifact();
        artifact.setMode("workplace");
        artifact.setGroupId(5L);
        artifact.setDatasetId(6L);
        artifact.setSummary("Memory source");
        artifact.setResultPreviewJson("[]");
        artifact.setArtifactSchemaVersion(2);

        Long artifactId = repository.saveAnalysisArtifact(artifact);

        ArtifactMemory memory = new ArtifactMemory();
        memory.setArtifactId(artifactId);
        memory.setGroupId(5L);
        memory.setDatasetId(6L);
        memory.setMemoryType("ANALYSIS_FINDING");
        memory.setScope("WORKSPACE_LOCAL");
        memory.setContent("A durable analysis finding");
        memory.setSummary("Durable finding");
        memory.setImportance(0.7);
        memory.setConfidence(0.8);
        memory.setStatus("ACTIVE");

        Long memoryId = repository.saveArtifactMemory(memory);
        var memories = repository.findArtifactMemoriesByArtifactId(artifactId);

        assertNotNull(memoryId);
        assertEquals(1, memories.size());
        assertEquals("ANALYSIS_FINDING", memories.get(0).getMemoryType());
        assertEquals("WORKSPACE_LOCAL", memories.get(0).getScope());
        assertEquals("Durable finding", memories.get(0).getSummary());
        assertEquals(0.7, memories.get(0).getImportance());
        assertEquals(0L, memories.get(0).getUseCount());

        assertEquals(1, repository.recordArtifactMemoryUsage(java.util.List.of(memoryId)));
        var usedMemories = repository.findArtifactMemoriesByArtifactId(artifactId);
        assertEquals(1L, usedMemories.get(0).getUseCount());
        assertNotNull(usedMemories.get(0).getLastUsedAt());

        assertEquals(memoryId, repository.findArtifactMemoryById(memoryId).getId());
        assertEquals(1, repository.findRecentArtifactMemoriesByGroupId(5L, 10, "ACTIVE").size());

        assertEquals(true, repository.updateArtifactMemoryImportance(memoryId, 0.9));
        assertEquals(0.9, repository.findArtifactMemoryById(memoryId).getImportance());

        assertEquals(true, repository.updateArtifactMemoryStatus(memoryId, "ARCHIVED"));
        assertEquals("ARCHIVED", repository.findArtifactMemoryById(memoryId).getStatus());
        assertEquals(0, repository.findArtifactMemoriesByArtifactId(artifactId).size());
        assertEquals(1, repository.findRecentArtifactMemoriesByGroupId(5L, 10, "ARCHIVED").size());
    }

    @Test
    void savesAndReadsContextTrace() throws Exception {
        DriverManagerDataSource dataSource = dataSource();
        DuckDBRepository repository = new DuckDBRepository(dataSource);
        repository.initSchema();

        ContextTrace trace = new ContextTrace();
        trace.setGroupId(12L);
        trace.setQuery("Explain revenue using memory");
        trace.setSelectedSchemaIdsJson("[1,2]");
        trace.setSelectedDocumentChunkIdsJson("[\"10:0-1\"]");
        trace.setSelectedMemoryIdsJson("[7,8]");
        trace.setFilteredItemsJson("{\"memoryCount\":2}");
        trace.setPackedContext("[Schema]\norders\n\n[Memories]\nprior revenue finding");

        Long traceId = repository.saveContextTrace(trace);
        ContextTrace saved = repository.findContextTraceById(traceId);

        assertNotNull(traceId);
        assertNotNull(saved);
        assertEquals(12L, saved.getGroupId());
        assertEquals("Explain revenue using memory", saved.getQuery());
        assertEquals("[1,2]", saved.getSelectedSchemaIdsJson());
        assertEquals("[\"10:0-1\"]", saved.getSelectedDocumentChunkIdsJson());
        assertEquals("[7,8]", saved.getSelectedMemoryIdsJson());
        assertEquals("{\"memoryCount\":2}", saved.getFilteredItemsJson());
        assertEquals("[Schema]\norders\n\n[Memories]\nprior revenue finding", saved.getPackedContext());
    }

    private DriverManagerDataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.duckdb.DuckDBDriver");
        dataSource.setUrl("jdbc:duckdb:" + tempDir.resolve("artifact-test.duckdb"));
        return dataSource;
    }
}
