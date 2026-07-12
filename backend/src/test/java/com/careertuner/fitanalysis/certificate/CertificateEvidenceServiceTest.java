package com.careertuner.fitanalysis.certificate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.careertuner.fitanalysis.certificate.CertificateScheduleEvidence.ScheduleRound;
import com.careertuner.fitanalysis.certificate.PrivateCertRegistrationEvidence.Match;
import com.careertuner.fitanalysis.dto.CertificateEvidenceResponse;

class CertificateEvidenceServiceTest {

    private final NationalQualificationCatalogProvider catalog = mock(NationalQualificationCatalogProvider.class);
    private final NationalTechExamScheduleProvider schedule = mock(NationalTechExamScheduleProvider.class);
    private final UnifiedExamScheduleProvider unifiedSchedule = mock(UnifiedExamScheduleProvider.class);
    private final NationalProfExamScheduleBundle profBundle = mock(NationalProfExamScheduleBundle.class);
    private final PrivateCertRegistrationProvider registration = mock(PrivateCertRegistrationProvider.class);
    private final CertificateEvidenceService service =
            new CertificateEvidenceService(catalog, schedule, unifiedSchedule, profBundle, registration);

    private void enable() {
        lenient().when(catalog.enabled()).thenReturn(true);
        // 기본: 통합 일정 API 미확인(UPSTREAM) → 레거시 getJMList 폴백. 통합 경로 자체는 전용 테스트에서 검증.
        lenient().when(unifiedSchedule.lookup(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> new CertificateScheduleEvidence(ScheduleEvidenceStatus.UPSTREAM_UNAVAILABLE,
                        inv.getArgument(0), inv.getArgument(1), "통합", "url", List.of()));
    }

    private static NationalQualificationCatalogEvidence cat(NationalQualificationCatalogStatus s,
                                                            NationalQualificationCatalogEntry e) {
        return new NationalQualificationCatalogEvidence(s, "q", e, "src", "url");
    }

    private static NationalQualificationCatalogEntry entry(String qualgb) {
        return new NationalQualificationCatalogEntry("1320", "정보처리기사", qualgb, "국가", "기사", "정보통신", "정보기술");
    }

    @Test
    void allProvidersDisabledReturnsEmptyWithoutCalls() {
        when(catalog.enabled()).thenReturn(false);
        when(schedule.enabled()).thenReturn(false);
        when(unifiedSchedule.enabled()).thenReturn(false);
        when(registration.enabled()).thenReturn(false);

        assertThat(service.collect(List.of("정보처리기사"))).isEmpty();
    }

    @Test
    void technicalWithJmCdPrefersUnifiedScheduleAndSkipsLegacy() {
        enable();
        when(catalog.lookup("정보처리기사")).thenReturn(cat(NationalQualificationCatalogStatus.FOUND, entry("T")));
        ScheduleRound round = new ScheduleRound("국가기술자격 기사 (2026년도 제3회)", "20260720", "20260723",
                "20260807", "20260909", "20261024", "20261107", "20261120");
        when(unifiedSchedule.lookup("1320", "정보처리기사")).thenReturn(new CertificateScheduleEvidence(
                ScheduleEvidenceStatus.VERIFIED_CURRENT, "1320", "정보처리기사", "통합", "url", List.of(round)));

        CertificateEvidenceResponse e = service.collect(List.of("정보처리기사")).get(0);

        assertThat(e.scheduleStatus()).isEqualTo(ScheduleEvidenceStatus.VERIFIED_CURRENT.name());
        assertThat(e.scheduleRounds()).hasSize(1);
        org.mockito.Mockito.verify(schedule, org.mockito.Mockito.never()).lookup(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void technicalUnifiedOfficialNoScheduleIsTrustedWithoutLegacyFallback() {
        enable();
        when(catalog.lookup("정보처리기사")).thenReturn(cat(NationalQualificationCatalogStatus.FOUND, entry("T")));
        when(unifiedSchedule.lookup("1320", "정보처리기사")).thenReturn(new CertificateScheduleEvidence(
                ScheduleEvidenceStatus.OFFICIAL_NO_SCHEDULE, "1320", "정보처리기사", "통합", "url", List.of()));

        CertificateEvidenceResponse e = service.collect(List.of("정보처리기사")).get(0);

        // 통합 API 의 00+0건은 확인된 미편성 — 레거시로 재조회하지 않는다(이중 조회·모순 응답 방지).
        assertThat(e.scheduleStatus()).isEqualTo(ScheduleEvidenceStatus.OFFICIAL_NO_SCHEDULE.name());
        org.mockito.Mockito.verify(schedule, org.mockito.Mockito.never()).lookup(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void technicalWithoutJmCdUsesLegacyPathOnly() {
        enable();
        NationalQualificationCatalogEntry noJmCd = new NationalQualificationCatalogEntry(
                null, "컴퓨터시스템기사", "T", "국가기술자격", "기사", null, null);
        when(catalog.lookup("컴퓨터시스템기사")).thenReturn(cat(NationalQualificationCatalogStatus.FOUND, noJmCd));
        when(schedule.lookup("컴퓨터시스템기사")).thenReturn(new CertificateScheduleEvidence(
                ScheduleEvidenceStatus.UPSTREAM_UNAVAILABLE, null, "컴퓨터시스템기사", "Q-Net", "url", List.of()));

        CertificateEvidenceResponse e = service.collect(List.of("컴퓨터시스템기사")).get(0);

        // jmCd 미매핑(canonical 미확정) 종목은 통합 API 를 부르지 않는다 — 오귀속 방지.
        assertThat(e.scheduleStatus()).isEqualTo(ScheduleEvidenceStatus.UPSTREAM_UNAVAILABLE.name());
        org.mockito.Mockito.verify(unifiedSchedule, org.mockito.Mockito.never())
                .lookup(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void legacyDisabledSkipsGetJmListAndDegradesHonestly() {
        // 정식 미사용 확정(기본): 통합 API 가 UPSTREAM 이어도 죽은 원서버(getJMList)를 부르지 않고 정직하게 UPSTREAM.
        CertificateEvidenceService noLegacy =
                new CertificateEvidenceService(catalog, schedule, unifiedSchedule, profBundle, registration, false);
        lenient().when(catalog.enabled()).thenReturn(true);
        when(catalog.lookup("정보처리기사")).thenReturn(cat(NationalQualificationCatalogStatus.FOUND, entry("T")));
        when(unifiedSchedule.lookup("1320", "정보처리기사")).thenReturn(new CertificateScheduleEvidence(
                ScheduleEvidenceStatus.UPSTREAM_UNAVAILABLE, "1320", "정보처리기사", "통합", "url", List.of()));

        CertificateEvidenceResponse e = noLegacy.collect(List.of("정보처리기사")).get(0);

        assertThat(e.scheduleStatus()).isEqualTo(ScheduleEvidenceStatus.UPSTREAM_UNAVAILABLE.name());
        assertThat(e.message()).contains("원활하지 않아").doesNotContain("일정이 없");
        org.mockito.Mockito.verify(schedule, org.mockito.Mockito.never()).lookup(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void legacyDisabledNoJmCdDoesNotCallAnyLiveProvider() {
        CertificateEvidenceService noLegacy =
                new CertificateEvidenceService(catalog, schedule, unifiedSchedule, profBundle, registration, false);
        lenient().when(catalog.enabled()).thenReturn(true);
        NationalQualificationCatalogEntry noJmCd = new NationalQualificationCatalogEntry(
                null, "컴퓨터시스템기사", "T", "국가기술자격", "기사", null, null);
        when(catalog.lookup("컴퓨터시스템기사")).thenReturn(cat(NationalQualificationCatalogStatus.FOUND, noJmCd));

        CertificateEvidenceResponse e = noLegacy.collect(List.of("컴퓨터시스템기사")).get(0);

        assertThat(e.scheduleStatus()).isEqualTo(ScheduleEvidenceStatus.UPSTREAM_UNAVAILABLE.name());
        org.mockito.Mockito.verify(schedule, org.mockito.Mockito.never()).lookup(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(unifiedSchedule, org.mockito.Mockito.never())
                .lookup(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void emptyCertNamesReturnsEmpty() {
        assertThat(service.collect(List.of())).isEmpty();
        assertThat(service.collect(null)).isEmpty();
    }

    @Test
    void nationalTechnicalVerifiedCarriesScheduleRounds() {
        enable();
        when(catalog.lookup("정보처리기사")).thenReturn(cat(NationalQualificationCatalogStatus.FOUND, entry("T")));
        ScheduleRound round = new ScheduleRound("기사(2026-1)", "20260113", "20260116",
                "20260215", "20260312", "20260419", "20260504", "20260530");
        when(schedule.lookup("정보처리기사")).thenReturn(new CertificateScheduleEvidence(
                ScheduleEvidenceStatus.VERIFIED_CURRENT, null, "정보처리기사", "Q-Net", "url", List.of(round)));

        CertificateEvidenceResponse e = service.collect(List.of("정보처리기사")).get(0);

        assertThat(e.kind()).isEqualTo(CertificateKind.NATIONAL_TECHNICAL.name());
        assertThat(e.scheduleStatus()).isEqualTo(ScheduleEvidenceStatus.VERIFIED_CURRENT.name());
        assertThat(e.scheduleRounds()).hasSize(1);
        assertThat(e.message()).contains("공식 확인 기준"); // 소스 중립 문구 — 출처는 sourceName 이 말한다
    }

    @Test
    void nationalTechnicalUpstreamSaysUnavailableNotAbsent() {
        enable();
        when(catalog.lookup("정보처리기사")).thenReturn(cat(NationalQualificationCatalogStatus.FOUND, entry("T")));
        when(schedule.lookup("정보처리기사")).thenReturn(new CertificateScheduleEvidence(
                ScheduleEvidenceStatus.UPSTREAM_UNAVAILABLE, null, "정보처리기사", "Q-Net", "url", List.of()));

        CertificateEvidenceResponse e = service.collect(List.of("정보처리기사")).get(0);

        assertThat(e.scheduleStatus()).isEqualTo(ScheduleEvidenceStatus.UPSTREAM_UNAVAILABLE.name());
        assertThat(e.message()).contains("원활하지 않아").contains("임의로 추정하지 않");
        assertThat(e.message()).doesNotContain("일정이 없");
    }

    @Test
    void nationalProfessionalWithoutBundleMatchIsNotApplicable() {
        enable();
        when(catalog.lookup("변리사")).thenReturn(cat(NationalQualificationCatalogStatus.FOUND, entry("S")));
        when(profBundle.lookup("변리사")).thenReturn(null);

        CertificateEvidenceResponse e = service.collect(List.of("변리사")).get(0);

        assertThat(e.kind()).isEqualTo(CertificateKind.NATIONAL_PROFESSIONAL.name());
        assertThat(e.scheduleStatus()).isEqualTo(ScheduleEvidenceStatus.NOT_APPLICABLE.name());
    }

    @Test
    void nationalProfessionalWithPreannouncementCarriesRoundsAndCaveat() {
        enable();
        when(catalog.lookup("공인노무사")).thenReturn(cat(NationalQualificationCatalogStatus.FOUND, entry("S")));
        ScheduleRound round = new ScheduleRound("2026년 1차", "20260330", "20260403",
                "20260523", "20260624", null, null, null);
        when(profBundle.lookup("공인노무사")).thenReturn(new CertificateScheduleEvidence(
                ScheduleEvidenceStatus.PREANNOUNCED, null, "공인노무사", "사전공고", "url", List.of(round)));

        CertificateEvidenceResponse e = service.collect(List.of("공인노무사")).get(0);

        assertThat(e.scheduleStatus()).isEqualTo(ScheduleEvidenceStatus.PREANNOUNCED.name());
        assertThat(e.scheduleRounds()).hasSize(1);
        // (안) 신뢰층 명시 — 확정 일정으로 단정하지 않는다.
        assertThat(e.message()).contains("사전공고").contains("확정");
    }

    @Test
    void privateRegisteredIsManualRequiredForSchedule() {
        enable();
        when(catalog.lookup("데이터분석전문가")).thenReturn(cat(NationalQualificationCatalogStatus.NOT_FOUND, null));
        when(registration.lookup("데이터분석전문가")).thenReturn(new PrivateCertRegistrationEvidence(
                PrivateCertRegistrationStatus.REGISTERED_ACTIVE, "데이터분석전문가", 1, "20251231", "PQI", "url",
                List.of(new Match("데이터분석전문가", "2020-1", "등록완료", "한국표준협회", "공인"))));

        CertificateEvidenceResponse e = service.collect(List.of("데이터분석전문가")).get(0);

        assertThat(e.kind()).isEqualTo(CertificateKind.PRIVATE_OR_OTHER.name());
        assertThat(e.scheduleStatus()).isEqualTo(ScheduleEvidenceStatus.MANUAL_REQUIRED.name());
        assertThat(e.registrationStatus()).isEqualTo(PrivateCertRegistrationStatus.REGISTERED_ACTIVE.name());
        assertThat(e.message()).contains("한국표준협회").contains("주관기관");
    }

    @Test
    void catalogUpstreamYieldsUnknownKind() {
        enable();
        when(catalog.lookup("정보처리기사"))
                .thenReturn(cat(NationalQualificationCatalogStatus.UPSTREAM_UNAVAILABLE, null));

        CertificateEvidenceResponse e = service.collect(List.of("정보처리기사")).get(0);

        assertThat(e.kind()).isEqualTo(CertificateKind.UNKNOWN.name());
        assertThat(e.scheduleStatus()).isEqualTo(ScheduleEvidenceStatus.UPSTREAM_UNAVAILABLE.name());
    }

    @Test
    void providerExceptionDoesNotBreakCollection() {
        enable();
        when(catalog.lookup("정보처리기사")).thenThrow(new RuntimeException("boom"));

        List<CertificateEvidenceResponse> result = service.collect(List.of("정보처리기사"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).kind()).isEqualTo(CertificateKind.UNKNOWN.name());
    }
}
