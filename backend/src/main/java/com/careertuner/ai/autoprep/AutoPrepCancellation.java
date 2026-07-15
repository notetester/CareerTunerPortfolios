package com.careertuner.ai.autoprep;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AutoPrep 한 실행의 협력적 취소 신호.
 *
 * <p>SSE 연결이 닫혀도 이미 외부 제공자에 전달된 HTTP 호출 자체를 항상 회수할 수는 없다. 대신 아직
 * 시작하지 않은 future를 취소하고, 각 핸들러가 유료 호출·DB 변경 직전에 이 신호를 확인해 후속 작업과
 * 결과 누적을 막는다. 이미 시작된 도메인 서비스의 원자적 호출은 그 서비스 계약대로 끝날 수 있다.</p>
 */
public final class AutoPrepCancellation {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicBoolean finished = new AtomicBoolean(false);
    private final ConcurrentLinkedQueue<Future<?>> futures = new ConcurrentLinkedQueue<>();

    public void register(Future<?> future) {
        if (future == null) {
            return;
        }
        futures.add(future);
        if (cancelled.get()) {
            future.cancel(true);
        }
    }

    public boolean cancel() {
        if (finished.get() || !cancelled.compareAndSet(false, true)) {
            return false;
        }
        futures.forEach(future -> future.cancel(true));
        return true;
    }

    public boolean isCancelled() {
        return cancelled.get() || Thread.currentThread().isInterrupted();
    }

    public void checkActive() {
        if (isCancelled()) {
            throw new AutoPrepCancelledException();
        }
    }

    public void finish() {
        finished.set(true);
        futures.clear();
    }
}
