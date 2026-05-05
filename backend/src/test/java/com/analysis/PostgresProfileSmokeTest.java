package com.analysis;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.analysis.persistence.AnalysisArtifactStore;
import com.analysis.persistence.DocumentStore;
import com.analysis.persistence.PostgresAnalysisArtifactStore;
import com.analysis.persistence.PostgresDocumentStore;
import com.analysis.persistence.PostgresWorkspaceCatalogStore;
import com.analysis.persistence.WorkspaceCatalogStore;
import com.analysis.model.entity.DatasetGroup;
import com.analysis.model.entity.DocumentAsset;
import com.analysis.model.entity.DocumentChunk;

@ActiveProfiles("postgres")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5433/analyst_workspace",
        "spring.datasource.username=analyst",
        "spring.datasource.password=analyst",
        "app.vector-store.enabled=false",
        "duckdb.path=./target/postgres-smoke.duckdb",
        "upload.path=./target/uploads-postgres-smoke"
})
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_SMOKE", matches = "true")
class PostgresProfileSmokeTest {

    @Autowired
    private WorkspaceCatalogStore workspaceCatalogStore;

    @Autowired
    private AnalysisArtifactStore analysisArtifactStore;

    @Autowired
    private DocumentStore documentStore;

    @Test
    void postgresProfileLoadsPostgresPersistenceStores() {
        assertInstanceOf(PostgresWorkspaceCatalogStore.class, workspaceCatalogStore);
        assertInstanceOf(PostgresAnalysisArtifactStore.class, analysisArtifactStore);
        assertInstanceOf(PostgresDocumentStore.class, documentStore);
    }

    @Test
    void postgresStoresCanRoundTripWorkspaceAndDocumentMetadata() throws Exception {
        DatasetGroup group = new DatasetGroup();
        group.setName("smoke-workspace-" + System.nanoTime());
        group.setDescription("Postgres smoke workspace");

        Long groupId = workspaceCatalogStore.saveDatasetGroup(group);
        Long documentId = null;
        try {
            DocumentAsset asset = new DocumentAsset();
            asset.setGroupId(groupId);
            asset.setName("smoke-note");
            asset.setOriginalFileName("smoke-note.txt");
            asset.setStoredPath("./target/uploads-postgres-smoke/smoke-note.txt");
            asset.setFileType("TXT");
            asset.setMimeType("text/plain");
            asset.setSizeBytes(42L);
            asset.setProcessingStatus("UPLOADED");
            asset.setChunkCount(0);

            documentId = documentStore.saveDocumentAsset(asset);
            assertNotNull(documentId);

            DocumentChunk chunk = new DocumentChunk();
            chunk.setDocumentId(documentId);
            chunk.setChunkIndex(0);
            chunk.setChunkText("Postgres smoke document chunk for pgvector memory validation.");
            chunk.setMetadataJson("{\"source\":\"smoke\"}");
            Long chunkId = documentStore.saveDocumentChunk(chunk);
            assertNotNull(chunkId);

            assertTrue(documentStore.updateDocumentAssetProcessing(documentId, "PARSED", null, 1));

            DocumentAsset persisted = documentStore.findDocumentAssetById(documentId);
            assertNotNull(persisted);
            assertEquals(groupId, persisted.getGroupId());
            assertEquals("PARSED", persisted.getProcessingStatus());
            assertEquals(1, persisted.getChunkCount());

            List<DocumentAsset> groupDocuments = documentStore.findDocumentAssetsByGroupId(groupId);
            assertEquals(1, groupDocuments.size());
            assertEquals(documentId, groupDocuments.get(0).getId());

            List<DocumentChunk> chunks = documentStore.findDocumentChunksByDocumentId(documentId);
            assertEquals(1, chunks.size());
            assertEquals("Postgres smoke document chunk for pgvector memory validation.", chunks.get(0).getChunkText());
        } finally {
            if (documentId != null) {
                documentStore.deleteDocumentAssetById(documentId);
            }
            if (groupId != null) {
                workspaceCatalogStore.deleteDatasetGroup(groupId);
            }
        }
    }
}
