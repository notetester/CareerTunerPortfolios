package com.careertuner.fitanalysis.dto;

import java.util.List;

import com.careertuner.fitanalysis.certificate.CertificateScheduleEvidence.ScheduleRound;

/**
 * 자격증 1건의 근거 응답 — 공식 출처 조회 결과를 정규화한 것. <b>확인된 것만 말하고, 확인 못 하면 솔직하게</b>.
 * 판단값이 아니라 부가 근거이므로 fitScore 등 규칙엔진 판단에는 관여하지 않는다(뉴로-심볼릭 불변식).
 *
 * @param certName           자격증명
 * @param kind               라우팅 종류(CertificateKind name)
 * @param scheduleStatus     시험일정 조회 상태(ScheduleEvidenceStatus name)
 * @param registrationStatus 민간 등록 상태(PrivateCertRegistrationStatus name, 국가자격이면 null)
 * @param message            사용자에게 보여줄 솔직한 설명(상태별 수위 조절)
 * @param sourceName         출처명(확인된 경우)
 * @param sourceUrl          출처 URL
 * @param scheduleRounds     확인된 회차별 일정(VERIFIED_CURRENT 일 때만 비어있지 않음)
 */
public record CertificateEvidenceResponse(
        String certName,
        String kind,
        String scheduleStatus,
        String registrationStatus,
        String message,
        String sourceName,
        String sourceUrl,
        List<ScheduleRound> scheduleRounds) {
}
