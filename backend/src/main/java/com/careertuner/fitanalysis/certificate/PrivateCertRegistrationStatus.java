package com.careertuner.fitanalysis.certificate;

/**
 * 민간자격 등록정보(15075600, odcloud) 조회 상태. odcloud 는 안정적이므로 여기서의 NOT_FOUND 는
 * 실제로 "등록 민간자격 아님"을 뜻한다(q-net 처럼 장애로 인한 미확인이 아님 — 장애는 UPSTREAM_UNAVAILABLE).
 *
 * <p>민간자격은 <b>존재/등록상태/기관 확인용</b>이며 시험일정은 제공하지 않는다(민간 일정은 중앙 데이터 없음 →
 * 주관기관 공식 페이지/수동 입력 = ScheduleEvidenceStatus.MANUAL_REQUIRED).
 */
public enum PrivateCertRegistrationStatus {
    /** 등록된(유효) 민간자격 확인 → 존재/기관 근거로 사용. */
    REGISTERED_ACTIVE,
    /** 폐지/취소된 민간자격만 확인 → 추천 금지 또는 경고. */
    ABOLISHED_OR_CANCELLED,
    /** 공식 등록정보에 없음(조회 정상). → 실재 자격 여부 불확실, 자격명 신중 취급. */
    NOT_FOUND,
    /** odcloud API 오류·타임아웃·키 없음 → 확인 불가(존재 부정 아님). */
    UPSTREAM_UNAVAILABLE
}
