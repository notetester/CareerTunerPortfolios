package com.careertuner.fitanalysis.certificate;

/**
 * 자격증 근거 라우팅 종류. 국가자격 목록(15003024)으로 판별하며, 종류에 따라 일정 근거 조회 경로가 다르다.
 */
public enum CertificateKind {
    /** 국가기술자격(T) — getJMList 시험일정 조회 대상. */
    NATIONAL_TECHNICAL,
    /** 국가전문자격(S) — getJMList 미적용(시행기관 확인 필요). */
    NATIONAL_PROFESSIONAL,
    /** 국가자격 목록에 없음 — 민간/벤더/기타(등록정보 확인, 일정은 주관기관). */
    PRIVATE_OR_OTHER,
    /** 종류 판별 불가(국가자격 목록 조회가 장애로 확인 불가). */
    UNKNOWN
}
