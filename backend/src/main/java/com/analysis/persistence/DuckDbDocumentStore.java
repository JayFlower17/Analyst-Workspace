package com.analysis.persistence;

import java.sql.SQLException;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import com.analysis.model.entity.DocumentAsset;
import com.analysis.model.entity.DocumentChunk;
import com.analysis.repository.DuckDBRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.persistence",
        name = "document-store",
        havingValue = "duckdb",
        matchIfMissing = true)
public class DuckDbDocumentStore implements DocumentStore {

    private final DuckDBRepository duckDBRepository;

    @Override
    public Long saveDocumentAsset(DocumentAsset asset) throws SQLException {
        return duckDBRepository.saveDocumentAsset(asset);
    }

    @Override
    public boolean updateDocumentAssetProcessing(Long documentId, String processingStatus, String processingError,
            Integer chunkCount) throws SQLException {
        return duckDBRepository.updateDocumentAssetProcessing(documentId, processingStatus, processingError, chunkCount);
    }

    @Override
    public Long saveDocumentChunk(DocumentChunk chunk) throws SQLException {
        return duckDBRepository.saveDocumentChunk(chunk);
    }

    @Override
    public DocumentAsset findDocumentAssetById(Long documentId) throws SQLException {
        return duckDBRepository.findDocumentAssetById(documentId);
    }

    @Override
    public List<DocumentAsset> findDocumentAssetsByGroupId(Long groupId) throws SQLException {
        return duckDBRepository.findDocumentAssetsByGroupId(groupId);
    }

    @Override
    public List<DocumentChunk> findDocumentChunksByDocumentId(Long documentId) throws SQLException {
        return duckDBRepository.findDocumentChunksByDocumentId(documentId);
    }

    @Override
    public void deleteDocumentChunksByDocumentId(Long documentId) throws SQLException {
        duckDBRepository.deleteDocumentChunksByDocumentId(documentId);
    }

    @Override
    public void deleteDocumentAssetById(Long documentId) throws SQLException {
        duckDBRepository.deleteDocumentAssetById(documentId);
    }
}
