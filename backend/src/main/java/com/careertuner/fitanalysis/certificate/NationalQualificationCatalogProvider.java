package com.careertuner.fitanalysis.certificate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 국가자격 종목 목록 provider — 공공데이터 15003024 {@code InquiryListNationalQualifcationSVC/getList}
 * (요청변수 serviceKey; 국가 시행종목 전체 목록 반환). 자격명이 국가자격인지 판별하고 canonical key(jmCd)·기술/전문
 * 구분·직무분야를 얻는다. <b>일정 조회가 아니라 catalog/정규화/라우팅 용도</b>다.
 *
 * <p>getList 는 numOfRows/pageNo 페이지네이션이 있어 기본값이 작으면 대부분 종목을 놓치므로 큰 numOfRows 로 전체를
 * 한 번에 받는다. 그래도 totalCount 가 수신량보다 크면(잘림) 무매칭을 부재로 단정하지 않고 UPSTREAM_UNAVAILABLE 로 낸다.
 * 게이트웨이 오류 envelope·resultCode≠00·정상 미확증도 {@link NationalQualificationCatalogStatus#UPSTREAM_UNAVAILABLE}
 * (부재 아님). serviceKey 는 재인코딩하지 않는다(디코딩 raw 키). 자격명은 정확 매칭(과매칭 방지). 공용 파싱은
 * {@link QnetXmlSupport}.
 */
@Component
public class NationalQualificationCatalogProvider {

    private static final Logger log = LoggerFactory.getLogger(NationalQualificationCatalogProvider.class);
    private static final String SOURCE_NAME = "한국산업인력공단 큐넷(Q-Net) 국가자격 종목 목록";
    private static final String SOURCE_URL = "https://www.q-net.or.kr/";
    private static final String OPERATION = "/InquiryListNationalQualifcationSVC/getList";
    private static final int NUM_OF_ROWS = 2000; // 국가 시행종목 전체(~수백)를 한 페이지로.

    private final String serviceKey;
    private final String qnetBaseUrl;
    private final Duration timeout;
    private final HttpClient httpClient;

    @Autowired
    public NationalQualificationCatalogProvider(
            @Value("${careertuner.certificate.data-go-kr.service-key:}") String serviceKey,
            @Value("${careertuner.certificate.data-go-kr.qnet-base-url:http://openapi.q-net.or.kr/api/service/rest}")
            String qnetBaseUrl,
            @Value("${careertuner.certificate.data-go-kr.timeout-seconds:15}") long timeoutSeconds) {
        this(serviceKey, qnetBaseUrl, Duration.ofSeconds(timeoutSeconds <= 0 ? 15 : timeoutSeconds),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    /** 테스트/구성용 생성자. */
    NationalQualificationCatalogProvider(String serviceKey, String qnetBaseUrl, Duration timeout, HttpClient httpClient) {
        this.serviceKey = serviceKey;
        this.qnetBaseUrl = qnetBaseUrl;
        this.timeout = timeout;
        this.httpClient = httpClient;
    }

    public boolean enabled() {
        return serviceKey != null && !serviceKey.isBlank();
    }

    /** 자격명으로 국가자격 목록을 조회한다. 실패 시 예외 없이 degrade. */
    public NationalQualificationCatalogEvidence lookup(String certName) {
        if (!enabled() || certName == null || certName.isBlank()) {
            return degraded(NationalQualificationCatalogStatus.UPSTREAM_UNAVAILABLE, certName);
        }
        try {
            String url = qnetBaseUrl + OPERATION
                    + "?serviceKey=" + serviceKey + "&numOfRows=" + NUM_OF_ROWS + "&pageNo=1";
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(timeout).GET().build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                log.debug("getList non-200: cert={} status={}", certName, response.statusCode());
                return degraded(NationalQualificationCatalogStatus.UPSTREAM_UNAVAILABLE, certName);
            }
            return parse(response.body(), certName);
        } catch (Exception e) {
            log.debug("getList lookup failed: cert={} err={}", certName, e.getClass().getSimpleName());
            return degraded(NationalQualificationCatalogStatus.UPSTREAM_UNAVAILABLE, certName);
        }
    }

    /**
     * 응답 파싱(순수 함수). 게이트웨이 오류·resultCode≠00·정상 미확증·목록 잘림 → UPSTREAM; 자격명 매칭 → FOUND;
     * 정상 확증 + 목록 완전 + 무매칭 → NOT_FOUND.
     */
    static NationalQualificationCatalogEvidence parse(String xml, String certName) {
        if (xml == null || xml.isBlank() || QnetXmlSupport.isGatewayError(xml)
                || QnetXmlSupport.explicitErrorCode(xml)) {
            return degraded(NationalQualificationCatalogStatus.UPSTREAM_UNAVAILABLE, certName);
        }
        for (String block : QnetXmlSupport.allBlocks(xml, "item")) {
            if (QnetXmlSupport.nameMatches(QnetXmlSupport.tagValue(block, "jmfldnm"), certName)) {
                NationalQualificationCatalogEntry entry = new NationalQualificationCatalogEntry(
                        QnetXmlSupport.tagValue(block, "jmcd"),
                        QnetXmlSupport.tagValue(block, "jmfldnm"),
                        QnetXmlSupport.tagValue(block, "qualgbcd"),
                        QnetXmlSupport.tagValue(block, "qualgbnm"),
                        QnetXmlSupport.tagValue(block, "seriesnm"),
                        QnetXmlSupport.tagValue(block, "obligfldnm"),
                        QnetXmlSupport.tagValue(block, "mdobligfldnm"));
                return new NationalQualificationCatalogEvidence(
                        NationalQualificationCatalogStatus.FOUND, certName, entry, SOURCE_NAME, SOURCE_URL);
            }
        }
        // 정상(00) 확증 + 목록이 잘리지 않았을 때만 '부재' 단정.
        if (QnetXmlSupport.normalConfirmed(xml) && !QnetXmlSupport.truncated(xml)) {
            return degraded(NationalQualificationCatalogStatus.NOT_FOUND, certName);
        }
        return degraded(NationalQualificationCatalogStatus.UPSTREAM_UNAVAILABLE, certName);
    }

    private static NationalQualificationCatalogEvidence degraded(
            NationalQualificationCatalogStatus status, String certName) {
        return new NationalQualificationCatalogEvidence(status, certName, null, SOURCE_NAME, SOURCE_URL);
    }
}
