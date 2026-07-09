package com.careertuner.fitanalysis.certificate;

/**
 * 자격증 전략 판단 상태값. 자격증은 CareerTuner 의 <b>보조 전략</b>이므로 "추천하지 않음"({@link #NOT_NEEDED})과
 * "후순위"({@link #OPTIONAL_LOW_PRIORITY})도 정상 결과다 — 자격증을 권하지 않는 것도 하나의 전략이다.
 *
 * <p>{@link #NOT_FEASIBLE_FOR_THIS_APPLICATION} 은 국가기술자격 시험일정(공공데이터 15003029) 등 일정 근거가
 * 붙는 후속 단계에서, 시험/발표일이 공고 마감 뒤라 이번 지원엔 반영 불가일 때 활성화된다(이번 단계 미사용).
 */
public enum CertificateStrategyStatus {
    /** 현 시점 자격증 전략 불필요(정상 결과). 게이트 OFF 의 기본값. */
    NOT_NEEDED,
    /** 사용자가 이미 보유한 자격증을 공고 맥락에서 강점으로 어필. */
    USE_EXISTING_AS_STRENGTH,
    /** 있으면 좋으나 후순위(정상 결과). 객관적 근거가 약할 때. */
    OPTIONAL_LOW_PRIORITY,
    /** 부족 역량 보완 수단으로 추천. */
    RECOMMENDED,
    /** 공고 명시 또는 면허형 직무라 강하게 필요. */
    REQUIRED_OR_STRONGLY_PREFERRED,
    /** 일정상 이번 지원엔 반영 어려움, 장기 보완용(일정 근거 연동 후 활성화). */
    NOT_FEASIBLE_FOR_THIS_APPLICATION
}
