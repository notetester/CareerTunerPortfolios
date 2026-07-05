package com.careertuner.companyanalysis.websearch;

import java.util.ArrayList;
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
     * 코퍼스 레벨 양성 정체성 판정(D-6 이슈A). 수집된 검색결과 중 대상 회사를 <b>양성 식별</b>하는
     * 결과가 하나라도 있으면 true. 하나도 없으면 false — 서비스는 이때 웹 근거를 공고-only 로 degrade 한다.
     *
     * <p>동명 접두충돌("가온테크" → "가온전선"·"가온칩스") 대응. filterObviousMismatches 의 keep 편향
     * (음성 전용 · 다른 회사 증명 시에만 제거)만으로는 marker 없는 실제 뉴스 제목을 못 걸러 오염이
     * corpus 전체를 지배하므로, 대상에 대한 <b>양성 근거를 코퍼스 단위로 한 번 요구</b>한다.
     * 회사명 미식별(빈 이름)이면 판정 불가로 false 를 반환한다.
     */
    public boolean hasPositiveIdentityMatch(CompanyIdentity identity, List<CompanyWebSearchResult> results) {
        if (results == null || results.isEmpty()) {
            return false;
        }
        return results.stream().anyMatch(result -> identifiesCompany(identity, result));
    }

    /** anchor/rival 판정에 쓰는 토큰·접두 공유의 최소 길이(2). 1글자 잡음 토큰 오양성 방지. */
    private static final int MIN_TOKEN_LENGTH = 2;

    /**
     * 결과 1건이 대상 회사를 양성 식별(anchor)하는가(D-6 이슈A · 2차 P1). filterObviousMismatches 와 같은
     * 정규화 로직을 재사용한다. anchor 조건(하나라도 만족):
     * <ul>
     *   <li>정규화한 대상 회사명이 제목/설명/링크(haystack)에 그대로 등장, 또는</li>
     *   <li>제목의 법인 표기 상호가 대상과 포함관계, 또는</li>
     *   <li>제목 토큰 T(len≥2) 와 대상명 N 이 <b>접두 관계</b>(T 가 N 의 접두이거나 N 이 T 의 접두).</li>
     * </ul>
     * 접두 관계 조건이 브랜드-only 결과("위버스컴퍼니"의 "위버스")를 잡는다. 반드시 접두로만 판정한다
     * — 부분문자열은 허용하지 않아 "컴퍼니"·"테크" 같은 접미 토큰이 오양성되지 않게 한다.
     * 회사명 미식별(빈 이름)이면 항상 false.
     */
    public boolean identifiesCompany(CompanyIdentity identity, CompanyWebSearchResult result) {
        String normalizedName = normalizeCompanyName(identity.companyName());
        if (normalizedName.isBlank() || result == null) {
            return false;
        }
        String haystack = normalizeText(
                result.title() + " " + result.description() + " " + result.link());
        if (haystack.contains(normalizedName)) {
            return true;
        }
        if (titleCorporateNameMatchesTarget(result.title(), normalizedName)) {
            return true;
        }
        for (String token : titleTokens(result.title())) {
            if (isPrefixRelated(token, normalizedName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 코퍼스 레벨 정체성 스크리닝(D-6 이슈A · 2차 보정 · 단일 소스). 두 단계:
     * <ol>
     *   <li>접두충돌 경쟁사(confusableRival) 제거 — 비-anchor 결과 중 접두 공유 후 발산하는 것만
     *       뺀다(대상명이 함께 나오는 anchor·접두 공유 없는 다른 브랜드는 유지).</li>
     *   <li>남은 결과 중 anchor 가 하나도 없으면 빈 목록으로 degrade — 공고-only 후퇴.</li>
     * </ol>
     * 서비스는 이 결과만 캐시·evidence 로 넘겨 오염이 캐시·R1 입력 어디에도 굳지 않게 한다.
     * 회사명 미식별·빈 코퍼스면 빈 목록.
     */
    public List<CompanyWebSearchResult> retainIdentifiableResults(
            CompanyIdentity identity, List<CompanyWebSearchResult> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        String normalizedName = normalizeCompanyName(identity.companyName());
        if (normalizedName.isBlank()) {
            return List.of();
        }
        List<CompanyWebSearchResult> screened = results.stream()
                .filter(result -> !isConfusableRival(identity, result))
                .toList();
        if (!hasPositiveIdentityMatch(identity, screened)) {
            return List.of();
        }
        return screened;
    }

    /**
     * 결과 1건이 접두충돌 경쟁사(confusableRival)인가(D-6 이슈A · 2차 P2). anchor 가 아니면서, 제목 토큰
     * T(len≥2) 중 하나가 대상명 N 과 <b>공통 접두를 {@value #MIN_TOKEN_LENGTH}자 이상 공유하지만
     * 접두 관계는 아닌</b>(발산) 경우 true. "가온테크" 대상에서 "가온전선"·"가온칩스"를 잡되, 공통 접두가
     * 없는 다른 브랜드("텀블벅")는 rival 이 아니라 유지한다(riding 정당).
     *
     * <p>anchor(대상명이 함께 나오거나 접두 관계)인 결과는 절대 rival 이 아니다 — 서비스는 rival 만
     * 제거하므로 대상 근거가 있는 결과는 보존된다.
     */
    public boolean isConfusableRival(CompanyIdentity identity, CompanyWebSearchResult result) {
        String normalizedName = normalizeCompanyName(identity.companyName());
        if (normalizedName.isBlank() || result == null) {
            return false;
        }
        if (identifiesCompany(identity, result)) {
            return false;
        }
        for (String token : titleTokens(result.title())) {
            if (commonPrefixLength(token, normalizedName) >= MIN_TOKEN_LENGTH
                    && !isPrefixRelated(token, normalizedName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 한글 제목 토큰 끝에 자주 붙는 단일 조사(1자). 브랜드 토큰("위버스가"·"가온전선이")에서 이 1자를 떼
     * 접두 anchor·rival 판정을 실제 상호 형태로 비교하기 위함. 2자 조사("에서"·"으로")는 실제 상호와의
     * 충돌 위험이 커 제외한다(안전 우선). anchor·rival 비교 전용이라 저장·URL·캐시엔 영향 없다.
     */
    private static final String COMMON_JOSA = "은는이가을를의에도로와과나만";

    /**
     * 제목을 공백·문장부호로 분리해 정규화한 토큰(len≥2) 목록. 각 토큰이 len≥3 이고 마지막 1자가 흔한
     * 단일 조사({@link #COMMON_JOSA})면 그 1자를 떼 실제 상호 형태로 비교한다("위버스가"→"위버스",
     * "가온전선이"→"가온전선"). len<3 토큰은 떼지 않는다(2자 토큰 훼손 방지).
     */
    private List<String> titleTokens(String title) {
        if (title == null || title.isBlank()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (String raw : title.split("[^\\p{L}\\p{N}]+")) {
            String token = stripTrailingParticle(normalizeText(raw));
            if (token.length() >= MIN_TOKEN_LENGTH) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    /** len≥3 이고 마지막 1자가 흔한 단일 조사면 그 1자를 뗀다. 그 외에는 원본 그대로. */
    private String stripTrailingParticle(String token) {
        if (token.length() >= 3 && COMMON_JOSA.indexOf(token.charAt(token.length() - 1)) >= 0) {
            return token.substring(0, token.length() - 1);
        }
        return token;
    }

    /** 한쪽이 다른 쪽의 접두이면(양방향) true. 두 값 모두 len≥2 일 때만 유효. */
    private boolean isPrefixRelated(String a, String b) {
        if (a.length() < MIN_TOKEN_LENGTH || b.length() < MIN_TOKEN_LENGTH) {
            return false;
        }
        return a.startsWith(b) || b.startsWith(a);
    }

    /** 두 문자열이 앞에서부터 공유하는 글자 수. */
    private int commonPrefixLength(String a, String b) {
        int limit = Math.min(a.length(), b.length());
        int i = 0;
        while (i < limit && a.charAt(i) == b.charAt(i)) {
            i++;
        }
        return i;
    }

    /** 제목의 법인 표기 상호 중 하나라도 대상 회사명과 포함관계(양방향)이면 true. */
    private boolean titleCorporateNameMatchesTarget(String title, String normalizedName) {
        Matcher matcher = EXPLICIT_CORPORATE_MENTION.matcher(title == null ? "" : title);
        while (matcher.find()) {
            String captured = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            String candidate = normalizeText(captured);
            if (candidate.isBlank()) {
                continue;
            }
            if (candidate.contains(normalizedName) || normalizedName.contains(candidate)) {
                return true;
            }
        }
        return false;
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

    /**
     * 법인 표기 제거 후 {@link #normalizeText}. "(주)가온 테크" → "가온테크".
     * 회사 단위 canonical 식별자로도 재사용된다(D-4b 검색 캐시 key) — 법인표기·기호·공백 차이를 흡수해
     * "(주) 가온테크"·"㈜가온테크"·"가온테크"를 같은 회사로 묶는다.
     */
    public String normalizeCompanyName(String name) {
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
