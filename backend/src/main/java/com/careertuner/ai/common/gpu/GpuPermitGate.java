package com.careertuner.ai.common.gpu;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.careertuner.ai.common.settings.AiRuntimeSettings;

/**
 * 단일 GPU(4090 Ollama)를 공유하는 전 도메인 공용 동시성 게이트(GPU 옵션4).
 *
 * <p>사용법 — GPU 로 나가는 HTTP 호출을 try-with-resources 로 감싼다:
 *
 * <pre>{@code
 * try (GpuPermit permit = gpuPermitGate.acquire("analysis")) {
 *     response = httpClient.send(request, ...);
 * }
 * }</pre>
 *
 * <p><b>런타임 제어(DB-first)</b>: ON/OFF·permits·acquire-timeout 을 생성 시점에 고정하지 않고
 * {@link AiRuntimeSettings} 를 통해 <b>호출 시점마다</b> 읽는다({@code application_runtime_setting}
 * DB 우선, 없으면 정적 프로퍼티 fallback). 따라서 DB 행만 바꾸면 재시작 없이 게이트가 켜지거나
 * permits 가 리사이즈된다. OFF 경로는 boolean/int 읽기 외에 아무 오버헤드도 없다.
 *
 * <p>이중 트랙 보장: 전역이 OFF 이거나 해당 도메인이 override 로 제외되어 있으면 {@link #acquire} 는
 * 아무것도 하지 않는 no-op permit 을 즉시 돌려준다 — 기존 무제약 경로와 동작이 동일하다. ON 이면
 * fair 세마포어에서 permit 을 얻을 때까지 대기하고, {@code acquire-timeout} 초과 시
 * {@link GpuPermitTimeoutException} 을 던져 각 도메인의 기존 실패 경로(폴백 등)로 흘린다.
 */
@Component
public class GpuPermitGate {

    private static final Logger log = LoggerFactory.getLogger(GpuPermitGate.class);

    /** OFF 경로에서 매번 새 객체를 만들지 않기 위한 공용 no-op permit. */
    private static final GpuPermit NOOP = new GpuPermit(null);

    /**
     * 런타임 설정 소스. {@link #disabled()} 테스트 팩토리에서는 {@code null} 이며, null 이면
     * 게이트는 항상 OFF(no-op)로 동작한다 — disabled 팩토리가 {@code AiRuntimeSettings} 를 요구하지 않게 한다.
     */
    private final AiRuntimeSettings settings;

    /** ON 경로 공용 세마포어. permits 는 런타임에 리사이즈된다. */
    private final ResizableSemaphore semaphore = new ResizableSemaphore(1);

    /** 현재 세마포어에 반영된 permit 수. 설정과 달라졌을 때만 리사이즈한다(volatile: 다중 스레드 가시성). */
    private volatile int configuredPermits = 1;

    public GpuPermitGate(AiRuntimeSettings settings) {
        this.settings = settings;
    }

    /** 테스트용: 항상 no-op 으로 동작하는 게이트(기존 무제약 경로) — AiRuntimeSettings 불필요. */
    public static GpuPermitGate disabled() {
        return new GpuPermitGate(null);
    }

    /**
     * 이 도메인에 게이트가 적용되는지 — 도메인 override 가 있으면 그 값, 없으면 전역 enabled.
     * settings 가 없으면(disabled 팩토리) 항상 OFF.
     */
    public boolean enabledFor(String domain) {
        if (settings == null) {
            return false;
        }
        Boolean override = settings.gpuGateDomainOverride(domain);
        return override != null ? override : settings.gpuGateEnabled();
    }

    /**
     * GPU 호출 permit 을 얻는다. OFF 면 즉시 no-op permit 반환(대기·계측 없음).
     *
     * @throws GpuPermitTimeoutException acquire-timeout 안에 permit 을 얻지 못한 경우
     */
    public GpuPermit acquire(String domain) {
        if (!enabledFor(domain)) {
            return NOOP;
        }
        // ON 경로: 매 호출마다 런타임 permits/timeout 을 반영한다.
        syncPermits(settings.gpuGatePermits());
        Duration timeout = settings.gpuGateAcquireTimeout();
        long startNanos = System.nanoTime();
        boolean acquired;
        try {
            acquired = semaphore.tryAcquire(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GpuPermitTimeoutException("GPU permit 대기 중 인터럽트 (domain=" + domain + ")", e);
        }
        long waitedMs = (System.nanoTime() - startNanos) / 1_000_000;
        if (!acquired) {
            log.warn("GPU permit 획득 실패 — domain={}, waitedMs={}, queueLength={}",
                    domain, waitedMs, semaphore.getQueueLength());
            throw new GpuPermitTimeoutException(
                    "GPU permit 을 " + timeout + " 안에 얻지 못함 (domain=" + domain + ")");
        }
        if (waitedMs > 1000) {
            log.info("GPU permit 대기 — domain={}, waitedMs={}", domain, waitedMs);
        }
        return new GpuPermit(semaphore);
    }

    /** 목표 permit 수로 세마포어를 리사이즈한다(현재값과 다를 때만). volatile 확인 후 락 안에서 확정한다. */
    private synchronized void syncPermits(int target) {
        if (target != configuredPermits) {
            semaphore.setPermits(target);
            configuredPermits = target;
        }
    }

    /**
     * permit 수를 런타임에 바꿀 수 있는 세마포어. delta 만큼 release/reducePermits 로 조정한다
     * ({@code reducePermits} 는 {@link Semaphore} 의 protected 메서드라 서브클래스에서 호출 가능).
     */
    private static final class ResizableSemaphore extends Semaphore {

        /** 이 세마포어에 현재 반영된 permit 수(리사이즈 delta 계산용). */
        private int current;

        ResizableSemaphore(int permits) {
            super(permits, true); // fair=true: burst 시 도메인 간 기아(starvation) 방지
            this.current = permits;
        }

        synchronized void setPermits(int target) {
            if (target > current) {
                release(target - current);
            } else if (target < current) {
                reducePermits(current - target);
            }
            current = target;
        }
    }

    /** 사용 후 반드시 close(try-with-resources) — no-op permit 은 close 도 no-op. */
    public static final class GpuPermit implements AutoCloseable {

        private final Semaphore semaphore;
        private final AtomicBoolean released = new AtomicBoolean(false);

        private GpuPermit(Semaphore semaphore) {
            this.semaphore = semaphore;
        }

        @Override
        public void close() {
            if (semaphore != null && released.compareAndSet(false, true)) {
                semaphore.release();
            }
        }
    }
}
