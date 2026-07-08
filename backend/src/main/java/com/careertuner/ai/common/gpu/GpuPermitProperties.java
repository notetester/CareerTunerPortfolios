package com.careertuner.ai.common.gpu;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * GPU 동시 사용 세마포어(GPU 옵션4) 설정.
 *
 * <p>단일 4090 Ollama 를 A~F 전 도메인이 공유하므로, 백엔드에서 GPU 호출 동시 수를 제한하는
 * 공용 게이트를 둔다. <b>기본은 OFF</b> — OFF 이면 모든 도메인이 기존 무제약 경로 그대로 동작한다
 * (게이트는 boolean 체크 한 번 외에 아무것도 하지 않는다).
 *
 * <pre>
 * careertuner:
 *   ai:
 *     gpu-gate:
 *       enabled: true            # 전역 ON/OFF (기본 false)
 *       permits: 2               # 동시 GPU 호출 허용 수
 *       acquire-timeout: 30s     # 대기 상한 — 초과 시 GpuPermitTimeoutException
 *       domains:                 # 도메인별 override (미지정 도메인은 전역값을 따른다)
 *         analysis: false        # 예: C 만 게이트에서 제외
 * </pre>
 */
@ConfigurationProperties(prefix = "careertuner.ai.gpu-gate")
public class GpuPermitProperties {

    /** 전역 ON/OFF. false 면 어떤 도메인도 게이트를 거치지 않는다(기존 무제약 경로). */
    private boolean enabled = false;

    /**
     * 동시에 GPU 호출을 허용할 요청 수(전역 세마포어). 게이트 ON 일 때만 적용된다.
     * 기본 12 = 2026-07-06 동시성 스윕에서 측정한 공유 4090 의 처리량 포화 무릎점
     * (NP=2/MLM=8 에서 동시성 12 이상은 처리량 정체·지연만 선형 증가). 과부하 시 꼬리지연
     * 폭주를 막는 안전밸브 값이며, 정상 트래픽(총 동시성 &lt;12)은 통과시킨다.
     */
    private int permits = 12;

    /** permit 대기 상한. 초과하면 해당 호출은 실패로 처리돼 각 도메인의 기존 실패 경로(폴백 등)를 탄다. */
    private Duration acquireTimeout = Duration.ofSeconds(30);

    /** 도메인별 ON/OFF override. key = 게이트 도메인 이름(analysis, correction, moderation ...). */
    private Map<String, Boolean> domains = new HashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getPermits() {
        return permits;
    }

    public void setPermits(int permits) {
        this.permits = permits;
    }

    public Duration getAcquireTimeout() {
        return acquireTimeout;
    }

    public void setAcquireTimeout(Duration acquireTimeout) {
        this.acquireTimeout = acquireTimeout;
    }

    public Map<String, Boolean> getDomains() {
        return domains;
    }

    public void setDomains(Map<String, Boolean> domains) {
        this.domains = domains;
    }
}
