package com.careertuner.admin.permission.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 관리자 세부 권한 집행 어노테이션.
 *
 * <p>컨트롤러 클래스 또는 핸들러 메서드에 붙이면 {@code AdminPermissionInterceptor}가
 * 실효 권한(직접 부여 ∪ 그룹 경유)을 조회해 나열된 코드 중 하나라도 보유해야 통과시킨다(ANY-of).
 * 메서드 어노테이션이 클래스 어노테이션보다 우선한다.</p>
 *
 * <ul>
 *   <li>SUPER_ADMIN role 은 항상 전체 통과.</li>
 *   <li>권한 코드는 기존 {@code admin_permission_policy} 카탈로그 값을 그대로 사용한다
 *       (예: CONTENT_MANAGE, CONTENT_ADMIN, POLICY_MANAGE, POLICY_ADMIN).</li>
 *   <li>이 어노테이션은 role 검사(AdminAccess)를 대체하지 않는 추가 방어선이다 —
 *       서비스 계층의 requireAdmin/requireSuperAdmin 은 그대로 유지한다.</li>
 * </ul>
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireAdminPermission {

    /** 필요한 권한 코드 목록. 하나라도 실효 보유하면 통과(ANY-of). */
    String[] value();
}
