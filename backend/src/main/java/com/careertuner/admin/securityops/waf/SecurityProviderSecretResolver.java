package com.careertuner.admin.securityops.waf;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * WAF 프로바이더 시크릿 참조 해석기.
 *
 * <p>DB에는 시크릿 값이 아니라 WAF 전용 환경 변수/프로퍼티 참조만 저장한다. 다른 애플리케이션
 * 시크릿이나 평문이 실수로 WAF 요청에 사용되지 않도록 허용 접두를 fail-closed로 제한한다.</p>
 */
@Component
@RequiredArgsConstructor
public class SecurityProviderSecretResolver {

    static final String ENV_REFERENCE_PREFIX = "ENV:CAREERTUNER_SECURITY_WAF_";
    static final String PROPERTY_REFERENCE_PREFIX = "PROP:careertuner.security.waf.";

    private final Environment environment;

    public String resolve(String apiKeyRef) {
        if (apiKeyRef == null || apiKeyRef.isBlank()) {
            return null;
        }

        String propertyName;
        if (apiKeyRef.startsWith(ENV_REFERENCE_PREFIX)
                && isValidEnvironmentName(apiKeyRef.substring("ENV:".length()))) {
            propertyName = apiKeyRef.substring("ENV:".length());
        } else if (apiKeyRef.startsWith(PROPERTY_REFERENCE_PREFIX)
                && isValidPropertyName(apiKeyRef.substring("PROP:".length()))) {
            propertyName = apiKeyRef.substring("PROP:".length());
        } else {
            throw new IllegalArgumentException("허용되지 않은 WAF 시크릿 참조입니다.");
        }

        String secret = environment.getProperty(propertyName);
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("WAF 시크릿 참조 값이 설정되지 않았습니다.");
        }
        return secret;
    }

    private boolean isValidEnvironmentName(String name) {
        return name.matches("CAREERTUNER_SECURITY_WAF_[A-Z0-9]+(?:_[A-Z0-9]+)*");
    }

    private boolean isValidPropertyName(String name) {
        return name.matches("careertuner\\.security\\.waf\\.[a-z0-9]+(?:[.-][a-z0-9]+)*");
    }
}
