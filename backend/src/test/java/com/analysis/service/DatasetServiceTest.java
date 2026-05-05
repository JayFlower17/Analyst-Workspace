package com.analysis.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;

import com.analysis.model.entity.Dataset;
import com.analysis.persistence.WorkspaceCatalogStore;
import com.analysis.repository.DuckDBRepository;

class DatasetServiceTest {

    @Test
    void deleteDatasetDropsDuckDbTableAndDeletesCatalogMetadata() throws SQLException {
        DuckDBRepository duckDBRepository = mock(DuckDBRepository.class);
        WorkspaceCatalogStore catalogStore = mock(WorkspaceCatalogStore.class);
        MetadataService metadataService = mock(MetadataService.class);
        DatasetService service = new DatasetService(duckDBRepository, catalogStore, metadataService);
        Dataset dataset = new Dataset();
        dataset.setId(42L);
        dataset.setTableName("dataset_sales_42");
        when(catalogStore.findDatasetById(42L)).thenReturn(dataset);

        service.deleteDataset(42L);

        verify(duckDBRepository).dropAnalysisTable("dataset_sales_42");
        verify(catalogStore).deleteDataset(42L);
    }

    @Test
    void deleteDatasetNoOpsWhenCatalogMetadataIsMissing() throws SQLException {
        DuckDBRepository duckDBRepository = mock(DuckDBRepository.class);
        WorkspaceCatalogStore catalogStore = mock(WorkspaceCatalogStore.class);
        MetadataService metadataService = mock(MetadataService.class);
        DatasetService service = new DatasetService(duckDBRepository, catalogStore, metadataService);
        when(catalogStore.findDatasetById(42L)).thenReturn(null);

        service.deleteDataset(42L);

        verify(duckDBRepository, never()).dropAnalysisTable(org.mockito.ArgumentMatchers.anyString());
        verify(catalogStore, never()).deleteDataset(42L);
    }
}
