package com.careertuner.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.careertuner.applicationcase.service.ApplicationCaseAccessService;
import com.careertuner.interview.domain.InterviewSession;
import com.careertuner.interview.dto.InterviewDispatchTarget;
import com.careertuner.interview.mapper.InterviewMapper;
import com.careertuner.interview.media.InterviewMediaMapper;
import com.careertuner.file.service.FileService;
import com.careertuner.notification.domain.Notification;
import com.careertuner.notification.domain.NotificationDestinationPlatform;
import com.careertuner.notification.service.NotificationService;

import tools.jackson.databind.ObjectMapper;

class InterviewDispatchTest {

    private final InterviewMapper interviewMapper = mock(InterviewMapper.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private InterviewServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InterviewServiceImpl(
                interviewMapper,
                mock(ApplicationCaseAccessService.class),
                mock(InterviewOpenAiClient.class),
                mock(InterviewAiUsageLogService.class),
                mock(InterviewAgentOrchestrator.class),
                mock(ObjectMapper.class),
                mock(InterviewBackgroundExecutor.class),
                notificationService,
                mock(FileService.class),
                mock(InterviewMediaMapper.class));
    }

    @Test
    void mobileTargetCreatesPhoneNotificationAndMobileSessionLink() {
        stubOwnedSession(11L, 101L, "BASIC");
        ArgumentCaptor<Notification> notification = ArgumentCaptor.forClass(Notification.class);

        service.dispatchSession(11L, 101L, InterviewDispatchTarget.MOBILE);

        verify(notificationService).notify(notification.capture());
        assertCommonContract(notification.getValue(), 11L, 101L);
        assertThat(notification.getValue().getTitle()).isEqualTo("데스크톱에서 면접 세션을 보냈어요");
        assertThat(notification.getValue().getMessage()).isEqualTo("기본 면접 세션을 폰에서 이어받을 수 있어요.");
        assertThat(notification.getValue().getLink()).isEqualTo("/m/session/101");
        assertThat(notification.getValue().getDestinationPlatform())
                .isEqualTo(NotificationDestinationPlatform.MOBILE);
    }

    @Test
    void desktopTargetCreatesMobileOriginNotificationAndDesktopSessionLink() {
        stubOwnedSession(12L, 102L, "JOB");
        ArgumentCaptor<Notification> notification = ArgumentCaptor.forClass(Notification.class);

        service.dispatchSession(12L, 102L, InterviewDispatchTarget.DESKTOP);

        verify(notificationService).notify(notification.capture());
        assertCommonContract(notification.getValue(), 12L, 102L);
        assertThat(notification.getValue().getTitle()).isEqualTo("모바일에서 면접 세션을 보냈어요");
        assertThat(notification.getValue().getMessage()).isEqualTo("직무 면접 세션을 데스크톱에서 이어받을 수 있어요.");
        assertThat(notification.getValue().getLink()).isEqualTo("/interview?session=102");
        assertThat(notification.getValue().getDestinationPlatform())
                .isEqualTo(NotificationDestinationPlatform.DESKTOP);
    }

    @Test
    void nullServiceTargetDefensivelyKeepsLegacyMobileDirection() {
        stubOwnedSession(13L, 103L, "PERSONALITY");
        ArgumentCaptor<Notification> notification = ArgumentCaptor.forClass(Notification.class);

        service.dispatchSession(13L, 103L, null);

        verify(notificationService).notify(notification.capture());
        assertThat(notification.getValue().getTitle()).startsWith("데스크톱에서");
        assertThat(notification.getValue().getLink()).isEqualTo("/m/session/103");
        assertThat(notification.getValue().getDestinationPlatform())
                .isEqualTo(NotificationDestinationPlatform.MOBILE);
    }

    private void stubOwnedSession(Long userId, Long sessionId, String mode) {
        when(interviewMapper.findSessionByIdAndUserId(sessionId, userId)).thenReturn(
                InterviewSession.builder().id(sessionId).applicationCaseId(1L).mode(mode).build());
    }

    private static void assertCommonContract(Notification notification, Long userId, Long sessionId) {
        assertThat(notification.getUserId()).isEqualTo(userId);
        assertThat(notification.getType()).isEqualTo("INTERVIEW_DISPATCH");
        assertThat(notification.getTargetType()).isEqualTo("INTERVIEW_SESSION");
        assertThat(notification.getTargetId()).isEqualTo(sessionId);
    }
}
