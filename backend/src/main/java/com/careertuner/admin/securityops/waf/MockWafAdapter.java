package com.careertuner.admin.securityops.waf;

import java.util.Locale;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.careertuner.admin.securityops.waf.WafSyncModels.WafProvider;
import com.careertuner.admin.securityops.waf.WafSyncModels.WafSyncResult;
import com.careertuner.admin.securityops.waf.WafSyncModels.WafSyncTarget;

/**
 * Mock WAF 어댑터 — 외부 호출 없이 로컬에서 SYNCED 처리(데모/미설정 환경). 최우선(@Order 0).
 * 실 제공자 endpoint(http/https)를 설정하면 HttpWafAdapter 가 대신 잡는다.
 */
@Component
@Order(0)
public class MockWafAdapter implements WafSyncAdapter {

    @Override
    public boolean supports(WafProvider provider, WafSyncTarget target) {
        if (provider == null) {
            return false;
        }
        String mode = norm(provider.getMode());
        String code = norm(provider.getProviderCode());
        String endpoint = provider.getEndpointUrl() == null ? "" : provider.getEndpointUrl().trim().toLowerCase(Locale.ROOT);
        return "MOCK".equals(mode)
                || endpoint.startsWith("mock://")
                || endpoint.isBlank()
                || code.contains("MOCK");
    }

    @Override
    public WafSyncResult sync(WafProvider provider, WafSyncTarget target) {
        return WafSyncResult.builder()
                .handled(true)
                .success(true)
                .status("SYNCED")
                .message("MOCK_SYNCED providerCode=" + norm(provider.getProviderCode())
                        + " op=" + target.operationType()
                        + " target=" + target.ruleType() + ":" + target.ruleValue()
                        + " (외부 호출 없이 로컬 처리)")
                .build();
    }

    private static String norm(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
