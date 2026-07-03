package com.careertuner.companyanalysis.websearch;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * 회사 식별 → 검색 쿼리 생성 + 동명 회사 판별(235 §2 CompanySourceResolver).
 *
 * <p><b>쿼리 생성:</b> 회사명 + 업종/지역 힌트를 함께 넣는다(235 §11). 구체적인 쿼리를 앞에,
 * 회사명 단독 쿼리를 마지막 폴백으로 둔다.
 *
 * <p><b>동명 판별 강도 = 중간(235 §11 확정):</b> 결과가 회사명과 <b>명백히 불일치할 때만</b> 제외한다.
 * <ul>
 *   <li>정규화한 회사명이 제목·스니펫·URL 어디든 등장 → 유지.</li>
 *   <li>회사명 미등장 자체는 "판별 불가"라 유지 — 짧은 스니펫·공식 사이트·브랜드/서비스명
 *       중심 결과에는 법인명이 안 보일 수 있다(D-2 evidence gate 가 원문 대조로 재검증).</li>
 *   <li>제외는 <b>제목이 법인 표기((주)·㈜·주식회사)로 다른 회사를 명시</b>하면서 대상 회사명이
 *       어디에도 없을 때만 — 이것만 "명백한 불일치"로 본다(keep 편향, unknowns 증가 방지).</li>
 * </ul>
 * 업종/지역 힌트 충돌 같은 약한 신호의 감점은 235 §3 웹 근거 신뢰도 규칙(D-2 이후) 범위.
 */
@Component
public class CompanySourceResolver {

    /** 회사명 앞뒤에 붙는 법인 표기 — 이름 정규화 시 제거한다. */
    private static final List<String> CORPORATE_MARKERS = List.of("주식회사", "유한회사", "(주)", "(유)", "㈜");

    /**
     * 제목에서 법인 표기로 명시된 상호를 찾는 패턴 — "(주)가온", "주식회사 가온", "가온㈜" 형태.
     * 접미형은 기호((주)·㈜)만 허용한다 — "다른 주식회사가" 같은 일반 서술의 오탐(과잉 제외)을 막기 위함.
     */
    private static final Pattern EXPLICIT_CORPORATE_MENTION = Pattern.compile(
            "(?:주식회사|\\(주\\)|㈜)\\s*([\\p{L}\\p{N}]+)|([\\p{L}\\p{N}]+)\\s*(?:\\(주\\)|㈜)");

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

    /**
     * 명백한 불일치 판정(중간 강도 — keep 편향). 회사명 미등장은 "판별 불가"라 유지하고,
     * 제목이 법인 표기로 다른 회사를 명시하면서 대상 회사명이 어디에도 없을 때만 true.
     */
    boolean isObviousMismatch(String normalizedName, CompanyWebSearchResult result) {
        String haystack = normalizeText(
                result.title() + " " + result.description() + " " + result.link());
        if (haystack.contains(normalizedName)) {
            return false;
        }
        return titleNamesUnrelatedCompany(result.title(), normalizedName);
    }

    /** 제목의 법인 표기 상호가 전부 대상 회사명과 무관(상호 포함 관계 없음)할 때만 true. */
    private boolean titleNamesUnrelatedCompany(String title, String normalizedName) {
        Matcher matcher = EXPLICIT_CORPORATE_MENTION.matcher(title);
        boolean foundUnrelated = false;
        while (matcher.find()) {
            String captured = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            String candidate = normalizeText(captured);
            if (candidate.isBlank()) {
                continue;
            }
            if (candidate.contains(normalizedName) || normalizedName.contains(candidate)) {
                // 대상과 겹치는 상호(부분 일치)면 확신이 없으므로 유지한다.
                return false;
            }
            foundUnrelated = true;
        }
        return foundUnrelated;
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
