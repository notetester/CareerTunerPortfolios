package com.careertuner.support.chatbot;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.careertuner.community.moderation.config.OllamaProperties;

/**
 * Ollama /api/embed 호출 클라이언트.
 * bge-m3 모델로 텍스트를 1024차원 벡터로 임베딩한다.
 * 기존 OllamaClient(chat 전용)를 수정하지 않고 별도로 구현.
 */
@Component
public class OllamaEmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaEmbeddingClient.class);

    private final RestClient restClient;
    private final ChatbotProperties chatbotProps;

    public OllamaEmbeddingClient(OllamaProperties ollamaProps, ChatbotProperties chatbotProps) {
        this.chatbotProps = chatbotProps;

        var jdkClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(jdkClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(60));

        this.restClient = RestClient.builder()
                .baseUrl(ollamaProps.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * 텍스트를 bge-m3로 임베딩한다.
     *
     * @param text 임베딩할 텍스트
     * @return 1024차원 double 배열
     */
    public double[] embed(String text) {
        Map<String, Object> request = Map.of(
                "model", chatbotProps.getEmbeddingModel(),
                "input", text
        );

        log.debug("Ollama 임베딩 요청: model={}, textLength={}",
                chatbotProps.getEmbeddingModel(), text.length());

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
                .uri("/api/embed")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(Map.class);

        if (response == null || !response.containsKey("embeddings")) {
            throw new IllegalStateException("Ollama 임베딩 응답이 비어 있습니다");
        }

        @SuppressWarnings("unchecked")
        List<List<Number>> embeddings = (List<List<Number>>) response.get("embeddings");
        List<Number> vector = embeddings.get(0);

        double[] result = new double[vector.size()];
        for (int i = 0; i < vector.size(); i++) {
            result[i] = vector.get(i).doubleValue();
        }

        log.debug("임베딩 완료: 차원={}", result.length);
        return result;
    }
}
