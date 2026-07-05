package com.careertuner.companyanalysis.websearch;

import java.time.Instant;

/**
 * 웹검색 결과 1건 — 스니펫·URL·제목·수집시각(235 §2 CompanyWebSearchClient 반환 계약).
 * D-2 에서 CompanyEvidenceCollector 가 evidence 후보(sourceKind=WEB, sourceRef=URL)로 정규화한다.
 */
public record CompanyWebSearchResult(
        NaverSearchCategory category,
        String title,
        String link,
        String description,
        Instant fetchedAt
) {
}
