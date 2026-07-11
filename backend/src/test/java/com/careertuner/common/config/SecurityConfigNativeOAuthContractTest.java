package com.careertuner.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class SecurityConfigNativeOAuthContractTest {

    @Test
    void nativeOAuthStartAndExchangeArePublicPostAuthenticationEntrypoints() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/careertuner/common/config/SecurityConfig.java"));
        int publicPostMatchers = source.indexOf(".requestMatchers(HttpMethod.POST,");
        int publicGetMatchers = source.indexOf(".requestMatchers(HttpMethod.GET,", publicPostMatchers);
        String postBlock = source.substring(publicPostMatchers, publicGetMatchers);

        assertThat(postBlock)
                .contains("\"/api/auth/oauth/*/native/start\"")
                .contains("\"/api/auth/oauth/native/exchange\"")
                .contains(".permitAll()");
    }
}
