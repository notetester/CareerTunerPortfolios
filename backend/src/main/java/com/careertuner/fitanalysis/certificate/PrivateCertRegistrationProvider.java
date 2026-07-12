package com.careertuner.fitanalysis.certificate;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.careertuner.fitanalysis.certificate.PrivateCertRegistrationEvidence.Match;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 민간자격 등록정보 provider — 공공데이터 15075600(한국직업능력연구원, odcloud JSON). 자격명 부분일치(LIKE)로
 * on-demand 조회해 <b>존재/등록상태/기관/공인여부</b>를 확인한다(82,413건 벌크 다운로드 불필요). q-net 과 달리 odcloud 는
 * 안정적이라, 매칭 0건은 실제 "미등록"({@link PrivateCertRegistrationStatus#NOT_FOUND}), API 오류만
 * {@link PrivateCertRegistrationStatus#UPSTREAM_UNAVAILABLE} 로 구분한다(장애≠부재).
 *
 * <p><b>시험일정은 다루지 않는다</b> — 민간자격 일정은 중앙 공공데이터가 없으므로 주관기관 공식 페이지/수동 입력
 * (ScheduleEvidenceStatus.MANUAL_REQUIRED)으로만 처리한다. 이 provider 는 절대 일정/날짜를 만들지 않는다.
 */
@Component
public class PrivateCertRegistrationProvider {

    private static final Logger log = LoggerFactory.getLogger(PrivateCertRegistrationProvider.class);
    private static final String SOURCE_NAME = "한국직업능력연구원 민간자격등록정보(민간자격정보서비스)";
    private static final String SOURCE_URL = "https://www.pqi.or.kr/";
    private static final int PER_PAGE = 5;

    private final String serviceKey;
    private final CertificateAliasCatalog aliasCatalog;
    private final String odcloudBaseUrl;
    private final String uddi;
    private final String snapshot;
    private final Duration timeout;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public PrivateCertRegistrationProvider(
            @Value("${careertuner.certificate.data-go-kr.service-key:}") String serviceKey,
            CertificateAliasCatalog aliasCatalog,
            @Value("${careertuner.certificate.data-go-kr.odcloud-base-url:https://api.odcloud.kr/api}")
            String odcloudBaseUrl,
            @Value("${careertuner.certificate.data-go-kr.private-cert-uddi:uddi:fadae7c0-8b78-400d-9581-d4003721de76}")
            String uddi,
            @Value("${careertuner.certificate.data-go-kr.private-cert-snapshot:20251231}") String snapshot,
            @Value("${careertuner.certificate.data-go-kr.timeout-seconds:15}") long timeoutSeconds,
            ObjectMapper objectMapper) {
        this(serviceKey, aliasCatalog, odcloudBaseUrl, uddi, snapshot,
                Duration.ofSeconds(timeoutSeconds <= 0 ? 15 : timeoutSeconds), objectMapper,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    /** 테스트/구성용 생성자. aliasCatalog null 이면 별칭 폴백 없이 동작. */
    PrivateCertRegistrationProvider(String serviceKey, CertificateAliasCatalog aliasCatalog,
                                    String odcloudBaseUrl, String uddi, String snapshot,
                                    Duration timeout, ObjectMapper objectMapper, HttpClient httpClient) {
        this.serviceKey = serviceKey;
        this.aliasCatalog = aliasCatalog;
        this.odcloudBaseUrl = odcloudBaseUrl;
        this.uddi = uddi;
        this.snapshot = snapshot;
        this.timeout = timeout;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public boolean enabled() {
        return serviceKey != null && !serviceKey.isBlank();
    }

    /** 자격명으로 민간자격 등록정보를 조회한다. 실패 시 예외를 던지지 않고 degrade 상태를 반환한다. */
    public PrivateCertRegistrationEvidence lookup(String certName) {
        // 키 없음·입력 없음 = 조회 불가(확인 불가) → UPSTREAM_UNAVAILABLE. 조회조차 안 한 것을 '미등록(NOT_FOUND)'으로
        // 단정하지 않는다. NOT_FOUND 는 정상 조회 후 0건일 때만.
        if (!enabled() || certName == null || certName.isBlank()) {
            return degraded(PrivateCertRegistrationStatus.UPSTREAM_UNAVAILABLE, certName);
        }
        try {
            // serviceKey 는 재인코딩하지 않음(디코딩 키 전제). cond·자격명만 인코딩.
            String cond = enc("cond[자격명::LIKE]");
            String url = odcloudBaseUrl + "/15075600/v1/" + uddi
                    + "?page=1&perPage=" + PER_PAGE + "&returnType=JSON"
                    + "&" + cond + "=" + enc(certName.trim())
                    + "&serviceKey=" + serviceKey;
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(timeout).GET().build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                log.debug("odcloud private-cert non-200: query={} status={}", certName, response.statusCode());
                return degraded(PrivateCertRegistrationStatus.UPSTREAM_UNAVAILABLE, certName);
            }
            PrivateCertRegistrationEvidence evidence = parse(response.body(), certName);
            if (evidence.status() == PrivateCertRegistrationStatus.NOT_FOUND && aliasCatalog != null) {
                // 통용 약어(SQLD 등)는 공식 등록명(SQL 등)과 달라 정상 조회에서도 0건이 된다 — 검증된 별칭으로 1회 재조회.
                // 재조회 결과는 등록명 정규화 일치 행만 채택한다(LIKE 과매칭으로 다른 기관 자격 오귀속 방지).
                String official = aliasCatalog.officialNameFor(certName).orElse(null);
                if (official != null && !QnetXmlSupport.norm(official).equals(QnetXmlSupport.norm(certName.trim()))) {
                    PrivateCertRegistrationEvidence resolved = lookupExact(official, certName);
                    if (resolved != null) {
                        return resolved;
                    }
                }
            }
            return evidence;
        } catch (Exception e) {
            // URL 에 serviceKey 가 들어가므로 예외 메시지(전체 URL 포함 가능)는 로깅하지 않는다 — 타입만.
            log.debug("odcloud private-cert lookup failed: query={} err={}", certName, e.getClass().getSimpleName());
            return degraded(PrivateCertRegistrationStatus.UPSTREAM_UNAVAILABLE, certName);
        }
    }

    /** 응답 파싱(인스턴스 메서드, 테스트 대상). 매칭 0건=NOT_FOUND, 파싱 실패=UPSTREAM_UNAVAILABLE. */
    PrivateCertRegistrationEvidence parse(String body, String query) {
        if (body == null || body.isBlank()) {
            return degraded(PrivateCertRegistrationStatus.UPSTREAM_UNAVAILABLE, query);
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (RuntimeException e) {
            return degraded(PrivateCertRegistrationStatus.UPSTREAM_UNAVAILABLE, query);
        }
        JsonNode data = root.path("data");
        if (!data.isArray()) {
            // data 배열 자체가 없는 200 응답(오류/쿼터 envelope 등 비정상) → 조회 미확증(부재 아님).
            return degraded(PrivateCertRegistrationStatus.UPSTREAM_UNAVAILABLE, query);
        }
        if (data.isEmpty()) {
            // 정상 조회 후 0건 → 실제 미등록.
            return new PrivateCertRegistrationEvidence(PrivateCertRegistrationStatus.NOT_FOUND,
                    query, 0, snapshot, SOURCE_NAME, SOURCE_URL, List.of());
        }
        int matchCount = root.path("matchCount").asInt(data.size());

        List<Match> matches = new ArrayList<>();
        boolean anyActive = false;
        boolean anyAbolished = false;
        for (JsonNode item : data) {
            String current = text(item, "현재상태");
            matches.add(new Match(text(item, "자격명"), text(item, "등록번호"),
                    current, text(item, "신청기관"), text(item, "공인여부")));
            if (current.contains("폐지") || current.contains("취소")) {
                anyAbolished = true;
            } else if (!current.isBlank()) {
                anyActive = true;
            }
        }
        PrivateCertRegistrationStatus status;
        if (anyActive) {
            status = PrivateCertRegistrationStatus.REGISTERED_ACTIVE;
        } else if (anyAbolished) {
            status = PrivateCertRegistrationStatus.ABOLISHED_OR_CANCELLED;
        } else {
            status = PrivateCertRegistrationStatus.REGISTERED_ACTIVE; // 등록정보에 존재(상태 불명) → 최소 실재 확인.
        }
        return new PrivateCertRegistrationEvidence(status, query, matchCount, snapshot,
                SOURCE_NAME, SOURCE_URL, List.copyOf(matches));
    }

    /** 별칭 해석 후 공식 등록명으로 재조회 — 등록명 정규화 일치 행만 채택. 실패/무일치는 null(원 결과 유지). */
    private PrivateCertRegistrationEvidence lookupExact(String officialName, String originalQuery) {
        try {
            String cond = enc("cond[자격명::LIKE]");
            // 별칭 재조회는 공식명이 흔한 부분문자열('SQL' 등)일 수 있어 정확명 행이 첫 페이지 밖으로 밀릴 수 있다 —
            // 여유 있게 받아 정규화 일치 행을 놓치지 않는다(perPage 5 → 20).
            String url = odcloudBaseUrl + "/15075600/v1/" + uddi
                    + "?page=1&perPage=20&returnType=JSON"
                    + "&" + cond + "=" + enc(officialName.trim())
                    + "&serviceKey=" + serviceKey;
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(timeout).GET().build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                return null;
            }
            PrivateCertRegistrationEvidence parsed = parse(response.body(), originalQuery);
            List<Match> exact = parsed.matches().stream()
                    .filter(match -> QnetXmlSupport.nameMatches(match.name(), officialName))
                    .toList();
            if (exact.isEmpty()) {
                return null;
            }
            boolean anyActive = exact.stream().anyMatch(match ->
                    match.currentStatus() != null && !match.currentStatus().contains("폐지")
                            && !match.currentStatus().contains("취소"));
            return new PrivateCertRegistrationEvidence(
                    anyActive ? PrivateCertRegistrationStatus.REGISTERED_ACTIVE
                              : PrivateCertRegistrationStatus.ABOLISHED_OR_CANCELLED,
                    originalQuery, exact.size(), snapshot, SOURCE_NAME, SOURCE_URL, List.copyOf(exact));
        } catch (Exception e) {
            log.debug("odcloud alias lookup failed: err={}", e.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 사용자 대면 검색 — 목록과 함께 <b>조회 상태를 보존</b>해 반환한다. 상태를 버리고 빈 목록만 주면
     * 런타임 실패(UPSTREAM_UNAVAILABLE)와 정상 0건(NOT_FOUND)을 호출부가 구분하지 못해 '오류'가 '부재'로
     * 표시되므로(오류≠부재), {@link PrivateCertRegistrationEvidence}(status+matches)를 그대로 넘긴다.
     */
    public PrivateCertRegistrationEvidence search(String query, int limit) {
        if (!enabled() || query == null || query.isBlank()) {
            return degraded(PrivateCertRegistrationStatus.UPSTREAM_UNAVAILABLE, query);
        }
        try {
            String cond = enc("cond[자격명::LIKE]");
            String url = odcloudBaseUrl + "/15075600/v1/" + uddi
                    + "?page=1&perPage=" + Math.max(1, Math.min(20, limit)) + "&returnType=JSON"
                    + "&" + cond + "=" + enc(query.trim())
                    + "&serviceKey=" + serviceKey;
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(timeout).GET().build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                return degraded(PrivateCertRegistrationStatus.UPSTREAM_UNAVAILABLE, query);
            }
            return parse(response.body(), query);
        } catch (Exception e) {
            log.debug("odcloud private-cert search failed: err={}", e.getClass().getSimpleName());
            return degraded(PrivateCertRegistrationStatus.UPSTREAM_UNAVAILABLE, query);
        }
    }

    private PrivateCertRegistrationEvidence degraded(PrivateCertRegistrationStatus status, String query) {
        return new PrivateCertRegistrationEvidence(status, query, 0, snapshot, SOURCE_NAME, SOURCE_URL, List.of());
    }

    private static String text(JsonNode item, String field) {
        return item.path(field).asText("").trim();
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
