package com.careertuner.admin.securityops.engine;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import lombok.RequiredArgsConstructor;

/**
 * 차단 인터셉터 등록. 모든 API 요청을 다른 인터셉터보다 먼저(최우선) 평가한다.
 *
 * <p>헬스체크는 로드밸런서/모니터링이 막히면 안 되므로 제외한다. ALLOW(화이트리스트) 규칙이
 * 우선 매칭되면 통과하므로 관리자 대역을 허용목록에 두면 잠금 위험을 피할 수 있다.</p>
 */
@Configuration
@RequiredArgsConstructor
public class BlockSecurityWebConfig implements WebMvcConfigurer {

    private final IpBlockInterceptor ipBlockInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(ipBlockInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/health", "/api/health/**")
                .order(Ordered.HIGHEST_PRECEDENCE);
    }
}
