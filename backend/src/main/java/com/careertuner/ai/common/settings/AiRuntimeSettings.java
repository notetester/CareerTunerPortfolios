package com.careertuner.ai.common.settings;

import java.time.Duration;

import org.springframework.stereotype.Service;

import com.careertuner.ai.common.gpu.GpuPermitProperties;
import com.careertuner.analysis.ai.provider.CareerAnalysisAiProviderProperties;
import com.careertuner.runtimesetting.service.RuntimeSettingService;

/**
 * 정적 설정(application*.yml/@ConfigurationProperties)과 DB 런타임 설정
 * ({@code application_runtime_setting})을 잇는 <b>AI 런타임 설정 브리지</b>.
 *
 * <p>모든 접근자는 <b>DB 키를 먼저 읽고, 실패하거나 행이 없으면 정적 프로퍼티 값을 그대로 fallback</b>
 * 으로 쓴다({@link RuntimeSettingService} 가 어떤 오류에서도 fallback 을 돌려주므로 안전하다).
 * 따라서 DB 에 해당 행이 없거나 DB 가 죽어도 <b>동작은 오늘과 동일</b>하다 — DB 행을 넣거나 바꾸면
 * 재시작 없이 게이트 ON/OFF·permits·타임아웃·C 폴백 시간예산이 런타임에 반영된다.
 *
 * <p>키 네이밍은 정적 프로퍼티 경로를 그대로 미러한다:
 * <ul>
 *   <li>{@code ai.gpu-gate.enabled} ← {@link GpuPermitProperties#isEnabled()}</li>
 *   <li>{@code ai.gpu-gate.permits} ← {@link GpuPermitProperties#getPermits()}</li>
 *   <li>{@code ai.gpu-gate.acquire-timeout-seconds} ← {@link GpuPermitProperties#getAcquireTimeout()}</li>
 *   <li>{@code ai.analysis.chain-total-time-budget-seconds} ← {@link CareerAnalysisAiProviderProperties#getChainTotalTimeBudget()}</li>
 *   <li>{@code ai.analysis.claude-timeout-seconds} ← {@link CareerAnalysisAiProviderProperties#getClaudeTimeout()}</li>
 *   <li>{@code ai.analysis.openai-timeout-seconds} ← {@link CareerAnalysisAiProviderProperties#getOpenaiTimeout()}</li>
 * </ul>
 *
 * <p>도메인별 게이트 override(도메인 단위 ON/OFF)는 DB 로 옮기지 않고 정적
 * {@link GpuPermitProperties#getDomains()} 에 그대로 둔다 — {@link #gpuGateDomainOverride(String)} 로 노출한다.
 */
@Service
public class AiRuntimeSettings {

    private final RuntimeSettingService settings;
    private final GpuPermitProperties gpuPermitProperties;
    private final CareerAnalysisAiProviderProperties props;

    public AiRuntimeSettings(RuntimeSettingService settings,
                             GpuPermitProperties gpuPermitProperties,
                             CareerAnalysisAiProviderProperties props) {
        this.settings = settings;
        this.gpuPermitProperties = gpuPermitProperties;
        this.props = props;
    }

    /* ── GPU permit gate(옵션4) ── */

    /** 게이트 전역 ON/OFF. DB 값 우선, 없으면 정적 {@code gpu-gate.enabled}. */
    public boolean gpuGateEnabled() {
        return settings.getBoolean("ai.gpu-gate.enabled", gpuPermitProperties.isEnabled());
    }

    /** 동시 GPU 호출 허용 permit 수. 최소 1 로 보정(0/음수 설정 방어). */
    public int gpuGatePermits() {
        return Math.max(1, settings.getInt("ai.gpu-gate.permits", gpuPermitProperties.getPermits()));
    }

    /** permit 대기 상한. DB 는 초 단위 정수로 저장, 여기서 Duration 으로 변환. */
    public Duration gpuGateAcquireTimeout() {
        return Duration.ofSeconds(
                settings.getInt("ai.gpu-gate.acquire-timeout-seconds",
                        (int) gpuPermitProperties.getAcquireTimeout().toSeconds()));
    }

    /**
     * 도메인별 게이트 override(도메인 단위 ON/OFF). 정적 프로퍼티에만 존재 —
     * 값이 있으면 그 boolean, 없으면 {@code null}(전역값을 따르라는 뜻).
     */
    public Boolean gpuGateDomainOverride(String domain) {
        return gpuPermitProperties.getDomains().get(domain);
    }

    /* ── C 폴백 체인(OSS→Claude→OpenAI) 시간 정책 ── */

    /** OSS(자체 3B) tier 총 시간예산(재시도·백오프 포함). 0/음수면 무제한(예산 OFF). */
    public Duration analysisOssTotalTimeBudget() {
        return secs("ai.analysis.oss-total-time-budget-seconds", props.getOss().getTotalTimeBudget());
    }

    /** 체인 총 시간예산(재시도 증폭 억제 보조 상한). */
    public Duration analysisChainTotalTimeBudget() {
        return secs("ai.analysis.chain-total-time-budget-seconds", props.getChainTotalTimeBudget());
    }

    /** Claude tier 최소 보장 per-attempt 타임아웃. */
    public Duration analysisClaudeTimeout() {
        return secs("ai.analysis.claude-timeout-seconds", props.getClaudeTimeout());
    }

    /** OpenAI tier 최소 보장 per-attempt 타임아웃. */
    public Duration analysisOpenaiTimeout() {
        return secs("ai.analysis.openai-timeout-seconds", props.getOpenaiTimeout());
    }

    /** DB 초 단위 정수 → Duration. 행이 없으면 정적 fallback 의 초를 그대로 쓴다(동작 불변). */
    private Duration secs(String key, Duration fallback) {
        return Duration.ofSeconds(settings.getInt(key, (int) fallback.toSeconds()));
    }
}
