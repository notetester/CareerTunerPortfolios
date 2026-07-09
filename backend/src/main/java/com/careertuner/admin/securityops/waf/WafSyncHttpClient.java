package com.careertuner.admin.securityops.waf;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.careertuner.admin.securityops.waf.WafSyncModels.WafProvider;
import com.careertuner.admin.securityops.waf.WafSyncModels.WafSyncResult;
import com.careertuner.admin.securityops.waf.WafSyncModels.WafSyncTarget;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 실제 WAF/CDN 아웃바운드 HTTP 동기화 클라이언트.
 *
 * <p>java.net.http.HttpClient 로 프로바이더 endpoint 에 규칙을 push 한다. 재시도+백오프, 메서드/헤더/시크릿
 * 해석, fail-open/fail-closed 상태 매핑을 지원한다. TripTogether {@code WafSyncHttpClient} 를 이식했다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WafSyncHttpClient {

    private final SecurityProviderSecretResolver secretResolver;
    private final ObjectMapper objectMapper;

    public WafSyncResult call(WafProvider provider, WafSyncTarget target) {
        int attempts = (provider.getRetryCount() == null ? 0 : Math.max(0, provider.getRetryCount())) + 1;
        int backoff = provider.getRetryBackoffMs() == null ? 500 : Math.max(0, provider.getRetryBackoffMs());
        WafSyncResult last = null;
        for (int i = 0; i < attempts; i++) {
            last = doCall(provider, target);
            if (last != null && last.isSuccess()) {
                return last;
            }
            if (i < attempts - 1 && backoff > 0) {
                try {
                    Thread.sleep((long) backoff * (i + 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return last;
    }

    private WafSyncResult doCall(WafProvider provider, WafSyncTarget target) {
        try {
            String endpoint = resolveEndpoint(provider.getEndpointUrl(), target);
            URI uri = validateExternalHttpEndpoint(provider, endpoint);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("providerCode", provider.getProviderCode());
            payload.put("syncEventId", target.syncEventId());
            payload.put("operation", target.operationType());
            payload.put("targetType", target.ruleType());
            payload.put("targetValue", target.ruleValue());

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofMillis(provider.getTimeoutMs() == null ? 3000 : provider.getTimeoutMs()))
                    .header("Content-Type", "application/json");
            applyExtraHeaders(builder, provider);

            String apiKey = secretResolver.resolve(provider.getApiKeyRef());
            if (apiKey != null && !apiKey.isBlank()) {
                builder.header("Authorization", "Bearer " + apiKey);
            }

            String body = objectMapper.writeValueAsString(payload);
            String method = resolveMethod(provider, target);
            switch (method) {
                case "DELETE" -> builder.method("DELETE", HttpRequest.BodyPublishers.ofString(body));
                case "PUT" -> builder.PUT(HttpRequest.BodyPublishers.ofString(body));
                case "PATCH" -> builder.method("PATCH", HttpRequest.BodyPublishers.ofString(body));
                default -> builder.POST(HttpRequest.BodyPublishers.ofString(body));
            }

            HttpResponse<String> response;
            try (HttpClient client = HttpClient.newHttpClient()) {
                response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            }
            boolean ok = response.statusCode() >= 200 && response.statusCode() < 300;
            return WafSyncResult.builder()
                    .handled(true)
                    .success(ok)
                    .status(ok ? "SYNCED" : failStatus(provider))
                    .message(detail(provider, "HTTP " + response.statusCode(), abbreviate(response.body())))
                    .build();
        } catch (Exception e) {
            log.warn("[WAF] 동기화 호출 실패 syncEventId={} provider={}: {}",
                    target.syncEventId(), provider.getProviderCode(), e.getMessage());
            return WafSyncResult.builder()
                    .handled(true)
                    .success(false)
                    .status(failStatus(provider))
                    .message(detail(provider, e.getClass().getSimpleName(), e.getMessage()))
                    .build();
        }
    }

    private String failStatus(WafProvider provider) {
        return provider.getFailOpen() != null && provider.getFailOpen() == 1 ? "PENDING" : "FAILED";
    }

    private String resolveEndpoint(String endpointUrl, WafSyncTarget target) {
        if (endpointUrl == null) {
            return "";
        }
        return endpointUrl
                .replace("{syncEventId}", safe(String.valueOf(target.syncEventId())))
                .replace("{operation}", safe(target.operationType()))
                .replace("{targetType}", safe(target.ruleType()))
                .replace("{targetValue}", safe(target.ruleValue()));
    }

    private URI validateExternalHttpEndpoint(WafProvider provider, String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("WAF endpointUrl 이 비어 있습니다: " + safe(provider.getProviderCode()));
        }
        URI uri = URI.create(endpoint);
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("지원하지 않는 WAF endpoint scheme: " + safe(scheme));
        }
        return uri;
    }

    private String resolveMethod(WafProvider provider, WafSyncTarget target) {
        if (provider.getRequestMethod() != null && !provider.getRequestMethod().isBlank()) {
            return provider.getRequestMethod().trim().toUpperCase();
        }
        String op = target.operationType();
        if ("DELETE".equalsIgnoreCase(op) || "UNBLOCK".equalsIgnoreCase(op) || "REMOVE".equalsIgnoreCase(op)) {
            return "DELETE";
        }
        return "POST";
    }

    private void applyExtraHeaders(HttpRequest.Builder builder, WafProvider provider) {
        String headers = provider.getRequestHeadersJson();
        if (headers == null || headers.isBlank()) {
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(headers);
            if (node.isObject()) {
                for (Map.Entry<String, JsonNode> e : node.properties()) {
                    String k = e.getKey();
                    if (k != null && !k.isBlank() && !"Content-Type".equalsIgnoreCase(k)) {
                        JsonNode v = e.getValue();
                        builder.header(k, v.isTextual() ? v.asText() : v.toString());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[WAF] requestHeadersJson 파싱 실패 provider={}: {}", provider.getProviderCode(), e.getMessage());
        }
    }

    private String detail(WafProvider provider, String result, String reason) {
        return "providerCode=" + safe(provider.getProviderCode())
                + "; result=" + safe(result)
                + "; failOpen=" + provider.getFailOpen()
                + "; reason=" + safe(reason);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String abbreviate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 500 ? value : value.substring(0, 500) + "...";
    }
}
