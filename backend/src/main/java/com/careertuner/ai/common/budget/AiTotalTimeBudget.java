package com.careertuner.ai.common.budget;

import java.time.Duration;

/**
 * AI 호출의 <b>총 시간예산</b>(재시도·백오프 포함 전체 상한) 공용 헬퍼 — E→C 로 이어진
 * totalTimeBudget 패턴을 전 도메인으로 확장하기 위한 단일 구현.
 *
 * <p><b>ON/OFF 시맨틱(핵심)</b>: 예산이 {@code null}/0/음수면 <b>무제한(OFF)</b> — 모든 메서드가
 * no-op 으로 동작해 기존 무예산 경로와 동일하다. 양수면 ON. 도메인별 프로퍼티
 * {@code total-time-budget: 0} 으로 언제든 끌 수 있다.
 *
 * <pre>{@code
 * AiTotalTimeBudget budget = AiTotalTimeBudget.start(props.getTotalTimeBudget());
 * for (int i = 0; i < attempts; i++) {
 *     if (budget.expired()) break;                       // 재시도 루프 상한
 *     send(request, budget.cap(props.getTimeout()));     // per-attempt 타임아웃 절삭
 *     sleep(budget.capBackoffMs(backoffMs));             // 백오프 절삭
 * }
 * }</pre>
 */
public final class AiTotalTimeBudget {

    private final boolean unlimited;
    private final long deadlineNanos;

    private AiTotalTimeBudget(boolean unlimited, long deadlineNanos) {
        this.unlimited = unlimited;
        this.deadlineNanos = deadlineNanos;
    }

    /** 호출 시작 시점에 생성. {@code total} 이 null/0/음수면 무제한(OFF). */
    public static AiTotalTimeBudget start(Duration total) {
        if (total == null || total.isZero() || total.isNegative()) {
            return new AiTotalTimeBudget(true, 0L);
        }
        return new AiTotalTimeBudget(false, System.nanoTime() + total.toNanos());
    }

    /**
     * 폴백 체인용 재시도 상한 deadline(System.nanoTime 기준). {@code total} 이 null/0/음수면
     * {@link Long#MAX_VALUE}(무제한). 각 tier 클라이언트는 <b>재시도 전</b>에만
     * {@code System.nanoTime() < chainDeadlineNanos} 를 확인한다 — 첫 시도는 절대 이 값에 막히지 않는다
     * (per-tier 최소 보장 타임아웃이 우선). 즉 total 은 재시도 증폭만 억제하는 보조 상한이다.
     */
    public static long deadlineNanos(Duration total) {
        if (total == null || total.isZero() || total.isNegative()) {
            return Long.MAX_VALUE;
        }
        return System.nanoTime() + total.toNanos();
    }

    /** 무제한(OFF) 여부. */
    public boolean unlimited() {
        return unlimited;
    }

    /** 예산 소진 여부 — 무제한이면 항상 false. */
    public boolean expired() {
        return !unlimited && System.nanoTime() >= deadlineNanos;
    }

    /**
     * per-attempt 타임아웃을 남은 예산으로 절삭한다. 무제한이면 {@code perAttempt} 그대로.
     * 예산이 이미 소진됐거나 {@code perAttempt} 가 null/0/음수(설정 오류)면 1ms
     * (사실상 즉시 실패 — 호출 전 {@link #expired()} 체크 권장).
     */
    public Duration cap(Duration perAttempt) {
        if (unlimited) {
            return perAttempt;
        }
        Duration remaining = remaining();
        if (remaining.compareTo(Duration.ofMillis(1)) < 0) {
            return Duration.ofMillis(1);
        }
        if (perAttempt == null || perAttempt.isZero() || perAttempt.isNegative()) {
            return Duration.ofMillis(1);
        }
        return perAttempt.compareTo(remaining) > 0 ? remaining : perAttempt;
    }

    /** 백오프 sleep 시간을 남은 예산으로 절삭한다(음수 방지). 무제한이면 그대로. */
    public long capBackoffMs(long backoffMs) {
        if (unlimited) {
            return backoffMs;
        }
        return Math.min(Math.max(0, backoffMs), Math.max(0, remaining().toMillis()));
    }

    /** 남은 예산 — 무제한이면 호출하지 말 것(로깅용으로만, 무제한 시 0 반환). */
    public Duration remaining() {
        if (unlimited) {
            return Duration.ZERO;
        }
        return Duration.ofNanos(Math.max(0, deadlineNanos - System.nanoTime()));
    }
}
