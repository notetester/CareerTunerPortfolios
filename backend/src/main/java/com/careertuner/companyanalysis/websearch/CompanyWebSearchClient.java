package com.careertuner.companyanalysis.websearch;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 네이버 검색 API 호출(기업분석 웹검색 primary — 235 §2·§11). 스니펫·URL·제목·수집시각을 반환한다.
 *
 * <p>인증은 {@code X-Naver-Client-Id} / {@code X-Naver-Client-Secret} 헤더.
 * 키는 {@link NaverSearchProperties} 가 env(NAVER_SEARCH_CLIENT_ID/_SECRET)에서 바인딩한다.
 * 시크릿은 로그·예외 메시지에 절대 싣지 않는다(오류 메시지는 status·네이버 errorCode 만).
 *
 * <p>검색 실패는 {@link CompanyWebSearchException} 으로 던져 하이브리드 폴백 계층(235 §5)이
 * 판단하게 한다. 재시도·캐시·비용 상한은 D-4 범위라 여기서는 다루지 않는다.
 */
@Service
@Slf4j
public class CompanyWebSearchClient {

    private final NaverSearchProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public CompanyWebSearchClient(NaverSearchProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout())
                .build());
    }

    CompanyWebSearchClient(NaverSearchProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    /** 키 설정 여부 — 폴백 판단(검색 단계 스킵 여부)에 쓴다. */
    public boolean configured() {
        return properties.configured();
    }

    public List<CompanyWebSearchResult> search(NaverSearchCategory category, String query) {
        if (!configured()) {
            throw new CompanyWebSearchException(
                    "네이버 검색 API 키(NAVER_SEARCH_CLIENT_ID/NAVER_SEARCH_CLIENT_SECRET)가 설정되어 있지 않습니다.");
        }
        if (query == null || query.isBlank()) {
            throw new CompanyWebSearchException("검색어가 비어 있어 네이버 검색을 수행할 수 없습니다.");
        }
        HttpRequest request = buildRequest(category, query);
        try {
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return parseResponse(category, response.statusCode(), response.body());
        } catch (HttpTimeoutException ex) {
            throw new CompanyWebSearchException(
                    "네이버 검색 요청이 %d초 안에 완료되지 않았습니다. (category=%s)"
                            .formatted(properties.getTimeout().toSeconds(), category),
                    ex);
        } catch (IOException ex) {
            throw new CompanyWebSearchException(
                    "네이버 검색 API와 통신하지 못했습니다. (category=%s)".formatted(category), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new CompanyWebSearchException("네이버 검색 요청이 중단되었습니다.", ex);
        }
    }

    HttpRequest buildRequest(NaverSearchCategory category, String query) {
        String url = "%s?query=%s&display=%d".formatted(
                properties.searchUrl(category.endpoint()),
                URLEncoder.encode(query, StandardCharsets.UTF_8),
                properties.getDisplay());
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(properties.getTimeout())
                .header("X-Naver-Client-Id", properties.getClientId())
                .header("X-Naver-Client-Secret", properties.getClientSecret())
                .GET()
                .build();
    }

    List<CompanyWebSearchResult> parseResponse(NaverSearchCategory category, int statusCode, String body) {
        if (statusCode < 200 || statusCode >= 300) {
            // 응답 body 원문은 싣지 않고 네이버 오류 필드만 추린다(민감정보 유입 차단).
            throw new CompanyWebSearchException(
                    "네이버 검색 요청이 실패했습니다. status=%d, errorCode=%s (category=%s)"
                            .formatted(statusCode, errorCode(body), category));
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (JacksonException ex) {
            throw new CompanyWebSearchException(
                    "네이버 검색 응답 JSON을 해석하지 못했습니다. (category=%s)".formatted(category));
        }
        Instant fetchedAt = Instant.now();
        List<CompanyWebSearchResult> results = new ArrayList<>();
        JsonNode items = root.path("items");
        if (items.isArray()) {
            for (JsonNode item : items) {
                String link = item.path("link").asText("");
                if (link.isBlank()) {
                    continue;
                }
                results.add(new CompanyWebSearchResult(
                        category,
                        cleanText(item.path("title").asText("")),
                        link,
                        cleanText(item.path("description").asText("")),
                        fetchedAt));
            }
        }
        return results;
    }

    /** 네이버가 title/description 에 넣는 &lt;b&gt; 강조 태그·HTML 엔티티를 평문으로 정리한다. */
    private String cleanText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Jsoup.parse(value).text().trim();
    }

    private String errorCode(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String code = root.path("errorCode").asText("");
            return code.isBlank() ? "unknown" : code;
        } catch (JacksonException ex) {
            return "unknown";
        }
    }
}
