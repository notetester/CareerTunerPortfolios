package com.careertuner.admin.permission.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.careertuner.admin.permission.web.AdminPermissionInterceptor;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 세부 권한 인터셉터 등록.
 *
 * <p>/api/admin/** 전체에 적용하며 {@code @RequireAdminPermission} 또는 명시적
 * {@code @AdminRoleOnly}가 없는 핸들러는 기본 거부한다.
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
