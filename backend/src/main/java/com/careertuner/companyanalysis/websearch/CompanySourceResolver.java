package com.careertuner.companyanalysis.websearch;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

/**
 * 회사 식별 → 검색 쿼리 생성 + 동명 회사 판별(235 §2 CompanySourceResolver).
 *
 * <p><b>쿼리 생성:</b> 회사명 + 업종/지역 힌트를 함께 넣는다(235 §11). 구체적인 쿼리를 앞에,
 * 회사명 단독 쿼리를 마지막 폴백으로 둔다.
 *
 * <p><b>동명 판별 강도 = 중간(235 §11 확정):</b> 결과가 회사명과 <b>명백히 불일치할 때만</b> 제외한다.
 * 정규화한 회사명이 제목·스니펫·URL 어디에도 등장하지 않으면 명백한 불일치로 보고,
 * 부분 일치·판별 불가는 모두 유지한다(과도 제거로 unknowns 를 늘리지 않기 위한 keep 편향).
 */
@Component
public class CompanySourceResolver {

    /** 회사명 앞뒤에 붙는 법인 표기 — 이름 정규화 시 제거한다. */
    private static final List<String> CORPORATE_MARKERS = List.of("주식회사", "유한회사", "(주)", "(유)", "㈜");

    /** 회사명 + 업종/지역 힌트 조합의 검색 쿼리 목록(구체 → 폴백 순, 중복 제거). 회사명이 없으면 빈 목록. */
    public List<String> buildQueries(CompanyIdentity identity) {
        String name = identity.companyName();
        if (name.isBlank()) {
            return List.of();
        }
        String industry = identity.industry();
        String region = identity.region();
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        if (!industry.isBlank() && !region.isBlank()) {
            queries.add(name + " " + industry + " " + region);
        }
        if (!industry.isBlank()) {
            queries.add(name + " " + industry);
        }
        if (!region.isBlank()) {
            queries.add(name + " " + region);
        }
        queries.add(name);
        return List.copyOf(queries);
    }

    /** 명백한 동명 불일치 결과만 제외한 목록. 회사명이 없어 판별 불가면 전부 유지한다. */
    public List<CompanyWebSearchResult> filterObviousMismatches(
            CompanyIdentity identity, List<CompanyWebSearchResult> results) {
        String normalizedName = normalizeCompanyName(identity.companyName());
        if (normalizedName.isBlank()) {
            return results;
        }
        return results.stream()
                .filter(result -> !isObviousMismatch(normalizedName, result))
                .toList();
    }

    /** 정규화한 회사명이 제목·스니펫·URL 어디에도 없을 때만 true(중간 강도 — keep 편향). */
    boolean isObviousMismatch(String normalizedName, CompanyWebSearchResult result) {
        String haystack = normalizeText(
                result.title() + " " + result.description() + " " + result.link());
        return !haystack.contains(normalizedName);
    }

    /** 법인 표기 제거 후 {@link #normalizeText}. "(주)가온 테크" → "가온테크". */
    String normalizeCompanyName(String name) {
        if (name == null) {
            return "";
        }
        String stripped = name;
        for (String marker : CORPORATE_MARKERS) {
            stripped = stripped.replace(marker, "");
        }
        return normalizeText(stripped);
    }

    /** 공백·문장부호 차이를 무시한 포함 판정을 위해 글자·숫자만 남기고 소문자화한다. */
    private String normalizeText(String value) {
        return value.replaceAll("[^\\p{L}\\p{N}]", "").toLowerCase(Locale.ROOT);
    }
}
