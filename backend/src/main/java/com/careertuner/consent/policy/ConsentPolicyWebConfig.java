package com.careertuner.consent.policy;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import lombok.RequiredArgsConstructor;

/** IP 차단 다음, 일반 활동 로그보다 먼저 동의 정책을 평가한다. */
@Configuration
@RequiredArgsConstructor
public class ConsentPolicyWebConfig implements WebMvcConfigurer {

    private final ConsentPolicyInterceptor consentPolicyInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(consentPolicyInterceptor)
                .addPathPatterns("/api/**")
                .order(-100);
    }
}
