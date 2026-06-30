package com.careertuner.fitanalysis.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.careertuner.analysis.ai.provider.CareerAnalysisAiUsage;
import com.careertuner.analysis.ai.provider.CareerAnalysisOpenAiClient;
import com.careertuner.analysis.ai.provider.CareerAnalysisOpenAiClient.StructuredResponse;

class OpenAiFitAnalysisAiServiceTest {

    private final FitAnalysisAiCommand command = new FitAnalysisAiCommand(
            "테스트기업",
            "백엔드 개발자",
            List.of("Java", "Spring"),
            List.of("AWS"),
            "REST API 개발",
            List.of("Java"),
            List.of("정보처리기사"),
            "백엔드 개발자");

    @Test
    void usesDeterministicMockWhenApiKeyIsMissing() {
        CareerAnalysisOpenAiClient client = mock(CareerAnalysisOpenAiClient.class);
        when(client.configured()).thenReturn(false);
        OpenAiFitAnalysisAiService service = new OpenAiFitAnalysisAiService(client, new MockFitAnalysisAiService(), new FitAnalysisStructuredMapper());

        FitAnalysisAiResult result = service.generate(command);

        assertThat(result.usage().mock()).isTrue();
        assertThat(result.usage().model()).isEqualTo("mock");
        assertThat(result.fitScore()).isBetween(0, 100);
        // 비교 매트릭스는 필수+우대 조건 수만큼, 판정은 MET/PARTIAL/UNMET 중 하나여야 한다.
        assertThat(result.conditionMatrix()).hasSize(3);
        assertThat(result.conditionMatrix())
                .allSatisfy(row -> assertThat(row.matchStatus()).isIn("MET", "PARTIAL", "UNMET"));
        assertThat(result.applyDecision()).isNotNull();
        assertThat(result.applyDecision().decision()).isIn("APPLY", "COMPLEMENT", "HOLD");
        assertThat(result.applyDecision().reasons()).isNotEmpty();
        assertThat(result.applyDecision().actions()).isNotEmpty();
    }

    @Test
    void mapsStructuredOpenAiResponseWhenApiKeyIsConfigured() throws Exception {
        CareerAnalysisOpenAiClient client = mock(CareerAnalysisOpenAiClient.class);
        var payload = mock(tools.jackson.databind.JsonNode.class);
        var score = mock(tools.jackson.databind.JsonNode.class);
        var matched = mock(tools.jackson.databind.JsonNode.class);
        var missing = mock(tools.jackson.databind.JsonNode.class);
        var study = mock(tools.jackson.databind.JsonNode.class);
        var certificates = mock(tools.jackson.databind.JsonNode.class);
        var strategy = mock(tools.jackson.databind.JsonNode.class);

        when(client.configured()).thenReturn(true);
        when(payload.path("fitScore")).thenReturn(score);
        when(payload.path("matchedSkills")).thenReturn(matched);
        when(payload.path("missingSkills")).thenReturn(missing);
        when(payload.path("recommendedStudy")).thenReturn(study);
        when(payload.path("recommendedCertificates")).thenReturn(certificates);
        when(payload.path("strategy")).thenReturn(strategy);
        when(score.asInt(0)).thenReturn(82);
        when(matched.isArray()).thenReturn(false);
        when(missing.isArray()).thenReturn(false);
        when(study.isArray()).thenReturn(false);
        when(certificates.isArray()).thenReturn(false);
        when(strategy.asText("")).thenReturn("Java 강점을 강조하고 AWS를 보완하세요.");
        when(client.request(anyString(), any(), anyString(), anyString()))
                .thenReturn(new StructuredResponse(
                        payload,
                        new CareerAnalysisAiUsage("gpt-5", 100, 50, 150, false)));

        OpenAiFitAnalysisAiService service = new OpenAiFitAnalysisAiService(client, new MockFitAnalysisAiService(), new FitAnalysisStructuredMapper());
        FitAnalysisAiResult result = service.generate(command);

        assertThat(result.fitScore()).isEqualTo(82);
        assertThat(result.strategy()).contains("AWS");
        assertThat(result.usage().mock()).isFalse();
        assertThat(result.usage().totalTokens()).isEqualTo(150);
    }

    @Test
    void fallsBackToMockAndExposesFailureWhenOpenAiRequestFails() {
        CareerAnalysisOpenAiClient client = mock(CareerAnalysisOpenAiClient.class);
        when(client.configured()).thenReturn(true);
        when(client.request(anyString(), any(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("invalid key"));

        OpenAiFitAnalysisAiService service = new OpenAiFitAnalysisAiService(client, new MockFitAnalysisAiService(), new FitAnalysisStructuredMapper());
        FitAnalysisAiResult result = service.generate(command);

        assertThat(result.status()).isEqualTo("FALLBACK");
        assertThat(result.usage().model()).isEqualTo("mock-fallback");
        assertThat(result.errorMessage()).contains("invalid key");
        assertThat(result.retryable()).isTrue();
    }

    @Test
    void downgradesApplyDecisionWhenLlmContradictsRequiredConditions() {
        // LLM 이 필수 조건 2개 미충족 + 60점인데 APPLY 라고 답한 모순 응답 → 가드레일이 COMPLEMENT 로 강등해야 한다.
        CareerAnalysisOpenAiClient client = mock(CareerAnalysisOpenAiClient.class);
        when(client.configured()).thenReturn(true);
        var payload = new tools.jackson.databind.ObjectMapper().readTree("""
                {
                  "fitScore": 60,
                  "conditionMatrix": [
                    {"condition": "Java", "conditionType": "REQUIRED", "matchStatus": "UNMET", "evidence": ""},
                    {"condition": "Spring", "conditionType": "REQUIRED", "matchStatus": "UNMET", "evidence": ""},
                    {"condition": "AWS", "conditionType": "PREFERRED", "matchStatus": "MET", "evidence": ""}
                  ],
                  "applyDecision": {"decision": "APPLY", "reasons": ["LLM 과대평가"], "actions": ["바로 지원"]}
                }
                """);
        when(client.request(anyString(), any(), anyString(), anyString()))
                .thenReturn(new StructuredResponse(payload, new CareerAnalysisAiUsage("gpt-test", 50, 50, 100, false)));

        OpenAiFitAnalysisAiService service = new OpenAiFitAnalysisAiService(client, new MockFitAnalysisAiService(), new FitAnalysisStructuredMapper());
        FitAnalysisAiResult result = service.generate(command);

        assertThat(result.applyDecision().decision()).isEqualTo("COMPLEMENT");
        assertThat(result.applyDecision().reasons()).anyMatch(reason -> reason.contains("자동 보정"));
        // AI 가 제시한 기존 사유/행동은 보존된다.
        assertThat(result.applyDecision().reasons()).contains("LLM 과대평가");
        assertThat(result.applyDecision().actions()).contains("바로 지원");
    }

    @Test
    void keepsApplyDecisionWhenRuleAllowsIt() {
        // 78점 + 필수 미충족 0개 → 강화된 규칙(필수 0개)에서도 APPLY 허용 구간이므로 가드가 개입하지 않는다.
        CareerAnalysisOpenAiClient client = mock(CareerAnalysisOpenAiClient.class);
        when(client.configured()).thenReturn(true);
        var payload = new tools.jackson.databind.ObjectMapper().readTree("""
                {
                  "fitScore": 78,
                  "conditionMatrix": [
                    {"condition": "Java", "conditionType": "REQUIRED", "matchStatus": "MET", "evidence": ""},
                    {"condition": "Spring", "conditionType": "REQUIRED", "matchStatus": "MET", "evidence": ""},
                    {"condition": "AWS", "conditionType": "PREFERRED", "matchStatus": "UNMET", "evidence": ""}
                  ],
                  "applyDecision": {"decision": "APPLY", "reasons": ["핵심 역량 충족"], "actions": ["지원"]}
                }
                """);
        when(client.request(anyString(), any(), anyString(), anyString()))
                .thenReturn(new StructuredResponse(payload, new CareerAnalysisAiUsage("gpt-test", 50, 50, 100, false)));

        OpenAiFitAnalysisAiService service = new OpenAiFitAnalysisAiService(client, new MockFitAnalysisAiService(), new FitAnalysisStructuredMapper());
        FitAnalysisAiResult result = service.generate(command);

        assertThat(result.applyDecision().decision()).isEqualTo("APPLY");
        assertThat(result.applyDecision().reasons()).noneMatch(reason -> reason.contains("자동 보정"));
    }

    @Test
    void downgradesApplyWhenSingleRequiredUnmet() {
        // 강화된 규칙: 필수 미충족이 1개라도 있으면 APPLY 불가 → COMPLEMENT 로 강등.
        CareerAnalysisOpenAiClient client = mock(CareerAnalysisOpenAiClient.class);
        when(client.configured()).thenReturn(true);
        var payload = new tools.jackson.databind.ObjectMapper().readTree("""
                {
                  "fitScore": 78,
                  "conditionMatrix": [
                    {"condition": "Java", "conditionType": "REQUIRED", "matchStatus": "MET", "evidence": ""},
                    {"condition": "Spring", "conditionType": "REQUIRED", "matchStatus": "UNMET", "evidence": ""}
                  ],
                  "applyDecision": {"decision": "APPLY", "reasons": ["핵심 역량 충족"], "actions": ["지원"]}
                }
                """);
        when(client.request(anyString(), any(), anyString(), anyString()))
                .thenReturn(new StructuredResponse(payload, new CareerAnalysisAiUsage("gpt-test", 50, 50, 100, false)));

        OpenAiFitAnalysisAiService service = new OpenAiFitAnalysisAiService(client, new MockFitAnalysisAiService(), new FitAnalysisStructuredMapper());
        FitAnalysisAiResult result = service.generate(command);

        assertThat(result.applyDecision().decision()).isEqualTo("COMPLEMENT");
        assertThat(result.applyDecision().reasons()).anyMatch(reason -> reason.contains("자동 보정"));
    }
}
