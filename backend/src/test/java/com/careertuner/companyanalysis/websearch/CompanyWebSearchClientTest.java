package com.careertuner.companyanalysis.websearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpTimeoutException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import tools.jackson.databind.ObjectMapper;

/**
 * 네이버 검색 클라이언트 단위 테스트 — 인증 헤더·4개 카테고리 요청 구성·응답 파싱·timeout/오류 처리.
 * HTTP 는 mock(실호출 없음), 시크릿은 dummy 만 사용하고 예외 메시지에 새지 않는지 함께 검증한다.
 */
class CompanyWebSearchClientTest {

    private static final String DUMMY_ID = "dummy-client-id";
    private static final String DUMMY_SECRET = "dummy-client-secret";

    private NaverSearchProperties properties;
    private HttpClient httpClient;
    private CompanyWebSearchClient client;

    @BeforeEach
    void setUp() {
        properties = new NaverSearchProperties();
        properties.setClientId(DUMMY_ID);
        properties.setClientSecret(DUMMY_SECRET);
        httpClient = mock(HttpClient.class);
        client = new CompanyWebSearchClient(properties, new ObjectMapper(), httpClient);
    }

    // ── 요청 구성 ──

    @Test
    void requestCarriesNaverAuthHeaders() {
        HttpRequest request = client.buildRequest(NaverSearchCategory.NEWS, "가온테크");

        assertThat(request.headers().firstValue("X-Naver-Client-Id")).contains(DUMMY_ID);
        assertThat(request.headers().firstValue("X-Naver-Client-Secret")).contains(DUMMY_SECRET);
        assertThat(request.method()).isEqualTo("GET");
    }

    @ParameterizedTest
    @EnumSource(NaverSearchCategory.class)
    void requestTargetsCategoryEndpoint(NaverSearchCategory category) {
        HttpRequest request = client.buildRequest(category, "가온테크");

        assertThat(request.uri().toString())
                .startsWith("https://openapi.naver.com/v1/search/" + category.endpoint() + "?");
    }

    @Test
    void requestEncodesQueryAndAppliesDisplay() {
        properties.setDisplay(5);

        HttpRequest request = client.buildRequest(NaverSearchCategory.WEBKR, "가온테크 IT 서비스");

        String uri = request.uri().toString();
        assertThat(uri).contains("query=%EA%B0%80%EC%98%A8%ED%85%8C%ED%81%AC+IT+%EC%84%9C%EB%B9%84%EC%8A%A4");
        assertThat(uri).contains("display=5");
    }

    // ── 응답 파싱 ──

    @Test
    void parseResponseReturnsCleanedSnippets() {
        String body = """
                {"lastBuildDate":"Wed, 03 Jul 2026 10:00:00 +0900","total":2,"start":1,"display":2,
                 "items":[
                   {"title":"<b>가온테크</b> 신규 서비스 출시","originallink":"https://news.example.com/1",
                    "link":"https://n.news.naver.com/article/1","description":"IT 기업 <b>가온테크</b>가 &quot;클라우드&quot; 서비스를 공개했다.","pubDate":"Wed, 03 Jul 2026 09:00:00 +0900"},
                   {"title":"업계 동향","link":"https://n.news.naver.com/article/2","description":"서버 가상화 시장 정리"}
                 ]}
                """;

        List<CompanyWebSearchResult> results =
                client.parseResponse(NaverSearchCategory.NEWS, 200, body);

        assertThat(results).hasSize(2);
        CompanyWebSearchResult first = results.get(0);
        assertThat(first.category()).isEqualTo(NaverSearchCategory.NEWS);
        assertThat(first.title()).isEqualTo("가온테크 신규 서비스 출시");
        assertThat(first.link()).isEqualTo("https://n.news.naver.com/article/1");
        assertThat(first.description()).isEqualTo("IT 기업 가온테크가 \"클라우드\" 서비스를 공개했다.");
        assertThat(first.fetchedAt()).isNotNull();
    }

    @Test
    void parseResponseSkipsItemsWithoutLink() {
        String body = """
                {"items":[{"title":"링크 없는 항목","description":"제외 대상"},
                          {"title":"정상","link":"https://example.com","description":"유지"}]}
                """;

        List<CompanyWebSearchResult> results =
                client.parseResponse(NaverSearchCategory.BLOG, 200, body);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).link()).isEqualTo("https://example.com");
    }

    @Test
    void parseResponseReturnsEmptyListForNoItems() {
        String body = """
                {"lastBuildDate":"Wed, 03 Jul 2026 10:00:00 +0900","total":0,"start":1,"display":0,"items":[]}
                """;

        assertThat(client.parseResponse(NaverSearchCategory.ENCYC, 200, body)).isEmpty();
    }

    // ── 오류 처리 ──

    @Test
    void errorStatusThrowsWithStatusAndErrorCodeButNoSecret() {
        String body = """
                {"errorMessage":"Incorrect authentication info","errorCode":"024"}
                """;

        assertThatThrownBy(() -> client.parseResponse(NaverSearchCategory.NEWS, 401, body))
                .isInstanceOf(CompanyWebSearchException.class)
                .hasMessageContaining("status=401")
                .hasMessageContaining("errorCode=024")
                .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain(DUMMY_SECRET));
    }

    @Test
    void rateLimitStatusThrows() {
        assertThatThrownBy(() -> client.parseResponse(NaverSearchCategory.WEBKR, 429, "{}"))
                .isInstanceOf(CompanyWebSearchException.class)
                .hasMessageContaining("status=429");
    }

    @Test
    void malformedSuccessBodyThrows() {
        assertThatThrownBy(() -> client.parseResponse(NaverSearchCategory.NEWS, 200, "not-json"))
                .isInstanceOf(CompanyWebSearchException.class)
                .hasMessageContaining("JSON");
    }

    @Test
    @SuppressWarnings("unchecked")
    void timeoutIsTranslatedToDomainExceptionWithoutSecret() throws Exception {
        when(httpClient.send(any(), any(java.net.http.HttpResponse.BodyHandler.class)))
                .thenThrow(new HttpTimeoutException("request timed out"));

        assertThatThrownBy(() -> client.search(NaverSearchCategory.NEWS, "가온테크"))
                .isInstanceOf(CompanyWebSearchException.class)
                .hasMessageContaining("완료되지 않았습니다")
                .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain(DUMMY_SECRET));
    }

    @Test
    @SuppressWarnings("unchecked")
    void ioFailureIsTranslatedToDomainException() throws Exception {
        when(httpClient.send(any(), any(java.net.http.HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("connection reset"));

        assertThatThrownBy(() -> client.search(NaverSearchCategory.BLOG, "가온테크"))
                .isInstanceOf(CompanyWebSearchException.class)
                .hasMessageContaining("통신하지 못했습니다");
    }

    @Test
    void missingKeysFailFastWithoutHttpCall() {
        properties.setClientSecret("");

        assertThatThrownBy(() -> client.search(NaverSearchCategory.NEWS, "가온테크"))
                .isInstanceOf(CompanyWebSearchException.class)
                .hasMessageContaining("NAVER_SEARCH_CLIENT_ID");
        verifyNoInteractions(httpClient);
    }

    @Test
    void blankQueryFailFastWithoutHttpCall() {
        assertThatThrownBy(() -> client.search(NaverSearchCategory.NEWS, "  "))
                .isInstanceOf(CompanyWebSearchException.class)
                .hasMessageContaining("검색어");
        verifyNoInteractions(httpClient);
    }
}
