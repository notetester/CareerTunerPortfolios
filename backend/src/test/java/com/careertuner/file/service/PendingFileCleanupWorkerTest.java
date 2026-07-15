package com.careertuner.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.file.domain.FileAsset;
import com.careertuner.file.mapper.FileAssetMapper;

class PendingFileCleanupWorkerTest {

    private final FileAssetMapper mapper = mock(FileAssetMapper.class);
    private final FileStorageService storage = mock(FileStorageService.class);
    private final PendingFileCleanupWorker worker = new PendingFileCleanupWorker(mapper, storage);

    @Test
    void batchMethodsAreNotTransactionalAndEachWorkerDeleteRequiresNewTransaction() throws Exception {
        assertThat(FileService.class.getMethod(
                "cleanupStalePendingCollaborationAttachments", int.class, int.class)
                .getAnnotation(Transactional.class)).isNull();
        assertThat(FileService.class.getMethod(
                "cleanupStalePendingAutoPrepAttachments", int.class, int.class)
                .getAnnotation(Transactional.class)).isNull();
        assertThat(FileService.class.getMethod(
                "cleanupStalePendingProfileImports", int.class, int.class)
                .getAnnotation(Transactional.class)).isNull();
        assertThat(FileService.class.getMethod(
                "cleanupStalePendingInterviewMedia", int.class, int.class)
                .getAnnotation(Transactional.class)).isNull();
        assertThat(FileService.class.getMethod(
                "cleanupStaleOrphanedInterviewMedia", int.class, int.class)
                .getAnnotation(Transactional.class)).isNull();

        assertThat(PendingFileCleanupWorker.class.getMethod(
                "deleteStaleCollaborationAttachment", FileAsset.class, LocalDateTime.class)
                .getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.REQUIRES_NEW);
        assertThat(PendingFileCleanupWorker.class.getMethod(
                "deleteStaleAutoPrepAttachment", FileAsset.class, LocalDateTime.class)
                .getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.REQUIRES_NEW);
        assertThat(PendingFileCleanupWorker.class.getMethod(
                "deleteStaleProfileImport", FileAsset.class, LocalDateTime.class)
                .getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.REQUIRES_NEW);
        assertThat(PendingFileCleanupWorker.class.getMethod(
                "deleteStaleInterviewMedia", FileAsset.class, LocalDateTime.class)
                .getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.REQUIRES_NEW);
        assertThat(PendingFileCleanupWorker.class.getMethod(
                "deleteStaleOrphanedInterviewMedia", FileAsset.class, LocalDateTime.class)
                .getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    @Test
    void collaborationDeleteRemovesStoredBytesOnlyAfterAtomicRowDelete() {
        FileAsset asset = asset(21L, "ATTACHMENT");
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        when(mapper.deleteStalePendingCollaborationAttachment(21L, 7L, cutoff)).thenReturn(1);

        assertThat(worker.deleteStaleCollaborationAttachment(asset, cutoff)).isTrue();

        verify(storage).snapshotIfExists("7/file-21");
        verify(storage).delete("7/file-21");
    }

    @Test
    void autoPrepDeleteRemovesStoredBytesOnlyAfterAtomicRowDelete() {
        FileAsset asset = asset(24L, "ATTACHMENT");
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        when(mapper.deleteStalePendingAutoPrepAttachment(24L, 7L, cutoff)).thenReturn(1);

        assertThat(worker.deleteStaleAutoPrepAttachment(asset, cutoff)).isTrue();

        verify(storage).snapshotIfExists("7/file-24");
        verify(storage).delete("7/file-24");
    }

    @Test
    void profileImportDeleteRemovesStoredBytesOnlyAfterAtomicRowDelete() {
        FileAsset asset = asset(25L, "RESUME");
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        when(mapper.deleteStalePendingProfileImport(25L, 7L, cutoff)).thenReturn(1);

        assertThat(worker.deleteStaleProfileImport(asset, cutoff)).isTrue();

        verify(storage).snapshotIfExists("7/file-25");
        verify(storage).delete("7/file-25");
    }

    @Test
    void lostClaimDoesNotTouchStoredBytes() {
        FileAsset asset = asset(22L, "VIDEO");
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        when(mapper.deleteStalePendingInterviewMedia(22L, 7L, cutoff)).thenReturn(0);

        assertThat(worker.deleteStaleInterviewMedia(asset, cutoff)).isFalse();

        verify(storage, never()).snapshotIfExists("7/file-22");
        verify(storage, never()).delete("7/file-22");
    }

    @Test
    void orphanDeleteRemovesMetadataBeforeStoredBytes() {
        FileAsset asset = asset(23L, "AUDIO");
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        when(mapper.deleteStaleOrphanedInterviewMedia(23L, 7L, cutoff)).thenReturn(1);

        assertThat(worker.deleteStaleOrphanedInterviewMedia(asset, cutoff)).isTrue();

        verify(storage).snapshotIfExists("7/file-23");
        verify(storage).delete("7/file-23");
    }

    private FileAsset asset(Long id, String kind) {
        return FileAsset.builder()
                .id(id)
                .ownerUserId(7L)
                .kind(kind)
                .storageKey("7/file-" + id)
                .build();
    }
}
