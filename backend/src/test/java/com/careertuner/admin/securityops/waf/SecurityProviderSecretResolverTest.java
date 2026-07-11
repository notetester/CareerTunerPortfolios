package com.careertuner.admin.securityops.waf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class SecurityProviderSecretResolverTest {

    @Test
    void resolvesOnlyWafScopedEnvironmentAndPropertyReferences() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("CAREERTUNER_SECURITY_WAF_CLOUDFLARE_API_KEY", "env-secret")
                .withProperty("careertuner.security.waf.cloudflare.api-key", "property-secret");
        SecurityProviderSecretResolver resolver = new SecurityProviderSecretResolver(environment);

        assertThat(resolver.resolve("ENV:CAREERTUNER_SECURITY_WAF_CLOUDFLARE_API_KEY"))
                .isEqualTo("env-secret");
        assertThat(resolver.resolve("PROP:careertuner.security.waf.cloudflare.api-key"))
                .isEqualTo("property-secret");
        assertThat(resolver.resolve(null)).isNull();
    }

    @Test
    void rejectsPlaintextAndReferencesOutsideWafScope() {
        SecurityProviderSecretResolver resolver = new SecurityProviderSecretResolver(new MockEnvironment());

        for (String reference : new String[] {
                "plaintext-api-key",
                "ENV:AWS_SECRET_ACCESS_KEY",
                "ENV:CAREERTUNER_SECURITY_WAF_",
                "PROP:spring.datasource.password",
                "PROP:careertuner.security.waf."
        }) {
            assertThatThrownBy(() -> resolver.resolve(reference))
                    .as(reference)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("허용되지 않은 WAF 시크릿 참조입니다.");
        }
    }

    @Test
    void failsClosedWhenAllowedReferenceHasNoValue() {
        SecurityProviderSecretResolver resolver = new SecurityProviderSecretResolver(new MockEnvironment());

        assertThatThrownBy(() -> resolver.resolve("ENV:CAREERTUNER_SECURITY_WAF_CLOUDFLARE_API_KEY"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("WAF 시크릿 참조 값이 설정되지 않았습니다.");
    }
}
