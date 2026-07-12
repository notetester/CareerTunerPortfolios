package com.careertuner.correction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import com.careertuner.applicationcase.service.ApplicationCaseAccessService;
import com.careertuner.billing.service.AiChargeService;
import com.careertuner.billing.policy.AiChargePreflightService;
import com.careertuner.correction.ai.CorrectionAiClient;
import com.careertuner.correction.domain.CorrectionRequest;
import com.careertuner.correction.dto.CorrectionCreateRequest;
import com.careertuner.correction.dto.CorrectionResponse;
import com.careertuner.correction.mapper.CorrectionMapper;
import com.careertuner.notification.service.NotificationService;

import tools.jackson.databind.ObjectMapper;

/**
 * 무과금(autoprep) 경로의 멱등키 동작 고정.
 *
 * <p>E 가 넣은 멱등성({@code uk_correction_request_user_key})은 유료 경로에만 걸려 있었고,
 * 챗봇 autoprep 은 {@code requestKey=null} 이라 재시도마다 AI 를 다시 태웠다.
 * 무과금이어도 키가 있으면 리플레이되고, 키가 없으면 기존대로 멱등성 없이 진행해야 한다.
 */
class CorrectionServiceUnchargedIdempotencyTest {

    private final CorrectionMapper correctionMapper = mock(CorrectionMapper.class);
    private final CorrectionAiClient aiClient = mock(CorrectionAiClient.class);
    private final CorrectionAiUsageLogService usageLogService = mock(CorrectionAiUsageLogService.class);
    private final ApplicationCaseAccessService applicationCaseAccessService = mock(ApplicationCaseAccessService.class);
    private final CorrectionContextService contextService = mock(CorrectionContextService.class);
    private final CorrectionSourceService sourceService = mock(CorrectionSourceService.class);
    private final AiChargePreflightService chargePreflightService = mock(AiChargePreflightService.class);
    private final AiChargeService aiChargeService = mock(AiChargeService.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);

    private final CorrectionService service = new CorrectionService(
            correctionMapper,
            aiClient,
            usageLogService,
            applicationCaseAccessService,
            contextService,
            sourceService,
            transactionTemplate,
            chargePreflightService,
            aiChargeService,
            notificationService,
            new ObjectMapper());

    private static CorrectionCreateRequest autoPrepRequest(String requestKey) {
        return new CorrectionCreateRequest("SELF_INTRO", null, "자소서 원문", null, null, null, null, requestKey);
    }

    private static CorrectionRequest existingSuccess() {
        return CorrectionRequest.builder()
                .id(99L)
                .userId(7L)
                .requestKey("autoprep:write:na:deadbeefdeadbeefdeadbeefdeadbeef")
                .correctionType("SELF_INTRO")
                .originalText("자소서 원문")
                .improvedText("교정된 원문")
                .resultJson("{}")
                .status("SUCCESS")
                .build();
    }

    @Test
    @DisplayName("무과금 경로도 requestKey 가 있으면 기존 결과를 리플레이하고 AI 를 재호출하지 않는다")
    void unchargedPathReplaysWhenRequestKeyMatchesExistingRow() {
        String key = "autoprep:write:na:deadbeefdeadbeefdeadbeefdeadbeef";
        when(correctionMapper.findByUserIdAndRequestKey(7L, key)).thenReturn(existingSuccess());

        CorrectionResponse response = service.createUnchargedForAutoPrep(7L, autoPrepRequest(key));

        assertThat(response.id()).isEqualTo(99L);
        assertThat(response.replayed()).isTrue();
        verify(aiClient, never()).correct(any(), any());
        verify(transactionTemplate, never()).execute(any());
    }

    @Test
    @DisplayName("requestKey 가 없으면 멱등성 조회 자체를 하지 않는다 — 기존 동작 유지")
    void unchargedPathWithoutRequestKeySkipsIdempotencyLookup() {
        when(aiClient.correct(any(), any())).thenThrow(new IllegalStateException("AI 호출까지 도달"));

        try {
            service.createUnchargedForAutoPrep(7L, autoPrepRequest(null));
        } catch (RuntimeException ignored) {
            // AI 호출 지점까지 흘러갔다는 것이 이 테스트의 관심사다.
        }

        verify(correctionMapper, never()).findByUserIdAndRequestKey(anyLong(), anyString());
    }
}
