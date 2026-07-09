package com.careertuner.companyanalysis.websearch;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * 네이버 검색 API(기업분석 웹검색 primary — 235 §11) 설정.
 *
 * <p>키는 env 로만 관리한다(커밋 금지). 공유 설정 파일(application.yaml)을 수정하지 않도록
 * prefix 를 {@code naver.search} 로 두어 Spring relaxed binding 으로 env 가 직접 바인딩되게 한다:
 * {@code NAVER_SEARCH_CLIENT_ID} → {@code naver.search.client-id},
 * {@code NAVER_SEARCH_CLIENT_SECRET} → {@code naver.search.client-secret}.
 *
 * <p>{@code clientSecret} 은 로그·예외 메시지·테스트 출력에 노출하지 않는다.
 * (Lombok 은 toString 을 생성하지 않으므로 기본 Object#toString 이 유지된다 — toString 추가 금지.)
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "naver.search")
public class NaverSearchProperties {

    private String clientId = "";
    private String clientSecret = "";
    private String baseUrl = "https://openapi.naver.com/v1/search";
    private Duration timeout = Duration.ofSeconds(10);
    /** 카테고리당 요청 결과 수(네이버 display, 최대 100). 근거 수집엔 상위 결과면 충분하다. */
    private int display = 10;

    public String searchUrl(String endpoint) {
        return baseUrl.replaceAll("/+$", "") + "/" + endpoint;
    }

    public boolean configured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }
}
