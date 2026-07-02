package com.careertuner.ai.common.gpu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import com.careertuner.ai.common.gpu.GpuPermitGate.GpuPermit;

/**
 * GPU 옵션4 이중 트랙 검증 — OFF(기본)면 기존 무제약 경로와 동일, ON 이면 permits 상한이 실제로 걸린다.
 */
class GpuPermitGateTest {

    private static GpuPermitProperties props(boolean enabled, int permits, Duration timeout) {
        GpuPermitProperties p = new GpuPermitProperties();
        p.setEnabled(enabled);
        p.setPermits(permits);
        p.setAcquireTimeout(timeout);
        return p;
    }

    @Test
    void offByDefaultIsUnlimitedNoop() {
        // 기본 프로퍼티 = OFF: permits=1 이어도 동시 acquire 가 전부 즉시 통과해야 한다
        GpuPermitProperties p = new GpuPermitProperties();
        assertThat(p.isEnabled()).isFalse();
        p.setPermits(1);
        GpuPermitGate gate = new GpuPermitGate(p);

        try (GpuPermit first = gate.acquire("analysis"); GpuPermit second = gate.acquire("correction")) {
            assertThat(gate.enabledFor("analysis")).isFalse();
        }
    }

    @Test
    void onEnforcesPermitLimitAndReleasesOnClose() throws Exception {
        GpuPermitGate gate = new GpuPermitGate(props(true, 1, Duration.ofMillis(200)));

        GpuPermit held = gate.acquire("analysis");
        // permit 이 점유된 동안 두 번째 acquire 는 timeout
        assertThatThrownBy(() -> gate.acquire("moderation"))
                .isInstanceOf(GpuPermitTimeoutException.class);

        // 해제 후에는 다른 스레드가 획득 가능
        held.close();
        AtomicBoolean acquired = new AtomicBoolean(false);
        CountDownLatch done = new CountDownLatch(1);
        Thread t = new Thread(() -> {
            try (GpuPermit permit = gate.acquire("moderation")) {
                acquired.set(true);
            } finally {
                done.countDown();
            }
        });
        t.start();
        assertThat(done.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(acquired).isTrue();
    }

    @Test
    void perDomainOverrideExcludesDomainFromEnabledGate() {
        GpuPermitProperties p = props(true, 1, Duration.ofMillis(200));
        p.setDomains(Map.of("analysis", false));
        GpuPermitGate gate = new GpuPermitGate(p);

        try (GpuPermit held = gate.acquire("moderation")) {
            // 전역 ON + permits=1 소진 상태에서도 override 된 도메인은 무제약 통과
            assertThatCode(() -> gate.acquire("analysis").close()).doesNotThrowAnyException();
        }
    }

    @Test
    void perDomainOverrideCanEnableSingleDomainWhileGlobalOff() {
        GpuPermitProperties p = props(false, 1, Duration.ofMillis(200));
        p.setDomains(Map.of("moderation", true));
        GpuPermitGate gate = new GpuPermitGate(p);

        assertThat(gate.enabledFor("moderation")).isTrue();
        assertThat(gate.enabledFor("analysis")).isFalse();

        try (GpuPermit held = gate.acquire("moderation")) {
            assertThatThrownBy(() -> gate.acquire("moderation"))
                    .isInstanceOf(GpuPermitTimeoutException.class);
        }
    }

    @Test
    void doubleCloseReleasesOnlyOnce() {
        GpuPermitGate gate = new GpuPermitGate(props(true, 1, Duration.ofMillis(200)));

        GpuPermit permit = gate.acquire("analysis");
        permit.close();
        permit.close(); // 중복 close 가 permit 을 초과 반납하지 않는다

        try (GpuPermit again = gate.acquire("analysis")) {
            assertThatThrownBy(() -> gate.acquire("analysis"))
                    .isInstanceOf(GpuPermitTimeoutException.class);
        }
    }

    @Test
    void disabledFactoryIsNoop() {
        GpuPermitGate gate = GpuPermitGate.disabled();
        try (GpuPermit a = gate.acquire("x"); GpuPermit b = gate.acquire("x")) {
            assertThat(gate.enabledFor("x")).isFalse();
        }
    }
}
