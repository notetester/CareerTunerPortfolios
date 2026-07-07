package com.careertuner.companyanalysis.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.service.AiUsageLogService;
import com.careertuner.applicationcase.service.ApplicationCaseAnalysisStatusService;
import com.careertuner.applicationcase.service.ApplicationCaseAccessService;
import com.careertuner.applicationcase.service.BAnalysisGenerationService;
import com.careertuner.applicationcase.service.BAnalysisGenerationService.GeneratedCompanyAnalysis;
import com.careertuner.applicationcase.service.BAnalysisJsonValidator;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.companyanalysis.domain.CompanyAnalysis;
import com.careertuner.companyanalysis.domain.CompanySearchCache;
import com.careertuner.companyanalysis.dto.CompanyAnalysisReviewRequest;
import com.careertuner.companyanalysis.dto.CompanyAnalysisResponse;
import com.careertuner.companyanalysis.mapper.CompanyAnalysisMapper;
import com.careertuner.companyanalysis.websearch.CompanyEvidenceCollector;
import com.careertuner.companyanalysis.websearch.CompanyIdentity;
import com.careertuner.companyanalysis.websearch.CompanySourceResolver;
import com.careertuner.companyanalysis.websearch.CompanyWebEvidence;
import com.careertuner.companyanalysis.websearch.CompanyWebSearchClient;
import com.careertuner.companyanalysis.websearch.CompanyWebSearchException;
import com.careertuner.companyanalysis.websearch.CompanyWebSearchProperties;
import com.careertuner.companyanalysis.websearch.CompanyWebSearchResult;
import com.careertuner.companyanalysis.websearch.NaverSearchCategory;
import com.careertuner.jobposting.domain.JobPosting;
import com.careertuner.notification.domain.Notification;
import com.careertuner.notification.service.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyAnalysisService {

    private static final String FEATURE_COMPANY_RESEARCH = "COMPANY_RESEARCH";
    private static final int COMPANY_INDUSTRY_MAX_LENGTH = 100;

    // 검색 호출·결과 상한은 CompanyWebSearchProperties(비용 상한 · D-4c)로 조정한다.
    private static final NaverSearchCategory[] SEARCH_CATEGORIES = NaverSearchCategory.values();

    private final ApplicationCaseAccessService accessService;
    private final CompanyAnalysisMapper companyAnalysisMapper;
    private final BAnalysisGenerationService bAnalysisGenerationService;
    private final AiUsageLogService aiUsageLogService;
    private final ApplicationCaseAnalysisStatusService statusService;
    private final TransactionTemplate transactionTemplate;
    private final BAnalysisJsonValidator analysisJsonValidator;
    private final BCompanyAnalysisCanonicalizer canonicalizer;
    private final NotificationService notificationService;

    // D-4b 웹검색 배선. flag OFF(기본)면 아래 협력자는 호출되지 않고 기존 공고-only 와 동일 동작.
    private final CompanyWebSearchProperties companyWebSearchProperties;
    private final CompanySourceResolver companySourceResolver;
    private final CompanyWebSearchClient companyWebSearchClient;
    private final CompanyEvidenceCollector companyEvidenceCollector;
    private final CompanySearchCacheService companySearchCacheService;
    // 캐시 results(CompanyWebSearchResult 목록) 직렬화용 — Boot 관리 Jackson3 mapper 주입.
    private final ObjectMapper objectMapper;

    public CompanyAnalysisResponse createCompanyAnalysis(Long userId, Long applicationCaseId) {
        ApplicationCase applicationCase = accessService.requireOwned(userId, applicationCaseId);
        ensureAnalysisRunnable(applicationCase.getStatus());
        JobPosting jobPosting = accessService.latestPostingRequired(applicationCaseId);
        String sourceText = accessService.sourceText(jobPosting);
        String previousStatus = applicationCase.getStatus();
        statusService.markAnalyzing(userId, applicationCaseId, previousStatus);
        try {
            // flag ON 이면 회사 식별 → (캐시 or 검색) → WEB evidence 를 한 번만 모은다. flag OFF 면 빈 목록.
            // 같은 목록을 R1 생성 입력(공고+웹)과 저장 gate(2소스) 양쪽에 넘긴다.
            List<CompanyWebEvidence> webEvidence = collectWebEvidence(applicationCase);
            GeneratedCompanyAnalysis generated =
                    bAnalysisGenerationService.generateCompanyAnalysis(applicationCase, sourceText, webEvidence);
            // 자동 파이프라인 경로와 동일한 canonicalizer 로 저장 전 정규화한다
            // (evidence gate 2소스[공고+WEB], ID/sourceKind/sourceRef 보정, unknowns 접기, sources 통일).
            // webEvidence 가 빈 목록이면 7-param 은 기존 공고-only 6-param 과 동일 결과다(D-2 계약).
            var payload = canonicalizer.canonicalizeForStorage(
                    generated.payload(),
                    jobPosting.getId(),
                    jobPosting.getRevision(),
                    sourceText,
                    applicationCase.getCompanyName(),
                    applicationCase.getJobTitle(),
                    webEvidence).payload();
            LocalDateTime checkedAt = LocalDateTime.now();
            return transactionTemplate.execute(status -> {
                CompanyAnalysis companyAnalysis = CompanyAnalysis.builder()
                        .applicationCaseId(applicationCaseId)
                        .jobPostingId(jobPosting.getId())
                        .jobPostingRevision(jobPosting.getRevision())
                        .companySummary(blankToNull(payload.companySummary()))
                        .recentIssues(blankToNull(payload.recentIssues()))
                        .industry(compactColumnText(payload.industry(), COMPANY_INDUSTRY_MAX_LENGTH))
                        .competitors(payload.competitors())
                        .interviewPoints(blankToNull(payload.interviewPoints()))
                        .sources(payload.sources())
                        .verifiedFacts(payload.verifiedFacts())
                        .aiInferences(payload.aiInferences())
                        .sourceType("JOB_POSTING")
                        .checkedAt(checkedAt)
                        .refreshRecommendedAt(checkedAt.plusDays(30))
                        .build();
                companyAnalysisMapper.insertCompanyAnalysis(companyAnalysis);
                CompanyAnalysisResponse response = toResponse(companyAnalysisMapper.findLatestCompanyAnalysisByCaseId(applicationCaseId));
                statusService.markReadyAfterAnalysis(userId, applicationCaseId, previousStatus);
                if (generated.fellBack()) {
                    aiUsageLogService.recordFailure(
                            userId,
                            applicationCaseId,
                            FEATURE_COMPANY_RESEARCH,
                            generated.fallbackAttemptedModel(),
                            generated.fallbackReason());
                }
                aiUsageLogService.recordLocalSuccess(userId, applicationCaseId, FEATURE_COMPANY_RESEARCH, payload.usage());
                // 기업 분석 저장이 성공하면 사용자에게 완료 알림을 남긴다.
                notificationService.notify(Notification.builder()
                        .userId(userId)
                        .type("COMPANY_ANALYSIS_COMPLETE")
                        .targetType("APPLICATION_CASE")
                        .targetId(applicationCaseId)
                        .title("기업 분석이 완료되었습니다")
                        .message("%s · %s 기업 분석 결과가 준비되었습니다.".formatted(
                                applicationCase.getCompanyName(), applicationCase.getJobTitle()))
                        .link("/applications/" + applicationCaseId + "/company-analysis")
                        .build());
                return response;
            });
        } catch (RuntimeException ex) {
            restorePreviousStatus(userId, applicationCaseId, previousStatus, ex);
            aiUsageLogService.recordFailure(userId, applicationCaseId, FEATURE_COMPANY_RESEARCH, userFacingFailureMessage(ex, "기업 분석 결과 저장 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."));
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public CompanyAnalysisResponse getCompanyAnalysis(Long userId, Long applicationCaseId) {
        accessService.requireOwned(userId, applicationCaseId);
        return toResponse(companyAnalysisMapper.findLatestCompanyAnalysisByCaseId(applicationCaseId));
    }

    @Transactional(readOnly = true)
    public List<CompanyAnalysisResponse> getCompanyAnalysisHistory(Long userId, Long applicationCaseId) {
        accessService.requireOwned(userId, applicationCaseId);
        return companyAnalysisMapper.findCompanyAnalysisHistoryByCaseId(applicationCaseId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CompanyAnalysisResponse reviewCompanyAnalysis(Long userId, Long applicationCaseId, Long analysisId, CompanyAnalysisReviewRequest request) {
        accessService.requireOwned(userId, applicationCaseId);
        CompanyAnalysis existing = companyAnalysisMapper.findCompanyAnalysisByIdAndCaseId(analysisId, applicationCaseId);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "기업 분석을 찾을 수 없습니다.");
        }

        CompanyAnalysis updated = CompanyAnalysis.builder()
                .id(existing.getId())
                .applicationCaseId(existing.getApplicationCaseId())
                .jobPostingId(existing.getJobPostingId())
                .jobPostingRevision(existing.getJobPostingRevision())
                .companySummary(defaultString(request.companySummary(), existing.getCompanySummary()))
                .recentIssues(defaultString(request.recentIssues(), existing.getRecentIssues()))
                .industry(compactColumnText(defaultString(request.industry(), existing.getIndustry()), COMPANY_INDUSTRY_MAX_LENGTH))
                .competitors(defaultString(request.competitors(), existing.getCompetitors()))
                .interviewPoints(defaultString(request.interviewPoints(), existing.getInterviewPoints()))
                .sources(defaultString(request.sources(), existing.getSources()))
                .verifiedFacts(defaultValidatedJson(request.verifiedFacts(), existing.getVerifiedFacts(), analysisJsonValidator::validateVerifiedFacts))
                .aiInferences(reviewedAiInferences(request.aiInferences(), existing.getAiInferences()))
                .sourceType(existing.getSourceType())
                .checkedAt(existing.getCheckedAt())
                .refreshRecommendedAt(existing.getRefreshRecommendedAt())
                .confirmedAt(Boolean.TRUE.equals(request.confirmed()) ? LocalDateTime.now() : existing.getConfirmedAt())
                .adminMemo(existing.getAdminMemo())
                .build();
        companyAnalysisMapper.updateCompanyAnalysisReview(updated);
        return toResponse(companyAnalysisMapper.findCompanyAnalysisByIdAndCaseId(analysisId, applicationCaseId));
    }

    // ── 웹검색 배선 (flag ON 경로 · D-4b/D-4c) ──

    /**
     * 공고+WEB 2소스용 WEB evidence 수집. flag OFF(기본) 또는 회사명 부재면 빈 목록을 반환해
     * 저장 결과를 기존 공고-only 와 동일하게 유지한다(검색·캐시 호출 0회).
     *
     * <p>flag ON: 회사 식별 → 캐시 조회(HIT 면 검색 미호출) → MISS 면 검색·정제 후 캐시 저장 →
     * (HIT/MISS 공통) 정제된 검색결과를 {@link CompanyEvidenceCollector} 로 매번 재실행해 evidence 생성.
     *
     * <p><b>degrade(D-4c):</b> flag ON 인데 웹검색이 실패({@link CompanyWebSearchException})하면 분석 전체를
     * 실패시키지 않고 공고-only(빈 목록)로 후퇴한다. 이후 {@code generateCompanyAnalysis} 의 기존 폴백 체인이
     * 그대로 동작한다. 실패는 캐시에 굳히지 않는다 — put 은 {@link #resolveSearchResults} 의 검색 성공 뒤에만
     * 실행되므로 예외 시 put 이 호출되지 않는다("[]" 로 7일 HIT 되는 것을 방지). 관측성 오염 방지를 위해
     * recordFailure 는 남기지 않고 WARN 로그만 남긴다(예외 메시지는 D-1 규약상 시크릿·응답 body 미포함).
     */
    public List<CompanyWebEvidence> collectWebEvidence(ApplicationCase applicationCase) {
        if (!companyWebSearchProperties.isEnabled()) {
            return List.of();
        }
        CompanyIdentity identity = toCompanyIdentity(applicationCase);
        if (identity.companyName().isBlank()) {
            return List.of();
        }
        String queryKey = companyCacheKey(identity);
        if (queryKey.isBlank()) {
            // 법인표기·기호만 남아 정규화 후 회사 식별 불가 — 검색/캐시 하지 않는다(put 빈 key 예외 방지).
            return List.of();
        }
        List<CompanyWebSearchResult> results;
        try {
            results = resolveSearchResults(identity, queryKey);
        } catch (CompanyWebSearchException ex) {
            log.warn("기업분석 웹검색 실패 — 공고-only 로 degrade 합니다: {}", ex.getMessage());
            return List.of();
        }
        // 코퍼스 레벨 정체성 게이트(D-6 이슈A · 동명 접두충돌)는 MISS 경로에서 캐시 put 전에 이미 적용됐고
        // (retainIdentifiableResults · 접두충돌 경쟁사 제거 + anchor 게이트), HIT 경로는 정제된 캐시를 그대로
        // 돌려주므로 여기 results 는 항상 clean 하다. evidence 는 캐시하지 않고 HIT/MISS 공통으로 collector 를
        // 매번 재실행한다.
        return companyEvidenceCollector.collect(identity, results);
    }

    /**
     * 캐시 HIT 면 저장된 검색결과 역직렬화(검색 미호출), MISS 면 검색·정제·정체성 게이트 후 캐시에 저장.
     *
     * <p><b>정체성 게이트를 put 전에 적용(D-6 이슈A · 2차 보정 추가 A):</b> {@code runSearch} 의 per-call
     * 필터 뒤에 코퍼스 레벨 스크리닝({@link CompanySourceResolver#retainIdentifiableResults})을 한 번 더 돌려
     * 접두충돌 경쟁사("가온전선"·"가온칩스")를 제거하고 anchor 가 없으면 빈 목록으로 degrade 한 <b>결과만</b>
     * 캐시에 저장한다. 이렇게 하면 오염 corpus 가 회사 cache key 에 TTL 동안 굳어 재조회를 억제하는 문제를
     * 막는다. 빈 목록 저장은 기존 D-4b "빈 배열도 put" 계약과 정합(같은 회사 재조회 시 HIT). 게이트는
     * flag ON·MISS 경로에서만 동작한다 — HIT 는 이미 정제된 캐시를 반환한다.
     */
    private List<CompanyWebSearchResult> resolveSearchResults(CompanyIdentity identity, String queryKey) {
        Optional<CompanySearchCache> cached = companySearchCacheService.get(queryKey);
        if (cached.isPresent()) {
            return deserializeResults(cached.get().getResults());
        }
        List<CompanyWebSearchResult> searched = runSearch(identity);
        // 접두충돌 경쟁사 제거 + anchor 게이트를 put 전에 적용 — 캐시에는 정제·게이트된 결과만 저장한다.
        List<CompanyWebSearchResult> gated = companySourceResolver.retainIdentifiableResults(identity, searched);
        if (gated.isEmpty() && !searched.isEmpty()) {
            // 관측성: 검색은 됐으나 대상 양성 근거가 없어 공고-only 로 degrade(시크릿·응답 body 미포함).
            log.warn("기업분석 웹검색 코퍼스에 대상 회사 양성 근거가 없어 공고-only 로 degrade 합니다 (수집 {}건).",
                    searched.size());
        }
        // 빈 배열이어도 put 한다 → 같은 회사 재조회 시 HIT(TTL 내 재검색 없음).
        // fetchedAt=null 로 넘겨 CompanySearchCacheService 의 주입 Clock(TTL 판정과 동일 시각원)이 채우게 한다.
        companySearchCacheService.put(queryKey, serializeResults(gated), null);
        return gated;
    }

    /**
     * 검색 실행(MISS). 회사명+힌트 쿼리(구체→폴백)를 카테고리별로 조회하고
     * 동명 불일치 제거 → URL blank/null 제외 → 정규화 URL 중복 제거 를 적용한 결과만 반환한다.
     * 분석 1건당 검색 호출·결과 수 상한(비용 상한 · D-4c, {@link CompanyWebSearchProperties})에서 조기 중단한다.
     */
    private List<CompanyWebSearchResult> runSearch(CompanyIdentity identity) {
        // env 오설정(0/음수)으로 flag ON 인데 웹검색이 조용히 꺼지는 오진을 막는다 — 최소 1 로 클램프한다.
        // 웹검색 비활성화는 상한이 아니라 enabled=false 로만 한다.
        int maxSearchCalls = Math.max(1, companyWebSearchProperties.getMaxSearchCallsPerAnalysis());
        int maxResults = Math.max(1, companyWebSearchProperties.getMaxResultsPerAnalysis());
        LinkedHashMap<String, CompanyWebSearchResult> byUrl = new LinkedHashMap<>();
        int calls = 0;
        for (String query : companySourceResolver.buildQueries(identity)) {
            for (NaverSearchCategory category : SEARCH_CATEGORIES) {
                if (calls >= maxSearchCalls || byUrl.size() >= maxResults) {
                    return List.copyOf(byUrl.values());
                }
                calls++;
                List<CompanyWebSearchResult> filtered =
                        companySourceResolver.filterObviousMismatches(identity, companyWebSearchClient.search(category, query));
                for (CompanyWebSearchResult result : filtered) {
                    String url = result.link();
                    if (url == null || url.isBlank()) {
                        continue; // WEB 출처는 URL 필수(D-2) — evidence 로 못 쓰므로 캐시에서 제외.
                    }
                    // 정규화 URL 기준 dedup(먼저 수집분 우선). 한 호출이 여러 건을 반환해도 결과 상한을 초과
                    // 저장하지 않도록, 새 결과가 추가돼 상한에 도달하면 즉시 중단한다(호출 전 검사만으론 부족 — 리뷰 반영).
                    if (byUrl.putIfAbsent(normalizeUrl(url), result) == null && byUrl.size() >= maxResults) {
                        return List.copyOf(byUrl.values());
                    }
                }
            }
        }
        return List.copyOf(byUrl.values());
    }

    /**
     * 회사 단위 cache key(234 §7). D-1 {@link CompanySourceResolver#normalizeCompanyName} 을 공유해
     * 법인표기((주)·㈜·주식회사)·기호·공백 차이를 흡수한다 — "(주) 가온테크"·"㈜가온테크"·"가온테크"가 같은 key.
     * 동명 판별용 업종/지역 힌트는 검색 query 에는 쓰지만 cache key 에는 포함하지 않는다.
     */
    String companyCacheKey(CompanyIdentity identity) {
        return companySourceResolver.normalizeCompanyName(identity.companyName());
    }

    private CompanyIdentity toCompanyIdentity(ApplicationCase applicationCase) {
        // ApplicationCase 에 업종/지역 필드가 없어 힌트는 비운다(있으면 검색 query 에만 반영).
        return new CompanyIdentity(applicationCase.getCompanyName(), "", "");
    }

    private static String normalizeUrl(String url) {
        String normalized = url.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private String serializeResults(List<CompanyWebSearchResult> results) {
        try {
            return objectMapper.writeValueAsString(results);
        } catch (JacksonException ex) {
            return "[]";
        }
    }

    private List<CompanyWebSearchResult> deserializeResults(String json) {
        if (isBlank(json)) {
            return List.of();
        }
        try {
            CompanyWebSearchResult[] parsed = objectMapper.readValue(json, CompanyWebSearchResult[].class);
            return parsed == null ? List.of() : List.of(parsed);
        } catch (JacksonException ex) {
            // 손상된 캐시 row 는 evidence 없음으로 방어(검색/hosted degrade 는 D-4c).
            return List.of();
        }
    }

    /**
     * 응답 직전 unknowns 펼치기 — 저장된 aiInferences 의 {@code kind=UNKNOWN} 마커를 분리해
     * virtual unknowns 로 내리고, aiInferences 에서는 제거한다. 프런트/하네스가 마커를
     * 직접 파싱하지 않게 하는 단일 지점이다. 마커 없는 기존 레코드는 그대로 통과한다.
     */
    private CompanyAnalysisResponse toResponse(CompanyAnalysis analysis) {
        if (analysis == null) {
            return null;
        }
        return CompanyAnalysisResponse.from(
                analysis,
                canonicalizer.withoutUnknownMarkers(analysis.getAiInferences()),
                canonicalizer.extractUnknowns(analysis.getAiInferences()));
    }

    /**
     * 검수 저장용 aiInferences 병합 — 응답에서 마커가 제거된 채 편집되므로, 저장 시 기존 레코드의
     * unknown 마커를 서버가 재부착한다. 프런트 additive key 보존 여부와 무관하게 마커가
     * 유실되거나 일반 추론으로 오염되지 않는다(231 문서 5-7 릴리스 제약의 구조적 해소).
     */
    private String reviewedAiInferences(String requested, String existingJson) {
        if (isBlank(requested)) {
            return existingJson;
        }
        String validated = analysisJsonValidator.validateAiInferences(requested.trim());
        return canonicalizer.mergeUnknownMarkers(validated, existingJson);
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static String defaultString(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value.trim();
    }

    private static String defaultValidatedJson(String value, String defaultValue, Function<String, String> validator) {
        if (isBlank(value)) {
            return defaultValue;
        }
        return validator.apply(value.trim());
    }

    private static void ensureAnalysisRunnable(String status) {
        if ("ANALYZING".equals(status)) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 분석이 진행 중입니다. 잠시 후 결과를 확인해 주세요.");
        }
        if (!"DRAFT".equals(status) && !"READY".equals(status)) {
            throw new BusinessException(ErrorCode.CONFLICT, "현재 상태에서는 분석을 다시 실행할 수 없습니다.");
        }
    }

    private static String compactColumnText(String value, int maxLength) {
        if (isBlank(value)) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String userFacingFailureMessage(RuntimeException ex, String fallback) {
        String message = ex.getMessage();
        if (isBlank(message)) {
            return fallback;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("### error")
                || lower.contains("sql:")
                || lower.contains("com.mysql")
                || lower.contains("org.springframework")
                || lower.contains("statement cancelled")
                || lower.contains("timeoutexception")) {
            return fallback;
        }
        return message.length() > 300 ? fallback : message;
    }

    private void restorePreviousStatus(Long userId, Long applicationCaseId, String previousStatus, RuntimeException ex) {
        try {
            statusService.restorePreviousStatus(userId, applicationCaseId, previousStatus);
        } catch (RuntimeException statusException) {
            ex.addSuppressed(statusException);
        }
    }

}
