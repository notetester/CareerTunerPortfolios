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
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.careertuner.fitanalysis.certificate.CertificateScheduleEvidence.ScheduleRound;

/**
 * 국가기술자격 종목별 시험일정 provider — 공공데이터 15003029 {@code InquiryTestInformationNTQSVC/getJMList}
 * (요청변수 {@code stdt}=조회시작년도; 해당 연도의 <b>전체 종목</b> 시행일정 목록을 반환). 특정 자격의 일정은
 * 응답을 자격명(jmfldmm)으로 <b>정확 매칭 필터</b>해서 얻는다 — 다른 종목의 날짜를 섞어 조작하지 않는다.
 *
 * <p><b>오류≠부재 원칙:</b> 게이트웨이 오류 envelope(HTTP 200 + resultCode 없음)·타임아웃·resultCode≠00·정상(00)
 * 미확증·목록 잘림은 모두 {@link ScheduleEvidenceStatus#UPSTREAM_UNAVAILABLE} 로 내린다.
 * {@link ScheduleEvidenceStatus#OFFICIAL_NO_SCHEDULE} 는 정상(00)을 확증하고 목록이 잘리지 않았는데도 해당 자격
 * 회차가 없을 때만 쓴다. C 는 장애 시 "일정이 없습니다"가 아니라 "공식 API 가 일시적으로 응답하지 않아 확인하지
 * 못했습니다"라고 말해야 한다.
 *
 * <p>serviceKey 는 재인코딩하지 않는다(디코딩 raw 키 전제). 공용 파싱 규칙은 {@link QnetXmlSupport}.
 */
@Component
public class NationalTechExamScheduleProvider {

    private static final Logger log = LoggerFactory.getLogger(NationalTechExamScheduleProvider.class);
    private static final String SOURCE_NAME = "한국산업인력공단 큐넷(Q-Net) 국가기술자격 시험정보";
    private static final String SOURCE_URL = "https://www.q-net.or.kr/";
    private static final String OPERATION = "/InquiryTestInformationNTQSVC/getJMList";
    private static final int NUM_OF_ROWS = 2000; // 연도 전체 종목을 한 페이지로(기본값 truncation 방지).

    private final String serviceKey;
    private final String qnetBaseUrl;
    private final Duration timeout;
    private final HttpClient httpClient;

    public NationalTechExamScheduleProvider(
            @Value("${careertuner.certificate.data-go-kr.service-key:}") String serviceKey,
            @Value("${careertuner.certificate.data-go-kr.qnet-base-url:https://openapi.q-net.or.kr/api/service/rest}")
            String qnetBaseUrl,
            @Value("${careertuner.certificate.data-go-kr.timeout-seconds:15}") long timeoutSeconds) {
        this(serviceKey, qnetBaseUrl, Duration.ofSeconds(timeoutSeconds <= 0 ? 15 : timeoutSeconds),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    /** 테스트/구성용 생성자. */
    NationalTechExamScheduleProvider(String serviceKey, String qnetBaseUrl, Duration timeout, HttpClient httpClient) {
        this.serviceKey = serviceKey;
        this.qnetBaseUrl = qnetBaseUrl;
        this.timeout = timeout;
        this.httpClient = httpClient;
    }

    /** 조회 가능 여부(키 설정됨). false 면 상위는 일정 근거 없이 진행한다. */
    public boolean enabled() {
        return serviceKey != null && !serviceKey.isBlank();
    }

    /**
     * 자격명으로 현재연도 시험일정을 조회한다. 어떤 실패(키 없음/입력 없음/타임아웃/오류/미확증)든 예외를 던지지 않고
     * degrade 상태를 반환한다.
     *
     * @param certName 국가기술자격 종목명(예: "정보처리기사"). getJMList 응답의 jmfldmm 과 정확 매칭한다.
     */
    public CertificateScheduleEvidence lookup(String certName) {
        if (!enabled() || certName == null || certName.isBlank()) {
            return degraded(ScheduleEvidenceStatus.UPSTREAM_UNAVAILABLE, certName);
        }
        try {
            // serviceKey 는 재인코딩하지 않음(디코딩 키 전제). stdt=현재연도, numOfRows 로 전체.
            String url = qnetBaseUrl + OPERATION + "?serviceKey=" + serviceKey
                    + "&stdt=" + Year.now().getValue() + "&numOfRows=" + NUM_OF_ROWS + "&pageNo=1";
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(timeout).GET().build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                log.debug("getJMList non-200: cert={} status={}", certName, response.statusCode());
                return degraded(ScheduleEvidenceStatus.UPSTREAM_UNAVAILABLE, certName);
            }
            return parse(response.body(), certName);
        } catch (Exception e) {
            // URL 에 serviceKey 가 들어가므로 예외 메시지(전체 URL 포함 가능)는 로깅하지 않는다 — 타입만.
            log.debug("getJMList lookup failed: cert={} err={}", certName, e.getClass().getSimpleName());
            return degraded(ScheduleEvidenceStatus.UPSTREAM_UNAVAILABLE, certName);
        }
    }

    /**
     * 응답 본문 파싱(순수 함수). 게이트웨이 오류·resultCode≠00·정상 미확증·목록 잘림은 UPSTREAM_UNAVAILABLE;
     * 정상(00) 확증 + 목록 완전 + 해당 자격 회차 없음은 OFFICIAL_NO_SCHEDULE; 자격명 매칭 회차가 있으면 VERIFIED_CURRENT.
     */
    static CertificateScheduleEvidence parse(String xml, String certName) {
        if (xml == null || xml.isBlank() || QnetXmlSupport.isGatewayError(xml)
                || QnetXmlSupport.explicitErrorCode(xml)) {
            return degraded(ScheduleEvidenceStatus.UPSTREAM_UNAVAILABLE, certName);
        }

        List<ScheduleRound> rounds = new ArrayList<>();
        List<String> itemBlocks = QnetXmlSupport.allBlocks(xml, "item");
        if (!itemBlocks.isEmpty()) {
            for (String block : itemBlocks) {
                if (QnetXmlSupport.nameMatches(QnetXmlSupport.tagValue(block, "jmfldmm"), certName)) {
                    addIfDated(rounds, block);
                }
            }
        } else if (hasScheduleField(xml) && QnetXmlSupport.nameMatches(QnetXmlSupport.tagValue(xml, "jmfldmm"), certName)) {
            // <item> 래핑이 아니어도 필드가 있고 자격명이 맞으면 한 회차로.
            addIfDated(rounds, xml);
        }

        if (!rounds.isEmpty()) {
            return new CertificateScheduleEvidence(ScheduleEvidenceStatus.VERIFIED_CURRENT,
                    null, certName, SOURCE_NAME, SOURCE_URL, List.copyOf(rounds));
        }
        // 정상(00) 확증 + 목록 완전 + 해당 자격 회차 없음 → 이번 연도 미편성(부재). 잘렸으면 부재로 단정하지 않음.
        if (QnetXmlSupport.normalConfirmed(xml) && !QnetXmlSupport.truncated(xml)) {
            return new CertificateScheduleEvidence(ScheduleEvidenceStatus.OFFICIAL_NO_SCHEDULE,
                    null, certName, SOURCE_NAME, SOURCE_URL, List.of());
        }
        return degraded(ScheduleEvidenceStatus.UPSTREAM_UNAVAILABLE, certName);
    }

    private static void addIfDated(List<ScheduleRound> rounds, String block) {
        ScheduleRound round = new ScheduleRound(
                QnetXmlSupport.tagValue(block, "implplannm"),
                QnetXmlSupport.tagValue(block, "docregstartdt"),
                QnetXmlSupport.tagValue(block, "docregenddt"),
                QnetXmlSupport.tagValue(block, "docexamstartdt"),
                QnetXmlSupport.tagValue(block, "docpassdt"),
                QnetXmlSupport.tagValue(block, "pracexamstartdt"),
                QnetXmlSupport.tagValue(block, "pracexamenddt"),
                QnetXmlSupport.tagValue(block, "pracpassstartdt"));
        if (hasAnyDate(round)) {
            rounds.add(round);
        }
    }

    private static CertificateScheduleEvidence degraded(ScheduleEvidenceStatus status, String certName) {
        return new CertificateScheduleEvidence(status, null, certName, SOURCE_NAME, SOURCE_URL, List.of());
    }

    private static boolean hasAnyDate(ScheduleRound r) {
        return notBlank(r.docRegStart()) || notBlank(r.docExam()) || notBlank(r.docPass())
                || notBlank(r.pracExamStart()) || notBlank(r.pracPass());
    }

    private static boolean hasScheduleField(String xml) {
        String lower = xml.toLowerCase(Locale.ROOT);
        return lower.contains("docregstartdt") || lower.contains("implplannm") || lower.contains("docexamstartdt");
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
