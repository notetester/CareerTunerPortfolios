package com.careertuner.fitanalysis.certificate;

/**
 * 자격증 시험일정 근거의 조회 상태. C 는 이 상태에 따라 답변 수위를 조절한다 —
 * <b>확인된 것만 출처와 함께 말하고, 불확실하면 날짜를 만들지 않는다</b>.
 *
 * <p>단일 공식 출처(q-net 국가기술자격 API)에서 도달 가능한 상태는 VERIFIED_CURRENT / OFFICIAL_NO_SCHEDULE /
 * NOT_FOUND 세 가지다. STALE_ONLY·CONFLICTING 은 다중 출처/과거 데이터 비교가 붙는 후속 단계에서 활성화된다.
 */
public enum ScheduleEvidenceStatus {
    /** 공식 출처에서 현재연도 일정 확인 → 일정 기반 전략 제공. */
    VERIFIED_CURRENT,
    /** 공식 응답은 정상이나 해당 종목 일정이 비어 있음 → 일반 학습계획만. */
    OFFICIAL_NO_SCHEDULE,
    /** 오래된 일정만 확인됨 → 날짜 단정 금지, 공식 재확인 표시(다중/과거 출처 연동 후 사용). */
    STALE_ONLY,
    /** 출처 간 일정 충돌 → 사용자에게 공식 확인 요청(다중 출처 연동 후 사용). */
    CONFLICTING,
    /** 신뢰 가능한 일정 미확인(키 없음·타임아웃·오류·미조회) → 일정 조언 생략, 날짜 미생성. */
    NOT_FOUND
}
