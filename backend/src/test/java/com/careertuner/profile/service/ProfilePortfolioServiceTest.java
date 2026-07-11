package com.careertuner.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.mockito.InOrder;

import com.careertuner.common.text.DocumentTextExtractor;
import com.careertuner.file.domain.FileAsset;
import com.careertuner.file.dto.FileAssetResponse;
import com.careertuner.file.service.FileService;
import com.careertuner.profile.domain.UserProfile;
import com.careertuner.profile.mapper.ProfileMapper;

class ProfilePortfolioServiceTest {

    private final ProfileMapper profileMapper = mock(ProfileMapper.class);
    private final FileService fileService = mock(FileService.class);
    private final ProfilePortfolioService service = new ProfilePortfolioService(
            profileMapper, fileService, new DocumentTextExtractor());

    @Test
    void uploadsPortfolioWithProfileReferenceFromTheStart() {
        UserProfile profile = UserProfile.builder().id(31L).userId(7L).build();
        when(profileMapper.findByUserIdForUpdate(7L)).thenReturn(profile);
        MockMultipartFile file = new MockMultipartFile(
                "file", "portfolio.pdf", "application/pdf", "pdf".getBytes(StandardCharsets.UTF_8));
        FileAssetResponse uploaded = new FileAssetResponse(
                101L, "PORTFOLIO", ProfilePortfolioService.REF_TYPE, 31L,
                "portfolio.pdf", "application/pdf", 3L, "/api/file/101/content", LocalDateTime.now());
        when(fileService.upload(7L, "PORTFOLIO", ProfilePortfolioService.REF_TYPE, 31L, file))
                .thenReturn(uploaded);

        FileAssetResponse result = service.upload(7L, file);

        assertThat(result.refType()).isEqualTo(ProfilePortfolioService.REF_TYPE);
        assertThat(result.refId()).isEqualTo(31L);
        verify(profileMapper).insertEmptyIfAbsent(7L);
        verify(profileMapper).findByUserIdForUpdate(7L);
        verify(fileService).upload(7L, "PORTFOLIO", ProfilePortfolioService.REF_TYPE, 31L, file);
    }

    @Test
    void adoptsOnlyOwnedPortfolioKindAndReturnsLinkedFiles() {
        UserProfile profile = UserProfile.builder().id(31L).userId(7L).build();
        FileAsset asset = portfolioAsset(101L, 7L, 31L, "portfolio.md", "text/markdown");
        when(profileMapper.findByUserIdForUpdate(7L)).thenReturn(profile);
        when(profileMapper.findByUserId(7L)).thenReturn(profile);
        when(fileService.findLinkedFiles(ProfilePortfolioService.REF_TYPE, 31L))
                .thenReturn(List.of(), List.of(asset));

        List<FileAssetResponse> result = service.link(7L, List.of(101L));

        verify(fileService).linkOwnedFilesOfKind(
                eq(7L), eq(List.of(101L)), eq("PORTFOLIO"),
                eq(ProfilePortfolioService.REF_TYPE), eq(31L), eq(10));
        assertThat(result).extracting(FileAssetResponse::id).containsExactly(101L);
        InOrder lockBeforeLink = inOrder(profileMapper, fileService);
        lockBeforeLink.verify(profileMapper).findByUserIdForUpdate(7L);
        lockBeforeLink.verify(fileService).findLinkedFiles(ProfilePortfolioService.REF_TYPE, 31L);
        lockBeforeLink.verify(fileService).linkOwnedFilesOfKind(
                eq(7L), eq(List.of(101L)), eq("PORTFOLIO"),
                eq(ProfilePortfolioService.REF_TYPE), eq(31L), eq(10));
    }

    @Test
    void doesNotAdoptMoreFilesWhenExistingLinksAlreadyReachMaximum() {
        UserProfile profile = UserProfile.builder().id(31L).userId(7L).build();
        List<FileAsset> existing = java.util.stream.LongStream.rangeClosed(1, 10)
                .mapToObj(id -> portfolioAsset(id, 7L, 31L, "portfolio-" + id + ".md", "text/markdown"))
                .toList();
        when(profileMapper.findByUserIdForUpdate(7L)).thenReturn(profile);
        when(profileMapper.findByUserId(7L)).thenReturn(profile);
        when(fileService.findLinkedFiles(ProfilePortfolioService.REF_TYPE, 31L)).thenReturn(existing);

        List<FileAssetResponse> result = service.link(7L, List.of(101L));

        verify(fileService, never()).linkOwnedFilesOfKind(
                eq(7L), eq(List.of(101L)), eq("PORTFOLIO"),
                eq(ProfilePortfolioService.REF_TYPE), eq(31L), anyInt());
        assertThat(result).hasSize(10);
    }

    @Test
    void exposesPortfolioAsSeparateAiEvidenceWithoutSelfIntroConversion() {
        UserProfile profile = UserProfile.builder().id(31L).userId(7L).build();
        FileAsset asset = portfolioAsset(101L, 7L, 31L, "project.md", "text/markdown");
        when(profileMapper.findByUserId(7L)).thenReturn(profile);
        when(fileService.findLinkedFiles(ProfilePortfolioService.REF_TYPE, 31L)).thenReturn(List.of(asset));
        when(fileService.download(7L, 101L)).thenReturn(new FileService.Download(
                asset,
                "결제 API의 멱등성을 설계해 중복 승인을 막았습니다.".getBytes(StandardCharsets.UTF_8)));

        String evidence = service.evidenceText(7L);

        assertThat(evidence)
                .contains("[포트폴리오 파일] project.md")
                .contains("결제 API의 멱등성")
                .doesNotContain("SELF_INTRO");
    }

    @Test
    void deletesOnlyPortfolioLinkedToCurrentProfile() {
        UserProfile profile = UserProfile.builder().id(31L).userId(7L).build();
        FileAsset asset = portfolioAsset(101L, 7L, 31L, "project.md", "text/markdown");
        when(profileMapper.findByUserId(7L)).thenReturn(profile);
        when(fileService.findLinkedFiles(ProfilePortfolioService.REF_TYPE, 31L)).thenReturn(List.of(asset));

        service.delete(7L, 101L);

        verify(fileService).deleteOwnedLinked(
                7L, 101L, "PORTFOLIO", ProfilePortfolioService.REF_TYPE, 31L);
        when(fileService.findLinkedFiles(ProfilePortfolioService.REF_TYPE, 31L)).thenReturn(List.of());
        assertThat(service.list(7L)).isEmpty();
        assertThat(service.evidenceText(7L)).isNull();
    }

    private static FileAsset portfolioAsset(Long id, Long ownerUserId, Long profileId,
                                            String name, String contentType) {
        return FileAsset.builder()
                .id(id)
                .ownerUserId(ownerUserId)
                .kind("PORTFOLIO")
                .refType(ProfilePortfolioService.REF_TYPE)
                .refId(profileId)
                .originalName(name)
                .contentType(contentType)
                .sizeBytes(100L)
                .storageKey("portfolio/" + id)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
