package com.careertuner.fitanalysis.ai;

import static org.assertj.core.api.Assertions.assertThat;
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
        MockFitAnalysisAiService mockSvc = mock(MockFitAnalysisAiService.class);
        CareerAnalysisOssClient client = mock(CareerAnalysisOssClient.class);
        CareerAnalysisAiProviderProperties props = new CareerAnalysisAiProviderProperties();
        props.setProvider("oss");
        when(client.available()).thenReturn(true);
        when(oss.generate(command)).thenReturn(tagged("oss-result", "careertuner-c-career-strategy-3b"));

        FallbackFitAnalysisAiService service =
                new FallbackFitAnalysisAiService(oss, anthropic, openAi, mockSvc, client, props);
        FitAnalysisAiResult result = service.generate(command);

        assertThat(result.strategy()).isEqualTo("oss-result");
        verify(anthropic, never()).generate(command);
        verify(openAi, never()).generate(command);
    }

    @Test
    void fallsBackToClaudeWhenOssFails() {
        OssFitAnalysisAiService oss = mock(OssFitAnalysisAiService.class);
        AnthropicFitAnalysisAiService anthropic = mock(AnthropicFitAnalysisAiService.class);
        OpenAiFitAnalysisAiService openAi = mock(OpenAiFitAnalysisAiService.class);
        MockFitAnalysisAiService mockSvc = mock(MockFitAnalysisAiService.class);
        CareerAnalysisOssClient client = mock(CareerAnalysisOssClient.class);
        CareerAnalysisAiProviderProperties props = new CareerAnalysisAiProviderProperties();
        props.setProvider("oss");
        when(client.available()).thenReturn(true);
        when(oss.generate(command)).thenThrow(new BusinessException(ErrorCode.INTERNAL_ERROR, "자체모델 실패"));
        when(anthropic.configured()).thenReturn(true);
        when(anthropic.generate(command)).thenReturn(tagged("claude-result", "claude-haiku"));

        FallbackFitAnalysisAiService service =
                new FallbackFitAnalysisAiService(oss, anthropic, openAi, mockSvc, client, props);
        FitAnalysisAiResult result = service.generate(command);

        assertThat(result.strategy()).isEqualTo("claude-result");
        verify(openAi, never()).generate(command);
    }

    @Test
    void usesClaudeBeforeOpenAiWhenConfigured() {
        OssFitAnalysisAiService oss = mock(OssFitAnalysisAiService.class);
        AnthropicFitAnalysisAiService anthropic = mock(AnthropicFitAnalysisAiService.class);
        OpenAiFitAnalysisAiService openAi = mock(OpenAiFitAnalysisAiService.class);
        MockFitAnalysisAiService mockSvc = mock(MockFitAnalysisAiService.class);
        CareerAnalysisOssClient client = mock(CareerAnalysisOssClient.class);
        CareerAnalysisAiProviderProperties props = new CareerAnalysisAiProviderProperties(); // 기본 provider=openai
        when(anthropic.configured()).thenReturn(true);
        when(anthropic.generate(command)).thenReturn(tagged("claude-result", "claude-haiku"));

        FallbackFitAnalysisAiService service =
                new FallbackFitAnalysisAiService(oss, anthropic, openAi, mockSvc, client, props);
        FitAnalysisAiResult result = service.generate(command);

        assertThat(result.strategy()).isEqualTo("claude-result");
        verify(oss, never()).generate(command);
        verify(openAi, never()).generate(command);
    }

    @Test
    void fallsBackToOpenAiWhenClaudeFails() {
        OssFitAnalysisAiService oss = mock(OssFitAnalysisAiService.class);
        AnthropicFitAnalysisAiService anthropic = mock(AnthropicFitAnalysisAiService.class);
        OpenAiFitAnalysisAiService openAi = mock(OpenAiFitAnalysisAiService.class);
        MockFitAnalysisAiService mockSvc = mock(MockFitAnalysisAiService.class);
        CareerAnalysisOssClient client = mock(CareerAnalysisOssClient.class);
        CareerAnalysisAiProviderProperties props = new CareerAnalysisAiProviderProperties();
        when(anthropic.configured()).thenReturn(true);
        when(anthropic.generate(command)).thenThrow(new BusinessException(ErrorCode.INTERNAL_ERROR, "Claude 실패"));
        when(openAi.generate(command)).thenReturn(tagged("openai-result", "gpt-5"));

        FallbackFitAnalysisAiService service =
                new FallbackFitAnalysisAiService(oss, anthropic, openAi, mockSvc, client, props);
        FitAnalysisAiResult result = service.generate(command);

        assertThat(result.strategy()).isEqualTo("openai-result");
    }

    @Test
    void usesOpenAiWhenProviderOpenaiAndClaudeNotConfigured() {
        OssFitAnalysisAiService oss = mock(OssFitAnalysisAiService.class);
        AnthropicFitAnalysisAiService anthropic = mock(AnthropicFitAnalysisAiService.class);
        OpenAiFitAnalysisAiService openAi = mock(OpenAiFitAnalysisAiService.class);
        MockFitAnalysisAiService mockSvc = mock(MockFitAnalysisAiService.class);
        CareerAnalysisOssClient client = mock(CareerAnalysisOssClient.class);
        CareerAnalysisAiProviderProperties props = new CareerAnalysisAiProviderProperties(); // 기본 provider=openai
        when(anthropic.configured()).thenReturn(false); // Claude 키 없음 → 건너뜀
        when(openAi.generate(command)).thenReturn(tagged("openai-result", "gpt-5"));

        FallbackFitAnalysisAiService service =
                new FallbackFitAnalysisAiService(oss, anthropic, openAi, mockSvc, client, props);
        FitAnalysisAiResult result = service.generate(command);

        assertThat(result.strategy()).isEqualTo("openai-result");
        verify(oss, never()).generate(command);
    }

    @Test
    void skipsOssWhenBaseUrlMissing() {
        OssFitAnalysisAiService oss = mock(OssFitAnalysisAiService.class);
        AnthropicFitAnalysisAiService anthropic = mock(AnthropicFitAnalysisAiService.class);
        OpenAiFitAnalysisAiService openAi = mock(OpenAiFitAnalysisAiService.class);
        MockFitAnalysisAiService mockSvc = mock(MockFitAnalysisAiService.class);
        CareerAnalysisOssClient client = mock(CareerAnalysisOssClient.class);
        CareerAnalysisAiProviderProperties props = new CareerAnalysisAiProviderProperties();
        props.setProvider("oss");
        when(client.available()).thenReturn(false); // base-url 미설정 → OSS 시도 안 함
        when(anthropic.configured()).thenReturn(false);
        when(openAi.generate(command)).thenReturn(tagged("openai-result", "gpt-5"));

        FallbackFitAnalysisAiService service =
                new FallbackFitAnalysisAiService(oss, anthropic, openAi, mockSvc, client, props);
        FitAnalysisAiResult result = service.generate(command);

        assertThat(result.strategy()).isEqualTo("openai-result");
        verify(oss, never()).generate(command);
    }

    @Test
    void chainBudgetExhaustedSkipsExternalTiersAndReturnsMock() throws Exception {
        OssFitAnalysisAiService oss = mock(OssFitAnalysisAiService.class);
        AnthropicFitAnalysisAiService anthropic = mock(AnthropicFitAnalysisAiService.class);
        OpenAiFitAnalysisAiService openAi = mock(OpenAiFitAnalysisAiService.class);
        MockFitAnalysisAiService mockSvc = mock(MockFitAnalysisAiService.class);
        CareerAnalysisOssClient client = mock(CareerAnalysisOssClient.class);
        CareerAnalysisAiProviderProperties props = new CareerAnalysisAiProviderProperties();
        props.setProvider("oss");
        props.setChainTotalTimeBudget(Duration.ofMillis(1)); // 아주 작은 체인 예산
        when(client.available()).thenReturn(true);
        // OSS 가 예산을 넘겨 소요한 뒤 실패 → 이후 Claude/OpenAI 는 예산 소진으로 건너뛰고 Mock 반환
        when(anthropic.configured()).thenReturn(true);
        when(oss.generate(command)).thenAnswer(inv -> {
            Thread.sleep(20);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "자체모델 실패");
        });
        when(mockSvc.generate(command)).thenReturn(tagged("mock-result", "mock"));

        FallbackFitAnalysisAiService service =
                new FallbackFitAnalysisAiService(oss, anthropic, openAi, mockSvc, client, props);
        FitAnalysisAiResult result = service.generate(command);

        assertThat(result.strategy()).isEqualTo("mock-result");
        verify(anthropic, never()).generate(command); // 체인 예산 소진 → 외부 tier 미시작
        verify(openAi, never()).generate(command);
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
