package com.careertuner.admin.securityops.waf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.careertuner.admin.securityops.waf.WafSyncModels.WafProvider;
import com.careertuner.admin.securityops.waf.WafSyncModels.WafSyncResult;
import com.careertuner.admin.securityops.waf.WafSyncModels.WafSyncTarget;

import tools.jackson.databind.ObjectMapper;

class WafSyncHttpClientSecurityTest {

    @Test
    void endpointValidationRunsBeforeSecretResolutionOrNetworkCall() {
        SecurityProviderSecretResolver secretResolver = mock(SecurityProviderSecretResolver.class);
        WafExternalEndpointValidator endpointValidator = mock(WafExternalEndpointValidator.class);
        when(endpointValidator.validate(anyString())).thenThrow(new IllegalArgumentException("blocked endpoint"));
        WafSyncHttpClient client = new WafSyncHttpClient(secretResolver, new ObjectMapper(), endpointValidator);
        WafProvider provider = WafProvider.builder()
                .providerCode("TEST_WAF")
                .providerType("WAF")
                .mode("HTTP")
                .enabled(true)
                .endpointUrl("https://127.0.0.1/rules")
                .apiKeyRef("ENV:CAREERTUNER_SECURITY_WAF_TEST_API_KEY")
                .retryCount(0)
                .failOpen(0)
                .build();
        WafSyncTarget target = new WafSyncTarget(1L, 2L, "UPSERT", "TEST_WAF", "IP", "203.0.113.1");

        WafSyncResult result = client.call(provider, target);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getMessage()).contains("IllegalArgumentException");
        verifyNoInteractions(secretResolver);
    }
}
