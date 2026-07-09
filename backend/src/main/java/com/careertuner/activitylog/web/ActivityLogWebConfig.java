package com.careertuner.activitylog.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import lombok.RequiredArgsConstructor;

/**
 * 활동 로그 인터셉터 등록. 차단 인터셉터(최우선) 다음 순서로 모든 /api 요청을 기록한다.
 * 헬스체크는 노이즈라 제외한다.
 */
@Configuration
@RequiredArgsConstructor
public class ActivityLogWebConfig implements WebMvcConfigurer {

    private final ActivityLogInterceptor activityLogInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(activityLogInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/health", "/api/health/**")
                .order(0);
    }
}
