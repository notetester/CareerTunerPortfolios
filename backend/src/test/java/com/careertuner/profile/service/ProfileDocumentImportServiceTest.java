package com.careertuner.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.careertuner.applicationcase.mapper.ApplicationCaseMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.text.DocumentTextExtractor;
import com.careertuner.consent.service.ConsentService;
import com.careertuner.file.domain.FileAsset;
import com.careertuner.file.service.FileService;
import com.careertuner.notification.service.NotificationService;
import com.careertuner.profile.ai.ProfileAiService;
import com.careertuner.profile.ai.ProfileResumeStructurer;
import com.careertuner.profile.domain.UserProfile;
import com.careertuner.profile.dto.ProfileDocumentImportRequest;
import com.careertuner.profile.dto.ProfileImportResponse;
import com.careertuner.profile.mapper.ProfileMapper;
import com.careertuner.common.text.DocumentTextExtractor.Failure;

import tools.jackson.databind.ObjectMapper;

/**
 * import 경로: 텍스트 덤프 + 실패 enum → 사용자 메시지 매핑 + RMW(self_intro 보존).
 */
class ProfileDocumentImportServiceTest {

    private static final AuthUser USER = new AuthUser(7L, "user@example.com", "USER");

    private ProfileMapper profileMapper;
    private FileService fileService;
    private ConsentService consentService;
    private TransactionTemplate transactionTemplate;
    private ProfileServiceImpl service;

    @BeforeEach
    void setUp() {
        profileMapper = mock(ProfileMapper.class);
        fileService = mock(FileService.class);
        consentService = mock(ConsentService.class);
        when(consentService.hasCurrentConsent(7L, "RESUME_ANALYSIS")).thenReturn(true);
        transactionTemplate = mock(TransactionTemplate.class);
        // execute 콜백을 즉시 실행
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> cb = inv.getArgument(0);
            return cb.doInTransaction(null);
        });
        service = new ProfileServiceImpl(
                profileMapper,
                mock(com.careertuner.profile.mapper.ProfileAiAnalysisMapper.class),
                mock(ApplicationCaseMapper.class),
                consentService,
                mock(ProfileAiService.class),
                mock(NotificationService.class),
                new ObjectMapper(),
                fileService,
                mock(ProfilePortfolioService.class),
                new DocumentTextExtractor(),
                mock(ProfileResumeStructurer.class),
                transactionTemplate);
    }

    @Test
    void importResumeText_preservesSelfIntro() {
        stubDownload(11L, "resume.txt", "text/plain", "Resume body Java Spring");
        UserProfile existing = UserProfile.builder()
                .userId(7L)
                .selfIntro("기존 자소서")
                .skills("[\"Python\"]")
                .versionNo(3)
                .build();
        UserProfile after = UserProfile.builder()
                .userId(7L)
                .selfIntro("기존 자소서")
                .skills("[\"Python\"]")
                .resumeText("Resume body Java Spring")
                .versionNo(4)
                .build();
        // 잠금 RMW 조회 → 저장 후 조회
        when(profileMapper.findByUserIdForUpdate(7L)).thenReturn(existing);
        when(profileMapper.findByUserId(7L)).thenReturn(after);
        doAnswer(inv -> null).when(profileMapper).upsert(any());

        ProfileImportResponse response = service.importDocument(
                USER, new ProfileDocumentImportRequest(11L, "RESUME_TEXT", 3));

        assertThat(response.truncated()).isFalse();
        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(profileMapper).insertEmptyIfAbsent(7L);
        verify(profileMapper).findByUserIdForUpdate(7L);
        verify(profileMapper).upsert(captor.capture());
        verify(profileMapper).insertVersionFromCurrent(7L, "DOCUMENT_IMPORT");
        UserProfile saved = captor.getValue();
        assertThat(saved.getResumeText()).contains("Resume body");
        assertThat(saved.getSelfIntro()).isEqualTo("기존 자소서");
        assertThat(saved.getSkills()).isEqualTo("[\"Python\"]");
    }

    @Test
    void failureMessages_mapAllEnums() {
        assertThat(ProfileServiceImpl.messageFor(Failure.NO_TEXT_LAYER))
                .contains("스캔한 PDF");
        assertThat(ProfileServiceImpl.messageFor(Failure.CORRUPTED))
                .contains("손상");
        assertThat(ProfileServiceImpl.messageFor(Failure.UNSUPPORTED_FORMAT))
                .contains("지원하지 않는 형식");
        assertThat(ProfileServiceImpl.messageFor(Failure.EMPTY))
                .contains("글자를 찾지");
    }

    @Test
    void unsupportedDoc_throwsMappedMessage() {
        stubDownload(12L, "old.doc", "application/msword", new byte[] { 1, 2, 3 });

        assertThatThrownBy(() -> service.importDocument(
                USER, new ProfileDocumentImportRequest(12L, "RESUME_TEXT", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("지원하지 않는 형식");
    }

    @Test
    void nullFileId_throwsSelectFileMessage() {
        assertThatThrownBy(() -> service.importDocument(
                USER, new ProfileDocumentImportRequest(null, "RESUME_TEXT", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("가져올 파일을 선택");
    }

    @Test
    void invalidTarget_throws() {
        assertThatThrownBy(() -> service.importDocument(
                USER, new ProfileDocumentImportRequest(1L, "OTHER", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("잘못된 요청");
    }

    @Test
    void truncatesLongText() {
        String longText = "x".repeat(ProfileServiceImpl.MAX_IMPORT_CHARS + 50);
        stubDownload(13L, "big.txt", "text/plain", longText);
        when(profileMapper.findByUserIdForUpdate(7L))
                .thenReturn(UserProfile.builder().userId(7L).versionNo(1).build());
        when(profileMapper.findByUserId(7L))
                .thenReturn(UserProfile.builder().userId(7L).versionNo(2).build());
        doAnswer(inv -> null).when(profileMapper).upsert(any());

        ProfileImportResponse response = service.importDocument(
                USER, new ProfileDocumentImportRequest(13L, "SELF_INTRO", 1));

        assertThat(response.truncated()).isTrue();
        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(profileMapper).upsert(captor.capture());
        assertThat(captor.getValue().getSelfIntro()).hasSize(ProfileServiceImpl.MAX_IMPORT_CHARS);
    }

    @Test
    void brandNewEmptyProfileAllowsNullBaseVersion() {
        stubDownload(14L, "resume.txt", "text/plain", "첫 이력서");
        UserProfile empty = UserProfile.builder()
                .userId(7L)
                .versionNo(1)
                .build();
        UserProfile after = UserProfile.builder()
                .userId(7L)
                .resumeText("첫 이력서")
                .versionNo(2)
                .build();
        when(profileMapper.insertEmptyIfAbsent(7L)).thenReturn(1);
        when(profileMapper.findByUserIdForUpdate(7L)).thenReturn(empty);
        when(profileMapper.findByUserId(7L)).thenReturn(after);

        ProfileImportResponse response = service.importDocument(
                USER, new ProfileDocumentImportRequest(14L, "RESUME_TEXT", null));

        assertThat(response.profile().versionNo()).isEqualTo(2);
        verify(profileMapper).initialize(any(UserProfile.class));
        verify(profileMapper, never()).upsert(any(UserProfile.class));
        verify(profileMapper).insertVersionFromCurrent(7L, "DOCUMENT_IMPORT");
    }

    @Test
    void existingNonEmptyProfileRejectsNullBaseVersionWithoutWrites() {
        stubDownload(15L, "resume.txt", "text/plain", "뒤늦은 이력서");
        UserProfile current = UserProfile.builder()
                .userId(7L)
                .desiredJob("백엔드 개발자")
                .versionNo(4)
                .build();
        when(profileMapper.findByUserIdForUpdate(7L)).thenReturn(current);

        assertThatThrownBy(() -> service.importDocument(
                USER, new ProfileDocumentImportRequest(15L, "RESUME_TEXT", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("최신 내용을 다시 불러온 뒤")
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(com.careertuner.common.exception.ErrorCode.CONFLICT);

        verify(profileMapper, never()).initialize(any(UserProfile.class));
        verify(profileMapper, never()).upsert(any(UserProfile.class));
        verify(profileMapper, never()).insertVersionFromCurrent(7L, "DOCUMENT_IMPORT");
    }

    @Test
    void staleBaseVersionRejectsImportWithoutUpsertOrVersionSnapshot() {
        stubDownload(16L, "resume.txt", "text/plain", "오래된 화면의 이력서");
        UserProfile current = UserProfile.builder()
                .userId(7L)
                .resumeText("다른 화면에서 저장한 최신 이력서")
                .versionNo(5)
                .build();
        when(profileMapper.findByUserIdForUpdate(7L)).thenReturn(current);

        assertThatThrownBy(() -> service.importDocument(
                USER, new ProfileDocumentImportRequest(16L, "RESUME_TEXT", 4)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("최신 내용을 다시 불러온 뒤")
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(com.careertuner.common.exception.ErrorCode.CONFLICT);

        verify(profileMapper, never()).initialize(any(UserProfile.class));
        verify(profileMapper, never()).upsert(any(UserProfile.class));
        verify(profileMapper, never()).insertVersionFromCurrent(7L, "DOCUMENT_IMPORT");
        verify(profileMapper, never()).findByUserId(7L);
    }

    private void stubDownload(Long fileId, String name, String contentType, String text) {
        stubDownload(fileId, name, contentType, text.getBytes(StandardCharsets.UTF_8));
    }

    private void stubDownload(Long fileId, String name, String contentType, byte[] bytes) {
        FileAsset asset = FileAsset.builder()
                .id(fileId)
                .ownerUserId(7L)
                .originalName(name)
                .contentType(contentType)
                .sizeBytes((long) bytes.length)
                .build();
        when(fileService.download(7L, fileId)).thenReturn(new FileService.Download(asset, bytes));
    }
}
