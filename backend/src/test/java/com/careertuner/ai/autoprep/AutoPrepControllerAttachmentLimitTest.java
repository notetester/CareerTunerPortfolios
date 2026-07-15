package com.careertuner.ai.autoprep;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.careertuner.ai.autoprep.dto.AutoPrepRequest;
import com.careertuner.ai.autoprep.dto.AutoPrepCancelRequest;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;

class AutoPrepControllerAttachmentLimitTest {

    private static final AuthUser USER = new AuthUser(7L, "user@example.com", "USER");

    private final AutoPrepOrchestrator orchestrator = mock(AutoPrepOrchestrator.class);
    private final AutoPrepIntakeService intakeService = mock(AutoPrepIntakeService.class);
    private final AutoPrepAttachmentLoader attachmentLoader = mock(AutoPrepAttachmentLoader.class);
    private final AutoPrepCaseCreationService caseCreationService = mock(AutoPrepCaseCreationService.class);
    private final AutoPrepController controller = new AutoPrepController(
            orchestrator, intakeService, attachmentLoader, caseCreationService);

    @Test
    void intakeRunAndStreamValidateTheSameCombinedAttachmentBoundary() {
        AutoPrepRequest request = new AutoPrepRequest(
                "준비해줘", null, null, null, List.of(21L), List.of(20L));
        SseEmitter emitter = new SseEmitter();
        org.mockito.Mockito.when(orchestrator.runStream(USER.id(), request)).thenReturn(emitter);

        controller.intake(USER, request);
        controller.run(USER, request);
        controller.runStream(USER, request);

        verify(attachmentLoader, times(3))
                .validateRequestLimit(USER.id(), request.jobPostingFileIds(), request.attachmentFileIds());
        verify(intakeService).intake(USER.id(), request);
        verify(orchestrator).run(USER.id(), request);
        verify(orchestrator).runStream(USER.id(), request);
    }

    @Test
    void validationFailureStopsStreamBeforeOrchestration() {
        AutoPrepRequest request = new AutoPrepRequest(
                "준비해줘", null, null, null, List.of(21L), List.of(20L));
        BusinessException rejected = new BusinessException(
                ErrorCode.INVALID_INPUT, "AutoPrep 첨부는 공고와 자소서를 합해 최대 1개까지 사용할 수 있습니다.");
        doThrow(rejected).when(attachmentLoader)
                .validateRequestLimit(USER.id(), request.jobPostingFileIds(), request.attachmentFileIds());

        assertThatThrownBy(() -> controller.runStream(USER, request)).isSameAs(rejected);

        verifyNoInteractions(orchestrator, intakeService);
    }

    @Test
    void binaryPostingAndResumeArePlanValidatedBeforeCaseCreation() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "job.pdf", "application/pdf", new byte[] { 1 });
        when(caseCreationService.createOrReuseUpload(USER.id(), 33L, file, "PDF"))
                .thenReturn(91L);

        controller.createJobPostingCase(USER, file, "PDF", 33L, List.of(44L));

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(attachmentLoader, caseCreationService);
        order.verify(attachmentLoader).validateRequestLimit(USER.id(), null, List.of(44L), 1);
        order.verify(caseCreationService).createOrReuseUpload(USER.id(), 33L, file, "PDF");
    }

    @Test
    void binaryPostingPlanFailureCreatesNoOrphanCase() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "job.pdf", "application/pdf", new byte[] { 1 });
        BusinessException rejected = new BusinessException(
                ErrorCode.INVALID_INPUT, "AutoPrep 첨부는 공고와 자소서를 합해 최대 1개까지 사용할 수 있습니다.");
        doThrow(rejected).when(attachmentLoader)
                .validateRequestLimit(USER.id(), null, List.of(44L), 1);

        assertThatThrownBy(() -> controller.createJobPostingCase(
                USER, file, "PDF", 33L, List.of(44L))).isSameAs(rejected);

        verifyNoInteractions(caseCreationService);
    }

    @Test
    void explicitCancelIsScopedByAuthenticatedUserAndRunId() {
        controller.cancelRun(USER, new AutoPrepCancelRequest("run_123"));

        verify(orchestrator).cancel(USER.id(), "run_123");
    }
}
