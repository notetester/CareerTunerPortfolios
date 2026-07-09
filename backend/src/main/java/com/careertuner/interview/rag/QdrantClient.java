package com.careertuner.interview.rag;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Qdrant REST 클라이언트 (의존성 추가 없이 HTTP 직접 호출). 컬렉션 보장 / 업서트 / 벡터 검색. */
@Service
public class QdrantClient {

    private final InterviewRagProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public QdrantClient(InterviewRagProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    /** 컬렉션이 없으면 생성한다(Cosine 거리). */
    public void ensureCollection() {
        String url = base() + "/collections/" + properties.getCollection();
        if (statusOf(HttpRequest.newBuilder(URI.create(url)).GET()) == 200) {
            return;
        }
        Map<String, Object> body = Map.of("vectors",
                Map.of("size", properties.getDimension(), "distance", "Cosine"));
        send(HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(toJson(body), StandardCharsets.UTF_8)));
    }

    public void upsert(long id, List<Float> vector, Map<String, Object> payload) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("id", id);
        point.put("vector", vector);
        point.put("payload", payload);
        Map<String, Object> body = Map.of("points", List.of(point));
        String url = base() + "/collections/" + properties.getCollection() + "/points?wait=true";
        send(HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(toJson(body), StandardCharsets.UTF_8)));
    }

    /** 해당 포인트(문서 id) 벡터를 컬렉션에서 삭제한다. */
    public void delete(long id) {
        Map<String, Object> body = Map.of("points", List.of(id));
        String url = base() + "/collections/" + properties.getCollection() + "/points/delete?wait=true";
        send(HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(body), StandardCharsets.UTF_8)));
    }

    public List<SearchHit> search(List<Float> vector, int topK) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vector", vector);
        body.put("limit", topK);
        body.put("with_payload", true);
        String url = base() + "/collections/" + properties.getCollection() + "/points/search";
        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(body), StandardCharsets.UTF_8)));
        List<SearchHit> hits = new ArrayList<>();
        try {
            JsonNode result = objectMapper.readTree(response.body()).path("result");
            if (result.isArray()) {
                for (JsonNode item : result) {
                    JsonNode payload = item.path("payload");
                    hits.add(new SearchHit(
                            payload.path("content").asText(""),
                            payload.path("kind").asText(""),
                            payload.path("title").asText(""),
                            item.path("score").asDouble(0)));
                }
            }
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "검색 응답을 처리하지 못했습니다.");
        }
        return hits;
    }

    // ───── 내부 ─────

    private String base() {
        return properties.getQdrantUrl().replaceAll("/+$", "");
    }

    private int statusOf(HttpRequest.Builder builder) {
        try {
            return httpClient.send(builder.timeout(Duration.ofSeconds(10)).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).statusCode();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Qdrant 연결에 실패했습니다.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Qdrant 요청이 중단되었습니다.");
        }
    }

    private HttpResponse<String> send(HttpRequest.Builder builder) {
        try {
            HttpResponse<String> response = httpClient.send(builder.timeout(Duration.ofSeconds(20)).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "Qdrant 요청 실패 (" + response.statusCode() + ")");
            }
            return response;
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Qdrant 연결에 실패했습니다.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Qdrant 요청이 중단되었습니다.");
        }
    }

    private String toJson(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Qdrant 요청을 구성하지 못했습니다.");
        }
    }

    public record SearchHit(String text, String kind, String title, double score) {
    }
}
