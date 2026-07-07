package com.careertuner.admin.permission.service;

import java.util.Set;

/**
 * 관리자 실효 권한 계산 서비스.
 *
 * <p>실효 권한 = 직접 부여(admin_user_permission) ∪ 그룹 경유
 * (admin_user_group → admin_permission_group_item). 모든 경로에서 정책·그룹의
 * active=1, 배정의 revoked_at IS NULL 이 전부 참이어야 유효하다.</p>
 */
public interface EffectivePermissionService {

    /** 사용자의 실효 권한 코드 집합(60초 인메모리 캐시). 없으면 빈 집합. */
    Set<String> getEffectivePermissions(Long userId);

    /** 나열된 코드 중 하나라도 실효 보유하면 true (ANY-of). */
    boolean hasAny(Long userId, String... permissionCodes);

    /** 특정 사용자 캐시 무효화. */
    void evict(Long userId);

    /** 전체 캐시 무효화(권한/그룹 변경 API 호출 직후). */
    void evictAll();
}
