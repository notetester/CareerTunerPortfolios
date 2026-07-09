package com.careertuner.fitanalysis.certificate;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpClient;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.careertuner.fitanalysis.certificate.CertificateScheduleEvidence.ScheduleRound;

class NationalTechExamScheduleProviderTest {

    private static String item(String name, String impl, String docExam, String docPass, String pracPass) {
        return "<item><jmfldmm>" + name + "</jmfldmm><implplannm>" + impl + "</implplannm>"
                + "<docregstartdt>20260113</docregstartdt><docregenddt>20260116</docregenddt>"
                + "<docexamstartdt>" + docExam + "</docexamstartdt><docexamenddt>" + docExam + "</docexamenddt>"
                + "<docpassdt>" + docPass + "</docpassdt>"
                + "<pracexamstartdt>20260419</pracexamstartdt><pracexamenddt>20260504</pracexamenddt>"
                + "<pracpassstartdt>" + pracPass + "</pracpassstartdt><pracpassenddt>" + pracPass + "</pracpassenddt></item>";
    }

    private static String ok(String... items) {
        return "<response><header><resultCode>00</resultCode><resultMsg>NORMAL SERVICE.</resultMsg></header>"
                + "<body><items>" + String.join("", items) + "</items></body></response>";
    }

    @Test
    void verifiedCurrentParsesMatchingCertRound() {
        CertificateScheduleEvidence e = NationalTechExamScheduleProvider.parse(
                ok(item("정보처리기사", "기사(2026년도 제1회)", "20260215", "20260312", "20260530")), "정보처리기사");

        assertThat(e.status()).isEqualTo(ScheduleEvidenceStatus.VERIFIED_CURRENT);
        assertThat(e.certName()).isEqualTo("정보처리기사");
        assertThat(e.rounds()).hasSize(1);
        ScheduleRound r = e.rounds().get(0);
        assertThat(r.docExam()).isEqualTo("20260215");
        assertThat(r.docPass()).isEqualTo("20260312");
        assertThat(r.pracPass()).isEqualTo("20260530");
    }

    @Test
    void filtersToRequestedCertAndDoesNotMergeOtherCerts() {
        // getJMList 는 연도 전체 종목을 반환한다 — 요청 자격 외 다른 종목의 날짜를 섞어 조작하면 안 된다(핵심 불변식).
        String yearList = ok(
                item("정보처리기사", "기사(2026-1)", "20260215", "20260312", "20260530"),
                item("정보처리기능사", "기능사(2026-1)", "20260126", "20260221", "20260418"),
                item("정보처리산업기사", "산업기사(2026-1)", "20260301", "20260320", "20260601"));
        CertificateScheduleEvidence e = NationalTechExamScheduleProvider.parse(yearList, "정보처리기사");

        assertThat(e.status()).isEqualTo(ScheduleEvidenceStatus.VERIFIED_CURRENT);
        assertThat(e.rounds()).hasSize(1);
        assertThat(e.rounds().get(0).docExam()).isEqualTo("20260215"); // 기사만, 기능사/산업기사 아님
    }

    @Test
    void gatewayErrorEnvelopeDegradesToUpstreamUnavailable() {
        // data.go.kr/q-net 인증·쿼터 오류는 HTTP 200 + resultCode 없는 OpenAPI_ServiceResponse envelope — 장애≠부재.
        String gatewayError = "<OpenAPI_ServiceResponse><cmmMsgHeader>"
                + "<returnReasonCode>30</returnReasonCode>"
                + "<returnAuthMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</returnAuthMsg>"
                + "<errMsg>SERVICE ERROR</errMsg></cmmMsgHeader></OpenAPI_ServiceResponse>";
        CertificateScheduleEvidence e = NationalTechExamScheduleProvider.parse(gatewayError, "정보처리기사");

        assertThat(e.status()).isEqualTo(ScheduleEvidenceStatus.UPSTREAM_UNAVAILABLE);
        assertThat(e.rounds()).isEmpty();
    }

    @Test
    void resultCode99DegradesToUpstreamUnavailable() {
        String timeout = "<response><header><resultCode>99</resultCode>"
                + "<resultMsg>java.net.SocketTimeoutException: connect timed out</resultMsg></header></response>";
        assertThat(NationalTechExamScheduleProvider.parse(timeout, "정보처리기사").status())
                .isEqualTo(ScheduleEvidenceStatus.UPSTREAM_UNAVAILABLE);
    }

    @Test
    void confirmedNormalButCertNotInListIsOfficialNoSchedule() {
        // resultCode 00 확증 + 요청 자격이 목록에 없음 → 이번 연도 미편성(부재).
        CertificateScheduleEvidence e = NationalTechExamScheduleProvider.parse(
                ok(item("정보처리기능사", "기능사(2026-1)", "20260126", "20260221", "20260418")), "정보처리기사");

        assertThat(e.status()).isEqualTo(ScheduleEvidenceStatus.OFFICIAL_NO_SCHEDULE);
        assertThat(e.rounds()).isEmpty();
    }

    @Test
    void resultCodeAbsentWithoutMatchIsUpstreamNotAbsence() {
        // resultCode 를 확증하지 못한 응답(빈/모호)은 '부재'로 단정하지 않는다.
        String ambiguous = "<response><body><items></items></body></response>";
        assertThat(NationalTechExamScheduleProvider.parse(ambiguous, "정보처리기사").status())
                .isEqualTo(ScheduleEvidenceStatus.UPSTREAM_UNAVAILABLE);
    }

    @Test
    void nestingAgnosticParsesWithoutItemWrapperWhenNameMatches() {
        String noItem = "<response><header><resultCode>00</resultCode></header><body>"
                + "<jmfldmm>정보처리기능사</jmfldmm><implplannm>기능사(2026-1)</implplannm>"
                + "<docexamstartdt>20260126</docexamstartdt><docpassdt>20260221</docpassdt>"
                + "</body></response>";
        CertificateScheduleEvidence e = NationalTechExamScheduleProvider.parse(noItem, "정보처리기능사");

        assertThat(e.status()).isEqualTo(ScheduleEvidenceStatus.VERIFIED_CURRENT);
        assertThat(e.rounds()).hasSize(1);
        assertThat(e.rounds().get(0).docExam()).isEqualTo("20260126");
    }

    @Test
    void truncatedListWithoutMatchIsUpstreamNotAbsence() {
        // totalCount(5000) > 수신 item(1) → 목록 잘림. 무매칭을 OFFICIAL_NO_SCHEDULE(부재)로 단정하지 않는다.
        String truncated = "<response><header><resultCode>00</resultCode></header><body><totalCount>5000</totalCount><items>"
                + item("정보처리기능사", "기능사(2026-1)", "20260126", "20260221", "20260418") + "</items></body></response>";
        assertThat(NationalTechExamScheduleProvider.parse(truncated, "정보처리기사").status())
                .isEqualTo(ScheduleEvidenceStatus.UPSTREAM_UNAVAILABLE);
    }

    @Test
    void blankKeyDoesNotCallApiAndDegrades() {
        NationalTechExamScheduleProvider provider = new NationalTechExamScheduleProvider(
                "", "http://unused.invalid", Duration.ofSeconds(1), HttpClient.newHttpClient());

        assertThat(provider.enabled()).isFalse();
        assertThat(provider.lookup("정보처리기사").status()).isEqualTo(ScheduleEvidenceStatus.UPSTREAM_UNAVAILABLE);
    }

    @Test
    void emptyBodyDegradesToUpstreamUnavailable() {
        assertThat(NationalTechExamScheduleProvider.parse("", "정보처리기사").status())
                .isEqualTo(ScheduleEvidenceStatus.UPSTREAM_UNAVAILABLE);
    }
}
