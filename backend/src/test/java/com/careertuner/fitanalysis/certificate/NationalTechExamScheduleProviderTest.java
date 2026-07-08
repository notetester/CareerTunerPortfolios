package com.careertuner.fitanalysis.certificate;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpClient;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.careertuner.fitanalysis.certificate.CertificateScheduleEvidence.ScheduleRound;

class NationalTechExamScheduleProviderTest {

    // 실제로 캡처한 q-net upstream 타임아웃 응답(정상 envelope 안에 resultCode 99). 성공으로 취급하면 안 된다.
    private static final String REAL_UPSTREAM_TIMEOUT =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<response><header><resultCode>99</resultCode>"
            + "<resultMsg>java.net.SocketTimeoutException: connect timed out</resultMsg></header></response>";

    private static final String VERIFIED_XML =
            "<response><header><resultCode>00</resultCode><resultMsg>NORMAL SERVICE.</resultMsg></header>"
            + "<body><items><item>"
            + "<jmfldmm>정보처리기사</jmfldmm><implplannm>기사(2026년도 제1회)</implplannm>"
            + "<docregstartdt>20260113</docregstartdt><docregenddt>20260116</docregenddt>"
            + "<docexamstartdt>20260215</docexamstartdt><docexamenddt>20260215</docexamenddt>"
            + "<docpassdt>20260312</docpassdt>"
            + "<pracregstartdt>20260324</pracregstartdt><pracregenddt>20260327</pracregenddt>"
            + "<pracexamstartdt>20260419</pracexamstartdt><pracexamenddt>20260504</pracexamenddt>"
            + "<pracpassstartdt>20260530</pracpassstartdt><pracpassenddt>20260530</pracpassenddt>"
            + "</item></items></body></response>";

    private static final String OK_EMPTY_XML =
            "<response><header><resultCode>00</resultCode><resultMsg>NORMAL SERVICE.</resultMsg></header>"
            + "<body><items></items></body></response>";

    @Test
    void verifiedCurrentParsesRoundDates() {
        CertificateScheduleEvidence e = NationalTechExamScheduleProvider.parse(VERIFIED_XML, "1320", null);

        assertThat(e.status()).isEqualTo(ScheduleEvidenceStatus.VERIFIED_CURRENT);
        assertThat(e.certName()).isEqualTo("정보처리기사");
        assertThat(e.rounds()).hasSize(1);
        ScheduleRound r = e.rounds().get(0);
        assertThat(r.round()).isEqualTo("기사(2026년도 제1회)");
        assertThat(r.docExam()).isEqualTo("20260215");
        assertThat(r.docPass()).isEqualTo("20260312");
        assertThat(r.pracPass()).isEqualTo("20260530");
        assertThat(e.sourceName()).contains("Q-Net");
    }

    @Test
    void realUpstreamTimeoutCode99DegradesToNotFound() {
        // 실제 q-net 불안정 응답 — 절대 성공으로 취급하지 않고 날짜를 만들지 않는다.
        CertificateScheduleEvidence e =
                NationalTechExamScheduleProvider.parse(REAL_UPSTREAM_TIMEOUT, "1320", "정보처리기사");

        assertThat(e.status()).isEqualTo(ScheduleEvidenceStatus.NOT_FOUND);
        assertThat(e.rounds()).isEmpty();
    }

    @Test
    void okResponseWithoutRoundsIsOfficialNoSchedule() {
        CertificateScheduleEvidence e = NationalTechExamScheduleProvider.parse(OK_EMPTY_XML, "9999", "희귀종목");

        assertThat(e.status()).isEqualTo(ScheduleEvidenceStatus.OFFICIAL_NO_SCHEDULE);
        assertThat(e.rounds()).isEmpty();
    }

    @Test
    void nestingAgnosticParsesWithoutItemWrapper() {
        // <item> 래핑이 아니어도 일정 필드가 있으면 한 회차로 인식(q-net 구조 변형 대비).
        String noItem = "<response><header><resultCode>00</resultCode></header><body>"
                + "<jmfldmm>정보처리기능사</jmfldmm><implplannm>기능사(2026년 제1회)</implplannm>"
                + "<docexamstartdt>20260126</docexamstartdt><docpassdt>20260221</docpassdt>"
                + "</body></response>";
        CertificateScheduleEvidence e = NationalTechExamScheduleProvider.parse(noItem, "1301", null);

        assertThat(e.status()).isEqualTo(ScheduleEvidenceStatus.VERIFIED_CURRENT);
        assertThat(e.rounds()).hasSize(1);
        assertThat(e.rounds().get(0).docExam()).isEqualTo("20260126");
    }

    @Test
    void blankServiceKeyDoesNotCallApiAndDegrades() {
        NationalTechExamScheduleProvider provider = new NationalTechExamScheduleProvider(
                "", "http://unused.invalid", Duration.ofSeconds(1), HttpClient.newHttpClient());

        assertThat(provider.enabled()).isFalse();
        CertificateScheduleEvidence e = provider.lookup("1320", "정보처리기사");
        assertThat(e.status()).isEqualTo(ScheduleEvidenceStatus.NOT_FOUND);
    }

    @Test
    void emptyBodyDegradesToNotFound() {
        assertThat(NationalTechExamScheduleProvider.parse("", "1320", null).status())
                .isEqualTo(ScheduleEvidenceStatus.NOT_FOUND);
    }
}
