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
 * 클래스와 메서드에 모두 선언되면 각 집합을 모두 통과해야 하므로, 클래스 READ와 메서드 쓰기 권한을
 * 결합할 수 있다.</p>
 *
 * <ul>
 *   <li>SUPER_ADMIN role 은 항상 전체 통과.</li>
 *   <li>권한 코드는 {@code AdminPermissionCatalog}의 정확한 도메인/행위 코드만 사용한다
 *       (예: CONTENT_READ, CONTENT_UPDATE, POLICY_UPDATE).</li>
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
