package com.careertuner.applicationcase.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Ollama {@code GET /api/tags} 로 설정된 로컬 모델이 실제 탑재돼 있는지 확인한다(모델 옵션 조회용).
 * enabled 여부만으로는 "모델 존재"를 보장하지 못하므로, 연결·모델 존재까지 확인한다.
 * 옵션 조회는 실패하면 안 되므로 연결 실패·타임아웃·미탑재는 예외 대신 false 로 degrade 하고,
 * 결과(성공·실패 모두)를 짧게 캐시해 옵션 요청마다 원격 왕복하지 않는다.
 */
@Component
public class OllamaModelProbe {

    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration CACHE_TTL = Duration.ofSeconds(30);

    private final BAnalysisProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private volatile CachedResult cached;

    public OllamaModelProbe(BAnalysisProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newBuilder()
                .connectTimeout(properties.getLocalLlm().getConnectTimeout())
                .build());
    }

    OllamaModelProbe(BAnalysisProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    /** 설정된 로컬 모델이 Ollama 에 존재하면 true. 연결 실패·타임아웃·미탑재면 false(throw 하지 않음). */
    public boolean modelAvailable() {
        CachedResult snapshot = cached;
        if (snapshot != null && !snapshot.isExpired()) {
            return snapshot.available();
        }
        boolean fresh = probe();
        cached = new CachedResult(fresh, Instant.now().plus(CACHE_TTL));
        return fresh;
    }

    private boolean probe() {
        BAnalysisProperties.LocalLlm local = properties.getLocalLlm();
        String wanted = local.getModel();
        if (wanted == null || wanted.isBlank()) {
            return false;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(tagsUrl(local)))
                    .timeout(READ_TIMEOUT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return false;
            }
            JsonNode models = objectMapper.readTree(response.body()).path("models");
            if (!models.isArray()) {
                return false;
            }
            for (JsonNode model : models) {
                if (matches(model.path("name").asText(""), wanted)
                        || matches(model.path("model").asText(""), wanted)) {
                    return true;
                }
            }
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        } catch (RuntimeException | IOException ex) {
            return false;
        }
    }

    /** "careertuner-b-jobposting-r1" 이 ":latest" 등 태그 표기와도 매칭되게 한다. */
    private static boolean matches(String candidate, String wanted) {
        if (candidate == null || candidate.isBlank()) {
            return false;
        }
        return candidate.equals(wanted) || candidate.startsWith(wanted + ":");
    }

    private static String tagsUrl(BAnalysisProperties.LocalLlm local) {
        return local.getBaseUrl().replaceAll("/+$", "") + "/api/tags";
    }

    private record CachedResult(boolean available, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
