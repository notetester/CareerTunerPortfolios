package com.careertuner.consent.policy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.careertuner.consent.domain.ConsentType;

/** 컨트롤러 또는 메서드가 실행 전에 요구하는 기능별 사용자 동의. */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresConsent {

    ConsentType[] value();
}
