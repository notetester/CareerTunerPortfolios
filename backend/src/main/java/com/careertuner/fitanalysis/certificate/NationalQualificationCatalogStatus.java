package com.careertuner.fitanalysis.certificate;

/**
 * 국가자격 종목 목록(15003024) 조회 상태. 자격명이 국가자격인지(기술/전문) 판별하고 canonical key(jmCd)를 얻는 용도.
 * odcloud 민간과 달리 q-net 이라, 오류/타임아웃/게이트웨이 오류는 {@link #UPSTREAM_UNAVAILABLE}(부재 아님).
 */
public enum NationalQualificationCatalogStatus {
    /** 국가자격 목록에서 해당 자격명 확인됨(entry 유효). */
    FOUND,
    /** 정상 조회했으나 국가자격 목록에 없음(→ 민간/기타일 수 있음). */
    NOT_FOUND,
    /** 공식 API 오류·타임아웃·게이트웨이 오류·키 없음·정상(00) 미확증 → 확인 불가(부재 아님). */
    UPSTREAM_UNAVAILABLE
}
