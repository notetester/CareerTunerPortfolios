package com.careertuner.fitanalysis.certificate;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 실제 번들 리소스(2025-12-31 스냅샷, 613종)를 로드해 검증한다 — 리소스 파손·인코딩 회귀를 빌드에서 잡는다. */
class NationalQualificationOfflineCatalogTest {

    private static final String RESOURCE = "cert/national-qualification-catalog-20251231.csv";
    private static final String JMCD_RESOURCE = "cert/national-tech-jmcd-20260711.csv";

    private static NationalQualificationOfflineCatalog loaded() {
        return new NationalQualificationOfflineCatalog(RESOURCE, JMCD_RESOURCE, "20251231", true);
    }

    @Test
    void bundledSnapshotLoadsAndResolvesTechnicalQualification() {
        NationalQualificationOfflineCatalog catalog = loaded();

        assertThat(catalog.available()).isTrue();
        NationalQualificationCatalogEvidence e = catalog.lookup("정보처리기사");
        assertThat(e.status()).isEqualTo(NationalQualificationCatalogStatus.FOUND);
        assertThat(e.entry().technical()).isTrue(); // T → 종목별 일정 조회 대상
        assertThat(e.entry().certName()).isEqualTo("정보처리기사");
        assertThat(e.entry().jmCd()).isEqualTo("1320"); // jmCd 매핑 리소스에서 채움(취득자 2023 실측 검증값)
        assertThat(e.sourceName()).contains("오프라인 스냅샷");
    }

    @Test
    void jmCdMappingFillsVerifiedCodesAndLeavesUnconfirmedNull() {
        NationalQualificationOfflineCatalog catalog = loaded();

        assertThat(catalog.lookup("한식조리기능사").entry().jmCd()).isEqualTo("7910"); // 공식 문서 샘플과 일치
        // 컴퓨터시스템기사(1321/1322 통합)는 canonical jmCd 미확정 — 추측 배정 대신 null(통합 일정 API 미호출→레거시 경로).
        assertThat(catalog.lookup("컴퓨터시스템기사").status()).isEqualTo(NationalQualificationCatalogStatus.FOUND);
        assertThat(catalog.lookup("컴퓨터시스템기사").entry().jmCd()).isNull();
        // S(전문자격)는 매핑 대상 아님 — null 유지.
        assertThat(catalog.lookup("공인노무사").entry().jmCd()).isNull();
    }

    @Test
    void missingJmCdMappingDoesNotBreakCatalog() {
        // jmCd 매핑은 선택 리소스 — 없어도 판별 기능은 그대로, jmCd 만 null.
        NationalQualificationOfflineCatalog catalog =
                new NationalQualificationOfflineCatalog(RESOURCE, "cert/no-such-jmcd.csv", "20251231", true);

        assertThat(catalog.available()).isTrue();
        assertThat(catalog.lookup("정보처리기사").status()).isEqualTo(NationalQualificationCatalogStatus.FOUND);
        assertThat(catalog.lookup("정보처리기사").entry().jmCd()).isNull();
    }

    @Test
    void professionalQualificationIsNotTechnical() {
        NationalQualificationCatalogEvidence e = loaded().lookup("공인노무사");

        assertThat(e.status()).isEqualTo(NationalQualificationCatalogStatus.FOUND);
        assertThat(e.entry().technical()).isFalse(); // S → 일정 체계 다름
    }

    @Test
    void nameMatchingIgnoresWhitespaceButNotSimilarNames() {
        NationalQualificationOfflineCatalog catalog = loaded();

        assertThat(catalog.lookup("정보처리 기사").status())
                .isEqualTo(NationalQualificationCatalogStatus.FOUND); // 공백 정규화(QnetXmlSupport.norm 동일 규칙)
        assertThat(catalog.lookup("정보처리기사").entry().certName()).isEqualTo("정보처리기사");
        // 산업기사가 있어도 '기사' 조회가 다른 종목에 과매칭되지 않는다(정확 매칭).
        // (정보처리기능사는 폐지돼 2025-12-31 스냅샷에 없음 — 실존 종목으로 검증)
        assertThat(catalog.lookup("정보처리산업기사").entry().certName()).isEqualTo("정보처리산업기사");
    }

    @Test
    void absentNameIsConfidentNotFoundBecauseSnapshotIsComplete() {
        // 스냅샷은 완전 목록이라 무매칭=NOT_FOUND 단정 가능(네트워크 경로의 잘림/게이트웨이 오판 리스크 없음).
        assertThat(loaded().lookup("SQLD").status())
                .isEqualTo(NationalQualificationCatalogStatus.NOT_FOUND);
    }

    @Test
    void blankQueryIsUnavailableNotNotFound() {
        assertThat(loaded().lookup("  ").status())
                .isEqualTo(NationalQualificationCatalogStatus.UPSTREAM_UNAVAILABLE);
    }

    @Test
    void missingResourceDegradesToUnavailableNotNotFound() {
        NationalQualificationOfflineCatalog catalog =
                new NationalQualificationOfflineCatalog("cert/does-not-exist.csv", JMCD_RESOURCE, "x", true);

        assertThat(catalog.available()).isFalse();
        // 로드 실패 상태의 무매칭을 '부재'로 단정하지 않는다(오류≠부재).
        assertThat(catalog.lookup("정보처리기사").status())
                .isEqualTo(NationalQualificationCatalogStatus.UPSTREAM_UNAVAILABLE);
    }

    @Test
    void disabledFlagSkipsLoading() {
        NationalQualificationOfflineCatalog catalog =
                new NationalQualificationOfflineCatalog(RESOURCE, JMCD_RESOURCE, "20251231", false);

        assertThat(catalog.available()).isFalse();
    }

    // ── 무결성 게이트(스냅샷 교체 워크플로 보호) — 파손 스냅샷의 NOT_FOUND 단정(오류≠부재 위반)을 로드 시점에 차단 ──

    @Test
    void wrongHeaderIsRejectedAsCorrupted() {
        // 다른 데이터셋 파일 오배치 — 헤더 불일치로 로드 실패 → 네트워크 폴백(확신 오답 없음).
        NationalQualificationOfflineCatalog catalog =
                new NationalQualificationOfflineCatalog("cert/test-bad-header.csv", JMCD_RESOURCE, "x", true);

        assertThat(catalog.available()).isFalse();
    }

    @Test
    void truncatedSnapshotBelowMinEntriesIsRejected() {
        // 헤더는 정상이지만 2행뿐(잘림) — 완전 목록 전제가 깨지므로 NOT_FOUND 단정 권한을 주지 않는다.
        NationalQualificationOfflineCatalog catalog =
                new NationalQualificationOfflineCatalog("cert/test-truncated.csv", JMCD_RESOURCE, "x", true);

        assertThat(catalog.available()).isFalse();
        assertThat(catalog.lookup("SQLD").status())
                .isEqualTo(NationalQualificationCatalogStatus.UPSTREAM_UNAVAILABLE);
    }

    @Test
    void cp949EncodedSnapshotIsRejectedNotMojibakeLoaded() {
        // 공공데이터포털 CSV 흔한 실수: CP949 인코딩 파일로 교체. REPLACE 디코딩이면 mojibake 613키로 '성공' 로드돼
        // 모든 실제 국가자격이 확신 NOT_FOUND(민간 경로 오라우팅)가 된다 — REPORT 디코더+헤더 검증이 차단해야 한다.
        NationalQualificationOfflineCatalog catalog =
                new NationalQualificationOfflineCatalog("cert/test-cp949-encoded.csv", JMCD_RESOURCE, "x", true);

        assertThat(catalog.available()).isFalse();
        assertThat(catalog.lookup("정보처리기사").status())
                .isEqualTo(NationalQualificationCatalogStatus.UPSTREAM_UNAVAILABLE);
    }
}
