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
        OpenAiFitAnalysisAiService service = new OpenAiFitAnalysisAiService(client, new MockFitAnalysisAiService());

        FitAnalysisAiResult result = service.generate(command);

        assertThat(result.usage().mock()).isTrue();
        assertThat(result.usage().model()).isEqualTo("mock");
        assertThat(result.fitScore()).isBetween(0, 100);
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

        OpenAiFitAnalysisAiService service = new OpenAiFitAnalysisAiService(client, new MockFitAnalysisAiService());
        FitAnalysisAiResult result = service.generate(command);

        assertThat(result.fitScore()).isEqualTo(82);
        assertThat(result.strategy()).contains("AWS");
        assertThat(result.usage().mock()).isFalse();
        assertThat(result.usage().totalTokens()).isEqualTo(150);
    }
}
