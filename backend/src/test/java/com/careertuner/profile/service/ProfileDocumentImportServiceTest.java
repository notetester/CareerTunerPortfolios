package com.careertuner.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
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
                .build();
        UserProfile after = UserProfile.builder()
                .userId(7L)
                .selfIntro("기존 자소서")
                .skills("[\"Python\"]")
                .resumeText("Resume body Java Spring")
                .build();
        // RMW 조회 → 저장 후 조회
        when(profileMapper.findByUserId(7L)).thenReturn(existing, after);
        doAnswer(inv -> null).when(profileMapper).upsert(any());

        ProfileImportResponse response = service.importDocument(
                USER, new ProfileDocumentImportRequest(11L, "RESUME_TEXT"));

        assertThat(response.truncated()).isFalse();
        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(profileMapper).upsert(captor.capture());
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
                USER, new ProfileDocumentImportRequest(12L, "RESUME_TEXT")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("지원하지 않는 형식");
    }

    @Test
    void nullFileId_throwsSelectFileMessage() {
        assertThatThrownBy(() -> service.importDocument(
                USER, new ProfileDocumentImportRequest(null, "RESUME_TEXT")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("가져올 파일을 선택");
    }

    @Test
    void invalidTarget_throws() {
        assertThatThrownBy(() -> service.importDocument(
                USER, new ProfileDocumentImportRequest(1L, "OTHER")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("잘못된 요청");
    }

    @Test
    void truncatesLongText() {
        String longText = "x".repeat(ProfileServiceImpl.MAX_IMPORT_CHARS + 50);
        stubDownload(13L, "big.txt", "text/plain", longText);
        when(profileMapper.findByUserId(7L)).thenReturn(UserProfile.builder().userId(7L).build());
        doAnswer(inv -> null).when(profileMapper).upsert(any());

        ProfileImportResponse response = service.importDocument(
                USER, new ProfileDocumentImportRequest(13L, "SELF_INTRO"));

        assertThat(response.truncated()).isTrue();
        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(profileMapper).upsert(captor.capture());
        assertThat(captor.getValue().getSelfIntro()).hasSize(ProfileServiceImpl.MAX_IMPORT_CHARS);
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
