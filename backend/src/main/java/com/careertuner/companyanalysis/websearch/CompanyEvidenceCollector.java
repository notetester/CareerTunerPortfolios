package com.careertuner.companyanalysis.websearch;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * 검색 결과를 evidence gate 대조용 WEB 근거 corpus 로 정규화한다(235 §2 CompanyEvidenceCollector).
 *
 * <ul>
 *   <li>동명 불일치 선제거 — {@link CompanySourceResolver#filterObviousMismatches} 재사용
 *       (중간 강도, 235 §11 — 여기서 걸러진 결과는 gate 대조 자체에 오르지 않는다).</li>
 *   <li>URL·수집시각·제목 보존 — sourceKind=WEB fact 의 sourceRef(URL)·신선도(fetchedAt) 원천.</li>
 *   <li>URL 없는 결과 제외 — WEB 근거의 sourceRef 는 URL 이어야 한다는 gate 계약을 입구에서 보장.</li>
 *   <li>동일 URL 중복 제거(카테고리 교차 중복) — 먼저 수집된 결과 우선.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class CompanyEvidenceCollector {

    private final CompanySourceResolver sourceResolver;

    public List<CompanyWebEvidence> collect(CompanyIdentity identity, List<CompanyWebSearchResult> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        List<CompanyWebSearchResult> filtered = sourceResolver.filterObviousMismatches(identity, results);
        Set<String> seenUrls = new LinkedHashSet<>();
        List<CompanyWebEvidence> evidences = new ArrayList<>();
        for (CompanyWebSearchResult result : filtered) {
            String url = result.link();
            if (url == null || url.isBlank() || !seenUrls.add(url)) {
                continue;
            }
            evidences.add(new CompanyWebEvidence(
                    url,
                    result.title() == null ? "" : result.title(),
                    result.description() == null ? "" : result.description(),
                    result.fetchedAt()));
        }
        return List.copyOf(evidences);
    }
}
