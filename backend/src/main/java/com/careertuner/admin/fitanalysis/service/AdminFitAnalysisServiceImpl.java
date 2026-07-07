package com.careertuner.admin.fitanalysis.service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.common.grid.PageResult;
import com.careertuner.admin.fitanalysis.domain.AdminFitAnalysisResult;
import com.careertuner.admin.fitanalysis.domain.AdminGateStatsRow;
import com.careertuner.admin.fitanalysis.dto.AdminFitAnalysisDetailResponse;
import com.careertuner.admin.fitanalysis.dto.AdminFitAnalysisListCriteria;
import com.careertuner.admin.fitanalysis.dto.AdminFitAnalysisListItemResponse;
import com.careertuner.admin.fitanalysis.dto.AdminFitAnalysisListQuery;
import com.careertuner.admin.fitanalysis.dto.AdminFitAnalysisMemoRequest;
import com.careertuner.admin.fitanalysis.dto.AdminGateReviewRequest;
import com.careertuner.admin.fitanalysis.dto.AdminFitAnalysisMemoResponse;
import com.careertuner.admin.fitanalysis.dto.AdminGateStatsResponse;
import com.careertuner.admin.fitanalysis.mapper.AdminFitAnalysisMapper;
import com.careertuner.admin.fitanalysis.domain.AdminFitAnalysisMemo;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.fitanalysis.dto.FitAnalysisLearningTaskResponse;
import com.careertuner.fitanalysis.dto.FitSafetyResponse;
import com.careertuner.fitanalysis.mapper.FitAnalysisMapper;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class AdminFitAnalysisServiceImpl implements AdminFitAnalysisService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<FitSafetyResponse.Reason>> GATE_REASON_LIST = new TypeReference<>() {
    };
    /** 분포 정렬 기준: 건수 내림차순, 동수는 알파벳순(관측 결과의 결정성 보장). */
    private static final Comparator<Map.Entry<String, Long>> COUNT_DESC_THEN_KEY =
            Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                    .thenComparing(Map.Entry.comparingByKey());
    private static final int TOP_CLAIM_LIMIT = 10;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final AdminFitAnalysisMapper adminFitAnalysisMapper;
    private final FitAnalysisMapper fitAnalysisMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResult<AdminFitAnalysisListItemResponse> list(AdminFitAnalysisListQuery query) {
        int page = Math.max(1, query.page());
        int size = query.size() <= 0 ? DEFAULT_PAGE_SIZE : Math.min(query.size(), MAX_PAGE_SIZE);
        AdminFitAnalysisListCriteria criteria = new AdminFitAnalysisListCriteria(
                query.reviewRequiredOnly(),
                query.query() == null ? null : query.query().trim(),
                normalizeEnum(query.band()),
                normalizeEnum(query.result()),
                query.memoOnly(),
                query.reanalysisOnly(),
                size,
                (page - 1) * size);

        long total = adminFitAnalysisMapper.countAll(criteria);
        List<AdminFitAnalysisListItemResponse> items = adminFitAnalysisMapper.findAll(criteria).stream()
                .map(result -> AdminFitAnalysisListItemResponse.of(
                        result,
                        parseList(result.getMatchedSkills()),
                        parseList(result.getMissingSkills())))
                .toList();
        return PageResult.of(items, total, page, size);
    }

    /** band/result 필터값 정규화: null/공백/대소문자 무시 후 대문자. 빈 값이면 'ALL'(필터 미적용). */
    private static String normalizeEnum(String value) {
        if (value == null || value.isBlank()) {
            return "ALL";
        }
        return value.trim().toUpperCase();
    }

    @Override
    @Transactional(readOnly = true)
    public AdminFitAnalysisDetailResponse get(Long id) {
        AdminFitAnalysisResult result = adminFitAnalysisMapper.findById(id);
        if (result == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "적합도 분석 결과를 찾을 수 없습니다.");
        }
        return AdminFitAnalysisDetailResponse.of(
                result,
                parseList(result.getMatchedSkills()),
                parseList(result.getMissingSkills()),
                parseList(result.getRecommendedStudy()),
                parseList(result.getRecommendedCertificates()),
                parseList(result.getScoreBasis()),
                parseList(result.getStrategyActions()),
                parseGateReasons(result.getGateReasonsJson()),
                fitAnalysisMapper.findLearningTasksByFitAnalysisId(id).stream()
                        .map(FitAnalysisLearningTaskResponse::from)
                        .toList(),
                listMemos(id));
    }

    /** gate 통계: 전체 gate 행을 분포로 집계한다(운영 gate reason 분포 관측 — 차기 model-card 개정·alias 후보 발굴의 전제). */
    @Override
    @Transactional(readOnly = true)
    public AdminGateStatsResponse getGateStats() {
        List<AdminGateStatsRow> rows = adminFitAnalysisMapper.findAllGateRows();

        Map<String, Long> byGateStatus = new HashMap<>();
        Map<String, Long> byReviewStatus = new HashMap<>();
        Map<String, Long> byMaxSeverity = new HashMap<>();
        Map<String, Long> byReasonType = new HashMap<>();
        Map<String, Long> byReasonSeverity = new HashMap<>();
        Map<String, Long> claimCounts = new HashMap<>();
        long brokenReasonsJsonCount = 0;

        for (AdminGateStatsRow row : rows) {
            // NULL 컬럼은 해당 분포에서 제외한다("null" 키를 만들지 않는다).
            tally(byGateStatus, row.getGateStatus());
            tally(byReviewStatus, row.getReviewStatus());
            tally(byMaxSeverity, row.getMaxSeverity());

            String json = row.getGateReasonsJson();
            if (json == null || json.isBlank()) {
                continue;                                          // reasons 없음 — broken 아님
            }
            List<FitSafetyResponse.Reason> reasons;
            try {
                reasons = objectMapper.readValue(json, GATE_REASON_LIST);
            } catch (Exception ignored) {
                brokenReasonsJsonCount++;                          // 깨진 JSON — 집계 중단 없이 건수만 센다
                continue;
            }
            if (reasons == null) {
                continue;
            }
            for (FitSafetyResponse.Reason reason : reasons) {
                if (reason == null) {
                    continue;
                }
                tally(byReasonType, reason.type());
                tally(byReasonSeverity, reason.severity());
                tally(claimCounts, reason.claim());
            }
        }

        List<AdminGateStatsResponse.TopClaim> topClaims = claimCounts.entrySet().stream()
                .sorted(COUNT_DESC_THEN_KEY)
                .limit(TOP_CLAIM_LIMIT)
                .map(entry -> new AdminGateStatsResponse.TopClaim(entry.getKey(), entry.getValue()))
                .toList();

        return new AdminGateStatsResponse(
                rows.size(),
                sortByCountDesc(byGateStatus),
                sortByCountDesc(byReviewStatus),
                sortByCountDesc(byMaxSeverity),
                sortByCountDesc(byReasonType),
                sortByCountDesc(byReasonSeverity),
                brokenReasonsJsonCount,
                topClaims);
    }

    private static final java.util.Set<String> REVIEW_STATUSES =
            java.util.Set.of("PENDING", "RESOLVED", "REANALYSIS_REQUESTED");

    @Override
    @Transactional
    public AdminFitAnalysisDetailResponse reviewGate(Long fitAnalysisId, Long adminUserId, AdminGateReviewRequest request) {
        String status = request.reviewStatus() == null ? "" : request.reviewStatus().trim().toUpperCase();
        if (!REVIEW_STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "reviewStatus 는 PENDING/RESOLVED/REANALYSIS_REQUESTED 중 하나여야 합니다.");
        }
        int updated = adminFitAnalysisMapper.updateGateReview(fitAnalysisId, adminUserId, status);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "gate 결과가 없는 분석입니다(R3 이전 분석은 검토 대상이 아닙니다).");
        }
        if (request.note() != null && !request.note().isBlank()) {
            AdminFitAnalysisMemo memo = AdminFitAnalysisMemo.builder()
                    .fitAnalysisId(fitAnalysisId)
                    .adminUserId(adminUserId)
                    .memoType("GATE_REVIEW")
                    .content(request.note().trim())
                    .build();
            adminFitAnalysisMapper.insertMemo(memo);
        }
        return get(fitAnalysisId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminFitAnalysisMemoResponse> listMemos(Long fitAnalysisId) {
        ensureFitAnalysisExists(fitAnalysisId);
        return adminFitAnalysisMapper.findMemosByFitAnalysisId(fitAnalysisId).stream()
                .map(AdminFitAnalysisMemoResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public AdminFitAnalysisMemoResponse createMemo(Long fitAnalysisId, Long adminUserId, AdminFitAnalysisMemoRequest request) {
        ensureFitAnalysisExists(fitAnalysisId);
        AdminFitAnalysisMemo memo = AdminFitAnalysisMemo.builder()
                .fitAnalysisId(fitAnalysisId)
                .adminUserId(adminUserId)
                .memoType(normalizeMemoType(request.memoType()))
                .content(request.content().trim())
                .build();
        adminFitAnalysisMapper.insertMemo(memo);
        return AdminFitAnalysisMemoResponse.from(adminFitAnalysisMapper.findMemoByIdAndFitAnalysisId(memo.getId(), fitAnalysisId));
    }

    @Override
    @Transactional
    public AdminFitAnalysisMemoResponse updateMemo(Long fitAnalysisId, Long memoId, AdminFitAnalysisMemoRequest request) {
        AdminFitAnalysisMemo memo = adminFitAnalysisMapper.findMemoByIdAndFitAnalysisId(memoId, fitAnalysisId);
        if (memo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "운영 메모를 찾을 수 없습니다.");
        }
        memo.setMemoType(normalizeMemoType(request.memoType()));
        memo.setContent(request.content().trim());
        int updated = adminFitAnalysisMapper.updateMemo(memo);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "운영 메모를 찾을 수 없습니다.");
        }
        return AdminFitAnalysisMemoResponse.from(adminFitAnalysisMapper.findMemoByIdAndFitAnalysisId(memoId, fitAnalysisId));
    }

    @Override
    @Transactional
    public void deleteMemo(Long fitAnalysisId, Long memoId) {
        int deleted = adminFitAnalysisMapper.deleteMemo(memoId, fitAnalysisId);
        if (deleted == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "운영 메모를 찾을 수 없습니다.");
        }
    }

    private List<String> parseList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, STRING_LIST).stream()
                    .filter(item -> item != null && !item.isBlank())
                    .map(String::trim)
                    .distinct()
                    .toList();
        } catch (Exception ignored) {
            return List.of(value.split(",")).stream()
                    .map(String::trim)
                    .filter(item -> !item.isBlank())
                    .distinct()
                    .toList();
        }
    }

    /** gate_reasons_json 을 reason 목록으로 파싱한다(없거나 깨지면 빈 목록). */
    private List<FitSafetyResponse.Reason> parseGateReasons(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            List<FitSafetyResponse.Reason> reasons = objectMapper.readValue(value, GATE_REASON_LIST);
            return reasons == null ? List.of() : reasons;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    /** 분포 집계: null/공백 키는 제외하고 건수를 1 올린다. */
    private static void tally(Map<String, Long> counts, String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        counts.merge(key.trim(), 1L, Long::sum);
    }

    /** 분포 맵을 건수 내림차순(동수는 알파벳순) LinkedHashMap 으로 정렬한다. */
    private static Map<String, Long> sortByCountDesc(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .sorted(COUNT_DESC_THEN_KEY)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (left, right) -> left, LinkedHashMap::new));
    }

    private void ensureFitAnalysisExists(Long fitAnalysisId) {
        if (adminFitAnalysisMapper.findById(fitAnalysisId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "적합도 분석 결과를 찾을 수 없습니다.");
        }
    }

    private static String normalizeMemoType(String value) {
        return value == null || value.isBlank() ? "GENERAL" : value.trim().toUpperCase();
    }
}
