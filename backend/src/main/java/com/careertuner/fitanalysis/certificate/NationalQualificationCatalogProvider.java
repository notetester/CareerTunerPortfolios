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
 * 국가자격 종목 목록 provider — 자격명이 국가자격인지 판별하고 기술/전문 구분·직무분야를 얻는다.
 * <b>일정 조회가 아니라 catalog/정규화/라우팅 용도</b>다.
 *
 * <p>조회는 {@link NationalQualificationOfflineCatalog 오프라인 스냅샷} 우선(네트워크 불요, Q-Net 장애 무관),
 * 스냅샷 미로드 시에만 공공데이터 15003024 {@code InquiryListNationalQualifcationSVC/getList}
 * (요청변수 serviceKey; 국가 시행종목 전체 목록 반환) 네트워크 경로를 쓴다.
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
    private final NationalQualificationOfflineCatalog offlineCatalog;

    @Autowired
    public NationalQualificationCatalogProvider(
            @Value("${careertuner.certificate.data-go-kr.service-key:}") String serviceKey,
            @Value("${careertuner.certificate.data-go-kr.qnet-base-url:http://openapi.q-net.or.kr/api/service/rest}")
            String qnetBaseUrl,
            @Value("${careertuner.certificate.data-go-kr.timeout-seconds:15}") long timeoutSeconds,
            NationalQualificationOfflineCatalog offlineCatalog) {
        this(serviceKey, qnetBaseUrl, Duration.ofSeconds(timeoutSeconds <= 0 ? 15 : timeoutSeconds),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), offlineCatalog);
    }

    /** 테스트/구성용 생성자. offlineCatalog 는 null 허용(네트워크 경로 단독 테스트). */
    NationalQualificationCatalogProvider(String serviceKey, String qnetBaseUrl, Duration timeout,
                                         HttpClient httpClient, NationalQualificationOfflineCatalog offlineCatalog) {
        this.serviceKey = serviceKey;
        this.qnetBaseUrl = qnetBaseUrl;
        this.timeout = timeout;
        this.httpClient = httpClient;
        this.offlineCatalog = offlineCatalog;
    }

    public boolean enabled() {
        return serviceKey != null && !serviceKey.isBlank();
    }

    /**
     * 자격명으로 국가자격 목록을 조회한다. <b>오프라인 스냅샷 우선</b> — 목록은 연 단위 스냅샷 성격이라(API 도
     * 동일 시점 데이터 반환) 스냅샷이 로드돼 있으면 네트워크 없이 즉시 판별하고, Q-Net 장애의 영향을 받지 않는다.
     * 스냅샷 미로드 시에만 기존 네트워크 경로로 동작한다. 실패 시 예외 없이 degrade.
     *
     * <p>단 서비스 레벨 근거 수집({@code CertificateEvidenceService.anyEnabled()})은 여전히 serviceKey 존재로
     * 게이트된다 — 키 없는 배포(mock 데모 등)에서 스냅샷만으로 근거 카드가 켜지는 동작 변화를 만들지 않기 위한
     * 의도적 보존이다. 스냅샷의 효용은 '키 있음 + Q-Net 장애' 시나리오에서 라우팅이 살아있는 것.
     */
    public NationalQualificationCatalogEvidence lookup(String certName) {
        if (certName == null || certName.isBlank()) {
            return degraded(NationalQualificationCatalogStatus.UPSTREAM_UNAVAILABLE, certName);
        }
        if (offlineCatalog != null && offlineCatalog.available()) {
            return offlineCatalog.lookup(certName);
        }
        if (!enabled()) {
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
