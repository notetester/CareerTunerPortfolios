package com.careertuner.admin.securityops.waf;

import java.util.Locale;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.careertuner.admin.securityops.waf.WafSyncModels.WafProvider;
import com.careertuner.admin.securityops.waf.WafSyncModels.WafSyncResult;
import com.careertuner.admin.securityops.waf.WafSyncModels.WafSyncTarget;

import lombok.RequiredArgsConstructor;

/**
 * 실 제공자 HTTP WAF 어댑터 — endpoint 가 http(s) 면 실제 아웃바운드 호출로 동기화. 후순위(@Order 100).
 */
@Component
@Order(100)
@RequiredArgsConstructor
public class HttpWafAdapter implements WafSyncAdapter {

    private final WafSyncHttpClient httpClient;

    @Override
    public boolean supports(WafProvider provider, WafSyncTarget target) {
        String endpoint = provider == null || provider.getEndpointUrl() == null
                ? "" : provider.getEndpointUrl().trim().toLowerCase(Locale.ROOT);
        return endpoint.startsWith("http://") || endpoint.startsWith("https://");
    }

    @Override
    public WafSyncResult sync(WafProvider provider, WafSyncTarget target) {
        return httpClient.call(provider, target);
    }
}
