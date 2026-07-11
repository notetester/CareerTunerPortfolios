package com.careertuner.fitanalysis.certificate;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.careertuner.fitanalysis.dto.CertificateEvidenceResponse;

/**
 * 자격증 근거 수집·정규화 서비스 — cert-need-gate 가 통과시킨 추천 자격증에 대해서만 공식 출처를 조회해
 * 근거({@link CertificateEvidenceResponse})로 정규화한다. <b>확인된 것만 말하고, 확인 못 하면 솔직하게</b>.
 *
 * <p>라우팅: 오프라인 스냅샷 카탈로그로 종류 판별 → 국가기술(T)은 jmCd 기반 통합 일정 API(살아 있는
 * apis.data.go.kr) 우선 + 레거시 getJMList 폴백, 국가전문(S)은 일정 자동확인 미연동, 목록에 없으면 민간
 * 등록정보 조회(일정은 주관기관=MANUAL_REQUIRED). 어떤 provider 실패도 예외를 전파하지 않고 degrade 하므로
 * <b>Q-Net 이 죽어 있어도 상위 적합도 분석은 깨지지 않는다</b>. 판단값(fitScore 등)엔 관여하지 않는다.
 *
 * <p>provider 가 모두 비활성(키 없음)이면 조회 자체를 하지 않고 빈 목록을 반환한다(불필요한 호출·지연 없음).
 */
@Service
public class CertificateEvidenceService {

    private static final Logger log = LoggerFactory.getLogger(CertificateEvidenceService.class);
    private static final String NATIONAL_SOURCE_NAME = "한국산업인력공단 큐넷(Q-Net)";
    private static final String NATIONAL_SOURCE_URL = "https://www.q-net.or.kr/";
    private static final String UPSTREAM_MSG =
            "한국산업인력공단 큐넷(Q-Net) 공식 서비스가 현재 원활하지 않아 시험일정을 확인하지 못했습니다. "
            + "날짜를 임의로 추정하지 않고, 공식 서비스 복구 후 재확인이 필요합니다.";

    private final NationalQualificationCatalogProvider catalog;
    private final NationalTechExamScheduleProvider schedule;
    private final UnifiedExamScheduleProvider unifiedSchedule;
    private final NationalProfExamScheduleBundle profScheduleBundle;
    private final PrivateCertRegistrationProvider registration;
    // 레거시 getJMList 라이브 폴백 사용 여부 — 기본 false(정식 미사용 확정). 원서버 복구 시 env 로 재활성.
    private final boolean legacyGetJmListEnabled;

    @org.springframework.beans.factory.annotation.Autowired
    public CertificateEvidenceService(NationalQualificationCatalogProvider catalog,
                                      NationalTechExamScheduleProvider schedule,
                                      UnifiedExamScheduleProvider unifiedSchedule,
                                      NationalProfExamScheduleBundle profScheduleBundle,
                                      PrivateCertRegistrationProvider registration,
                                      @org.springframework.beans.factory.annotation.Value(
                                          "${careertuner.certificate.data-go-kr.legacy-getjmlist-enabled:false}")
                                      boolean legacyGetJmListEnabled) {
        this.catalog = catalog;
        this.schedule = schedule;
        this.unifiedSchedule = unifiedSchedule;
        this.profScheduleBundle = profScheduleBundle;
        this.registration = registration;
        this.legacyGetJmListEnabled = legacyGetJmListEnabled;
    }

    /** 테스트/구성용 — 레거시 폴백 여부를 명시. */
    CertificateEvidenceService(NationalQualificationCatalogProvider catalog,
                               NationalTechExamScheduleProvider schedule,
                               UnifiedExamScheduleProvider unifiedSchedule,
                               NationalProfExamScheduleBundle profScheduleBundle,
                               PrivateCertRegistrationProvider registration) {
        this(catalog, schedule, unifiedSchedule, profScheduleBundle, registration, true);
    }

    /** 하나라도 조회 가능한 provider 가 있는지(키 설정). false 면 근거 수집을 아예 하지 않는다. */
    public boolean anyEnabled() {
        return catalog.enabled() || schedule.enabled() || unifiedSchedule.enabled() || registration.enabled();
    }

    /**
     * 추천 자격증 목록에 대한 근거를 수집한다. 게이트 OFF(빈 목록)거나 provider 미활성이면 빈 목록.
     * 개별 자격증 조회 실패는 그 자격증만 UNKNOWN 으로 내리고 전체를 중단시키지 않는다.
     */
    public List<CertificateEvidenceResponse> collect(List<String> certNames) {
        if (certNames == null || certNames.isEmpty() || !anyEnabled()) {
            return List.of();
        }
        List<CertificateEvidenceResponse> out = new ArrayList<>();
        for (String certName : certNames) {
            if (certName == null || certName.isBlank()) {
                continue;
            }
            String cert = certName.trim();
            try {
                out.add(evidenceFor(cert));
            } catch (RuntimeException e) {
                log.debug("cert evidence failed: cert={} err={}", cert, e.getClass().getSimpleName());
                out.add(unknown(cert));
            }
        }
        return out;
    }

    private CertificateEvidenceResponse evidenceFor(String cert) {
        NationalQualificationCatalogEvidence cat = catalog.lookup(cert);
        return switch (cat.status()) {
            case FOUND -> cat.entry() != null && cat.entry().technical()
                    ? technical(cert, technicalSchedule(cert, cat.entry()))
                    : professional(cert);
            case NOT_FOUND -> privateOrOther(cert, registration.lookup(cert));
            case UPSTREAM_UNAVAILABLE -> unknown(cert);
        };
    }

    /**
     * 국가기술자격 일정 조회 — 카탈로그에 검증된 jmCd 가 있으면 <b>통합 일정 API(살아 있는 호스트)</b>로 조회한다.
     * 레거시 getJMList 라이브 폴백은 <b>정식 미사용 확정(2026-07-12)</b>이라 기본 비활성이며,
     * {@code legacy-getjmlist-enabled=true}(원서버 복구 시 env) 일 때만 통합 API 실패/미매핑 종목에서 폴백한다.
     * 폴백이 꺼져 있고 통합으로 확인 못 하면 정직한 UPSTREAM_UNAVAILABLE 로 남는다(날짜 미생성).
     */
    private CertificateScheduleEvidence technicalSchedule(String cert, NationalQualificationCatalogEntry entry) {
        if (entry.jmCd() != null && !entry.jmCd().isBlank()) {
            CertificateScheduleEvidence unified = unifiedSchedule.lookup(entry.jmCd(), cert);
            if (unified.status() != ScheduleEvidenceStatus.UPSTREAM_UNAVAILABLE || !legacyGetJmListEnabled) {
                return unified;
            }
        }
        if (!legacyGetJmListEnabled) {
            // 레거시 미사용 확정 — 미매핑 종목/통합 실패는 죽은 원서버를 부르지 않고 정직하게 '확인 못 함'.
            return new CertificateScheduleEvidence(ScheduleEvidenceStatus.UPSTREAM_UNAVAILABLE,
                    entry.jmCd(), cert, NATIONAL_SOURCE_NAME, NATIONAL_SOURCE_URL, List.of());
        }
        return schedule.lookup(cert);
    }

    private CertificateEvidenceResponse technical(String cert, CertificateScheduleEvidence s) {
        // 메시지는 소스 중립("공식 확인") — 통합 API·레거시 getJMList 어느 쪽이 답했든 한국산업인력공단 공식 데이터이고,
        // 구체 출처는 같은 카드의 sourceName/sourceUrl 이 말한다(하드코딩 'Q-Net' 표기가 통합 소스와 모순되던 문제 정정).
        String message = switch (s.status()) {
            case VERIFIED_CURRENT ->
                "공식 확인 기준 시험일정입니다. 시험 일정은 변경될 수 있으니 접수 전 공식 페이지 재확인이 필요합니다.";
            case OFFICIAL_NO_SCHEDULE ->
                "공식 확인 결과, 현재 올해 시행일정이 편성되지 않았습니다.";
            default -> UPSTREAM_MSG; // UPSTREAM_UNAVAILABLE 등
        };
        return new CertificateEvidenceResponse(cert, CertificateKind.NATIONAL_TECHNICAL.name(),
                s.status().name(), null, message, s.sourceName(), s.sourceUrl(), s.rounds());
    }

    private CertificateEvidenceResponse professional(String cert) {
        // 공단 연간 사전공고 번들(당해연도 한정)에 있으면 PREANNOUNCED 신뢰층으로 일정 제공 — (안)임을 명시.
        CertificateScheduleEvidence pre = profScheduleBundle.lookup(cert);
        if (pre != null) {
            return new CertificateEvidenceResponse(cert, CertificateKind.NATIONAL_PROFESSIONAL.name(),
                    pre.status().name(), null,
                    "공단 연간 사전공고(안) 기준 일정입니다. 자격별 최종 시행계획 공고로 확정되므로, 접수 전 반드시 공식 공고를 확인하세요.",
                    pre.sourceName(), pre.sourceUrl(), pre.rounds());
        }
        // 번들 무매칭/연도 불일치 — '일정 없음' 단정 없이 시행기관 확인 안내(사전공고 37종 밖 자격 포함).
        return new CertificateEvidenceResponse(cert, CertificateKind.NATIONAL_PROFESSIONAL.name(),
                ScheduleEvidenceStatus.NOT_APPLICABLE.name(), null,
                "국가전문자격 시험일정은 아직 자동 확인이 연동되지 않아, 시행기관 공식 페이지에서 일정을 확인해야 합니다.",
                NATIONAL_SOURCE_NAME, NATIONAL_SOURCE_URL, List.of());
    }

    private CertificateEvidenceResponse privateOrOther(String cert, PrivateCertRegistrationEvidence r) {
        String message = switch (r.status()) {
            case REGISTERED_ACTIVE ->
                "민간자격 등록이 확인됐습니다%s. 다만 시험일정은 중앙 공공데이터에 없어 주관기관 공식 페이지 확인이 필요합니다."
                        .formatted(institution(r));
            case ABOLISHED_OR_CANCELLED ->
                "등록 폐지/취소 이력이 있는 민간자격입니다 — 추천 시 주의가 필요합니다. 주관기관 확인이 필요합니다.";
            case NOT_FOUND ->
                "공식 민간자격 등록정보에서 확인되지 않았습니다 — 자격명을 재확인하세요.";
            case UPSTREAM_UNAVAILABLE ->
                "민간자격 등록정보 조회가 일시적으로 어려워 확인하지 못했습니다.";
        };
        // 민간자격 일정은 중앙 공공데이터에 없으므로 항상 주관기관 확인(MANUAL_REQUIRED).
        return new CertificateEvidenceResponse(cert, CertificateKind.PRIVATE_OR_OTHER.name(),
                ScheduleEvidenceStatus.MANUAL_REQUIRED.name(), r.status().name(), message,
                r.sourceName(), r.sourceUrl(), List.of());
    }

    private CertificateEvidenceResponse unknown(String cert) {
        return new CertificateEvidenceResponse(cert, CertificateKind.UNKNOWN.name(),
                ScheduleEvidenceStatus.UPSTREAM_UNAVAILABLE.name(), null,
                "공식 자격 데이터 서비스가 현재 원활하지 않아 자격 종류·일정을 확인하지 못했습니다. 날짜를 임의로 추정하지 않습니다.",
                NATIONAL_SOURCE_NAME, NATIONAL_SOURCE_URL, List.of());
    }

    private static String institution(PrivateCertRegistrationEvidence r) {
        if (r.matches() == null || r.matches().isEmpty()) {
            return "";
        }
        String inst = r.matches().get(0).institution();
        return inst == null || inst.isBlank() ? "" : "(신청기관: " + inst.trim() + ")";
    }
}
