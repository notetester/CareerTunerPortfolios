package com.careertuner.fitanalysis.certificate;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

/** 실제 번들 리소스(2026 사전공고 조인 산출물, 36자격)를 로드해 검증 — 리소스 파손 회귀를 빌드에서 잡는다. */
class NationalProfExamScheduleBundleTest {

    private static final String RESOURCE = "cert/national-prof-schedule-2026-preannounced.json";
    private final ObjectMapper objectMapper = new ObjectMapper();

    private NationalProfExamScheduleBundle loaded() {
        return new NationalProfExamScheduleBundle(RESOURCE, true, objectMapper);
    }

    /** 번들 대상 연도(리소스의 year)가 현재 KST 연도와 같은지 — 달력 경과로 테스트가 깨지지 않게 명시 게이트. */
    private static boolean bundleYearIsCurrent() {
        return java.time.Year.now(java.time.ZoneId.of("Asia/Seoul")).getValue() == 2026;
    }

    @Test
    void bundledPreannouncementResolvesKnownProfessionalCert() {
        org.junit.jupiter.api.Assumptions.assumeTrue(bundleYearIsCurrent(),
                "2026 번들 — 연도 경과 시 available()=false 가 정상이므로 이 케이스는 skip(새해 번들 교체 신호)");
        NationalProfExamScheduleBundle bundle = loaded();

        assertThat(bundle.available()).isTrue();
        CertificateScheduleEvidence e = bundle.lookup("공인노무사");
        assertThat(e).isNotNull();
        assertThat(e.status()).isEqualTo(ScheduleEvidenceStatus.PREANNOUNCED);
        assertThat(e.rounds()).isNotEmpty();
        assertThat(e.rounds().get(0).docExam()).matches("\\d{8}");
        assertThat(e.sourceName()).contains("사전공고");
    }

    @Test
    void groupMembersShareSchedule() {
        org.junit.jupiter.api.Assumptions.assumeTrue(bundleYearIsCurrent(), "2026 번들 — 연도 경과 시 skip");
        NationalProfExamScheduleBundle bundle = loaded();
        // 검수사·검량사·감정사는 동일 시행계획 공유(사전공고 명시).
        assertThat(bundle.lookup("검수사")).isNotNull();
        assertThat(bundle.lookup("검량사")).isNotNull();
        assertThat(bundle.lookup("감정사")).isNotNull();
        assertThat(bundle.lookup("검수사").rounds()).isEqualTo(bundle.lookup("검량사").rounds());
    }

    @Test
    void unknownOrExcludedCertReturnsNullNotEmptySchedule() {
        org.junit.jupiter.api.Assumptions.assumeTrue(bundleYearIsCurrent(), "2026 번들 — 연도 경과 시 skip");
        NationalProfExamScheduleBundle bundle = loaded();
        // 정수시설운영관리사는 사전공고 제외(재공고 예정) — '일정 없음' 단정 없이 null(기존 안내 경로).
        assertThat(bundle.lookup("정수시설운영관리사")).isNull();
        assertThat(bundle.lookup("존재하지않는자격")).isNull();
        assertThat(bundle.lookup(" ")).isNull();
    }

    @Test
    void missingResourceIsUnavailable() {
        NationalProfExamScheduleBundle bundle =
                new NationalProfExamScheduleBundle("cert/no-such-bundle.json", true, objectMapper);
        assertThat(bundle.available()).isFalse();
        assertThat(bundle.lookup("공인노무사")).isNull();
    }

    @Test
    void disabledFlagSkipsLoading() {
        NationalProfExamScheduleBundle bundle = new NationalProfExamScheduleBundle(RESOURCE, false, objectMapper);
        assertThat(bundle.available()).isFalse();
    }
}
