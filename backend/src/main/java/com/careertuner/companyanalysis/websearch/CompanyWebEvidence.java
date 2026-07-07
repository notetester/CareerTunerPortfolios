package com.careertuner.companyanalysis.websearch;

import java.time.Instant;

/**
 * evidence gate 2소스 대조용 WEB 근거 1건(235 §2 CompanyEvidenceCollector 출력).
 * sourceKind=WEB fact 의 sourceRef 가 되는 {@code url} 과 신선도 판단용 {@code fetchedAt}(235 §4),
 * 대조 텍스트({@code title}+{@code snippet})를 보존한다.
 */
public record CompanyWebEvidence(
        String url,
        String title,
        String snippet,
        Instant fetchedAt
) {
}
