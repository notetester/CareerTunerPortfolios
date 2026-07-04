package com.careertuner.ai.common.gpu;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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
 * <p>이중 트랙 보장: {@code careertuner.ai.gpu-gate.enabled=false}(기본) 또는 해당 도메인이
 * {@code domains.<이름>: false} 로 제외되어 있으면 {@link #acquire} 는 아무것도 하지 않는 no-op
 * permit 을 즉시 돌려준다 — 기존 무제약 경로와 동작이 동일하다. ON 이면 fair 세마포어에서 permit 을
 * 얻을 때까지 대기하고, {@code acquire-timeout} 초과 시 {@link GpuPermitTimeoutException} 을 던져
 * 각 도메인의 기존 실패 경로(폴백 등)로 흘린다.
 */
@Component
public class GpuPermitGate {

    private static final Logger log = LoggerFactory.getLogger(GpuPermitGate.class);

    /** OFF 경로에서 매번 새 객체를 만들지 않기 위한 공용 no-op permit. */
    private static final GpuPermit NOOP = new GpuPermit(null);

    private final GpuPermitProperties properties;
    private final Semaphore semaphore;

    public GpuPermitGate(GpuPermitProperties properties) {
        this.properties = properties;
        // fair=true: burst 시 도메인 간 기아(starvation) 방지
        this.semaphore = new Semaphore(Math.max(1, properties.getPermits()), true);
        if (properties.isEnabled()) {
            log.info("GPU permit gate ON — permits={}, acquireTimeout={}, domainOverrides={}",
                    properties.getPermits(), properties.getAcquireTimeout(), properties.getDomains());
        }
    }

    /** 테스트용: 항상 no-op 으로 동작하는 게이트(기존 무제약 경로). */
    public static GpuPermitGate disabled() {
        return new GpuPermitGate(new GpuPermitProperties());
    }

    /** 이 도메인에 게이트가 적용되는지 — 도메인 override 가 있으면 그 값, 없으면 전역 enabled. */
    public boolean enabledFor(String domain) {
        Boolean override = properties.getDomains().get(domain);
        return override != null ? override : properties.isEnabled();
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
        Duration timeout = properties.getAcquireTimeout();
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
