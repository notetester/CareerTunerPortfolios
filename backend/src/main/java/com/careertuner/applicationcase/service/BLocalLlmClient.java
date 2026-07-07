package com.careertuner.applicationcase.service;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.careertuner.ai.common.gpu.GpuPermitGate;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class BLocalLlmClient {

    private final BAnalysisProperties properties;
    private final GpuPermitGate gpuPermitGate;

    public BLocalLlmClient(BAnalysisProperties properties, GpuPermitGate gpuPermitGate) {
        this.properties = properties;
        this.gpuPermitGate = gpuPermitGate;
    }

    public String chat(String systemPrompt, String userPrompt, Map<String, Object> jsonSchema) {
        BAnalysisProperties.LocalLlm local = properties.getLocalLlm();
        var jdkClient = HttpClient.newBuilder()
                .connectTimeout(local.getConnectTimeout())
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(jdkClient);
        // 예산 ON 이면 read timeout 을 예산으로 절삭(단일 시도 대비)
        requestFactory.setReadTimeout(capReadTimeout(local.getReadTimeout(), local.getTotalTimeBudget()));

        RestClient restClient = RestClient.builder()
                .baseUrl(local.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
        OllamaChatRequest request = new OllamaChatRequest(
                local.getModel(),
                false,
                false,
                Map.of("temperature", 0, "num_ctx", local.getNumCtx(), "num_predict", local.getNumPredict()),
                jsonSchema,
                List.of(
                        new Message("system", systemPrompt),
                        new Message("user", userPrompt)));

        log.debug("B analysis local LLM request: model={}, promptLength={}", local.getModel(), userPrompt.length());
        OllamaChatResponse response;
        try (GpuPermitGate.GpuPermit permit = gpuPermitGate.acquire("b-analysis")) {
            response = restClient.post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(OllamaChatResponse.class);
        }
        if (response == null || response.message() == null || response.message().content() == null) {
            throw new IllegalStateException("Ollama response is empty.");
        }
        return response.message().content();
    }

    /** 총 시간예산이 양수(ON)면 read timeout 을 예산 이하로 절삭한다. 0/음수/null 은 무제한(OFF, 기존 동작). */
    private static Duration capReadTimeout(Duration readTimeout, Duration totalTimeBudget) {
        if (totalTimeBudget == null || totalTimeBudget.isZero() || totalTimeBudget.isNegative()) {
            return readTimeout;
        }
        return readTimeout.compareTo(totalTimeBudget) <= 0 ? readTimeout : totalTimeBudget;
    }

    public record OllamaChatRequest(
            String model,
            boolean stream,
            boolean think,
            Map<String, Object> options,
            Map<String, Object> format,
            List<Message> messages
    ) {
    }

    public record Message(String role, String content) {
    }

    public record OllamaChatResponse(MessageContent message) {
    }

    public record MessageContent(String content) {
    }
}
