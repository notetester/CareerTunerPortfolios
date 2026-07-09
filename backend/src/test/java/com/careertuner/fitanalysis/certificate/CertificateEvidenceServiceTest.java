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
    private final PrivateCertRegistrationProvider registration = mock(PrivateCertRegistrationProvider.class);
    private final CertificateEvidenceService service =
            new CertificateEvidenceService(catalog, schedule, registration);

    private void enable() {
        lenient().when(catalog.enabled()).thenReturn(true);
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
        when(registration.enabled()).thenReturn(false);

        assertThat(service.collect(List.of("정보처리기사"))).isEmpty();
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
        assertThat(e.message()).contains("Q-Net 공식 확인");
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
    void nationalProfessionalIsNotApplicableForSchedule() {
        enable();
        when(catalog.lookup("변리사")).thenReturn(cat(NationalQualificationCatalogStatus.FOUND, entry("S")));

        CertificateEvidenceResponse e = service.collect(List.of("변리사")).get(0);

        assertThat(e.kind()).isEqualTo(CertificateKind.NATIONAL_PROFESSIONAL.name());
        assertThat(e.scheduleStatus()).isEqualTo(ScheduleEvidenceStatus.NOT_APPLICABLE.name());
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
