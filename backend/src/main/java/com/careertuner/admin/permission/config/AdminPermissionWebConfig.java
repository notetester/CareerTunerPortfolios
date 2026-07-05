package com.careertuner.admin.permission.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.careertuner.admin.permission.web.AdminPermissionInterceptor;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 세부 권한 인터셉터 등록.
 *
 * <p>/api/admin/** 전체에 적용하되, 실제 검사는 {@code @RequireAdminPermission}
 * 어노테이션이 붙은 핸들러에서만 수행된다(어노테이션 없는 컨트롤러는 기존 role 검사만).
 * evict 훅(/api/admin/super/** 쓰기 감지)을 위해 super 경로도 포함한다.</p>
 */
@Configuration
@RequiredArgsConstructor
public class AdminPermissionWebConfig implements WebMvcConfigurer {

    private final AdminPermissionInterceptor adminPermissionInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminPermissionInterceptor)
                .addPathPatterns("/api/admin/**");
    }
}
