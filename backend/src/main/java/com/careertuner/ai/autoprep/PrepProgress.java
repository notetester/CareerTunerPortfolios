package com.careertuner.ai.autoprep;

/**
 * 단계 진행 보고 채널. 핸들러가 내부 세부 작업(서브스텝)을 시작할 때 호출하면
 * 오케스트레이터가 SSE 로 실시간 전송한다. 동기 실행(run)에서는 {@link #NOOP} 를 넘겨 무시한다.
 */
@FunctionalInterface
public interface PrepProgress {

    /** 세부 작업 한 단계 시작을 알린다. name=단계명, desc=짧은 설명. */
    void substep(String name, String desc);

    /** 진행 보고가 필요 없는 동기 실행용 no-op. */
    PrepProgress NOOP = (name, desc) -> { };
}
