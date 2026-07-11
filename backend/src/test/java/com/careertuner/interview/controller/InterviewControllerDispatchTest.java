package com.careertuner.interview.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import com.careertuner.common.security.AuthUser;
import com.careertuner.interview.dto.DispatchInterviewSessionRequest;
import com.careertuner.interview.dto.InterviewDispatchTarget;
import com.careertuner.interview.realtime.InterviewRealtimeService;
import com.careertuner.interview.service.InterviewService;

class InterviewControllerDispatchTest {

    private final InterviewService interviewService = mock(InterviewService.class);
    private final InterviewController controller = new InterviewController(
            interviewService, mock(InterviewRealtimeService.class));
    private final AuthUser user = new AuthUser(7L, "user@example.com", "USER");

    @Test
    void missingBodyDefaultsToMobileForBackwardCompatibility() {
        var response = controller.dispatchSession(user, 91L, null);

        assertThat(response.success()).isTrue();
        verify(interviewService).dispatchSession(7L, 91L, InterviewDispatchTarget.MOBILE);
    }

    @Test
    void emptyBodyDefaultsToMobileForBackwardCompatibility() {
        var response = controller.dispatchSession(
                user, 92L, new DispatchInterviewSessionRequest(null));

        assertThat(response.success()).isTrue();
        verify(interviewService).dispatchSession(7L, 92L, InterviewDispatchTarget.MOBILE);
    }

    @Test
    void explicitDesktopTargetIsForwardedWithoutDirectionLoss() {
        var response = controller.dispatchSession(
                user, 93L, new DispatchInterviewSessionRequest(InterviewDispatchTarget.DESKTOP));

        assertThat(response.success()).isTrue();
        verify(interviewService).dispatchSession(7L, 93L, InterviewDispatchTarget.DESKTOP);
    }
}
