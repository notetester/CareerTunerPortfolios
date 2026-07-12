package com.careertuner.correction.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.careertuner.ai.common.model.RequestedAiModel;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.correction.ai.CorrectionAiClient.CorrectionCommand;
import com.careertuner.correction.ai.CorrectionAiClient.CorrectionPayload;
import com.careertuner.correction.ai.CorrectionAiClient.Usage;
import com.careertuner.runtimesetting.service.RuntimeSettingService;

/** E 첨삭 모델 선택 라우팅 — 선택 tier 부터 시작, 실패 시 하위 폴백, 전부 실패 시 AI_UNAVAILABLE(안전망 없음). */
class CorrectionModelSelectionTest {

    private final CorrectionAiProperties props = new CorrectionAiProperties();
    private final OpenAiCorrectionProvider openAi = mock(OpenAiCorrectionProvider.class);
    private final SelfLlmCorrectionProvider selfLlm = mock(SelfLlmCorrectionProvider.class);
    private final AnthropicCorrectionProvider anthropic = mock(AnthropicCorrectionProvider.class);
    private final CorrectionModelWarmupService warmup = mock(CorrectionModelWarmupService.class);
    private final RuntimeSettingService runtimeSettings = mock(RuntimeSettingService.class);
    private final CorrectionAiClient client =
            new CorrectionAiClient(props, openAi, selfLlm, anthropic, warmup, runtimeSettings);

    private static final CorrectionCommand CMD = new CorrectionCommand(
            "SELF_INTRO", "SELF_INTRO", null, null, null, "원문입니다.", null);

    private static CorrectionPayload payload(String tag) {
        return new CorrectionPayload("개선-" + tag, tag, List.of(), List.of(), List.of(), new Usage("model-" + tag, 1, 1, 2));
    }

    @BeforeEach
    void setUp() {
        // self tier: 예산 OFF(무제한), 1회 시도, 짧은 타임아웃.
        props.getSelf().setTotalTimeBudget(Duration.ZERO);
        props.getSelf().setMaxAttempts(1);
        props.getSelf().setTimeout(Duration.ofSeconds(30));
        props.getSelf().setRetryBackoff(Duration.ZERO);
        lenient().when(runtimeSettings.getInt(anyString(), anyInt())).thenAnswer(inv -> inv.getArgument(1));
        lenient().when(selfLlm.correct(any(), any(), any(), any())).thenReturn(payload("SELF"));
        lenient().when(anthropic.correct(any())).thenReturn(payload("CLAUDE"));
        lenient().when(openAi.correct(any())).thenReturn(payload("OPENAI"));
    }

    @Test
    void autoWithSelfEnabledPrefersSelf() {
        props.setProvider("self");
        props.getSelf().setBaseUrl("http://self");

        assertThat(client.correct(CMD, RequestedAiModel.AUTO).summary()).isEqualTo("SELF");
        verify(anthropic, never()).correct(any());
        verify(openAi, never()).correct(any());
    }

    @Test
    void openAiChoiceIsolatesToOpenAi() {
        props.setProvider("self");
        props.getSelf().setBaseUrl("http://self");
        lenient().when(anthropic.configured()).thenReturn(true);

        assertThat(client.correct(CMD, RequestedAiModel.OPENAI).summary()).isEqualTo("OPENAI");
        verify(selfLlm, never()).correct(any(), any(), any(), any());
        verify(anthropic, never()).correct(any());
    }

    @Test
    void claudeChoiceSkipsSelfAndUsesAnthropic() {
        props.setProvider("self");
        props.getSelf().setBaseUrl("http://self");
        when(anthropic.configured()).thenReturn(true);

        assertThat(client.correct(CMD, RequestedAiModel.CLAUDE).summary()).isEqualTo("CLAUDE");
        verify(selfLlm, never()).correct(any(), any(), any(), any());
    }

    @Test
    void careertunerChoiceForcesSelfEvenWhenGlobalProviderIsOpenAi() {
        // 전역 provider=openai 라 selfProviderEnabled=false 지만, 엔드포인트가 있으면 명시 선택이 자체모델을 시도.
        props.setProvider("openai");
        props.getSelf().setBaseUrl("http://self");

        assertThat(client.correct(CMD, RequestedAiModel.CAREERTUNER).summary()).isEqualTo("SELF");
    }

    @Test
    void claudeFailureFallsThroughToOpenAi() {
        when(anthropic.configured()).thenReturn(true);
        when(anthropic.correct(any())).thenThrow(new RuntimeException("claude down"));

        assertThat(client.correct(CMD, RequestedAiModel.CLAUDE).summary()).isEqualTo("OPENAI");
    }

    @Test
    void allTiersFailingYieldsAiUnavailableNotGarbage() {
        props.setProvider("openai"); // self 비활성
        when(anthropic.configured()).thenReturn(false);
        when(openAi.correct(any())).thenThrow(new RuntimeException("openai down"));

        assertThatThrownBy(() -> client.correct(CMD, RequestedAiModel.AUTO))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("일시적으로 사용할 수 없");
    }
}
