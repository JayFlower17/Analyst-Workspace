package com.analysis.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.analysis.model.entity.ArtifactMemory;
import com.analysis.persistence.AnalysisArtifactStore;

class ArtifactMemoryServiceTest {

    @Test
    void getsRecentMemoriesWithNormalizedStatusAndSafeLimit() throws Exception {
        AnalysisArtifactStore repository = mock(AnalysisArtifactStore.class);
        ArtifactMemoryService service = new ArtifactMemoryService(repository);
        ArtifactMemory memory = memory(3L, "ACTIVE", 0.7);
        when(repository.findRecentArtifactMemoriesByGroupId(8L, 50, "ACTIVE")).thenReturn(List.of(memory));

        List<ArtifactMemory> memories = service.getRecentMemories(8L, 99, "active");

        assertEquals(1, memories.size());
        assertEquals(3L, memories.get(0).getId());
        verify(repository).findRecentArtifactMemoriesByGroupId(8L, 50, "ACTIVE");
    }

    @Test
    void updatesMemoryLifecycleAndImportance() throws Exception {
        AnalysisArtifactStore repository = mock(AnalysisArtifactStore.class);
        ArtifactMemoryService service = new ArtifactMemoryService(repository);

        ArtifactMemory archived = memory(4L, "ARCHIVED", 0.7);
        when(repository.updateArtifactMemoryStatus(4L, "ARCHIVED")).thenReturn(true);
        when(repository.findArtifactMemoryById(4L)).thenReturn(archived);

        assertEquals("ARCHIVED", service.archiveMemory(4L).getStatus());
        verify(repository).updateArtifactMemoryStatus(4L, "ARCHIVED");

        ArtifactMemory updatedImportance = memory(4L, "ARCHIVED", 0.9);
        when(repository.updateArtifactMemoryImportance(4L, 0.9)).thenReturn(true);
        when(repository.findArtifactMemoryById(4L)).thenReturn(updatedImportance);

        assertEquals(0.9, service.updateImportance(4L, 0.9).getImportance());
        verify(repository).updateArtifactMemoryImportance(4L, 0.9);
    }

    @Test
    void rejectsInvalidStatusAndImportance() {
        AnalysisArtifactStore repository = mock(AnalysisArtifactStore.class);
        ArtifactMemoryService service = new ArtifactMemoryService(repository);

        assertThrows(IllegalArgumentException.class, () -> service.getRecentMemories(1L, 10, "STALE"));
        assertThrows(IllegalArgumentException.class, () -> service.updateImportance(1L, 1.5));
    }

    private ArtifactMemory memory(Long id, String status, Double importance) {
        ArtifactMemory memory = new ArtifactMemory();
        memory.setId(id);
        memory.setGroupId(8L);
        memory.setStatus(status);
        memory.setImportance(importance);
        return memory;
    }
}
