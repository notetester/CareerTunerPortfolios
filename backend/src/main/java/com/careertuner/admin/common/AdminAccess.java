package com.careertuner.admin.common;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;

/**
 * 관리자 API 공통 권한 검사.
 *
 * <p>기존 ADMIN 역할은 일반 운영 기능 접근을 허용하고, SUPER_ADMIN은 관리자 권한/정책처럼
 * 더 넓은 운영 권한이 필요한 기능에 사용한다. URL 레벨 SecurityConfig도 같은 기준을 맞춘다.</p>
 */
public final class AdminAccess {

    private AdminAccess() {
    }

    public static void requireAdmin(AuthUser authUser) {
        if (authUser == null || !isAdmin(authUser)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "관리자 권한이 필요합니다.");
        }
    }

    public static void requireSuperAdmin(AuthUser authUser) {
        if (authUser == null || !"SUPER_ADMIN".equals(authUser.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "슈퍼 관리자 권한이 필요합니다.");
        }
    }

    public static boolean isAdmin(AuthUser authUser) {
        return authUser != null && ("ADMIN".equals(authUser.role()) || "SUPER_ADMIN".equals(authUser.role()));
    }
}
