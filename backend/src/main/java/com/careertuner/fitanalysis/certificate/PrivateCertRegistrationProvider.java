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
    private final String odcloudBaseUrl;
    private final String uddi;
    private final String snapshot;
    private final Duration timeout;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public PrivateCertRegistrationProvider(
            @Value("${careertuner.certificate.data-go-kr.service-key:}") String serviceKey,
            @Value("${careertuner.certificate.data-go-kr.odcloud-base-url:https://api.odcloud.kr/api}")
            String odcloudBaseUrl,
            @Value("${careertuner.certificate.data-go-kr.private-cert-uddi:uddi:fadae7c0-8b78-400d-9581-d4003721de76}")
            String uddi,
            @Value("${careertuner.certificate.data-go-kr.private-cert-snapshot:20251231}") String snapshot,
            @Value("${careertuner.certificate.data-go-kr.timeout-seconds:15}") long timeoutSeconds,
            ObjectMapper objectMapper) {
        this(serviceKey, odcloudBaseUrl, uddi, snapshot,
                Duration.ofSeconds(timeoutSeconds <= 0 ? 15 : timeoutSeconds), objectMapper,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    /** 테스트/구성용 생성자. */
    PrivateCertRegistrationProvider(String serviceKey, String odcloudBaseUrl, String uddi, String snapshot,
                                    Duration timeout, ObjectMapper objectMapper, HttpClient httpClient) {
        this.serviceKey = serviceKey;
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
            return parse(response.body(), certName);
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
        if (!data.isArray() || data.isEmpty()) {
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
