package com.careertuner.admin.securityops.waf;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * 프로바이더 시크릿 해석. {@code ENV:NAME}/{@code PROP:key} 접두면 Spring Environment 에서 값을 읽고,
 * 그 외에는 평문으로 취급한다. 실제 API 키를 DB 에 평문 저장하지 않도록 참조만 저장하는 패턴.
 */
@Component
@RequiredArgsConstructor
public class SecurityProviderSecretResolver {

    private final Environment environment;

    public String resolve(String apiKeyRef) {
        if (apiKeyRef == null || apiKeyRef.isBlank()) {
            return null;
        }
        if (apiKeyRef.startsWith("ENV:")) {
            return environment.getProperty(apiKeyRef.substring("ENV:".length()));
        }
        if (apiKeyRef.startsWith("PROP:")) {
            return environment.getProperty(apiKeyRef.substring("PROP:".length()));
        }
        return apiKeyRef;
    }
}
