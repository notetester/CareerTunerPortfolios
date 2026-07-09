package com.careertuner.interview.rag;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.careertuner.applicationcase.service.OpenAiProperties;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** OpenAI 임베딩 API 클라이언트. 텍스트 → 벡터. (키/baseUrl 은 공통 openai 설정 재사용) */
@Service
public class EmbeddingClient {

    private final OpenAiProperties openAiProperties;
    private final InterviewRagProperties ragProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public EmbeddingClient(OpenAiProperties openAiProperties, InterviewRagProperties ragProperties,
                           ObjectMapper objectMapper) {
        this.openAiProperties = openAiProperties;
        this.ragProperties = ragProperties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    public float[] embed(String text) {
        if (!openAiProperties.configured()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "OpenAI API 키가 설정되어 있지 않습니다.");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", ragProperties.getEmbeddingModel());
        body.put("input", text == null ? "" : text);
        try {
            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder(URI.create(embeddingsUrl()))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + openAiProperties.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "임베딩 생성에 실패했습니다.");
            }
            JsonNode arr = objectMapper.readTree(response.body()).path("data").path(0).path("embedding");
            if (!arr.isArray() || arr.isEmpty()) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "임베딩 응답이 비어 있습니다.");
            }
            float[] vector = new float[arr.size()];
            for (int i = 0; i < arr.size(); i++) {
                vector[i] = (float) arr.get(i).asDouble();
            }
            return vector;
        } catch (BusinessException ex) {
            throw ex;
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "임베딩 응답을 처리하지 못했습니다.");
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "임베딩 요청을 보내지 못했습니다.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "임베딩 요청이 중단되었습니다.");
        }
    }

    public List<Float> embedAsList(String text) {
        float[] v = embed(text);
        List<Float> list = new java.util.ArrayList<>(v.length);
        for (float f : v) {
            list.add(f);
        }
        return list;
    }

    private String embeddingsUrl() {
        return openAiProperties.getBaseUrl().replaceAll("/+$", "") + "/embeddings";
    }
}
