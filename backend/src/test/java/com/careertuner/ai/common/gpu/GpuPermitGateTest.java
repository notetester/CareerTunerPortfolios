package com.careertuner.ai.common.gpu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import com.careertuner.ai.common.gpu.GpuPermitGate.GpuPermit;
import com.careertuner.ai.common.settings.AiRuntimeSettings;

/**
 * GPU 옵션4 이중 트랙 검증 — OFF(기본)면 기존 무제약 경로와 동일, ON 이면 permits 상한이 실제로 걸린다.
 *
 * <p>게이트는 이제 ON/OFF·permits·acquire-timeout 을 {@link AiRuntimeSettings} 에서 <b>호출 시점마다</b>
 * 읽으므로, 각 테스트는 의도한 enabled/permits/timeout 을 돌려주는 mock 설정으로 게이트를 만든다.
 * 도메인 override 는 기본적으로 없음({@code null} → 전역값)으로 스텁한다.
 */
class GpuPermitGateTest {

    /** 전역 enabled/permits/timeout 을 돌려주고, 모든 도메인 override 는 null(전역값 사용)인 mock 설정. */
    private static AiRuntimeSettings settings(boolean enabled, int permits, Duration timeout) {
        AiRuntimeSettings s = mock(AiRuntimeSettings.class);
        when(s.gpuGateEnabled()).thenReturn(enabled);
        when(s.gpuGatePermits()).thenReturn(permits);
        when(s.gpuGateAcquireTimeout()).thenReturn(timeout);
        // Mockito 는 Boolean 반환 메서드의 unstubbed 값을 null 이 아닌 false 로 주므로, 도메인 override 를 명시적으로
        // null(=전역값 따름)로 스텁한다. 특정 도메인 override 테스트는 이 뒤에 개별 stub 으로 덮어쓴다.
        when(s.gpuGateDomainOverride(anyString())).thenReturn(null);
        return s;
    }

    private static GpuPermitGate gate(boolean enabled, int permits, Duration timeout) {
        return new GpuPermitGate(settings(enabled, permits, timeout));
    }

    @Test
    void offByDefaultIsUnlimitedNoop() {
        // 전역 OFF: permits=1 이어도 동시 acquire 가 전부 즉시 통과해야 한다
        GpuPermitGate gate = gate(false, 1, Duration.ofMillis(200));

        try (GpuPermit first = gate.acquire("analysis"); GpuPermit second = gate.acquire("correction")) {
            assertThat(gate.enabledFor("analysis")).isFalse();
        }
    }

    @Test
    void onEnforcesPermitLimitAndReleasesOnClose() throws Exception {
        GpuPermitGate gate = gate(true, 1, Duration.ofMillis(200));

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
        AiRuntimeSettings s = settings(true, 1, Duration.ofMillis(200));
        when(s.gpuGateDomainOverride("analysis")).thenReturn(false); // analysis 만 게이트에서 제외
        GpuPermitGate gate = new GpuPermitGate(s);

        try (GpuPermit held = gate.acquire("moderation")) {
            // 전역 ON + permits=1 소진 상태에서도 override 된 도메인은 무제약 통과
            assertThatCode(() -> gate.acquire("analysis").close()).doesNotThrowAnyException();
        }
    }

    @Test
    void perDomainOverrideCanEnableSingleDomainWhileGlobalOff() {
        AiRuntimeSettings s = settings(false, 1, Duration.ofMillis(200));
        when(s.gpuGateDomainOverride("moderation")).thenReturn(true); // 전역 OFF 여도 moderation 만 ON
        GpuPermitGate gate = new GpuPermitGate(s);

        assertThat(gate.enabledFor("moderation")).isTrue();
        assertThat(gate.enabledFor("analysis")).isFalse();

        try (GpuPermit held = gate.acquire("moderation")) {
            assertThatThrownBy(() -> gate.acquire("moderation"))
                    .isInstanceOf(GpuPermitTimeoutException.class);
        }
    }

    @Test
    void doubleCloseReleasesOnlyOnce() {
        GpuPermitGate gate = gate(true, 1, Duration.ofMillis(200));

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

    @Test
    void permitsResizeAtRuntimeWhenSettingsChange() {
        // 런타임 리사이즈: 처음엔 permits=1(두 번째 acquire 는 timeout), 설정이 2로 바뀌면 두 번째도 통과한다.
        AiRuntimeSettings s = mock(AiRuntimeSettings.class);
        when(s.gpuGateEnabled()).thenReturn(true);
        when(s.gpuGateAcquireTimeout()).thenReturn(Duration.ofMillis(200));
        when(s.gpuGatePermits()).thenReturn(1);
        when(s.gpuGateDomainOverride(anyString())).thenReturn(null);
        GpuPermitGate gate = new GpuPermitGate(s);

        try (GpuPermit first = gate.acquire("analysis")) {
            // permits=1 소진 → 두 번째는 실패
            assertThatThrownBy(() -> gate.acquire("analysis"))
                    .isInstanceOf(GpuPermitTimeoutException.class);

            // 설정을 2로 올리면 다음 acquire 가 세마포어를 리사이즈해 두 번째 permit 을 발급한다.
            when(s.gpuGatePermits()).thenReturn(2);
            assertThatCode(() -> gate.acquire("analysis").close()).doesNotThrowAnyException();
        }
    }
}
