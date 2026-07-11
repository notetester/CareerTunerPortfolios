package com.careertuner.fitanalysis.certificate;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpClient;
import java.time.Duration;

import org.junit.jupiter.api.Test;

class NationalQualificationCatalogProviderTest {

    private static String item(String qualgbcd, String qualgbnm, String jmcd, String jmfldnm) {
        return "<item><qualgbcd>" + qualgbcd + "</qualgbcd><qualgbnm>" + qualgbnm + "</qualgbnm>"
                + "<seriescd>1</seriescd><seriesnm>기사</seriesnm>"
                + "<jmcd>" + jmcd + "</jmcd><jmfldnm>" + jmfldnm + "</jmfldnm>"
                + "<obligfldcd>21</obligfldcd><obligfldnm>정보통신</obligfldnm>"
                + "<mdobligfldcd>211</mdobligfldcd><mdobligfldnm>정보기술</mdobligfldnm></item>";
    }

    private static String ok(String... items) {
        return "<response><header><resultCode>00</resultCode><resultMsg>NORMAL SERVICE.</resultMsg></header>"
                + "<body><items>" + String.join("", items) + "</items></body></response>";
    }

    @Test
    void foundTechnicalQualificationExposesJmCdAndRouting() {
        NationalQualificationCatalogEvidence e = NationalQualificationCatalogProvider.parse(
                ok(item("T", "국가기술자격", "1320", "정보처리기사")), "정보처리기사");

        assertThat(e.status()).isEqualTo(NationalQualificationCatalogStatus.FOUND);
        assertThat(e.entry().jmCd()).isEqualTo("1320");
        assertThat(e.entry().certName()).isEqualTo("정보처리기사");
        assertThat(e.entry().jobField()).isEqualTo("정보통신");
        assertThat(e.entry().technical()).isTrue(); // 기술자격 → getJMList 일정 조회 대상
    }

    @Test
    void professionalQualificationIsNotTechnical() {
        NationalQualificationCatalogEvidence e = NationalQualificationCatalogProvider.parse(
                ok(item("S", "국가전문자격", "9999", "변리사")), "변리사");

        assertThat(e.status()).isEqualTo(NationalQualificationCatalogStatus.FOUND);
        assertThat(e.entry().technical()).isFalse(); // 전문자격 → 일정 체계 다름
    }

    @Test
    void exactMatchDoesNotConfuseSimilarNames() {
        String list = ok(
                item("T", "국가기술자격", "1301", "정보처리기능사"),
                item("T", "국가기술자격", "1320", "정보처리기사"),
                item("T", "국가기술자격", "2290", "정보처리산업기사"));
        NationalQualificationCatalogEvidence e = NationalQualificationCatalogProvider.parse(list, "정보처리기사");

        assertThat(e.status()).isEqualTo(NationalQualificationCatalogStatus.FOUND);
        assertThat(e.entry().jmCd()).isEqualTo("1320"); // 기사만, 기능사/산업기사 아님
    }

    @Test
    void confirmedNormalButNameNotInListIsNotFound() {
        NationalQualificationCatalogEvidence e = NationalQualificationCatalogProvider.parse(
                ok(item("T", "국가기술자격", "1320", "정보처리기사")), "SQLD");

        assertThat(e.status()).isEqualTo(NationalQualificationCatalogStatus.NOT_FOUND);
        assertThat(e.entry()).isNull();
    }

    @Test
    void gatewayErrorEnvelopeDegradesToUpstreamUnavailable() {
        String gatewayError = "<OpenAPI_ServiceResponse><cmmMsgHeader>"
                + "<returnReasonCode>30</returnReasonCode>"
                + "<returnAuthMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</returnAuthMsg></cmmMsgHeader></OpenAPI_ServiceResponse>";
        assertThat(NationalQualificationCatalogProvider.parse(gatewayError, "정보처리기사").status())
                .isEqualTo(NationalQualificationCatalogStatus.UPSTREAM_UNAVAILABLE);
    }

    @Test
    void resultCode99DegradesToUpstreamUnavailable() {
        String timeout = "<response><header><resultCode>99</resultCode>"
                + "<resultMsg>SocketTimeoutException</resultMsg></header></response>";
        assertThat(NationalQualificationCatalogProvider.parse(timeout, "정보처리기사").status())
                .isEqualTo(NationalQualificationCatalogStatus.UPSTREAM_UNAVAILABLE);
    }

    @Test
    void resultCodeAbsentWithoutMatchIsUpstreamNotNotFound() {
        String ambiguous = "<response><body><items></items></body></response>";
        assertThat(NationalQualificationCatalogProvider.parse(ambiguous, "정보처리기사").status())
                .isEqualTo(NationalQualificationCatalogStatus.UPSTREAM_UNAVAILABLE);
    }

    @Test
    void truncatedListWithoutMatchIsUpstreamNotNotFound() {
        // totalCount(3000) > 수신 item(1) → 목록 잘림. 뒤쪽에 있을 수 있는 자격을 '부재'로 단정하지 않는다.
        String truncated = "<response><header><resultCode>00</resultCode></header>"
                + "<body><totalCount>3000</totalCount><items>"
                + item("T", "국가기술자격", "1320", "정보처리기사") + "</items></body></response>";
        assertThat(NationalQualificationCatalogProvider.parse(truncated, "SQLD").status())
                .isEqualTo(NationalQualificationCatalogStatus.UPSTREAM_UNAVAILABLE);
    }

    @Test
    void blankKeyDoesNotCallApiAndDegrades() {
        NationalQualificationCatalogProvider provider = new NationalQualificationCatalogProvider(
                "", "http://unused.invalid", Duration.ofSeconds(1), HttpClient.newHttpClient(), null);

        assertThat(provider.enabled()).isFalse();
        assertThat(provider.lookup("정보처리기사").status())
                .isEqualTo(NationalQualificationCatalogStatus.UPSTREAM_UNAVAILABLE);
    }

    @Test
    void loadedSnapshotAnswersWithoutNetworkOrServiceKey() {
        // 스냅샷 우선 — 키가 없고 네트워크가 죽어 있어도(Q-Net 장애 등) 종류 판별·라우팅이 동작한다.
        NationalQualificationOfflineCatalog snapshot = new NationalQualificationOfflineCatalog(
                "cert/national-qualification-catalog-20251231.csv", "cert/national-tech-jmcd-20260711.csv", "20251231", true);
        NationalQualificationCatalogProvider provider = new NationalQualificationCatalogProvider(
                "", "http://unused.invalid", Duration.ofSeconds(1), HttpClient.newHttpClient(), snapshot);

        assertThat(provider.lookup("정보처리기사").status()).isEqualTo(NationalQualificationCatalogStatus.FOUND);
        assertThat(provider.lookup("정보처리기사").entry().technical()).isTrue();
        assertThat(provider.lookup("공인노무사").entry().technical()).isFalse();
        assertThat(provider.lookup("SQLD").status()).isEqualTo(NationalQualificationCatalogStatus.NOT_FOUND);
    }

    @Test
    void unloadableSnapshotFallsBackToLegacyKeyGate() {
        // 스냅샷 로드 실패는 새로운 단일 장애점이 아니다 — 기존 네트워크 경로 규칙(키 없음=degrade)로 복귀.
        NationalQualificationOfflineCatalog broken = new NationalQualificationOfflineCatalog(
                "cert/does-not-exist.csv", "cert/national-tech-jmcd-20260711.csv", "x", true);
        NationalQualificationCatalogProvider provider = new NationalQualificationCatalogProvider(
                "", "http://unused.invalid", Duration.ofSeconds(1), HttpClient.newHttpClient(), broken);

        assertThat(provider.lookup("정보처리기사").status())
                .isEqualTo(NationalQualificationCatalogStatus.UPSTREAM_UNAVAILABLE);
    }
}
