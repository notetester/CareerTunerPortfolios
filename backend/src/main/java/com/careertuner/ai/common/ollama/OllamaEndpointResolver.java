package com.careertuner.ai.common.ollama;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ollama 엔드포인트 폴백 리졸버.
 *
 * <p>공유 4090(Ollama)이 꺼져 있을 때 로컬 Ollama 로 자동 전환하기 위한 공용 유틸.
 * 후보 목록 = [설정된 {@code ai.ollama.base-url}] + {@code ai.ollama.fallback-base-urls}
 * (콤마 구분, env {@code AI_OLLAMA_FALLBACK_BASE_URLS}, 기본 {@code http://localhost:11434}).
 *
 * <ul>
 *   <li>{@link #resolve()} — 최초 사용/캐시 만료 시 {@code GET {base}/api/tags}(타임아웃 1.5초)로
 *       후보를 순서대로 프로브해 살아있는 첫 후보를 60초 캐시 후 반환한다.</li>
 *   <li>모든 후보가 죽어 있으면 <b>설정된 base-url 을 그대로 반환</b>한다 — 리졸버가 기존 동작
 *       (설정값 그대로 사용)보다 나빠지지 않게 하는 보수적 폴백(FcmPushClient 의 graceful 패턴과 동일 취지).</li>
 *   <li>{@link #reportFailure(String)} — 호출 실패를 보고받으면 캐시를 무효화해 다음 {@link #resolve()}가
 *       즉시 재프로브(다음 후보 재시도 1회)하게 한다.</li>
 * </ul>
 *
 * <p>부팅 시 1회 프로퍼티 교체는 {@link OllamaEndpointFallbackConfig} 참고. 이 빈은 요청 시점마다
 * 살아있는 엔드포인트를 알고 싶은 호출부(런타임 폴백)를 위한 확장점이다.
 */
public class OllamaEndpointResolver {

    private static final Logger log = LoggerFactory.getLogger(OllamaEndpointResolver.class);

    /** 프로브 타임아웃(연결+응답). 짧게 잡아 죽은 후보에서 빨리 빠져나온다. */
    public static final Duration PROBE_TIMEOUT = Duration.ofMillis(1500);
    /** 살아있는 후보 캐시 유지 시간. */
    public static final Duration CACHE_TTL = Duration.ofSeconds(60);

    private final String primaryBaseUrl;
    private final List<String> candidates;
    private final HttpClient httpClient;

    private volatile String cachedBaseUrl;
    private volatile long cachedAtMillis;

    /**
     * @param primaryBaseUrl   설정된 기본 base-url (후보 1순위, 전멸 시 최종 반환값)
     * @param fallbackBaseUrls 폴백 후보 목록(콤마 구분 파싱 결과). null/빈 값 허용.
     */
    public OllamaEndpointResolver(String primaryBaseUrl, List<String> fallbackBaseUrls) {
        this.primaryBaseUrl = normalize(primaryBaseUrl);
        // 순서 유지 + 중복 제거: 설정값이 항상 1순위.
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        ordered.add(this.primaryBaseUrl);
        if (fallbackBaseUrls != null) {
            for (String url : fallbackBaseUrls) {
                if (url != null && !url.isBlank()) {
                    ordered.add(normalize(url));
                }
            }
        }
        this.candidates = List.copyOf(ordered);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(PROBE_TIMEOUT)
                .build();
    }

    /** 콤마 구분 문자열을 후보 목록으로 파싱한다. */
    public static List<String> parseCandidates(String commaSeparated) {
        List<String> parsed = new ArrayList<>();
        if (commaSeparated == null || commaSeparated.isBlank()) {
            return parsed;
        }
        for (String piece : commaSeparated.split(",")) {
            if (!piece.isBlank()) {
                parsed.add(piece.trim());
            }
        }
        return parsed;
    }

    /** 설정된 기본 base-url (프로브와 무관한 원래 설정값). */
    public String primaryBaseUrl() {
        return primaryBaseUrl;
    }

    /** 후보 전체(설정값 + 폴백, 순서 유지). */
    public List<String> candidates() {
        return candidates;
    }

    /**
     * 현재 살아있는 Ollama base-url 을 반환한다.
     * 캐시가 유효하면 캐시를, 아니면 후보를 순서대로 프로브한 결과를 60초 캐시 후 반환한다.
     * 전 후보가 죽어 있으면 설정된 base-url 을 반환한다(기존 실패 경로 유지).
     */
    public String resolve() {
        String cached = cachedBaseUrl;
        if (cached != null && System.currentTimeMillis() - cachedAtMillis < CACHE_TTL.toMillis()) {
            return cached;
        }
        synchronized (this) {
            // 동시 진입 시 한 스레드만 프로브하도록 캐시를 재확인한다.
            cached = cachedBaseUrl;
            if (cached != null && System.currentTimeMillis() - cachedAtMillis < CACHE_TTL.toMillis()) {
                return cached;
            }
            String resolved = probeFirstAlive();
            cachedBaseUrl = resolved;
            cachedAtMillis = System.currentTimeMillis();
            return resolved;
        }
    }

    /**
     * 호출 실패 보고 — 해당 엔드포인트가 캐시돼 있으면 무효화해 다음 resolve() 가 재프로브(다음 후보 시도)하게 한다.
     * 호출부는 "실패 → reportFailure → resolve 재시도 1회" 패턴으로 폴백을 구현한다.
     */
    public void reportFailure(String failedBaseUrl) {
        if (failedBaseUrl == null) {
            return;
        }
        String normalized = normalize(failedBaseUrl);
        if (normalized.equals(cachedBaseUrl)) {
            cachedBaseUrl = null;
            log.info("[ollama] 엔드포인트 실패 보고({}) — 캐시 무효화, 다음 호출에서 재프로브", normalized);
        }
    }

    /** 후보를 순서대로 프로브해 살아있는 첫 후보를 반환. 전멸이면 설정값(primary)을 반환한다. */
    private String probeFirstAlive() {
        for (String candidate : candidates) {
            if (isAlive(candidate)) {
                if (!candidate.equals(primaryBaseUrl)) {
                    log.info("[ollama] 설정된 엔드포인트({}) 응답 없음 → 폴백 {} 사용(60초 캐시)", primaryBaseUrl, candidate);
                }
                return candidate;
            }
        }
        log.warn("[ollama] 살아있는 Ollama 후보 없음({}) — 설정값 {} 그대로 사용(기존 실패 경로 유지)",
                candidates, primaryBaseUrl);
        return primaryBaseUrl;
    }

    /** GET {base}/api/tags 프로브. 2xx 응답이면 살아있다고 판단한다. */
    boolean isAlive(String baseUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/tags"))
                    .timeout(PROBE_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            log.debug("[ollama] 프로브 실패 {}: {}", baseUrl, e.toString());
            return false;
        }
    }

    private static String normalize(String url) {
        return url == null ? "" : url.trim().replaceAll("/+$", "");
    }
}
