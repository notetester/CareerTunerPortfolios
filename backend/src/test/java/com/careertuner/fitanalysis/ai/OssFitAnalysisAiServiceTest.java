package com.careertuner.fitanalysis.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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

    // ── grounding guard: 부족 역량을 '보유'로 서술하면 위반 ──
    @Test
    void groundingViolation_flagsMissingSkillDescribedAsPossessed() {
        // command: required[Java,Spring]∩profile[Java] → missing = Spring(필수), AWS(우대)
        List<String> missing = List.of("Spring", "AWS");
        assertThat(OssFitAnalysisAiService.groundingViolation(missing, "요약입니다.", List.of("Spring 보유로 즉시 투입 가능")))
                .contains("Spring");
        assertThat(OssFitAnalysisAiService.groundingViolation(missing, "Spring은 강점입니다.", List.of()))
                .contains("Spring");
    }

    @Test
    void groundingViolation_noFalsePositiveOnNegationOrLackOrMatchedSkill() {
        List<String> missing = List.of("Spring", "AWS");
        // 결핍 문맥(부족) → 위반 아님
        assertThat(OssFitAnalysisAiService.groundingViolation(missing, "Spring 경험이 부족합니다.", List.of())).isNull();
        // 부정(보유하지 않) → 위반 아님
        assertThat(OssFitAnalysisAiService.groundingViolation(missing, "Spring을 보유하지 않았습니다.", List.of())).isNull();
        // '즉시 지원하기보다는' → 스킬 보유 서술 아님 → 위반 아님
        assertThat(OssFitAnalysisAiService.groundingViolation(missing, "즉시 지원하기보다는 역량을 보완하세요.", List.of())).isNull();
        // 매칭 역량(Java)을 보유라 해도 missing 이 아니므로 위반 아님
        assertThat(OssFitAnalysisAiService.groundingViolation(missing, "Java를 보유하고 있습니다.", List.of("Java 보유"))).isNull();
    }

    @Test
    void retriesOnGroundingViolationThenFallsBack() {
        CareerAnalysisOssClient client = mock(CareerAnalysisOssClient.class);
        JsonNode violating = MAPPER.readTree("""
                {"fitSummary":"설명","strengths":["Spring 보유로 즉시 투입 가능"],"risks":[],"strategyActions":[],"learningTaskReasons":[]}""");
        when(client.requestFitExplain(anyString(), anyString())).thenReturn(violating);

        assertThatThrownBy(() -> service(client).generate(command))
                .isInstanceOf(BusinessException.class);
        // groundingRetries 기본 1 → 총 2회 호출 후 폴백 유도
        verify(client, times(2)).requestFitExplain(anyString(), anyString());
    }

    @Test
    void heldCertificateNotFlaggedEvenIfRuleEngineMissing() {
        // 정보처리기사: required 이면서 profileCertificates 로 보유 → 규칙엔진 missing 에 남지만
        // 모델이 '보유'라 말해도 사실이므로 grounding 오탐 아님(라이브 회귀 case 2 100% 폴백 원인 수정).
        FitAnalysisAiCommand certCmd = new FitAnalysisAiCommand(
                "공단", "전산직", List.of("Java", "정보처리기사"), List.of(), "전산 개발",
                List.of("Java"), List.of("정보처리기사"), "전산직");
        CareerAnalysisOssClient client = mock(CareerAnalysisOssClient.class);
        JsonNode explain = MAPPER.readTree("""
                {"fitSummary":"정보처리기사 자격증을 보유해 기본 역량을 갖춤","strengths":["정보처리기사 보유"],
                 "risks":[],"strategyActions":[],"learningTaskReasons":[]}""");
        when(client.requestFitExplain(anyString(), anyString())).thenReturn(explain);

        FitAnalysisAiResult result = service(client).generate(certCmd);

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.strategy()).contains("정보처리기사");
        verify(client, times(1)).requestFitExplain(anyString(), anyString());  // 재호출 없음(오탐 아님)
    }

    @Test
    void recoversWhenRetryReturnsGroundedExplanation() {
        CareerAnalysisOssClient client = mock(CareerAnalysisOssClient.class);
        JsonNode violating = MAPPER.readTree("""
                {"fitSummary":"설명","strengths":["Spring 보유"],"risks":[],"strategyActions":[],"learningTaskReasons":[]}""");
        JsonNode clean = MAPPER.readTree("""
                {"fitSummary":"Spring 부족, 보완 권장","strengths":["Java 보유"],"risks":["Spring 미보유"],
                 "strategyActions":["Spring 학습"],"learningTaskReasons":[]}""");
        when(client.requestFitExplain(anyString(), anyString())).thenReturn(violating).thenReturn(clean);

        FitAnalysisAiResult result = service(client).generate(command);

        assertThat(result.strategy()).isEqualTo("Spring 부족, 보완 권장");
        verify(client, times(2)).requestFitExplain(anyString(), anyString());
    }
}
