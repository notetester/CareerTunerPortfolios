package com.careertuner.billing.policy;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import lombok.RequiredArgsConstructor;

/** 동의 검증 뒤, 실제 컨트롤러/AI 호출보다 먼저 비용 확인을 집행한다. */
@Configuration
@RequiredArgsConstructor
public class AiChargePreflightWebConfig implements WebMvcConfigurer {

    private final AiChargePreflightInterceptor interceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                .addPathPatterns("/api/**")
                .order(-90);
    }
}
