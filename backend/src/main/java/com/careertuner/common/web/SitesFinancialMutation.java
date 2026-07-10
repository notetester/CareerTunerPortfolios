package com.careertuner.common.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Codex Sites에서 실행할 수 없는 금전·구독·환불·크레딧·쿠폰 가치 변경 API 표식. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SitesFinancialMutation {
}
