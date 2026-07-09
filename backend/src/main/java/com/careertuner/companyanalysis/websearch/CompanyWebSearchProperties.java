package com.careertuner.companyanalysis.websearch;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * 기업분석 웹검색 파이프라인 feature flag·비용 상한(235 §5·§7·§10 · D-4b/D-4c).
 *
 * <p><b>기본 false.</b> NAVER 키 존재({@link NaverSearchProperties#configured()})와 기능 활성화는 별개다 —
 * 키가 있어도 이 flag 가 꺼져 있으면 검색·캐시·WEB evidence 생성을 하지 않고 기존 공고-only 경로와
 * 동일하게 동작한다. env(CAREERTUNER_COMPANY_WEBSEARCH_ENABLED)로 relaxed binding 되며,
 * 공유 application.yaml 은 수정하지 않는다.
 *
 * <p>비용 상한(D-4c): 분석 1건당 검색 호출·입력 결과 수를 제한해 쿼터/입력 예산을 보호한다.
 * env(CAREERTUNER_COMPANY_WEBSEARCH_MAX_SEARCH_CALLS_PER_ANALYSIS 등)로 조정한다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "careertuner.company-websearch")
public class CompanyWebSearchProperties {

    private boolean enabled = false;

    /**
     * 분석 1건당 검색 호출 상한(235 §7 3~5회 + 네이버 카테고리 4개 구조).
     * 0/음수로 오설정돼도 서비스가 최소 1 로 클램프한다 — 웹검색 비활성화는 {@link #enabled}=false 로만 한다.
     */
    private int maxSearchCallsPerAnalysis = 4;

    /**
     * 분석 1건당 입력/저장 결과 상한(정규화 URL 중복 제거 후 기준).
     * 0/음수로 오설정돼도 서비스가 최소 1 로 클램프한다.
     */
    private int maxResultsPerAnalysis = 12;
}
