package com.careertuner.fitanalysis.certificate;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.careertuner.fitanalysis.dto.CertificateSearchResponse;

/**
 * 자격증 통합 검색 — 국가자격은 오프라인 스냅샷(네트워크 불요), 민간자격은 등록정보 라이브 조회(LIKE).
 * 검증된 별칭(SQLD→SQL 등)이 있으면 두 이름 모두로 찾는다. 트랜잭션 없음(외부 I/O 포함, DB 미사용).
 */
@Service
public class CertificateSearchService {

    private static final int NATIONAL_LIMIT = 15;
    private static final int PRIVATE_LIMIT = 10;

    private final NationalQualificationOfflineCatalog offlineCatalog;
    private final PrivateCertRegistrationProvider registration;
    private final CertificateAliasCatalog aliasCatalog;
    private final NationalProfExamScheduleBundle profScheduleBundle;

    public CertificateSearchService(NationalQualificationOfflineCatalog offlineCatalog,
                                    PrivateCertRegistrationProvider registration,
                                    CertificateAliasCatalog aliasCatalog,
                                    NationalProfExamScheduleBundle profScheduleBundle) {
        this.offlineCatalog = offlineCatalog;
        this.registration = registration;
        this.aliasCatalog = aliasCatalog;
        this.profScheduleBundle = profScheduleBundle;
    }

    public CertificateSearchResponse search(String query) {
        String q = query == null ? "" : query.trim();
        if (q.length() < 2) {
            return new CertificateSearchResponse(q, null, List.of(), false, List.of(), false);
        }
        String alias = aliasCatalog.officialNameFor(q).orElse(null);

        // 국가자격: 원명 + 별칭 둘 다(중복 제거). 스냅샷 미로드면 조회 불능 — 빈 목록을 '부재'로 오독하지 않게 플래그로 구분.
        boolean nationalUnavailable = !offlineCatalog.available();
        List<NationalQualificationCatalogEntry> national = new ArrayList<>(offlineCatalog.search(q, NATIONAL_LIMIT));
        if (alias != null) {
            for (NationalQualificationCatalogEntry entry : offlineCatalog.search(alias, NATIONAL_LIMIT)) {
                if (national.stream().noneMatch(existing -> existing.certName().equals(entry.certName()))) {
                    national.add(entry);
                }
            }
        }
        List<CertificateSearchResponse.NationalItem> nationalItems = national.stream()
                .limit(NATIONAL_LIMIT)
                .map(entry -> new CertificateSearchResponse.NationalItem(
                        entry.certName(),
                        entry.technical() ? CertificateKind.NATIONAL_TECHNICAL.name()
                                          : CertificateKind.NATIONAL_PROFESSIONAL.name(),
                        scheduleQueryable(entry)))
                .toList();

        // 민간자격: 라이브 LIKE — provider 미활성/런타임 실패(UPSTREAM_UNAVAILABLE)면 '조회 실패'로 표시(미등록 오독 방지).
        boolean privateFailed = !registration.enabled();
        List<CertificateSearchResponse.PrivateItem> privateItems = List.of();
        if (registration.enabled()) {
            String privateQuery = alias != null ? alias : q;
            PrivateCertRegistrationEvidence evidence = registration.search(privateQuery, PRIVATE_LIMIT);
            // 별칭 결과가 없고(0건) 상태가 실패가 아니면 원명으로도 한번 더(별칭 미매칭 케이스).
            if (evidence.matches().isEmpty()
                    && evidence.status() != PrivateCertRegistrationStatus.UPSTREAM_UNAVAILABLE && alias != null) {
                evidence = registration.search(q, PRIVATE_LIMIT);
            }
            privateFailed = evidence.status() == PrivateCertRegistrationStatus.UPSTREAM_UNAVAILABLE;
            privateItems = evidence.matches().stream()
                    .map(match -> new CertificateSearchResponse.PrivateItem(
                            match.name(), match.currentStatus(), match.institution(), match.registrationNo()))
                    .toList();
        }
        return new CertificateSearchResponse(q, alias, nationalItems, nationalUnavailable, privateItems, privateFailed);
    }

    private boolean scheduleQueryable(NationalQualificationCatalogEntry entry) {
        if (entry.technical()) {
            return entry.jmCd() != null && !entry.jmCd().isBlank();
        }
        return profScheduleBundle.lookup(entry.certName()) != null;
    }
}
