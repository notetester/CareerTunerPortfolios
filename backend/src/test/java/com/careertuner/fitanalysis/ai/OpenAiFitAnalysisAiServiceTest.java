package com.careertuner.fitanalysis.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.careertuner.analysis.ai.provider.CareerAnalysisAiUsage;
import com.careertuner.analysis.ai.provider.CareerAnalysisOpenAiClient;
import com.careertuner.analysis.ai.provider.CareerAnalysisOpenAiClient.StructuredResponse;

import tools.jackson.databind.ObjectMapper;

/**
 * OpenAI 단계의 뉴로-심볼릭 계약 검증 — 판단값은 규칙엔진 skeleton 소유, LLM 은 설명만.
 */
class OpenAiFitAnalysisAiServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // 규칙엔진 결정론 값: required[Java,Spring]∩profile[Java] → matched=[Java], missing=[Spring, AWS]
    private final FitAnalysisAiCommand command = new FitAnalysisAiCommand(
            "테스트기업",
            "백엔드 개발자",
            List.of("Java", "Spring"),
            List.of("AWS"),
            "REST API 개발",
            List.of("Java"),
            List.of("정보처리기사"),
            "백엔드 개발자");

    private final FitAnalysisAiResult skeleton = new MockFitAnalysisAiService().generate(command);

    private OpenAiFitAnalysisAiService service(CareerAnalysisOpenAiClient client) {
        return new OpenAiFitAnalysisAiService(client, new MockFitAnalysisAiService(), new FitExplainAssembler());
    }

    @Test
    void usesDeterministicMockWhenApiKeyIsMissing() {
        CareerAnalysisOpenAiClient client = mock(CareerAnalysisOpenAiClient.class);
        when(client.configured()).thenReturn(false);

        FitAnalysisAiResult result = service(client).generate(command);

        assertThat(result.usage().mock()).isTrue();
        assertThat(result.fitScore()).isBetween(0, 100);
        assertThat(result.conditionMatrix()).hasSize(3);
        assertThat(result.applyDecision().decision()).isIn("APPLY", "COMPLEMENT", "HOLD");
    }

    @Test
    void mergesExplanationOntoRuleEngineSkeleton() {
        CareerAnalysisOpenAiClient client = mock(CareerAnalysisOpenAiClient.class);
        when(client.configured()).thenReturn(true);
        var payload = MAPPER.readTree("""
                {"fitSummary":"Java 역량을 보유하고 있습니다. Spring 경험이 부족하여 보완이 필요합니다.",
                 "strengths":["Java 보유"],
                 "risks":["Spring 미보유"],
                 "strategyActions":["Spring 학습 후 재분석"],
                 "learningTaskReasons":[{"skill":"Spring","why":"공고 필수 역량이라 우선 보완"}]}""");
        when(client.request(anyString(), any(), anyString(), anyString()))
                .thenReturn(new StructuredResponse(payload, new CareerAnalysisAiUsage("gpt-5", 100, 50, 150, false)));

        FitAnalysisAiResult result = service(client).generate(command);

        // 판단값은 전부 규칙엔진 skeleton 과 동일(LLM 소유 아님).
        assertThat(result.fitScore()).isEqualTo(skeleton.fitScore());
        assertThat(result.matchedSkills()).isEqualTo(skeleton.matchedSkills());
        assertThat(result.missingSkills()).isEqualTo(skeleton.missingSkills());
        assertThat(result.applyDecision().decision()).isEqualTo(skeleton.applyDecision().decision());
        assertThat(result.conditionMatrix()).isEqualTo(skeleton.conditionMatrix());
        // 설명 텍스트는 LLM 출력.
        assertThat(result.strategy()).isEqualTo("Java 역량을 보유하고 있습니다. Spring 경험이 부족하여 보완이 필요합니다.");
        assertThat(result.strategyActions()).containsExactly("Spring 학습 후 재분석");
        assertThat(result.gapRecommendations())
                .filteredOn(gap -> "Spring".equals(gap.skill()))
                .allSatisfy(gap -> assertThat(gap.reason()).isEqualTo("공고 필수 역량이라 우선 보완"));
        assertThat(result.usage().totalTokens()).isEqualTo(150);
        assertThat(result.status()).isEqualTo("SUCCESS");
    }

    @Test
    void usesExplainOnlyContractAndIgnoresJudgmentFieldsInPayload() {
        // LLM 이 판단값 유사 필드를 몰래 반환해도 조립기는 읽지 않는다(구조적 제거).
        CareerAnalysisOpenAiClient client = mock(CareerAnalysisOpenAiClient.class);
        when(client.configured()).thenReturn(true);
        var payload = MAPPER.readTree("""
                {"fitSummary":"정상 설명입니다.",
                 "strengths":[], "risks":[], "strategyActions":[], "learningTaskReasons":[],
                 "fitScore": 99,
                 "matchedSkills": ["Spark"],
                 "applyDecision": {"decision": "APPLY"}}""");
        when(client.request(anyString(), any(), anyString(), anyString()))
                .thenReturn(new StructuredResponse(payload, new CareerAnalysisAiUsage("gpt-5", 10, 10, 20, false)));

        FitAnalysisAiResult result = service(client).generate(command);

        assertThat(result.fitScore()).isEqualTo(skeleton.fitScore());
        assertThat(result.matchedSkills()).isEqualTo(skeleton.matchedSkills()).doesNotContain("Spark");
        assertThat(result.applyDecision().decision()).isEqualTo(skeleton.applyDecision().decision());
        // 시스템 프롬프트도 설명 전용 계약(FIT_EXPLAIN)이어야 한다.
        org.mockito.Mockito.verify(client).request(anyString(), any(),
                contains("점수나 판단을 새로 만들거나 바꾸지 않는다"), anyString());
    }

    @Test
    void fallsBackToSkeletonAndExposesFailureWhenOpenAiRequestFails() {
        CareerAnalysisOpenAiClient client = mock(CareerAnalysisOpenAiClient.class);
        when(client.configured()).thenReturn(true);
        when(client.request(anyString(), any(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("invalid key"));

        FitAnalysisAiResult result = service(client).generate(command);

        assertThat(result.status()).isEqualTo("FALLBACK");
        assertThat(result.usage().model()).isEqualTo("mock-fallback");
        assertThat(result.errorMessage()).contains("invalid key");
        assertThat(result.retryable()).isTrue();
        // FALLBACK 도 판단값은 규칙엔진 값 그대로.
        assertThat(result.fitScore()).isEqualTo(skeleton.fitScore());
        assertThat(result.applyDecision().decision()).isEqualTo(skeleton.applyDecision().decision());
    }

    @Test
    void groundingViolationFallsBackToDeterministicResult() {
        // 부족 역량(Spring)을 보유로 서술 → E1 동일 휴리스틱 검출 → FALLBACK(안전망), 판단값 불변.
        CareerAnalysisOpenAiClient client = mock(CareerAnalysisOpenAiClient.class);
        when(client.configured()).thenReturn(true);
        var payload = MAPPER.readTree("""
                {"fitSummary":"Spring 경험을 보유하고 있습니다.",
                 "strengths":[], "risks":[], "strategyActions":[], "learningTaskReasons":[]}""");
        when(client.request(anyString(), any(), anyString(), anyString()))
                .thenReturn(new StructuredResponse(payload, new CareerAnalysisAiUsage("gpt-5", 10, 10, 20, false)));

        FitAnalysisAiResult result = service(client).generate(command);

        assertThat(result.status()).isEqualTo("FALLBACK");
        assertThat(result.errorMessage()).contains("grounding");
        assertThat(result.fitScore()).isEqualTo(skeleton.fitScore());
    }

    @Test
    void blankFitSummaryFallsBack() {
        CareerAnalysisOpenAiClient client = mock(CareerAnalysisOpenAiClient.class);
        when(client.configured()).thenReturn(true);
        var payload = MAPPER.readTree("""
                {"fitSummary":"", "strengths":[], "risks":[], "strategyActions":[], "learningTaskReasons":[]}""");
        when(client.request(anyString(), any(), anyString(), anyString()))
                .thenReturn(new StructuredResponse(payload, new CareerAnalysisAiUsage("gpt-5", 10, 10, 20, false)));

        FitAnalysisAiResult result = service(client).generate(command);

        assertThat(result.status()).isEqualTo("FALLBACK");
        assertThat(result.fitScore()).isEqualTo(skeleton.fitScore());
    }
}
