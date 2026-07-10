package com.careertuner.admin.permission.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 세부 권한이 아직 없는 관리자의 권한 조회·요청처럼 ADMIN 역할만으로 열어야 하는 명시적 예외.
 * 새 관리자 핸들러는 이 어노테이션 또는 {@link RequireAdminPermission} 중 하나를 선언해야 한다.
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AdminRoleOnly {
}
