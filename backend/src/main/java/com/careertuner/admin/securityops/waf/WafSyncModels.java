package com.careertuner.admin.securityops.waf;

import lombok.Builder;
import lombok.Data;

/** WAF 동기화 도메인 모델 묶음. */
public final class WafSyncModels {

    private WafSyncModels() {
    }

    /** 처리 대기 중인 WAF 동기화 대상(이벤트 + 규칙 조인). */
    public record WafSyncTarget(
            Long syncEventId,
            Long blockRuleId,
            String operationType,   // UPSERT/DELETE
            String providerCode,
            String ruleType,        // IP/CIDR/COUNTRY/ASN...
            String ruleValue) {
    }

    /** WAF 프로바이더 설정(admin_security_provider_config + config_json 파싱). */
    @Data
    @Builder
    public static class WafProvider {
        private String providerCode;
        private String providerType;
        private String mode;           // MOCK / HTTP
        private boolean enabled;
        private String endpointUrl;
        private Integer timeoutMs;
        private Integer retryCount;
        private Integer retryBackoffMs;
        private Integer failOpen;       // 1=fail-open(대기), else fail-closed
        private String apiKeyRef;       // ENV:/PROP: 또는 평문
        private String requestMethod;   // POST/PUT/PATCH/DELETE
        private String requestHeadersJson;
    }

    /** 어댑터 처리 결과. */
    @Data
    @Builder
    public static class WafSyncResult {
        private boolean handled;
        private boolean success;
        private String status;   // SYNCED / FAILED / PENDING
        private String message;
    }
}
