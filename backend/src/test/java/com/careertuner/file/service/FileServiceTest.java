package com.careertuner.file.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
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
        when(mapper.deleteByIdAndOwnerIfUnlinked(11L, 7L)).thenReturn(1);

        service.deleteOwnedUnlinked(7L, 11L);

        verify(mapper).deleteByIdAndOwnerIfUnlinked(11L, 7L);
        verify(storage).delete("7/file-11");
    }

    @Test
    void genericDeleteRejectsLinkedDomainFile() {
        FileAsset asset = asset(11L, 7L, "INTERVIEW_ANSWER", 44L, "AUDIO");
        when(mapper.findById(11L)).thenReturn(asset);

        assertThatThrownBy(() -> service.deleteOwnedUnlinked(7L, 11L))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> org.assertj.core.api.Assertions.assertThat(
                        ((BusinessException) error).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));

        verify(mapper, never()).deleteByIdAndOwnerIfUnlinked(11L, 7L);
        verify(storage, never()).delete("7/file-11");
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
