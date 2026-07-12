package com.careertuner.correction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.careertuner.ai.common.model.RequestedAiModel;
import com.careertuner.applicationcase.service.ApplicationCaseAccessService;
import com.careertuner.billing.dto.AiChargeCommand;
import com.careertuner.billing.dto.AiChargeResult;
import com.careertuner.billing.policy.AiChargePreflightService;
import com.careertuner.billing.service.AiChargeService;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.correction.ai.CorrectionAiClient;
import com.careertuner.correction.ai.CorrectionAiClient.CorrectionPayload;
import com.careertuner.correction.ai.CorrectionAiClient.Usage;
import com.careertuner.correction.domain.CorrectionRequest;
import com.careertuner.correction.dto.CorrectionCreateRequest;
import com.careertuner.correction.dto.CorrectionInterviewSourceResponse;
import com.careertuner.correction.dto.CorrectionResponse;
import com.careertuner.correction.mapper.CorrectionMapper;
import com.careertuner.notification.service.NotificationService;

import tools.jackson.databind.ObjectMapper;

class CorrectionServiceTest {

    private final CorrectionMapper correctionMapper = org.mockito.Mockito.mock(CorrectionMapper.class);
    private final CorrectionAiClient aiClient = org.mockito.Mockito.mock(CorrectionAiClient.class);
    private final CorrectionAiUsageLogService usageLogService = org.mockito.Mockito.mock(CorrectionAiUsageLogService.class);
    private final ApplicationCaseAccessService applicationCaseAccessService =
            org.mockito.Mockito.mock(ApplicationCaseAccessService.class);
    private final CorrectionContextService contextService = org.mockito.Mockito.mock(CorrectionContextService.class);
    private final CorrectionSourceService sourceService = org.mockito.Mockito.mock(CorrectionSourceService.class);
    private final AiChargePreflightService chargePreflightService = org.mockito.Mockito.mock(AiChargePreflightService.class);
    private final AiChargeService aiChargeService = org.mockito.Mockito.mock(AiChargeService.class);
    private final NotificationService notificationService = org.mockito.Mockito.mock(NotificationService.class);

    private final CorrectionService service = new CorrectionService(
            correctionMapper,
            aiClient,
            usageLogService,
            applicationCaseAccessService,
            contextService,
            sourceService,
            transactionTemplate(),
            chargePreflightService,
            aiChargeService,
            notificationService,
            new ObjectMapper());

    @Test
    void createPassesActualTokenUsageToChargeServiceAfterPersistingCorrection() {
        stubSuccessfulCorrection();
        when(aiChargeService.charge(any())).thenReturn(AiChargeResult.credit(2, 8));

        CorrectionResponse response = service.create(1L, request("AI_USAGE:test-1"));

        assertThat(response.id()).isEqualTo(77L);
        ArgumentCaptor<AiChargeCommand> captor = ArgumentCaptor.forClass(AiChargeCommand.class);
        verify(aiChargeService).charge(captor.capture());
        verify(chargePreflightService).requireAcknowledged(
                1L, "CORRECTION_SELF_INTRO", "AI_USAGE:test-1");
        assertThat(captor.getValue()).satisfies(command -> {
            assertThat(command.userId()).isEqualTo(1L);
            assertThat(command.featureType()).isEqualTo("CORRECTION_SELF_INTRO");
            assertThat(command.refType()).isEqualTo("CORRECTION");
            assertThat(command.refId()).isEqualTo(77L);
            assertThat(command.aiUsageLogId()).isEqualTo(501L);
            assertThat(command.creditCost()).isNull();
            assertThat(command.tokenUsage()).isEqualTo(30);
            assertThat(command.policyAcknowledgementKey()).isEqualTo("AI_USAGE:test-1");
        });
        assertThat(response.chargedCredit()).isEqualTo(2);
        assertThat(response.totalTokens()).isEqualTo(30);
        assertThat(response.replayed()).isFalse();
        ArgumentCaptor<CorrectionRequest> correctionCaptor = ArgumentCaptor.forClass(CorrectionRequest.class);
        verify(correctionMapper).insert(correctionCaptor.capture());
        assertThat(correctionCaptor.getValue().getRequestKey()).isEqualTo("correction:test-request");
        verify(notificationService).notify(any());
    }

    @Test
    void explicitModelUsesSelectedModelOverload() {
        when(aiClient.correct(any(), eq(RequestedAiModel.CLAUDE))).thenReturn(payload());
        when(usageLogService.recordSuccess(1L, null, "CORRECTION_SELF_INTRO", payload().usage()))
                .thenReturn(501L);
        when(aiChargeService.charge(any())).thenReturn(AiChargeResult.credit(2, 8));
        doAnswer(invocation -> {
            CorrectionRequest correction = invocation.getArgument(0);
            correction.setId(77L);
            return null;
        }).when(correctionMapper).insert(any());

        CorrectionResponse response = service.create(
                1L, request("AI_USAGE:test-model"), RequestedAiModel.CLAUDE);

        assertThat(response.id()).isEqualTo(77L);
        verify(aiClient).correct(any(), eq(RequestedAiModel.CLAUDE));
    }

    @Test
    void completedRequestKeyReturnsExistingResultWithoutAiOrCharge() {
        CorrectionRequest existing = existingCorrection();
        when(correctionMapper.findByUserIdAndRequestKey(1L, "correction:test-request")).thenReturn(existing);

        CorrectionResponse response = service.create(1L, request("AI_USAGE:test-replay"));

        assertThat(response.id()).isEqualTo(88L);
        assertThat(response.replayed()).isTrue();
        verify(aiClient, never()).correct(any(), any());
        verify(usageLogService, never()).recordSuccess(anyLong(), any(), anyString(), any());
        verify(aiChargeService, never()).charge(any());
        verify(chargePreflightService, never()).requireAcknowledged(anyLong(), anyString(), anyString());
        verify(notificationService, never()).notify(any());
    }

    @Test
    void concurrentDuplicateInsertReturnsWinnerWithoutSecondCharge() {
        CorrectionRequest existing = existingCorrection();
        when(correctionMapper.findByUserIdAndRequestKey(1L, "correction:test-request"))
                .thenReturn(null, existing);
        when(aiClient.correct(any(), any())).thenReturn(payload());
        when(usageLogService.recordSuccess(1L, null, "CORRECTION_SELF_INTRO", payload().usage()))
                .thenReturn(502L);
        doThrow(new DataIntegrityViolationException("duplicate request key")).when(correctionMapper).insert(any());

        CorrectionResponse response = service.create(1L, request("AI_USAGE:test-race"));

        assertThat(response.id()).isEqualTo(88L);
        assertThat(response.replayed()).isTrue();
        verify(aiChargeService, never()).charge(any());
        verify(notificationService, never()).notify(any());
    }

    @Test
    void aiFailureRecordsFailureWithoutCharging() {
        when(aiClient.correct(any(), any())).thenThrow(new BusinessException(ErrorCode.AI_UNAVAILABLE, "AI failed"));

        assertThatThrownBy(() -> service.create(1L, request("AI_USAGE:test-2")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AI_UNAVAILABLE);

        verify(usageLogService).recordFailure(1L, null, "CORRECTION_SELF_INTRO", "AI failed");
        verify(aiChargeService, never()).charge(any());
        verify(correctionMapper, never()).insert(any());
    }

    @Test
    void unconfirmedChargeResultRejectsCorrection() {
        stubSuccessfulCorrection();
        when(aiChargeService.charge(any()))
                .thenReturn(AiChargeResult.skipped(null, 0, 10, "NO_CREDIT_USED"));

        assertThatThrownBy(() -> service.create(1L, request("AI_USAGE:test-3")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INTERNAL_ERROR);

        verify(notificationService, never()).notify(any());
    }

    @Test
    void autoPrepCorrectionPreservesExistingUnchargedFlow() {
        stubSuccessfulCorrection();

        CorrectionResponse response = service.createUnchargedForAutoPrep(1L, request(null));

        assertThat(response.id()).isEqualTo(77L);
        verify(aiChargeService, never()).charge(any());
        verify(notificationService).notify(any());
    }

    @Test
    void invalidPolicyKeyStopsBeforeAiCall() {
        assertThatThrownBy(() -> service.create(1L, request("invalid key")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verify(aiClient, never()).correct(any(), any());
        verify(usageLogService, never()).recordFailure(anyLong(), any(), anyString(), anyString());
    }

    @Test
    void ownedInterviewSourceOverridesClientTextAndCarriesSourceIdentity() {
        CorrectionInterviewSourceResponse source = new CorrectionInterviewSourceResponse(
                55L, 44L, 33L, 22L, "실제 질문", "실제 저장 답변", 72, "구체화 필요", null);
        when(sourceService.interviewAnswer(1L, 55L)).thenReturn(source);
        com.careertuner.applicationcase.domain.ApplicationCase applicationCase =
                com.careertuner.applicationcase.domain.ApplicationCase.builder()
                        .id(44L).userId(1L).companyName("회사").jobTitle("직무").build();
        when(applicationCaseAccessService.requireOwned(1L, 44L)).thenReturn(applicationCase);
        when(aiClient.correct(any(), eq(RequestedAiModel.CLAUDE))).thenReturn(payload());
        when(usageLogService.recordSuccess(1L, 44L, "CORRECTION_INTERVIEW_ANSWER", payload().usage()))
                .thenReturn(501L);
        when(aiChargeService.charge(any())).thenReturn(AiChargeResult.credit(2, 8));
        doAnswer(invocation -> {
            CorrectionRequest correction = invocation.getArgument(0);
            correction.setId(77L);
            return null;
        }).when(correctionMapper).insert(any());

        CorrectionCreateRequest request = new CorrectionCreateRequest(
                "INTERVIEW_ANSWER", 44L, "클라이언트가 바꾼 원문", "INTERVIEW_ANSWER", 55L,
                "클라이언트 질문", "AI_USAGE:interview", "correction:interview");
        service.create(1L, request, RequestedAiModel.CLAUDE);

        ArgumentCaptor<com.careertuner.correction.ai.CorrectionAiClient.CorrectionCommand> command =
                ArgumentCaptor.forClass(com.careertuner.correction.ai.CorrectionAiClient.CorrectionCommand.class);
        verify(aiClient).correct(command.capture(), eq(RequestedAiModel.CLAUDE));
        assertThat(command.getValue().originalText()).isEqualTo("실제 저장 답변");
        assertThat(command.getValue().questionText()).isEqualTo("실제 질문");
        assertThat(command.getValue().sourceRefId()).isEqualTo(55L);
        ArgumentCaptor<CorrectionRequest> row = ArgumentCaptor.forClass(CorrectionRequest.class);
        verify(correctionMapper).insert(row.capture());
        assertThat(row.getValue().getSourceRefId()).isEqualTo(55L);
        assertThat(row.getValue().getOriginalText()).isEqualTo("실제 저장 답변");
    }

    @Test
    void invalidRequestKeyStopsBeforeAiCall() {
        CorrectionCreateRequest invalid = new CorrectionCreateRequest(
                "SELF_INTRO", null, "original", "DIRECT_INPUT", null, "question",
                "AI_USAGE:test", "invalid key");

        assertThatThrownBy(() -> service.create(1L, invalid))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verify(aiClient, never()).correct(any(), any());
    }

    @Test
    void failureLogErrorDoesNotHideOriginalAiFailure() {
        BusinessException aiFailure = new BusinessException(ErrorCode.AI_UNAVAILABLE, "AI failed");
        when(aiClient.correct(any(), any())).thenThrow(aiFailure);
        doThrow(new RuntimeException("log failed"))
                .when(usageLogService).recordFailure(1L, null, "CORRECTION_SELF_INTRO", "AI failed");

        assertThatThrownBy(() -> service.create(1L, request("AI_USAGE:test-4")))
                .isSameAs(aiFailure);

        verify(aiChargeService, never()).charge(any());
    }

    private void stubSuccessfulCorrection() {
        when(aiClient.correct(any(), any())).thenReturn(payload());
        when(usageLogService.recordSuccess(1L, null, "CORRECTION_SELF_INTRO", payload().usage()))
                .thenReturn(501L);
        doAnswer(invocation -> {
            CorrectionRequest correction = invocation.getArgument(0);
            correction.setId(77L);
            return null;
        }).when(correctionMapper).insert(any());
    }

    private static CorrectionCreateRequest request(String policyAcknowledgementKey) {
        return new CorrectionCreateRequest(
                "SELF_INTRO",
                null,
                "original",
                "DIRECT_INPUT",
                null,
                "question",
                policyAcknowledgementKey,
                "correction:test-request");
    }

    private static CorrectionRequest existingCorrection() {
        return CorrectionRequest.builder()
                .id(88L)
                .userId(1L)
                .requestKey("correction:test-request")
                .correctionType("SELF_INTRO")
                .sourceType("DIRECT_INPUT")
                .originalText("original")
                .improvedText("improved")
                .resultJson("{\"summary\":\"summary\"}")
                .status("SUCCESS")
                .aiUsageLogId(700L)
                .build();
    }

    private static CorrectionPayload payload() {
        return new CorrectionPayload(
                "improved",
                "summary",
                List.of("issue"),
                List.of("reason"),
                List.of("suggestion"),
                new Usage("model", 10, 20, 30));
    }

    private static TransactionTemplate transactionTemplate() {
        return new TransactionTemplate() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction(org.mockito.Mockito.mock(TransactionStatus.class));
            }
        };
    }
}
