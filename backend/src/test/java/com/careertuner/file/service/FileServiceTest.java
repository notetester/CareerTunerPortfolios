package com.careertuner.file.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.file.domain.FileAsset;
import com.careertuner.file.mapper.FileAssetMapper;

class FileServiceTest {

    private final FileStorageService storage = mock(FileStorageService.class);
    private final FileAssetMapper mapper = mock(FileAssetMapper.class);
    private final FileService service = new FileService(storage, mapper);

    @Test
    void removesStoredBytesWhenMetadataInsertFails() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.txt", "text/plain", "resume".getBytes(StandardCharsets.UTF_8));
        when(storage.store(7L, file)).thenReturn(new FileStorageService.Stored("7/new-file", 6L));
        doThrow(new IllegalStateException("db failed")).when(mapper).insert(org.mockito.ArgumentMatchers.any());

        assertThatThrownBy(() -> service.upload(7L, "RESUME", null, null, file))
                .isInstanceOf(IllegalStateException.class);

        verify(storage).delete("7/new-file");
    }

    @Test
    void deletesOnlyOwnedUnlinkedUpload() {
        FileAsset asset = asset(11L, 7L, null, null, "ATTACHMENT");
        when(mapper.findById(11L)).thenReturn(asset);
        when(mapper.deleteByIdAndOwnerIfPending(11L, 7L)).thenReturn(1);

        service.deleteOwnedUnlinked(7L, 11L);

        verify(mapper).deleteByIdAndOwnerIfPending(11L, 7L);
        verify(storage).delete("7/file-11");
    }

    @Test
    void deletesOwnedCollaborationUploadThatHasPurposeButNoMessageYet() {
        FileAsset asset = asset(12L, 7L, "COLLAB_MESSAGE", null, "ATTACHMENT");
        when(mapper.findById(12L)).thenReturn(asset);
        when(mapper.deleteByIdAndOwnerIfPending(12L, 7L)).thenReturn(1);

        service.deleteOwnedUnlinked(7L, 12L);

        verify(mapper).deleteByIdAndOwnerIfPending(12L, 7L);
        verify(storage).delete("7/file-12");
    }

    @Test
    void genericDeleteRejectsLinkedDomainFile() {
        FileAsset asset = asset(11L, 7L, "INTERVIEW_ANSWER", 44L, "AUDIO");
        when(mapper.findById(11L)).thenReturn(asset);

        assertThatThrownBy(() -> service.deleteOwnedUnlinked(7L, 11L))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> org.assertj.core.api.Assertions.assertThat(
                        ((BusinessException) error).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));

        verify(mapper, never()).deleteByIdAndOwnerIfPending(11L, 7L);
        verify(storage, never()).delete("7/file-11");
    }

    @Test
    void sentCollaborationAttachmentCannotBeDeletedByGenericEndpoint() {
        FileAsset asset = asset(13L, 7L, "COLLAB_MESSAGE", 99L, "ATTACHMENT");
        when(mapper.findById(13L)).thenReturn(asset);

        assertThatThrownBy(() -> service.deleteOwnedUnlinked(7L, 13L))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> org.assertj.core.api.Assertions.assertThat(
                        ((BusinessException) error).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));

        verify(mapper, never()).deleteByIdAndOwnerIfPending(13L, 7L);
        verify(storage, never()).delete("7/file-13");
    }

    @Test
    void concurrentMessageClaimPreventsStoredBytesDeletion() {
        FileAsset asset = asset(14L, 7L, "COLLAB_MESSAGE", null, "ATTACHMENT");
        when(mapper.findById(14L)).thenReturn(asset);
        when(mapper.deleteByIdAndOwnerIfPending(14L, 7L)).thenReturn(0);

        assertThatThrownBy(() -> service.deleteOwnedUnlinked(7L, 14L))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> org.assertj.core.api.Assertions.assertThat(
                        ((BusinessException) error).getErrorCode()).isEqualTo(ErrorCode.CONFLICT));

        verify(storage, never()).delete("7/file-14");
    }

    @Test
    void staleCleanupDeletesOnlyRowsThatStillMatchAtomicPendingCondition() {
        FileAsset deletable = asset(21L, 7L, "COLLAB_MESSAGE", null, "ATTACHMENT");
        FileAsset claimedDuringCleanup = asset(22L, 7L, "COLLAB_MESSAGE", null, "ATTACHMENT");
        when(mapper.findStalePendingCollaborationAttachments(
                org.mockito.ArgumentMatchers.any(LocalDateTime.class), org.mockito.ArgumentMatchers.eq(100)))
                .thenReturn(List.of(deletable, claimedDuringCleanup));
        when(mapper.deleteStalePendingCollaborationAttachment(
                org.mockito.ArgumentMatchers.eq(21L), org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class))).thenReturn(1);
        when(mapper.deleteStalePendingCollaborationAttachment(
                org.mockito.ArgumentMatchers.eq(22L), org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class))).thenReturn(0);

        int deleted = service.cleanupStalePendingCollaborationAttachments(1, 1000);

        org.assertj.core.api.Assertions.assertThat(deleted).isEqualTo(1);
        verify(storage).delete("7/file-21");
        verify(storage, never()).delete("7/file-22");
        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mapper).findStalePendingCollaborationAttachments(cutoff.capture(), org.mockito.ArgumentMatchers.eq(100));
        org.assertj.core.api.Assertions.assertThat(cutoff.getValue())
                .isBeforeOrEqualTo(LocalDateTime.now().minusHours(24))
                .isAfter(LocalDateTime.now().minusHours(25));
    }

    @Test
    void claimsPendingInterviewMediaWithPurposeAndOwnerGuard() {
        FileAsset linked = asset(31L, 7L, "INTERVIEW_ANSWER", 51L, "AUDIO");
        when(mapper.claimOwnedPendingFile(
                31L, 7L, "AUDIO", "INTERVIEW_ANSWER", 51L)).thenReturn(1);
        when(mapper.findById(31L)).thenReturn(linked);

        FileAsset result = service.claimOwnedPendingFile(
                7L, 31L, "AUDIO", "INTERVIEW_ANSWER", 51L);

        org.assertj.core.api.Assertions.assertThat(result).isSameAs(linked);
    }

    @Test
    void staleInterviewCleanupDeletesOnlyStillPendingMedia() {
        FileAsset audio = asset(32L, 7L, "INTERVIEW_ANSWER", null, "AUDIO");
        FileAsset claimed = asset(33L, 7L, "INTERVIEW_ANSWER", null, "VIDEO");
        when(mapper.findStalePendingInterviewMedia(
                org.mockito.ArgumentMatchers.any(LocalDateTime.class), org.mockito.ArgumentMatchers.eq(100)))
                .thenReturn(List.of(audio, claimed));
        when(mapper.deleteStalePendingInterviewMedia(
                org.mockito.ArgumentMatchers.eq(32L), org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class))).thenReturn(1);
        when(mapper.deleteStalePendingInterviewMedia(
                org.mockito.ArgumentMatchers.eq(33L), org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class))).thenReturn(0);

        int deleted = service.cleanupStalePendingInterviewMedia(2, 500);

        org.assertj.core.api.Assertions.assertThat(deleted).isEqualTo(1);
        verify(storage).delete("7/file-32");
        verify(storage, never()).delete("7/file-33");
    }

    @Test
    void deleteRejectsDifferentOwner() {
        when(mapper.findById(11L)).thenReturn(asset(11L, 8L, null, null, "RESUME"));

        assertThatThrownBy(() -> service.deleteOwnedUnlinked(7L, 11L))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> org.assertj.core.api.Assertions.assertThat(
                        ((BusinessException) error).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void linkedDeleteRequiresExactKindAndProfileReference() {
        FileAsset asset = asset(11L, 7L, "USER_PROFILE_PORTFOLIO", 31L, "PORTFOLIO");
        when(mapper.findById(11L)).thenReturn(asset);
        when(mapper.deleteByIdAndOwnerAndRef(
                11L, 7L, "PORTFOLIO", "USER_PROFILE_PORTFOLIO", 31L)).thenReturn(1);

        service.deleteOwnedLinked(7L, 11L, "PORTFOLIO", "USER_PROFILE_PORTFOLIO", 31L);

        verify(mapper).deleteByIdAndOwnerAndRef(
                11L, 7L, "PORTFOLIO", "USER_PROFILE_PORTFOLIO", 31L);
        verify(storage).delete("7/file-11");
    }

    private static FileAsset asset(Long id, Long ownerId, String refType, Long refId, String kind) {
        return FileAsset.builder()
                .id(id)
                .ownerUserId(ownerId)
                .kind(kind)
                .refType(refType)
                .refId(refId)
                .storageKey(ownerId + "/file-" + id)
                .build();
    }
}
