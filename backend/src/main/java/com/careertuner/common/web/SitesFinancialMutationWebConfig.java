package com.careertuner.common.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import lombok.RequiredArgsConstructor;

/** IP 차단 다음, 동의·활동 로그보다 먼저 Sites 금융 mutation 정책을 적용한다. */
@Configuration
@RequiredArgsConstructor
public class SitesFinancialMutationWebConfig implements WebMvcConfigurer {

    private final SitesFinancialMutationInterceptor sitesFinancialMutationInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 경로 패턴을 두지 않고 최종 HandlerMethod의 표식을 검사해 percent-encoding/정규화 우회를 막는다.
        registry.addInterceptor(sitesFinancialMutationInterceptor).order(-200);
    }
}
