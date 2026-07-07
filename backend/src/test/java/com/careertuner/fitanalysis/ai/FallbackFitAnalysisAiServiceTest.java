package com.careertuner.fitanalysis.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.careertuner.analysis.ai.provider.CareerAnalysisAiProviderProperties;
import com.careertuner.analysis.ai.provider.CareerAnalysisAiUsage;
import com.careertuner.analysis.ai.provider.CareerAnalysisOssClient;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;

/**
 * C 적합도 AI 폴백 디스패처(OSS→Claude→OpenAI→Mock) 검증.
 *
 * <p>하이브리드 폴백-타임아웃: 설정된 각 tier 는 예산 소진으로 건너뛰지 않고 항상 최소 한 번 시도되며,
 * 외부 tier 는 per-attempt 타임아웃(Duration) + 체인 데드라인(long) 오버로드로 호출된다. Mock 은
 * OpenAI tier 내부 폴백으로만 도달한다(별도 Mock tier 없음).
 */
class FallbackFitAnalysisAiServiceTest {

    private final FitAnalysisAiCommand command = new FitAnalysisAiCommand(
            "테스트기업", "백엔드 개발자", List.of("Java"), List.of("AWS"), "개발",
            List.of("Java"), List.of(), "백엔드 개발자");

    private FitAnalysisAiResult tagged(String strategy, String usageModel) {
        return new FitAnalysisAiResult(70, List.of("Java"), List.of(), List.of(), List.of(),
                strategy, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                new FitApplyDecision("COMPLEMENT", List.of(), List.of()),
                new CareerAnalysisAiUsage(usageModel, 0, 0, 0, false), "SUCCESS", null, false);
    }

    @Test
    void usesOssWhenProviderOssAndAvailable() {
        OssFitAnalysisAiService oss = mock(OssFitAnalysisAiService.class);
        AnthropicFitAnalysisAiService anthropic = mock(AnthropicFitAnalysisAiService.class);
        OpenAiFitAnalysisAiService openAi = mock(OpenAiFitAnalysisAiService.class);
        CareerAnalysisOssClient client = mock(CareerAnalysisOssClient.class);
        CareerAnalysisAiProviderProperties props = new CareerAnalysisAiProviderProperties();
        props.setProvider("oss");
        when(client.available()).thenReturn(true);
        when(oss.generate(command)).thenReturn(tagged("oss-result", "careertuner-c-career-strategy-3b"));

        FallbackFitAnalysisAiService service =
                new FallbackFitAnalysisAiService(oss, anthropic, openAi, client, props);
        FitAnalysisAiResult result = service.generate(command);

        assertThat(result.strategy()).isEqualTo("oss-result");
        verify(anthropic, never()).generate(eq(command), any(), anyLong());
        verify(openAi, never()).generate(eq(command), any(), anyLong());
    }

    @Test
    void fallsBackToClaudeWhenOssFails() {
        OssFitAnalysisAiService oss = mock(OssFitAnalysisAiService.class);
        AnthropicFitAnalysisAiService anthropic = mock(AnthropicFitAnalysisAiService.class);
        OpenAiFitAnalysisAiService openAi = mock(OpenAiFitAnalysisAiService.class);
        CareerAnalysisOssClient client = mock(CareerAnalysisOssClient.class);
        CareerAnalysisAiProviderProperties props = new CareerAnalysisAiProviderProperties();
        props.setProvider("oss");
        when(client.available()).thenReturn(true);
        when(oss.generate(command)).thenThrow(new BusinessException(ErrorCode.INTERNAL_ERROR, "자체모델 실패"));
        when(anthropic.configured()).thenReturn(true);
        when(anthropic.generate(eq(command), any(), anyLong())).thenReturn(tagged("claude-result", "claude-haiku"));

        FallbackFitAnalysisAiService service =
                new FallbackFitAnalysisAiService(oss, anthropic, openAi, client, props);
        FitAnalysisAiResult result = service.generate(command);

        assertThat(result.strategy()).isEqualTo("claude-result");
        verify(openAi, never()).generate(eq(command), any(), anyLong());
    }

    @Test
    void usesClaudeBeforeOpenAiWhenConfigured() {
        OssFitAnalysisAiService oss = mock(OssFitAnalysisAiService.class);
        AnthropicFitAnalysisAiService anthropic = mock(AnthropicFitAnalysisAiService.class);
        OpenAiFitAnalysisAiService openAi = mock(OpenAiFitAnalysisAiService.class);
        CareerAnalysisOssClient client = mock(CareerAnalysisOssClient.class);
        CareerAnalysisAiProviderProperties props = new CareerAnalysisAiProviderProperties(); // 기본 provider=openai
        when(anthropic.configured()).thenReturn(true);
        when(anthropic.generate(eq(command), any(), anyLong())).thenReturn(tagged("claude-result", "claude-haiku"));

        FallbackFitAnalysisAiService service =
                new FallbackFitAnalysisAiService(oss, anthropic, openAi, client, props);
        FitAnalysisAiResult result = service.generate(command);

        assertThat(result.strategy()).isEqualTo("claude-result");
        verify(oss, never()).generate(command);
        verify(openAi, never()).generate(eq(command), any(), anyLong());
    }

    @Test
    void fallsBackToOpenAiWhenClaudeFails() {
        OssFitAnalysisAiService oss = mock(OssFitAnalysisAiService.class);
        AnthropicFitAnalysisAiService anthropic = mock(AnthropicFitAnalysisAiService.class);
        OpenAiFitAnalysisAiService openAi = mock(OpenAiFitAnalysisAiService.class);
        CareerAnalysisOssClient client = mock(CareerAnalysisOssClient.class);
        CareerAnalysisAiProviderProperties props = new CareerAnalysisAiProviderProperties();
        when(anthropic.configured()).thenReturn(true);
        when(anthropic.generate(eq(command), any(), anyLong()))
                .thenThrow(new BusinessException(ErrorCode.INTERNAL_ERROR, "Claude 실패"));
        when(openAi.generate(eq(command), any(), anyLong())).thenReturn(tagged("openai-result", "gpt-5"));

        FallbackFitAnalysisAiService service =
                new FallbackFitAnalysisAiService(oss, anthropic, openAi, client, props);
        FitAnalysisAiResult result = service.generate(command);

        assertThat(result.strategy()).isEqualTo("openai-result");
    }

    @Test
    void usesOpenAiWhenProviderOpenaiAndClaudeNotConfigured() {
        OssFitAnalysisAiService oss = mock(OssFitAnalysisAiService.class);
        AnthropicFitAnalysisAiService anthropic = mock(AnthropicFitAnalysisAiService.class);
        OpenAiFitAnalysisAiService openAi = mock(OpenAiFitAnalysisAiService.class);
        CareerAnalysisOssClient client = mock(CareerAnalysisOssClient.class);
        CareerAnalysisAiProviderProperties props = new CareerAnalysisAiProviderProperties(); // 기본 provider=openai
        when(anthropic.configured()).thenReturn(false); // Claude 키 없음 → 건너뜀
        when(openAi.generate(eq(command), any(), anyLong())).thenReturn(tagged("openai-result", "gpt-5"));

        FallbackFitAnalysisAiService service =
                new FallbackFitAnalysisAiService(oss, anthropic, openAi, client, props);
        FitAnalysisAiResult result = service.generate(command);

        assertThat(result.strategy()).isEqualTo("openai-result");
        verify(oss, never()).generate(command);
    }

    @Test
    void skipsOssWhenBaseUrlMissing() {
        OssFitAnalysisAiService oss = mock(OssFitAnalysisAiService.class);
        AnthropicFitAnalysisAiService anthropic = mock(AnthropicFitAnalysisAiService.class);
        OpenAiFitAnalysisAiService openAi = mock(OpenAiFitAnalysisAiService.class);
        CareerAnalysisOssClient client = mock(CareerAnalysisOssClient.class);
        CareerAnalysisAiProviderProperties props = new CareerAnalysisAiProviderProperties();
        props.setProvider("oss");
        when(client.available()).thenReturn(false); // base-url 미설정 → OSS 시도 안 함
        when(anthropic.configured()).thenReturn(false);
        when(openAi.generate(eq(command), any(), anyLong())).thenReturn(tagged("openai-result", "gpt-5"));

        FallbackFitAnalysisAiService service =
                new FallbackFitAnalysisAiService(oss, anthropic, openAi, client, props);
        FitAnalysisAiResult result = service.generate(command);

        assertThat(result.strategy()).isEqualTo("openai-result");
        verify(oss, never()).generate(command);
    }

    @Test
    void everyTierAttemptedBeforeMock() {
        OssFitAnalysisAiService oss = mock(OssFitAnalysisAiService.class);
        AnthropicFitAnalysisAiService anthropic = mock(AnthropicFitAnalysisAiService.class);
        OpenAiFitAnalysisAiService openAi = mock(OpenAiFitAnalysisAiService.class);
        CareerAnalysisOssClient client = mock(CareerAnalysisOssClient.class);
        CareerAnalysisAiProviderProperties props = new CareerAnalysisAiProviderProperties();
        props.setProvider("oss");
        when(client.available()).thenReturn(true);
        // OSS 실패 → Claude 실패 → OpenAI 성공: 예산 소진으로 tier 를 건너뛰지 않고 각 tier 를 모두 시도한다.
        when(oss.generate(command)).thenThrow(new BusinessException(ErrorCode.INTERNAL_ERROR, "자체모델 실패"));
        when(anthropic.configured()).thenReturn(true);
        when(anthropic.generate(eq(command), any(), anyLong()))
                .thenThrow(new BusinessException(ErrorCode.INTERNAL_ERROR, "Claude 실패"));
        when(openAi.generate(eq(command), any(), anyLong())).thenReturn(tagged("openai-result", "gpt-5"));

        FallbackFitAnalysisAiService service =
                new FallbackFitAnalysisAiService(oss, anthropic, openAi, client, props);
        FitAnalysisAiResult result = service.generate(command);

        assertThat(result.strategy()).isEqualTo("openai-result");
        // 세 tier 가 각각 실제로 시도됐는지 검증(외부 tier 는 per-attempt 타임아웃 + 체인 데드라인 인자와 함께).
        verify(oss).generate(command);
        verify(anthropic).generate(eq(command), any(Duration.class), anyLong());
        verify(openAi).generate(eq(command), any(Duration.class), anyLong());
    }

    @Test
    void ossDefaultsHonorMaxTokenAndModel() {
        CareerAnalysisAiProviderProperties props = new CareerAnalysisAiProviderProperties();
        // 통합 주의사항: 설명이 길어 1024 미만이면 JSON truncation → 최소 1024(권장 1280~1536)
        assertThat(props.getOss().getMaxTokens()).isGreaterThanOrEqualTo(1024);
        assertThat(props.getOss().getModel()).isEqualTo("careertuner-c-career-strategy-3b");
        assertThat(props.getOss().getTemperature()).isEqualTo(0.2);
        assertThat(props.isOss()).isFalse(); // 기본은 openai
    }
}
