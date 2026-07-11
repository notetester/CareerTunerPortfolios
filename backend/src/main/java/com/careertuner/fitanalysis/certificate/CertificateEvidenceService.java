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
 * <p>라우팅: 국가자격 목록(15003024)으로 종류 판별 → 국가기술(T)은 getJMList 일정 조회, 국가전문(S)은 일정 API 미적용,
 * 목록에 없으면 민간 등록정보 조회(일정은 주관기관=MANUAL_REQUIRED). 어떤 provider 실패도 예외를 전파하지 않고
 * degrade 하므로 <b>Q-Net 이 죽어 있어도 상위 적합도 분석은 깨지지 않는다</b>. 판단값(fitScore 등)엔 관여하지 않는다.
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
    private final PrivateCertRegistrationProvider registration;

    public CertificateEvidenceService(NationalQualificationCatalogProvider catalog,
                                      NationalTechExamScheduleProvider schedule,
                                      PrivateCertRegistrationProvider registration) {
        this.catalog = catalog;
        this.schedule = schedule;
        this.registration = registration;
    }

    /** 하나라도 조회 가능한 provider 가 있는지(키 설정). false 면 근거 수집을 아예 하지 않는다. */
    public boolean anyEnabled() {
        return catalog.enabled() || schedule.enabled() || registration.enabled();
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
                    ? technical(cert, schedule.lookup(cert))
                    : professional(cert);
            case NOT_FOUND -> privateOrOther(cert, registration.lookup(cert));
            case UPSTREAM_UNAVAILABLE -> unknown(cert);
        };
    }

    private CertificateEvidenceResponse technical(String cert, CertificateScheduleEvidence s) {
        String message = switch (s.status()) {
            case VERIFIED_CURRENT ->
                "Q-Net 공식 확인 기준 시험일정입니다. 시험 일정은 변경될 수 있으니 접수 전 공식 페이지 재확인이 필요합니다.";
            case OFFICIAL_NO_SCHEDULE ->
                "Q-Net 정상 확인 결과, 현재 올해 시행일정이 편성되지 않았습니다.";
            default -> UPSTREAM_MSG; // UPSTREAM_UNAVAILABLE 등
        };
        return new CertificateEvidenceResponse(cert, CertificateKind.NATIONAL_TECHNICAL.name(),
                s.status().name(), null, message, s.sourceName(), s.sourceUrl(), s.rounds());
    }

    private CertificateEvidenceResponse professional(String cert) {
        // 국가전문자격 시행일정 API(InquiryTestDatesNationalProfessionalQualificationSVC)가 존재하나 계열코드(seriesCd)
        // 매핑표 미확보 + Q-Net 장애로 아직 연동하지 않았다 — 자동 확인 대상이 아니라는 표현 대신 '현재 미연동'으로 솔직하게.
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
