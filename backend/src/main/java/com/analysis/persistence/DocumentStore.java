package com.analysis.persistence;

import java.sql.SQLException;
import java.util.List;

import com.analysis.model.entity.DocumentAsset;
import com.analysis.model.entity.DocumentChunk;

public interface DocumentStore {

    Long saveDocumentAsset(DocumentAsset asset) throws SQLException;

    boolean updateDocumentAssetProcessing(Long documentId, String processingStatus, String processingError,
            Integer chunkCount) throws SQLException;

    Long saveDocumentChunk(DocumentChunk chunk) throws SQLException;

    DocumentAsset findDocumentAssetById(Long documentId) throws SQLException;

    List<DocumentAsset> findDocumentAssetsByGroupId(Long groupId) throws SQLException;

    List<DocumentChunk> findDocumentChunksByDocumentId(Long documentId) throws SQLException;

    void deleteDocumentChunksByDocumentId(Long documentId) throws SQLException;

    void deleteDocumentAssetById(Long documentId) throws SQLException;
}
