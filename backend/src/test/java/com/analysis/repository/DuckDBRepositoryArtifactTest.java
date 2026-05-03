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

    private DriverManagerDataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.duckdb.DuckDBDriver");
        dataSource.setUrl("jdbc:duckdb:" + tempDir.resolve("artifact-test.duckdb"));
        return dataSource;
    }
}
