package com.careertuner.auth.config;

import java.net.URI;
import java.util.Arrays;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.careertuner.common.config.CareerTunerProperties;

import lombok.RequiredArgsConstructor;

/** mock OAuth가 로컬 개발 환경 밖에서 인증 수단으로 노출되는 것을 부팅 단계에서 차단한다. */
@Component
@RequiredArgsConstructor
public class OAuthMockSafetyGuard implements SmartInitializingSingleton {

    private final CareerTunerProperties properties;
    private final Environment environment;

    @Override
    public void afterSingletonsInstantiated() {
        if (!properties.getOauth().isMockEnabled()) {
            return;
        }

        String[] activeProfiles = environment.getActiveProfiles();
        boolean exclusivelyLocalProfile = activeProfiles.length == 1
                && Arrays.stream(activeProfiles).allMatch("local"::equalsIgnoreCase);
        if (!exclusivelyLocalProfile || !isLoopbackApiBase(properties.getApp().getApiBaseUrl())) {
            throw new IllegalStateException(
                    "OAuth mock은 local 프로파일과 loopback API 주소에서만 활성화할 수 있습니다.");
        }
    }

    static boolean isLoopbackApiBase(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl == null ? "" : rawUrl.trim());
            String host = uri.getHost();
            return host != null && ("localhost".equalsIgnoreCase(host)
                    || host.toLowerCase().endsWith(".localhost")
                    || "127.0.0.1".equals(host)
                    || "::1".equals(host)
                    || "[::1]".equals(host));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
