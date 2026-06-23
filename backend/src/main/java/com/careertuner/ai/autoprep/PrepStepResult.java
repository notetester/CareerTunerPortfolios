package com.careertuner.ai.autoprep;

/**
 * 한 단계(파트) 실행 결과. 프론트 타임라인/SSE 표시와 다음 단계 입력에 쓰인다.
 * status: DONE | SKIPPED | FAILED.
 */
public record PrepStepResult(
    String key,
    String status,
    String summary,
    Object detail,
    long elapsedMs
) {
    public static PrepStepResult done(String key, String summary, Object detail, long elapsedMs) {
        return new PrepStepResult(key, "DONE", summary, detail, elapsedMs);
    }

    public static PrepStepResult skipped(String key, String summary) {
        return new PrepStepResult(key, "SKIPPED", summary, null, 0L);
    }

    public static PrepStepResult failed(String key, String summary, long elapsedMs) {
        return new PrepStepResult(key, "FAILED", summary, null, elapsedMs);
    }
}
