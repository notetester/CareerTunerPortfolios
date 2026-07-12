package com.careertuner.fitanalysis.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
import com.careertuner.ai.common.settings.AiRuntimeSettings;
import com.careertuner.analysis.ai.provider.CareerAnalysisAiProviderProperties;
import com.careertuner.analysis.ai.provider.CareerAnalysisOssClient;

/**
 * C 적합도 모델 선택 라우팅 + <b>뉴로-심볼릭 불변식</b> 검증: 어느 모델을 골라도 판단값(fitScore/matched/missing)은
 * 동일하고 설명 provider 만 달라진다. 선택 tier 가 실패/미가용이면 하위 tier→안전망까지 폴백해 화면이 안 깨진다.
 */
class FallbackFitAnalysisModelSelectionTest {

    private final OssFitAnalysisAiService oss = mock(OssFitAnalysisAiService.class);
    private final AnthropicFitAnalysisAiService anthropic = mock(AnthropicFitAnalysisAiService.class);
    private final OpenAiFitAnalysisAiService openAi = mock(OpenAiFitAnalysisAiService.class);
    private final CareerAnalysisOssClient ossClient = mock(CareerAnalysisOssClient.class);
    private final CareerAnalysisAiProviderProperties props = mock(CareerAnalysisAiProviderProperties.class);
    private final AiRuntimeSettings settings = mock(AiRuntimeSettings.class);
    private final FallbackFitAnalysisAiService service =
            new FallbackFitAnalysisAiService(oss, anthropic, openAi, ossClient, props, settings);

    private static final FitAnalysisAiCommand CMD = new FitAnalysisAiCommand(
            "삼성SDS", "백엔드", List.of("Java"), List.of("Kafka"), "개발",
            List.of("Java"), List.of(), "백엔드");

    /** 판단값은 세 provider 가 동일(현실에선 셋 다 규칙엔진으로 계산) — 설명(strategy)만 provider 태그로 구분. */
    private static FitAnalysisAiResult tagged(String providerTag) {
        return new FitAnalysisAiResult(76, List.of("Java"), List.of("Kafka"),
                List.of(), List.of(), providerTag, List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), null, null, "SUCCESS", null, false);
    }

    @BeforeEach
    void setUp() {
        lenient().when(settings.analysisChainTotalTimeBudget()).thenReturn(Duration.ofSeconds(120));
        lenient().when(settings.analysisClaudeTimeout()).thenReturn(Duration.ofSeconds(30));
        lenient().when(settings.analysisOpenaiTimeout()).thenReturn(Duration.ofSeconds(30));
        lenient().when(oss.generate(any())).thenReturn(tagged("OSS"));
        lenient().when(anthropic.generate(any(), any(), anyLong())).thenReturn(tagged("CLAUDE"));
        lenient().when(openAi.generate(any(), any(), anyLong())).thenReturn(tagged("OPENAI"));
    }

    @Test
    void autoWithOssEnabledPrefersSelfModel() {
        when(props.isOss()).thenReturn(true);
        when(ossClient.available()).thenReturn(true);

        assertThat(service.generate(CMD, RequestedAiModel.AUTO).strategy()).isEqualTo("OSS");
        verify(anthropic, never()).generate(any(), any(), anyLong());
        verify(openAi, never()).generate(any(), any(), anyLong());
    }

    @Test
    void autoWithOssOffAndNoClaudeKeyGoesOpenAi() {
        // 기본 config(provider=openai) + Claude 키 없음 = 현행 동작 그대로.
        when(props.isOss()).thenReturn(false);
        when(anthropic.configured()).thenReturn(false);

        assertThat(service.generate(CMD, RequestedAiModel.AUTO).strategy()).isEqualTo("OPENAI");
        verify(oss, never()).generate(any());
    }

    @Test
    void openAiChoiceIsolatesToOpenAiEvenWhenSelfAndClaudeAvailable() {
        lenient().when(props.isOss()).thenReturn(true);
        lenient().when(ossClient.available()).thenReturn(true);
        lenient().when(anthropic.configured()).thenReturn(true);

        assertThat(service.generate(CMD, RequestedAiModel.OPENAI).strategy()).isEqualTo("OPENAI");
        verify(oss, never()).generate(any());
        verify(anthropic, never()).generate(any(), any(), anyLong());
    }

    @Test
    void claudeChoiceSkipsSelfTier() {
        lenient().when(props.isOss()).thenReturn(true);
        lenient().when(ossClient.available()).thenReturn(true);
        when(anthropic.configured()).thenReturn(true);

        assertThat(service.generate(CMD, RequestedAiModel.CLAUDE).strategy()).isEqualTo("CLAUDE");
        verify(oss, never()).generate(any());
    }

    @Test
    void careertunerChoiceForcesSelfEvenWhenGlobalToggleOff() {
        // 명시 선택은 provider=openai 전역 토글을 우회해 자체모델을 시도한다(엔드포인트가 있으면).
        when(props.isOss()).thenReturn(false);
        when(ossClient.available()).thenReturn(true);

        assertThat(service.generate(CMD, RequestedAiModel.CAREERTUNER).strategy()).isEqualTo("OSS");
    }

    @Test
    void selectedTierFailureFallsThroughToSafetyNet() {
        when(props.isOss()).thenReturn(true);
        when(ossClient.available()).thenReturn(true);
        when(oss.generate(any())).thenThrow(new RuntimeException("OSS down"));
        when(anthropic.configured()).thenReturn(false);

        // 자체모델을 골랐지만 죽어도 OpenAI 내부 Mock 안전망까지 폴백 → 화면 안 깨짐.
        assertThat(service.generate(CMD, RequestedAiModel.CAREERTUNER).strategy()).isEqualTo("OPENAI");
    }

    @Test
    void judgmentValuesIdenticalAcrossAllModelChoices() {
        when(props.isOss()).thenReturn(true);
        when(ossClient.available()).thenReturn(true);
        when(anthropic.configured()).thenReturn(true);

        for (RequestedAiModel model : RequestedAiModel.values()) {
            FitAnalysisAiResult r = service.generate(CMD, model);
            assertThat(r.fitScore()).as("fitScore for %s", model).isEqualTo(76);
            assertThat(r.matchedSkills()).as("matched for %s", model).containsExactly("Java");
            assertThat(r.missingSkills()).as("missing for %s", model).containsExactly("Kafka");
        }
    }
}
