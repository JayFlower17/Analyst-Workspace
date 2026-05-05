package com.analysis.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.analysis.model.dto.ArtifactDetailResponse;
import com.analysis.model.entity.AnalysisArtifact;
import com.analysis.model.entity.ArtifactMemory;
import com.analysis.model.entity.ContextTrace;
import com.analysis.model.enums.ChartType;
import com.analysis.model.execution.ToolExecutionLog;
import com.analysis.model.execution.ToolExecutionType;
import com.analysis.model.report.AnalysisEvidenceSummary;
import com.analysis.model.report.AnalysisReport;
import com.analysis.model.validation.AnalysisValidationReport;
import com.analysis.persistence.AnalysisArtifactStore;
import com.fasterxml.jackson.databind.ObjectMapper;

class ArtifactServiceTest {

    @Test
    void getArtifactDetailParsesPersistedAnalysisReport() throws Exception {
        AnalysisArtifactStore repository = mock(AnalysisArtifactStore.class);
        ObjectMapper objectMapper = new ObjectMapper();
        ArtifactService service = new ArtifactService(repository, objectMapper);

        AnalysisEvidenceSummary persistedEvidence = new AnalysisEvidenceSummary(
                1L,
                2,
                0,
                List.of("orders", "traffic"),
                "SKIP",
                0,
                List.of(),
                true);
        AnalysisReport persistedReport = new AnalysisReport(
                "Orders summary",
                List.of(),
                4,
                ChartType.BAR,
                true,
                "SELECT * FROM orders",
                persistedEvidence,
                AnalysisValidationReport.ok(),
                List.of(),
                List.of(new ToolExecutionLog(
                        ToolExecutionType.SQL_EXECUTION,
                        "SqlExecutor",
                        "EXECUTE_SQL",
                        true,
                        "ok",
                        12,
                        Map.of("sqlPresent", true),
                        Map.of("rowCount", 4))));

        AnalysisArtifact artifact = new AnalysisArtifact();
        artifact.setId(7L);
        artifact.setContextTraceId(21L);
        artifact.setSummary("Orders summary");
        artifact.setResultPreviewJson("[{\"channel\":\"Ads\",\"revenue\":100}]");
        artifact.setArtifactSchemaVersion(2);
        artifact.setAnalysisReportJson(objectMapper.writeValueAsString(persistedReport));
        artifact.setEvidenceSummaryJson(objectMapper.writeValueAsString(persistedEvidence));
        artifact.setExecutionLogsJson(objectMapper.writeValueAsString(persistedReport.executionLogs()));
        artifact.setValidationReportJson(objectMapper.writeValueAsString(persistedReport.validationReport()));
        artifact.setRiskNoticesJson(objectMapper.writeValueAsString(persistedReport.riskNotices()));
        ArtifactMemory memory = new ArtifactMemory();
        memory.setId(11L);
        memory.setArtifactId(7L);
        memory.setMemoryType("ANALYSIS_FINDING");
        memory.setSummary("Orders summary");
        when(repository.findAnalysisArtifactById(7L)).thenReturn(artifact);
        when(repository.findArtifactMemoriesByArtifactId(7L)).thenReturn(List.of(memory));
        ContextTrace trace = new ContextTrace();
        trace.setId(21L);
        trace.setGroupId(1L);
        trace.setQuery("Show orders");
        trace.setSelectedMemoryIdsJson("[11]");
        when(repository.findContextTraceById(21L)).thenReturn(trace);

        ArtifactDetailResponse detail = service.getArtifactDetail(7L);

        assertTrue(detail.isReportAvailable());
        assertNotNull(detail.getAnalysisReport());
        assertEquals("Orders summary", detail.getAnalysisReport().summary());
        assertEquals(4, detail.getAnalysisReport().rowCount());
        assertEquals(ChartType.BAR, detail.getAnalysisReport().recommendedChart());
        assertEquals(1, detail.getAnalysisReport().executionLogs().size());
        assertEquals(ToolExecutionType.SQL_EXECUTION, detail.getAnalysisReport().executionLogs().get(0).toolType());
        assertNotNull(detail.getEvidence());
        assertEquals(2, detail.getEvidence().datasetCount());
        assertEquals(List.of("orders", "traffic"), detail.getEvidence().datasetNames());
        assertEquals(1, detail.getExecutionLogs().size());
        assertNotNull(detail.getValidationReport());
        assertTrue(detail.getValidationReport().passed());
        assertEquals(List.of(), detail.getRiskNotices());
        assertEquals(1, detail.getMemories().size());
        assertEquals("ANALYSIS_FINDING", detail.getMemories().get(0).getMemoryType());
        assertEquals(21L, detail.getContextTraceId());
        assertNotNull(detail.getContextTrace());
        assertEquals("[11]", detail.getContextTrace().getSelectedMemoryIdsJson());
        assertEquals(1, detail.getResultPreview().size());
        assertEquals("Ads", detail.getResultPreview().get(0).get("channel"));
    }

    @Test
    void getArtifactDetailKeepsLegacyArtifactReadable() throws Exception {
        AnalysisArtifactStore repository = mock(AnalysisArtifactStore.class);
        ArtifactService service = new ArtifactService(repository, new ObjectMapper());

        AnalysisArtifact artifact = new AnalysisArtifact();
        artifact.setId(8L);
        artifact.setSummary("Legacy summary");
        artifact.setResultPreviewJson(null);
        artifact.setArtifactSchemaVersion(null);
        artifact.setAnalysisReportJson(null);
        artifact.setEvidenceSummaryJson(null);
        artifact.setExecutionLogsJson(null);
        artifact.setValidationReportJson(null);
        artifact.setRiskNoticesJson(null);
        when(repository.findAnalysisArtifactById(8L)).thenReturn(artifact);

        ArtifactDetailResponse detail = service.getArtifactDetail(8L);

        assertFalse(detail.isReportAvailable());
        assertEquals("Legacy summary", detail.getSummary());
        assertEquals(List.of(), detail.getResultPreview());
        assertNull(detail.getAnalysisReport());
        assertNull(detail.getEvidence());
        assertEquals(List.of(), detail.getExecutionLogs());
        assertNull(detail.getValidationReport());
        assertEquals(List.of(), detail.getRiskNotices());
    }

    @Test
    void archiveRestoreAndDeleteArtifactReturnUpdatedDetail() throws Exception {
        AnalysisArtifactStore repository = mock(AnalysisArtifactStore.class);
        ArtifactService service = new ArtifactService(repository, new ObjectMapper());

        AnalysisArtifact archived = new AnalysisArtifact();
        archived.setId(9L);
        archived.setSummary("Archived summary");
        archived.setArtifactStatus("ARCHIVED");
        when(repository.updateAnalysisArtifactStatus(9L, "ARCHIVED")).thenReturn(true);
        when(repository.findAnalysisArtifactById(9L)).thenReturn(archived);

        ArtifactDetailResponse archiveDetail = service.archiveArtifact(9L);

        assertEquals("ARCHIVED", archiveDetail.getArtifactStatus());
        verify(repository).updateAnalysisArtifactStatus(9L, "ARCHIVED");

        AnalysisArtifact restored = new AnalysisArtifact();
        restored.setId(9L);
        restored.setSummary("Restored summary");
        restored.setArtifactStatus("ACTIVE");
        when(repository.updateAnalysisArtifactStatus(9L, "ACTIVE")).thenReturn(true);
        when(repository.findAnalysisArtifactById(9L)).thenReturn(restored);

        ArtifactDetailResponse restoreDetail = service.restoreArtifact(9L);

        assertEquals("ACTIVE", restoreDetail.getArtifactStatus());
        verify(repository).updateAnalysisArtifactStatus(9L, "ACTIVE");

        AnalysisArtifact deleted = new AnalysisArtifact();
        deleted.setId(9L);
        deleted.setSummary("Deleted summary");
        deleted.setArtifactStatus("DELETED");
        when(repository.updateAnalysisArtifactStatus(9L, "DELETED")).thenReturn(true);
        when(repository.findAnalysisArtifactById(9L)).thenReturn(deleted);

        ArtifactDetailResponse deleteDetail = service.deleteArtifact(9L);

        assertEquals("DELETED", deleteDetail.getArtifactStatus());
        verify(repository).updateAnalysisArtifactStatus(9L, "DELETED");
    }
}
