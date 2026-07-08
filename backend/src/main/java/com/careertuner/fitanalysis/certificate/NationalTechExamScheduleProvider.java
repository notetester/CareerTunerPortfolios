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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.careertuner.fitanalysis.certificate.CertificateScheduleEvidence.ScheduleRound;

/**
 * 국가기술자격 종목별 시험일정 provider — 공공데이터 15003029 {@code InquiryTestInformationNTQSVC/getJMList}
 * (종목코드 jmCd 로 현재연도 시행일정 조회). <b>확인된 것만 말하고 불확실하면 날짜를 만들지 않는다</b>는 원칙에 따라,
 * 키 없음·타임아웃·오류·빈 응답은 모두 {@link ScheduleEvidenceStatus#NOT_FOUND}/OFFICIAL_NO_SCHEDULE 로 degrade 한다.
 *
 * <p>이 q-net API 는 upstream 이 간헐적으로 불안정하다(정상 응답 envelope 안에 resultCode 99 =
 * "SocketTimeoutException" 이 담겨 옴). 그런 응답은 절대 성공으로 취급하지 않고 NOT_FOUND 로 내린다.
 *
 * <p>파싱은 <b>태그명 기반(중첩 무관)</b>이라 {@code <items><item>} 래핑 여부와 무관하게 필드를 찾는다.
 * getJMList 응답 필드는 소문자(docregstartdt 등)라 대소문자 무시로 매칭한다.
 */
@Component
public class NationalTechExamScheduleProvider {

    private static final Logger log = LoggerFactory.getLogger(NationalTechExamScheduleProvider.class);
    private static final String SOURCE_NAME = "한국산업인력공단 큐넷(Q-Net) 국가기술자격 시험정보";
    private static final String OPERATION = "/InquiryTestInformationNTQSVC/getJMList";

    private final String serviceKey;
    private final String qnetBaseUrl;
    private final Duration timeout;
    private final HttpClient httpClient;

    public NationalTechExamScheduleProvider(
            @Value("${careertuner.certificate.data-go-kr.service-key:}") String serviceKey,
            @Value("${careertuner.certificate.data-go-kr.qnet-base-url:http://openapi.q-net.or.kr/api/service/rest}")
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
     * 종목코드로 시험일정을 조회한다. 어떤 실패(키 없음/타임아웃/오류/빈 응답)든 예외를 던지지 않고 degrade 상태를 반환한다.
     *
     * @param jmCd        국가기술자격 종목코드(4자리)
     * @param certNameHint 공식 응답에 종목명이 없을 때 사용할 힌트(없으면 null)
     */
    public CertificateScheduleEvidence lookup(String jmCd, String certNameHint) {
        if (!enabled() || jmCd == null || jmCd.isBlank()) {
            return degraded(ScheduleEvidenceStatus.NOT_FOUND, jmCd, certNameHint);
        }
        try {
            String url = qnetBaseUrl + OPERATION
                    + "?serviceKey=" + enc(serviceKey) + "&jmCd=" + enc(jmCd.trim());
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(timeout).GET().build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                log.debug("getJMList non-200: jmCd={} status={}", jmCd, response.statusCode());
                return degraded(ScheduleEvidenceStatus.NOT_FOUND, jmCd, certNameHint);
            }
            return parse(response.body(), jmCd, certNameHint);
        } catch (Exception e) {
            // 타임아웃/네트워크/파싱 실패 → 날짜를 만들지 않고 조용히 degrade.
            log.debug("getJMList lookup failed: jmCd={} err={}", jmCd, e.toString());
            return degraded(ScheduleEvidenceStatus.NOT_FOUND, jmCd, certNameHint);
        }
    }

    /** 응답 본문 파싱(순수 함수, 테스트 대상). resultCode 가 정상(00)이 아니면 NOT_FOUND. */
    static CertificateScheduleEvidence parse(String xml, String jmCd, String certNameHint) {
        if (xml == null || xml.isBlank()) {
            return degraded(ScheduleEvidenceStatus.NOT_FOUND, jmCd, certNameHint);
        }
        String resultCode = tagValue(xml, "resultCode");
        if (resultCode != null && !resultCode.equals("00") && !resultCode.equals("0")) {
            // 예: 99 = q-net upstream SocketTimeout. 정상 응답 아님 → 날짜 미생성.
            return degraded(ScheduleEvidenceStatus.NOT_FOUND, jmCd, certNameHint);
        }

        List<String> itemBlocks = allBlocks(xml, "item");
        if (itemBlocks.isEmpty() && hasScheduleField(xml)) {
            itemBlocks = List.of(xml); // <item> 래핑이 아니어도 필드가 있으면 전체를 한 회차로.
        }

        List<ScheduleRound> rounds = new ArrayList<>();
        String certName = certNameHint;
        for (String block : itemBlocks) {
            if (certName == null || certName.isBlank()) {
                certName = tagValue(block, "jmfldmm");
            }
            ScheduleRound round = new ScheduleRound(
                    tagValue(block, "implplannm"),
                    tagValue(block, "docregstartdt"),
                    tagValue(block, "docregenddt"),
                    tagValue(block, "docexamstartdt"),
                    tagValue(block, "docpassdt"),
                    tagValue(block, "pracexamstartdt"),
                    tagValue(block, "pracexamenddt"),
                    tagValue(block, "pracpassstartdt"));
            if (hasAnyDate(round)) {
                rounds.add(round);
            }
        }

        if (rounds.isEmpty()) {
            return new CertificateScheduleEvidence(ScheduleEvidenceStatus.OFFICIAL_NO_SCHEDULE,
                    jmCd, certNameHint, SOURCE_NAME, sourceUrl(jmCd), List.of());
        }
        return new CertificateScheduleEvidence(ScheduleEvidenceStatus.VERIFIED_CURRENT,
                jmCd, certName, SOURCE_NAME, sourceUrl(jmCd), List.copyOf(rounds));
    }

    private static CertificateScheduleEvidence degraded(ScheduleEvidenceStatus status, String jmCd, String certName) {
        return new CertificateScheduleEvidence(status, jmCd, certName, SOURCE_NAME, sourceUrl(jmCd), List.of());
    }

    private static String sourceUrl(String jmCd) {
        return "https://www.q-net.or.kr/";
    }

    private static boolean hasAnyDate(ScheduleRound r) {
        return notBlank(r.docRegStart()) || notBlank(r.docExam()) || notBlank(r.docPass())
                || notBlank(r.pracExamStart()) || notBlank(r.pracPass());
    }

    private static boolean hasScheduleField(String xml) {
        String lower = xml.toLowerCase();
        return lower.contains("docregstartdt") || lower.contains("implplannm") || lower.contains("docexamstartdt");
    }

    private static String tagValue(String xml, String tag) {
        Matcher m = Pattern.compile("<" + tag + ">(.*?)</" + tag + ">",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(xml);
        if (m.find()) {
            String v = m.group(1).trim();
            return v.isEmpty() ? null : v;
        }
        return null;
    }

    private static List<String> allBlocks(String xml, String tag) {
        List<String> blocks = new ArrayList<>();
        Matcher m = Pattern.compile("<" + tag + ">(.*?)</" + tag + ">",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(xml);
        while (m.find()) {
            blocks.add(m.group(1));
        }
        return blocks;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
