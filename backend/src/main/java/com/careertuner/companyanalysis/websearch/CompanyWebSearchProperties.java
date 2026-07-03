package com.careertuner.companyanalysis.websearch;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * 기업분석 웹검색 파이프라인 feature flag(235 §5·§10 · D-4b).
 *
 * <p><b>기본 false.</b> NAVER 키 존재({@link NaverSearchProperties#configured()})와 기능 활성화는 별개다 —
 * 키가 있어도 이 flag 가 꺼져 있으면 검색·캐시·WEB evidence 생성을 하지 않고 기존 공고-only 경로와
 * 동일하게 동작한다. env(CAREERTUNER_COMPANY_WEBSEARCH_ENABLED)로 relaxed binding 되며,
 * 공유 application.yaml 은 수정하지 않는다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "careertuner.company-websearch")
public class CompanyWebSearchProperties {

    private boolean enabled = false;
}
