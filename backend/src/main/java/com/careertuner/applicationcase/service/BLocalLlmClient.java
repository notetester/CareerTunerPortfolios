package com.careertuner.applicationcase.service;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class BLocalLlmClient {

    private final BAnalysisProperties properties;

    public BLocalLlmClient(BAnalysisProperties properties) {
        this.properties = properties;
    }

    public String chat(String systemPrompt, String userPrompt, Map<String, Object> jsonSchema) {
        return chat(null, systemPrompt, userPrompt, jsonSchema);
    }

    public String chat(String modelOverride, String systemPrompt, String userPrompt, Map<String, Object> jsonSchema) {
        BAnalysisProperties.LocalLlm local = properties.getLocalLlm();
        String model = (modelOverride != null && !modelOverride.isBlank()) ? modelOverride : local.getModel();
        var jdkClient = HttpClient.newBuilder()
                .connectTimeout(local.getConnectTimeout())
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(jdkClient);
        requestFactory.setReadTimeout(local.getReadTimeout());

        RestClient restClient = RestClient.builder()
                .baseUrl(local.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
        OllamaChatRequest request = new OllamaChatRequest(
                model,
                false,
                false,
                Map.of("temperature", 0, "num_ctx", 8192, "num_predict", local.getNumPredict()),
                jsonSchema,
                List.of(
                        new Message("system", systemPrompt),
                        new Message("user", userPrompt)));

        log.debug("B analysis local LLM request: model={}, promptLength={}", local.getModel(), userPrompt.length());
        OllamaChatResponse response = restClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(OllamaChatResponse.class);
        if (response == null || response.message() == null || response.message().content() == null) {
            throw new IllegalStateException("Ollama response is empty.");
        }
        return response.message().content();
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
