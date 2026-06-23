package com.careertuner.ai.autoprep;

/**
 * 6파트 각 도메인이 구현하는 단계 핸들러. 오케스트레이터가 key 로 찾아 호출한다.
 * 새 파트는 이 인터페이스를 구현한 @Component 를 추가하기만 하면 자동 등록된다(오케 무변경).
 */
public interface PrepStepHandler {

    /** 이 핸들러가 처리하는 단계 key. 예: PROFILE / JOB / FIT / WRITE / INTERVIEW / COMMUNITY. */
    String key();

    /** 현재 실행 가능한지(서빙·데이터 준비 여부). false 면 오케가 SKIPPED 처리. */
    default boolean enabled() {
        return true;
    }

    /** 단계 실행. 실패 시 BusinessException 을 던지면 오케가 FAILED 로 기록하고 계속 진행한다. */
    PrepStepResult handle(PrepStepContext context);
}
