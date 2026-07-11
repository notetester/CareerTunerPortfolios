package com.careertuner.fitanalysis.certificate;

/**
 * 자격증 시험일정 근거의 조회 상태. C 는 이 상태에 따라 답변 수위를 조절한다 —
 * <b>확인된 것만 출처와 함께 말하고, 불확실하면 날짜를 만들지 않는다</b>.
 *
 * <p><b>중요(오류≠부재 구분):</b> 공식 API 가 타임아웃/오류/미응답인 것({@link #UPSTREAM_UNAVAILABLE})과
 * 조회는 됐으나 일정이 실제로 없는 것({@link #OFFICIAL_NO_SCHEDULE})/자격 자체가 없는 것({@link #NOT_FOUND})은
 * 전혀 다르다. API 장애를 "일정 없음"이라고 말하면 안 되므로 반드시 분리한다. 예: q-net 이 정상 envelope 안에
 * resultCode 99(SocketTimeout)를 담아 보내면 이는 UPSTREAM_UNAVAILABLE 이지 NOT_FOUND 가 아니다.
 */
public enum ScheduleEvidenceStatus {
    /** 공식 출처에서 현재연도 일정 확인 → 일정 기반 전략 제공. */
    VERIFIED_CURRENT,
    /**
     * 공단 연간 <b>사전공고(안)</b> 기준 일정 — 자격별 최종 시행계획 공고로 확정되기 전 단계.
     * VERIFIED_CURRENT 보다 낮은 신뢰층: 일정 제시는 하되 '변경 가능, 최종 공고 확인 필수'를 명시한다(국가전문자격).
     */
    PREANNOUNCED,
    /** 공식 응답은 정상이나 해당 종목 일정이 비어 있음 → 일반 학습계획만. */
    OFFICIAL_NO_SCHEDULE,
    /** 오래된 일정만 확인됨 → 날짜 단정 금지, 공식 재확인 표시(다중/과거 출처 연동 후 사용). */
    STALE_ONLY,
    /** 출처 간 일정 충돌 → 사용자에게 공식 확인 요청(다중 출처 연동 후 사용). */
    CONFLICTING,
    /** 해당 자격/일정 자체를 찾지 못함(조회는 정상). → 일정 조언 생략. */
    NOT_FOUND,
    /** 확인 불가 — 공식 API 오류·타임아웃·미응답, 키 없음, 조회 입력 없음, 정상(00) 미확증. → "일정 없음"이 아니라 "확인 못함". */
    UPSTREAM_UNAVAILABLE,
    /** 중앙 공공데이터엔 없고 주관기관/관리자/사용자 입력이 필요함(민간자격 일정 등). */
    MANUAL_REQUIRED,
    /** 해당 자격 유형에는 적용 가능한 일정 provider 가 없음(예: 국가전문자격 — getJMList 미적용). */
    NOT_APPLICABLE
}
