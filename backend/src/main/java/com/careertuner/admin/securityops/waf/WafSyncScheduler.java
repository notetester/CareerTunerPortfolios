package com.careertuner.admin.securityops.waf;

import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.careertuner.admin.securityops.waf.WafSyncMapper.WafProviderRow;
import com.careertuner.admin.securityops.waf.WafSyncModels.WafProvider;
import com.careertuner.admin.securityops.waf.WafSyncModels.WafSyncResult;
import com.careertuner.admin.securityops.waf.WafSyncModels.WafSyncTarget;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * WAF 동기화 큐 드레인 스케줄러.
 *
 * <p>QUEUED/PENDING WAF 이벤트를 주기적으로 처리한다: 활성 프로바이더 설정을 해석해 알맞은 어댑터
 * (Mock 또는 실 HTTP)로 동기화하고 이벤트 상태를 SYNCED/FAILED/PENDING 으로 갱신한다.
 * 이전엔 queueWafSync 가 이벤트만 쌓고 아무도 처리하지 않아 영구 QUEUED 였다 — 그 처리기다.
 * TripTogether {@code processWafSyncQueueOnce} 이식.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WafSyncScheduler {

    private static final int BATCH = 20;

    private final WafSyncMapper mapper;
    private final List<WafSyncAdapter> adapters;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${careertuner.security.waf.sync.fixed-delay-ms:300000}")
    public void processWafSyncQueueOnce() {
        drainOnce();
    }

    /** 대기 이벤트를 1배치 처리하고 처리 건수를 반환한다(스케줄러·수동 트리거 공용). */
    public int drainOnce() {
        List<WafSyncTarget> targets;
        try {
            targets = mapper.findPendingWafSyncTargets(BATCH);
        } catch (Exception e) {
            log.warn("[WAF] 대기 이벤트 조회 실패: {}", e.getMessage());
            return 0;
        }
        if (targets == null || targets.isEmpty()) {
            return 0;
        }
        WafProvider provider = resolveProvider();
        int synced = 0;
        int failed = 0;
        for (WafSyncTarget target : targets) {
            WafSyncResult result = process(provider, target);
            if (result.isSuccess()) {
                synced++;
            } else {
                failed++;
            }
        }
        log.info("[WAF] 큐 드레인 완료: 처리 {}건 (SYNCED {}, FAILED/PENDING {})", targets.size(), synced, failed);
        return targets.size();
    }

    private WafSyncResult process(WafProvider provider, WafSyncTarget target) {
        WafSyncResult result;
        try {
            WafSyncAdapter adapter = adapters.stream()
                    .filter(a -> a.supports(provider, target))
                    .findFirst()
                    .orElse(null);
            if (adapter == null) {
                result = WafSyncModels.WafSyncResult.builder()
                        .handled(false).success(false).status("FAILED")
                        .message("지원 어댑터 없음 provider=" + provider.getProviderCode()).build();
            } else {
                result = adapter.sync(provider, target);
            }
        } catch (Exception e) {
            result = WafSyncModels.WafSyncResult.builder()
                    .handled(false).success(false).status("FAILED")
                    .message(e.getClass().getSimpleName() + ": " + e.getMessage()).build();
        }
        try {
            mapper.updateWafEventResult(target.syncEventId(), result.getStatus(),
                    truncate(result.getMessage(), 2000), result.isSuccess() ? null : truncate(result.getMessage(), 1000));
        } catch (Exception e) {
            log.warn("[WAF] 이벤트 상태 갱신 실패 id={}: {}", target.syncEventId(), e.getMessage());
        }
        return result;
    }

    /** 활성 WAF 프로바이더 설정을 해석한다. 없으면 Mock 폴백. */
    private WafProvider resolveProvider() {
        WafProviderRow row = null;
        try {
            row = mapper.findEnabledWafProvider();
        } catch (Exception e) {
            log.warn("[WAF] 프로바이더 조회 실패: {}", e.getMessage());
        }
        if (row == null) {
            return WafProvider.builder()
                    .providerCode("MOCK_WAF").providerType("WAF").mode("MOCK").enabled(false).build();
        }
        JsonNode config = parseConfig(row.configJson());
        return WafProvider.builder()
                .providerCode(row.providerCode())
                .providerType(row.providerType())
                .mode(row.mode())
                .enabled(row.enabled())
                .endpointUrl(row.endpointUrl())
                .timeoutMs(intVal(config, "timeoutMs", 3000))
                .retryCount(intVal(config, "retryCount", 1))
                .retryBackoffMs(intVal(config, "retryBackoffMs", 500))
                .failOpen(intVal(config, "failOpen", 1))
                .apiKeyRef(textVal(config, "apiKeyRef"))
                .requestMethod(textVal(config, "requestMethod"))
                .requestHeadersJson(textVal(config, "requestHeadersJson"))
                .build();
    }

    private JsonNode parseConfig(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(configJson);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private Integer intVal(JsonNode node, String field, int fallback) {
        JsonNode v = node == null ? null : node.get(field);
        return v == null || v.isNull() ? fallback : v.asInt(fallback);
    }

    private String textVal(JsonNode node, String field) {
        JsonNode v = node == null ? null : node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
