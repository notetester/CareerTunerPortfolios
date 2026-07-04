package com.careertuner.companyanalysis.websearch;

/**
 * 공고분석 결과에서 추출한 회사 식별 정보(235 §1 회사 식별 단계의 입력).
 * industry/region 은 공고에 없으면 빈 문자열 — 검색 쿼리 힌트·동명 판별에만 쓴다.
 */
public record CompanyIdentity(String companyName, String industry, String region) {

    public CompanyIdentity {
        companyName = normalize(companyName);
        industry = normalize(industry);
        region = normalize(region);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
