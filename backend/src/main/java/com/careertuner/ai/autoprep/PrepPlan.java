package com.careertuner.ai.autoprep;

import java.util.List;

/**
 * 두뇌(AutoPrepPlanner)가 만든 실행 계획.
 * steps 는 실행할 핸들러 key 목록(순서대로). 예: ["PROFILE","JOB","FIT","WRITE","INTERVIEW","COMMUNITY"].
 */
public record PrepPlan(
    String intent,
    PrepSlots slots,
    List<String> steps
) {
    /** 표준 풀파이프 순서(6파트). 미지정 시 기본값으로 쓴다. */
    public static List<String> defaultSteps() {
        return List.of("PROFILE", "JOB", "FIT", "WRITE", "INTERVIEW", "COMMUNITY");
    }
}
