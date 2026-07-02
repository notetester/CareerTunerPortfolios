package com.careertuner.fitanalysis.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.careertuner.analysis.ai.provider.CareerAnalysisAiUsage;
import com.careertuner.analysis.ai.provider.CareerAnalysisAnthropicClient;
import com.careertuner.analysis.ai.provider.CareerAnalysisOpenAiClient.StructuredResponse;
import com.careertuner.common.exception.BusinessException;

import tools.jackson.databind.ObjectMapper;

/**
 * Claude(Haiku) 단계의 뉴로-심볼릭 계약 검증 — 판단값은 규칙엔진 skeleton 소유, LLM 은 설명만.
 * 검증 실패(grounding 등)는 예외로 던져 상위 디스패처가 OpenAI 로 폴백하게 한다.
 */
class AnthropicFitAnalysisAiServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final FitAnalysisAiCommand command = new FitAnalysisAiCommand(
            "테스트기업", "백엔드 개발자",
            List.of("Java", "Spring"), List.of("AWS"), "REST API 개발",
            List.of("Java"), List.of("정보처리기사"), "백엔드 개발자");

    private final FitAnalysisAiResult skeleton = new MockFitAnalysisAiService().generate(command);

    private AnthropicFitAnalysisAiService service(CareerAnalysisAnthropicClient client) {
        return new AnthropicFitAnalysisAiService(client, new MockFitAnalysisAiService(), new FitExplainAssembler());
    }

    @Test
    void configuredDelegatesToClient() {
        CareerAnalysisAnthropicClient client = mock(CareerAnalysisAnthropicClient.class);
        when(client.configured()).thenReturn(true);

        assertThat(service(client).configured()).isTrue();
    }

    @Test
    void mergesExplanationOntoRuleEngineSkeleton() {
        CareerAnalysisAnthropicClient client = mock(CareerAnalysisAnthropicClient.class);
        var payload = MAPPER.readTree("""
                {"fitSummary":"Java 역량을 보유하고 있습니다. Spring 경험이 부족하여 보완이 필요합니다.",
                 "strengths":["Java 보유"],
                 "risks":["Spring 미보유"],
                 "strategyActions":["Spring 학습 후 재분석"],
                 "learningTaskReasons":[{"skill":"Spring","why":"공고 필수 역량"}]}""");
        when(client.request(anyString(), any(), anyString(), anyString()))
                .thenReturn(new StructuredResponse(payload, new CareerAnalysisAiUsage("claude-haiku", 100, 50, 150, false)));

        FitAnalysisAiResult result = service(client).generate(command);

        // 판단값은 전부 규칙엔진 skeleton 소유(OSS 경로와 동일 계약).
        assertThat(result.fitScore()).isEqualTo(skeleton.fitScore());
        assertThat(result.matchedSkills()).isEqualTo(skeleton.matchedSkills());
        assertThat(result.missingSkills()).isEqualTo(skeleton.missingSkills());
        assertThat(result.applyDecision().decision()).isEqualTo(skeleton.applyDecision().decision());
        assertThat(result.strategy()).isEqualTo("Java 역량을 보유하고 있습니다. Spring 경험이 부족하여 보완이 필요합니다.");
        assertThat(result.usage().model()).isEqualTo("claude-haiku");
        // 설명 전용 시스템 프롬프트(FIT_EXPLAIN) 사용 확인.
        verify(client).request(anyString(), any(), contains("점수나 판단을 새로 만들거나 바꾸지 않는다"), anyString());
    }

    @Test
    void groundingViolationThrowsSoDispatcherFallsBack() {
        // 부족 역량(Spring)을 보유로 서술 → 예외 → 상위 FallbackFitAnalysisAiService 가 OpenAI 단계로 폴백.
        CareerAnalysisAnthropicClient client = mock(CareerAnalysisAnthropicClient.class);
        var payload = MAPPER.readTree("""
                {"fitSummary":"Spring 경험을 보유하고 있습니다.",
                 "strengths":[], "risks":[], "strategyActions":[], "learningTaskReasons":[]}""");
        when(client.request(anyString(), any(), anyString(), anyString()))
                .thenReturn(new StructuredResponse(payload, new CareerAnalysisAiUsage("claude-haiku", 10, 10, 20, false)));

        assertThatThrownBy(() -> service(client).generate(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("grounding");
    }

    @Test
    void blankFitSummaryThrows() {
        CareerAnalysisAnthropicClient client = mock(CareerAnalysisAnthropicClient.class);
        var payload = MAPPER.readTree("""
                {"fitSummary":"", "strengths":[], "risks":[], "strategyActions":[], "learningTaskReasons":[]}""");
        when(client.request(anyString(), any(), anyString(), anyString()))
                .thenReturn(new StructuredResponse(payload, new CareerAnalysisAiUsage("claude-haiku", 10, 10, 20, false)));

        assertThatThrownBy(() -> service(client).generate(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("fitSummary");
    }
}
