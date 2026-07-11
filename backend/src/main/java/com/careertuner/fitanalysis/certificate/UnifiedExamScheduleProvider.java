package com.careertuner.fitanalysis.certificate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.careertuner.fitanalysis.certificate.CertificateScheduleEvidence.ScheduleRound;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 통합 국가자격 시험일정 provider — 공공데이터 15074408 {@code B490007/qualExamSchd/getQualExamSchdList}
 * (apis.data.go.kr, JSON). 죽어 있는 구형 {@code openapi.q-net.or.kr}(getJMList)와 <b>별개 호스트</b>라
 * Q-Net 게이트웨이 장애의 영향을 받지 않는다(2026-07-11 실측: resultCode 00, 국가기술 481종 오류 0).
 *
 * <p><b>jmCd(종목코드) 기반 종목별 조회 전용</b> — 응답에 종목명이 없어(description 에 등급·회차만) 이름 매칭이
 * 불가능하므로, jmCd 없는 조회는 하지 않는다(다른 종목 일정을 이 종목 것으로 오귀속하는 사고 방지). jmCd 는
 * {@link NationalQualificationOfflineCatalog} 의 검증된 매핑에서 온다.
 *
 * <p>실측 제약: {@code numOfRows} 최대 50(초과 시 resultCode 930), implYy 당해연도 전용. 상설검정 종목은
 * 회차가 50 을 넘을 수 있어(최대 41 관측) totalCount 가 수신량보다 크면 추가 페이지를 읽고, 그래도 잘리면
 * 부재 단정 없이 UPSTREAM_UNAVAILABLE. resultCode 00 + 0건은 당해연도 미편성(OFFICIAL_NO_SCHEDULE) —
 * 이 API 는 00 이 진짜 정상 확증이다(구형 게이트웨이의 오류 envelope 혼동 없음).
 */
@Component
public class UnifiedExamScheduleProvider {

    private static final Logger log = LoggerFactory.getLogger(UnifiedExamScheduleProvider.class);
    private static final String SOURCE_NAME = "한국산업인력공단 국가자격 시험일정(공공데이터포털 통합 조회)";
    private static final String SOURCE_URL = "https://www.q-net.or.kr/";
    private static final int NUM_OF_ROWS = 50; // API 상한(초과 시 930)
    private static final int MAX_PAGES = 3;    // 상설검정 최대 41회차 관측 — 150 이면 충분, 무한 루프 방지

    private final String serviceKey;
    private final String baseUrl;
    private final Duration timeout;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public UnifiedExamScheduleProvider(
            @Value("${careertuner.certificate.data-go-kr.service-key:}") String serviceKey,
            @Value("${careertuner.certificate.data-go-kr.unified-base-url:https://apis.data.go.kr/B490007}")
            String baseUrl,
            @Value("${careertuner.certificate.data-go-kr.timeout-seconds:15}") long timeoutSeconds,
            ObjectMapper objectMapper) {
        this(serviceKey, baseUrl, Duration.ofSeconds(timeoutSeconds <= 0 ? 15 : timeoutSeconds), objectMapper,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    /** 테스트/구성용 생성자. */
    UnifiedExamScheduleProvider(String serviceKey, String baseUrl, Duration timeout,
                                ObjectMapper objectMapper, HttpClient httpClient) {
        this.serviceKey = serviceKey;
        this.baseUrl = baseUrl;
        this.timeout = timeout;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public boolean enabled() {
        return serviceKey != null && !serviceKey.isBlank();
    }

    /**
     * 종목코드로 당해연도 시행일정을 조회한다. jmCd 가 없으면 조회하지 않고 UPSTREAM_UNAVAILABLE
     * (호출부가 레거시 경로로 폴백). 실패 시 예외 없이 degrade.
     */
    public CertificateScheduleEvidence lookup(String jmCd, String certName) {
        if (!enabled() || jmCd == null || jmCd.isBlank()) {
            return degraded(jmCd, certName);
        }
        try {
            List<ScheduleRound> rounds = new ArrayList<>();
            int received = 0;
            int totalCount = Integer.MAX_VALUE;
            for (int page = 1; page <= MAX_PAGES && received < totalCount; page++) {
                // serviceKey 는 재인코딩하지 않음(디코딩 raw 키 전제). jmCd 는 4자리 숫자라 인코딩 불요.
                String url = baseUrl + "/qualExamSchd/getQualExamSchdList"
                        + "?serviceKey=" + serviceKey + "&numOfRows=" + NUM_OF_ROWS + "&pageNo=" + page
                        + "&dataFormat=json&implYy=" + Year.now().getValue() + "&jmCd=" + jmCd.trim();
                HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(timeout).GET().build();
                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() != 200) {
                    log.debug("unified schedule non-200: jmCd={} status={}", jmCd, response.statusCode());
                    return degraded(jmCd, certName);
                }
                PageResult result = parsePage(response.body());
                if (result == null) {
                    return degraded(jmCd, certName);
                }
                totalCount = result.totalCount;
                received += result.itemCount;
                rounds.addAll(result.rounds);
            }
            if (received < totalCount) {
                // MAX_PAGES 로도 못 다 받음(비정상 규모) — 잘린 목록으로 일정을 말하지 않는다.
                log.debug("unified schedule truncated: jmCd={} received={} total={}", jmCd, received, totalCount);
                return degraded(jmCd, certName);
            }
            if (received == 0) {
                // resultCode 00 정상 확증 + 수신 item 0건 → 당해연도 시행계획 미편성(확인된 부재).
                return new CertificateScheduleEvidence(ScheduleEvidenceStatus.OFFICIAL_NO_SCHEDULE,
                        jmCd, certName, SOURCE_NAME, SOURCE_URL, List.of());
            }
            if (rounds.isEmpty()) {
                // item 은 있는데 파싱 가능한 날짜가 하나도 없음(상류 필드명 변경·공고 전 빈 날짜 등) —
                // '미편성 확증'이 아니라 확인 실패다(00+0건만 미편성). degrade 해야 레거시 폴백도 살아난다.
                log.debug("unified schedule items dateless: jmCd={} received={}", jmCd, received);
                return degraded(jmCd, certName);
            }
            return new CertificateScheduleEvidence(ScheduleEvidenceStatus.VERIFIED_CURRENT,
                    jmCd, certName, SOURCE_NAME, SOURCE_URL, List.copyOf(rounds));
        } catch (Exception e) {
            // URL 에 serviceKey 가 들어가므로 예외 메시지(전체 URL 포함 가능)는 로깅하지 않는다 — 타입만.
            log.debug("unified schedule lookup failed: jmCd={} err={}", jmCd, e.getClass().getSimpleName());
            return degraded(jmCd, certName);
        }
    }

    /** 한 페이지 파싱 결과 — resultCode≠00/형식 이상은 null(호출부가 degrade). */
    record PageResult(int totalCount, int itemCount, List<ScheduleRound> rounds) {
    }

    /** 응답 한 페이지 파싱(인스턴스 메서드, 테스트 대상). */
    PageResult parsePage(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (RuntimeException e) {
            return null;
        }
        if (!"00".equals(root.path("header").path("resultCode").asText(""))) {
            return null;
        }
        JsonNode bodyNode = root.path("body");
        JsonNode items = bodyNode.path("items");
        // items 배열과 숫자 totalCount 둘 다 있어야 완전 수신을 판정할 수 있다 — 하나라도 빠지면 형식 미확증(degrade).
        // (0건 응답도 items=[] + totalCount=0 을 반환함을 실측 확인 — 누락은 정상 응답이 아니다.)
        if (!items.isArray() || !bodyNode.path("totalCount").isNumber()) {
            return null;
        }
        List<ScheduleRound> rounds = new ArrayList<>();
        for (JsonNode item : items) {
            ScheduleRound round = new ScheduleRound(
                    text(item, "description"),
                    text(item, "docRegStartDt"),
                    text(item, "docRegEndDt"),
                    text(item, "docExamStartDt"),
                    text(item, "docPassDt"),
                    text(item, "pracExamStartDt"),
                    text(item, "pracExamEndDt"),
                    text(item, "pracPassDt"));
            if (hasAnyDate(round)) {
                rounds.add(round);
            }
        }
        return new PageResult(bodyNode.path("totalCount").asInt(), items.size(), rounds);
    }

    private static boolean hasAnyDate(ScheduleRound r) {
        return notBlank(r.docRegStart()) || notBlank(r.docExam()) || notBlank(r.docPass())
                || notBlank(r.pracExamStart()) || notBlank(r.pracPass());
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String text(JsonNode item, String field) {
        String v = item.path(field).asText("").trim();
        return v.isEmpty() ? null : v;
    }

    private CertificateScheduleEvidence degraded(String jmCd, String certName) {
        return new CertificateScheduleEvidence(ScheduleEvidenceStatus.UPSTREAM_UNAVAILABLE,
                jmCd, certName, SOURCE_NAME, SOURCE_URL, List.of());
    }
}
