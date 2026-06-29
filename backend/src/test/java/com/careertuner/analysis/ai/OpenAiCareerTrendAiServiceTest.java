package com.careertuner.analysis.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.careertuner.analysis.ai.provider.CareerAnalysisOpenAiClient;
import tools.jackson.databind.ObjectMapper;

class OpenAiCareerTrendAiServiceTest {

    @Test
    void fallsBackAndMarksRunRetryableWhenOpenAiFails() throws Exception {
        CareerAnalysisOpenAiClient client = mock(CareerAnalysisOpenAiClient.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(client.configured()).thenReturn(true);
        when(client.request(anyString(), any(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("upstream failure"));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        OpenAiCareerTrendAiService service = new OpenAiCareerTrendAiService(
                client,
                new MockCareerTrendAiService(),
                new CareerTrendStructuredMapper(objectMapper));

        CareerTrendAiResult result = service.generate(new CareerTrendAiCommand(null, null, null, null, null, null));

        assertThat(result.status()).isEqualTo("FALLBACK");
        assertThat(result.usage().model()).isEqualTo("mock-fallback");
        assertThat(result.retryable()).isTrue();
    }
}
