package com.careertuner.fitanalysis.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.careertuner.analysis.ai.provider.CareerAnalysisAiProviderProperties;
import com.careertuner.analysis.ai.provider.CareerAnalysisOssClient;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * C 자체모델(OSS) 적합도 분석의 뉴로-심볼릭 조립 검증.
 * 점수/판단/매칭은 규칙엔진(Mock) 값을 유지하고, 설명 텍스트만 자체모델 출력으로 교체되는지 확인한다.
 */
class OssFitAnalysisAiServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // 규칙엔진 결정론 값: required[Java,Spring]∩profile[Java] → fitScore 45, 필수 미충족 1개(Spring) → HOLD
    private final FitAnalysisAiCommand command = new FitAnalysisAiCommand(
            "테스트기업", "백엔드 개발자",
            List.of("Java", "Spring"), List.of("AWS"), "REST API 개발",
            List.of("Java"), List.of("정보처리기사"), "백엔드 개발자");

    private OssFitAnalysisAiService service(CareerAnalysisOssClient client) {
        return new OssFitAnalysisAiService(client, new MockFitAnalysisAiService(),
                new CareerAnalysisAiProviderProperties());
    }

    @Test
    void mergesRuleEngineSkeletonWithModelExplanation() {
        CareerAnalysisOssClient client = mock(CareerAnalysisOssClient.class);
        JsonNode explain = MAPPER.readTree("""
                {"fitSummary":"필수 일부 보유, 보완 권장",
                 "strengths":["Java 보유"],
                 "risks":["Spring 미보유"],
                 "strategyActions":["Spring 학습 후 재분석"],
                 "learningTaskReasons":[{"skill":"Spring","why":"필수 역량"}]}""");
        when(client.requestFitExplain(anyString(), anyString())).thenReturn(explain);

        FitAnalysisAiResult result = service(client).generate(command);

        // 설명 텍스트는 자체모델 출력
        assertThat(result.strategy()).isEqualTo("필수 일부 보유, 보완 권장");
        assertThat(result.strategyActions()).containsExactly("Spring 학습 후 재분석");
        // 점수/판단/매칭은 규칙엔진(서버 권위)
        assertThat(result.fitScore()).isEqualTo(45);
        assertThat(result.applyDecision().decision()).isEqualTo("HOLD");
        assertThat(result.matchedSkills()).contains("Java");
        assertThat(result.usage().model()).isEqualTo("careertuner-c-career-strategy-3b");
        assertThat(result.usage().mock()).isFalse();
        assertThat(result.status()).isEqualTo("SUCCESS");
        // gap reason 은 자체모델 learningTaskReasons 로 교체(매칭된 Spring)
        assertThat(result.gapRecommendations())
                .anySatisfy(gap -> {
                    assertThat(gap.skill()).isEqualTo("Spring");
                    assertThat(gap.reason()).isEqualTo("필수 역량");
                });
    }

    @Test
    void ignoresForbiddenScoreKeysFromModel() {
        CareerAnalysisOssClient client = mock(CareerAnalysisOssClient.class);
        // 모델이 금지키(fitScore/score/applyDecision/decision)를 내도 결과엔 반영되지 않아야 한다(구조적 제거).
        JsonNode explain = MAPPER.readTree("""
                {"fitScore":999,"score":999,"applyDecision":"APPLY","decision":"APPLY",
                 "fitSummary":"설명","strengths":[],"risks":[],"strategyActions":[],"learningTaskReasons":[]}""");
        when(client.requestFitExplain(anyString(), anyString())).thenReturn(explain);

        FitAnalysisAiResult result = service(client).generate(command);

        assertThat(result.fitScore()).isEqualTo(45);                    // 규칙엔진 값(999 아님)
        assertThat(result.applyDecision().decision()).isEqualTo("HOLD"); // 규칙엔진 판단(APPLY 아님)
    }

    @Test
    void throwsWhenModelSummaryBlank() {
        CareerAnalysisOssClient client = mock(CareerAnalysisOssClient.class);
        JsonNode explain = MAPPER.readTree("""
                {"fitSummary":"   ","strengths":[],"risks":[],"strategyActions":[],"learningTaskReasons":[]}""");
        when(client.requestFitExplain(anyString(), anyString())).thenReturn(explain);

        assertThatThrownBy(() -> service(client).generate(command))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void propagatesWhenModelClientFails() {
        CareerAnalysisOssClient client = mock(CareerAnalysisOssClient.class);
        when(client.requestFitExplain(anyString(), anyString()))
                .thenThrow(new BusinessException(ErrorCode.INTERNAL_ERROR, "자체모델 응답이 JSON 형식이 아닙니다."));

        assertThatThrownBy(() -> service(client).generate(command))
                .isInstanceOf(BusinessException.class);
    }
}
