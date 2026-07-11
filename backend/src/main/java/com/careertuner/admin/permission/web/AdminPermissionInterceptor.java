package com.careertuner.admin.permission.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.careertuner.admin.permission.annotation.AdminRoleOnly;
import com.careertuner.admin.permission.annotation.RequireAdminPermission;
import com.careertuner.admin.permission.service.EffectivePermissionService;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * {@link RequireAdminPermission} 집행 인터셉터.
 *
 * <p>preHandle: 클래스와 메서드의 어노테이션을 각각 읽어 실효 권한을 검사한다.
 * SUPER_ADMIN role 은 전체 통과, ADMIN role 은 각 선언 집합에서 하나 이상씩 보유해야 한다.
 * 따라서 클래스의 READ와 메서드의 CREATE/UPDATE/DELETE가 함께 선언되면 둘 다 필요하다.
 * 미보유 시 {@code BusinessException(FORBIDDEN)} — 전역 예외 처리기가 ApiResponse 403 으로 변환한다.</p>
 *
 * <p>afterCompletion: 권한/그룹 변경 API(/api/admin/super/** 의 쓰기 요청)가 성공하면
 * 실효 권한 캐시를 전체 무효화한다(SuperAdminController 는 W3/공통 소유라 직접 수정하지 않고
 * 경로 관찰로 evict 를 건다 — 60초 TTL 의 지연 제거용).</p>
 */
@Component
@RequiredArgsConstructor
public class AdminPermissionInterceptor implements HandlerInterceptor {

    private static final String SUPER_ADMIN_PATH_PREFIX = "/api/admin/super";

    private final EffectivePermissionService effectivePermissionService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        AuthUser authUser = currentAuthUser();
        if (authUser == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "관리자 권한이 필요합니다.");
        }
        if ("SUPER_ADMIN".equals(authUser.role())) {
            return true; // SUPER_ADMIN 은 세부 권한과 무관하게 전체 통과
        }
        if (!"ADMIN".equals(authUser.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "관리자 권한이 필요합니다.");
        }
        if (isRoleOnly(handlerMethod)) {
            return true;
        }
        RequireAdminPermission onClass = handlerMethod.getBeanType().getAnnotation(RequireAdminPermission.class);
        RequireAdminPermission onMethod = handlerMethod.getMethodAnnotation(RequireAdminPermission.class);
        if ((onClass == null || onClass.value().length == 0)
                && (onMethod == null || onMethod.value().length == 0)) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "관리자 API에 필요한 세부 권한 정책이 선언되지 않았습니다.");
        }
        requireAny(authUser, onClass);
        requireAny(authUser, onMethod);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 권한 변경 API 성공 후 캐시 무효화 — 조회(GET) 는 제외, 4xx/5xx 응답도 제외.
        if (request.getRequestURI() != null
                && request.getRequestURI().startsWith(SUPER_ADMIN_PATH_PREFIX)
                && !"GET".equalsIgnoreCase(request.getMethod())
                && response.getStatus() < 400) {
            effectivePermissionService.evictAll();
        }
    }

    private void requireAny(AuthUser authUser, RequireAdminPermission required) {
        if (required == null || required.value().length == 0) {
            return;
        }
        if (!effectivePermissionService.hasAny(authUser.id(), required.value())) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "이 기능을 수행할 관리자 세부 권한이 없습니다. (필요 권한: "
                            + String.join(", ", required.value()) + ")");
        }
    }

    private boolean isRoleOnly(HandlerMethod handlerMethod) {
        AdminRoleOnly onMethod = handlerMethod.getMethodAnnotation(AdminRoleOnly.class);
        if (onMethod != null) {
            return true;
        }
        // 메서드의 세부 권한 선언이 클래스 role-only 기본값보다 우선한다.
        if (handlerMethod.getMethodAnnotation(RequireAdminPermission.class) != null) {
            return false;
        }
        return handlerMethod.getBeanType().getAnnotation(AdminRoleOnly.class) != null;
    }

    private AuthUser currentAuthUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser authUser)) {
            return null;
        }
        return authUser;
    }
}
