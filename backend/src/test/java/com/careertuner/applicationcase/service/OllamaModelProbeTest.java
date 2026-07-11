package com.careertuner.applicationcase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

/** Ollama {@code /api/tags} 기반 모델 존재 확인·안전 degrade·TTL 캐시 검증(mock HttpClient). */
class OllamaModelProbeTest {

    private final HttpClient httpClient = mock(HttpClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private OllamaModelProbe probe(String model) {
        BAnalysisProperties properties = new BAnalysisProperties();
        properties.getLocalLlm().setModel(model);
        return new OllamaModelProbe(properties, objectMapper, httpClient);
    }

    @SuppressWarnings("unchecked")
    private void stubResponse(int status, String body) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        doReturn(response).when(httpClient).send(any(), any());
    }

    @Test
    void modelPresentIncludingTagVariantReturnsTrue() throws Exception {
        stubResponse(200, "{\"models\":[{\"name\":\"careertuner-b-jobposting-r1:latest\"}]}");
        assertThat(probe("careertuner-b-jobposting-r1").modelAvailable()).isTrue();
    }

    @Test
    void modelAbsentReturnsFalse() throws Exception {
        stubResponse(200, "{\"models\":[{\"name\":\"llama3:latest\"}]}");
        assertThat(probe("careertuner-b-jobposting-r1").modelAvailable()).isFalse();
    }

    @Test
    void non2xxReturnsFalse() throws Exception {
        stubResponse(500, "");
        assertThat(probe("r1").modelAvailable()).isFalse();
    }

    @Test
    void blankModelReturnsFalseWithoutHttpCall() throws Exception {
        assertThat(probe("").modelAvailable()).isFalse();
        verify(httpClient, times(0)).send(any(), any());
    }

    @Test
    void resultIsCachedWithinTtl() throws Exception {
        stubResponse(200, "{\"models\":[{\"name\":\"r1\"}]}");
        OllamaModelProbe probe = probe("r1");
        probe.modelAvailable();
        probe.modelAvailable();
        verify(httpClient, times(1)).send(any(), any());
    }

    @Test
    void falseResultIsAlsoCachedWithinTtl() throws Exception {
        stubResponse(200, "{\"models\":[{\"name\":\"other:latest\"}]}");
        OllamaModelProbe probe = probe("careertuner-b-jobposting-r1");
        assertThat(probe.modelAvailable()).isFalse();
        assertThat(probe.modelAvailable()).isFalse();
        // 실패(미탑재) 결과도 TTL 동안 캐시 → 반복 호출이 원격 왕복을 재발생시키지 않는다.
        verify(httpClient, times(1)).send(any(), any());
    }
}
