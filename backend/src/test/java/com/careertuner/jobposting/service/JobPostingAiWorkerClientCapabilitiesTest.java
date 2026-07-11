package com.careertuner.jobposting.service;

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

import com.careertuner.jobposting.service.JobPostingAiWorkerClient.WorkerCapabilities;

import tools.jackson.databind.ObjectMapper;

/** 워커 {@code /capabilities} 파싱·방어(status/미지 엔진)·안전 degrade·TTL 캐시 검증(mock HttpClient). */
class JobPostingAiWorkerClientCapabilitiesTest {

    private final HttpClient httpClient = mock(HttpClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private JobPostingAiWorkerClient client(boolean enabled) {
        JobPostingAiWorkerProperties props = new JobPostingAiWorkerProperties();
        props.setEnabled(enabled);
        return new JobPostingAiWorkerClient(props, objectMapper, httpClient);
    }

    @SuppressWarnings("unchecked")
    private void stubResponse(int status, String body) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        doReturn(response).when(httpClient).send(any(), any());
    }

    @Test
    void okWithKnownEnginesIsAvailableAndReady() throws Exception {
        stubResponse(200, "{\"status\":\"ok\",\"readyEngines\":[\"paddleocr\",\"ppstructure\"]}");
        WorkerCapabilities caps = client(true).capabilities();
        assertThat(caps.available()).isTrue();
        assertThat(caps.readyEngines()).containsExactly("paddleocr", "ppstructure");
        assertThat(caps.anyEngineReady()).isTrue();
    }

    @Test
    void unknownEnginesAreFilteredSoNotReady() throws Exception {
        stubResponse(200, "{\"status\":\"ok\",\"readyEngines\":[\"tesseract\",\"foo\"]}");
        WorkerCapabilities caps = client(true).capabilities();
        assertThat(caps.available()).isTrue();
        assertThat(caps.readyEngines()).isEmpty();
        assertThat(caps.anyEngineReady()).isFalse();
    }

    @Test
    void statusNotOkIsUnavailable() throws Exception {
        stubResponse(200, "{\"status\":\"degraded\",\"readyEngines\":[\"paddleocr\"]}");
        assertThat(client(true).capabilities().available()).isFalse();
    }

    @Test
    void non2xxIsUnavailable() throws Exception {
        stubResponse(503, "");
        assertThat(client(true).capabilities().available()).isFalse();
    }

    @Test
    void disabledWorkerShortCircuitsWithoutHttpCall() throws Exception {
        WorkerCapabilities caps = client(false).capabilities();
        assertThat(caps.available()).isFalse();
        verify(httpClient, times(0)).send(any(), any());
    }

    @Test
    void resultIsCachedWithinTtl() throws Exception {
        stubResponse(200, "{\"status\":\"ok\",\"readyEngines\":[\"paddleocr\"]}");
        JobPostingAiWorkerClient client = client(true);
        client.capabilities();
        client.capabilities();
        verify(httpClient, times(1)).send(any(), any());
    }

    @Test
    void failureResultIsAlsoCachedWithinTtl() throws Exception {
        stubResponse(503, "");
        JobPostingAiWorkerClient client = client(true);
        assertThat(client.capabilities().available()).isFalse();
        assertThat(client.capabilities().available()).isFalse();
        // 실패 결과도 TTL 동안 캐시 → 반복 호출이 원격 왕복(timeout)을 재발생시키지 않는다.
        verify(httpClient, times(1)).send(any(), any());
    }
}
