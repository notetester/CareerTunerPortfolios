package com.careertuner.billing.policy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 실제 비용이 발생할 수 있어 preview/환불정책 확인 헤더가 필수인 API. */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresAiCharge {
    String value();
}
