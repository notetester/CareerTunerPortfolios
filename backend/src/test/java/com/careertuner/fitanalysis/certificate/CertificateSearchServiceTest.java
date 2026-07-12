package com.careertuner.fitanalysis.certificate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.careertuner.fitanalysis.certificate.PrivateCertRegistrationEvidence.Match;
import com.careertuner.fitanalysis.dto.CertificateSearchResponse;

class CertificateSearchServiceTest {

    private final NationalQualificationOfflineCatalog offlineCatalog = mock(NationalQualificationOfflineCatalog.class);
    private final PrivateCertRegistrationProvider registration = mock(PrivateCertRegistrationProvider.class);
    private final CertificateAliasCatalog aliasCatalog = mock(CertificateAliasCatalog.class);
    private final NationalProfExamScheduleBundle profBundle = mock(NationalProfExamScheduleBundle.class);
    private final CertificateSearchService service =
            new CertificateSearchService(offlineCatalog, registration, aliasCatalog, profBundle);

    private static PrivateCertRegistrationEvidence evidence(PrivateCertRegistrationStatus status, Match... matches) {
        return new PrivateCertRegistrationEvidence(status, "q", matches.length, "20251231", "src", "url", List.of(matches));
    }

    @Test
    void privateUpstreamFailureIsFlaggedNotShownAsAbsence() {
        when(offlineCatalog.available()).thenReturn(true);
        when(offlineCatalog.search("정보처리기사", 15)).thenReturn(List.of());
        when(aliasCatalog.officialNameFor("정보처리기사")).thenReturn(java.util.Optional.empty());
        when(registration.enabled()).thenReturn(true);
        // odcloud 장애: 정상 0건이 아니라 UPSTREAM_UNAVAILABLE — '미등록'으로 표시하면 안 된다(오류≠부재).
        when(registration.search("정보처리기사", 10)).thenReturn(evidence(PrivateCertRegistrationStatus.UPSTREAM_UNAVAILABLE));

        CertificateSearchResponse response = service.search("정보처리기사");

        assertThat(response.privateLookupFailed()).isTrue();
        assertThat(response.privateMatches()).isEmpty();
    }

    @Test
    void privateGenuineZeroIsNotFlaggedAsFailure() {
        when(offlineCatalog.available()).thenReturn(true);
        when(offlineCatalog.search("없는자격", 15)).thenReturn(List.of());
        when(aliasCatalog.officialNameFor("없는자격")).thenReturn(java.util.Optional.empty());
        when(registration.enabled()).thenReturn(true);
        when(registration.search("없는자격", 10)).thenReturn(evidence(PrivateCertRegistrationStatus.NOT_FOUND));

        CertificateSearchResponse response = service.search("없는자격");

        assertThat(response.privateLookupFailed()).isFalse();
        assertThat(response.privateMatches()).isEmpty();
    }

    @Test
    void aliasResolvesToOfficialNameForBothPaths() {
        when(offlineCatalog.available()).thenReturn(true);
        when(offlineCatalog.search("SQLD", 15)).thenReturn(List.of());
        when(offlineCatalog.search("SQL", 15)).thenReturn(List.of());
        when(aliasCatalog.officialNameFor("SQLD")).thenReturn(java.util.Optional.of("SQL"));
        when(registration.enabled()).thenReturn(true);
        when(registration.search("SQL", 10)).thenReturn(evidence(PrivateCertRegistrationStatus.REGISTERED_ACTIVE,
                new Match("SQL", "2020-1", "등록완료", "한국데이터산업진흥원", "공인")));

        CertificateSearchResponse response = service.search("SQLD");

        assertThat(response.resolvedAlias()).isEqualTo("SQL");
        assertThat(response.privateMatches()).extracting(CertificateSearchResponse.PrivateItem::name).contains("SQL");
        assertThat(response.privateLookupFailed()).isFalse();
    }

    @Test
    void snapshotUnavailableIsFlaggedNotShownAsAbsence() {
        when(offlineCatalog.available()).thenReturn(false);
        when(offlineCatalog.search("정보처리", 15)).thenReturn(List.of());
        when(aliasCatalog.officialNameFor("정보처리")).thenReturn(java.util.Optional.empty());
        lenient().when(registration.enabled()).thenReturn(false);

        CertificateSearchResponse response = service.search("정보처리");

        assertThat(response.nationalUnavailable()).isTrue();
    }

    @Test
    void shortQueryReturnsEmptyWithoutLookups() {
        CertificateSearchResponse response = service.search("정");

        assertThat(response.national()).isEmpty();
        assertThat(response.privateMatches()).isEmpty();
        assertThat(response.privateLookupFailed()).isFalse();
    }
}
