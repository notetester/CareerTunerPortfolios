package com.careertuner.admin.analytics.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.analytics.domain.AdminAnalysisSource;
import com.careertuner.admin.analytics.domain.AdminCareerRunMemo;
import com.careertuner.admin.analytics.dto.AdminAnalysisFailureResponse;
import com.careertuner.admin.analytics.dto.AdminAnalyticsStatsResponse;
import com.careertuner.admin.analytics.dto.AdminAnalyticsSummaryResponse;
import com.careertuner.admin.analytics.dto.AdminCareerAnalysisRunResponse;
import com.careertuner.admin.analytics.dto.AdminCareerRunMemoRequest;
import com.careertuner.admin.analytics.dto.AdminCareerRunMemoResponse;
import com.careertuner.admin.analytics.dto.AdminCountResponse;
import com.careertuner.admin.analytics.dto.AdminDailyUsageResponse;
import com.careertuner.admin.analytics.dto.AdminFitScoreBandResponse;
import com.careertuner.admin.analytics.dto.AdminQualityFlagResponse;
import com.careertuner.admin.analytics.dto.AdminRecentAnalysisResponse;
import com.careertuner.admin.analytics.dto.AdminSkillGapResponse;
import com.careertuner.admin.analytics.mapper.AdminAnalyticsMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class AdminAnalyticsServiceImpl implements AdminAnalyticsService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final AdminAnalyticsMapper adminAnalyticsMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public AdminAnalyticsSummaryResponse getSummary() {
        List<AdminAnalysisSource> analyses = adminAnalyticsMapper.findLatestAnalyses();

        return new AdminAnalyticsSummaryResponse(
                stats(analyses),
                adminAnalyticsMapper.countUsersByPlan().stream().map(AdminCountResponse::from).toList(),
                adminAnalyticsMapper.countApplicationsByStatus().stream().map(AdminCountResponse::from).toList(),
                skillGaps(analyses),
                fitScoreBands(analyses),
                analyses.stream()
                        .limit(8)
                        .map(AdminRecentAnalysisResponse::from)
                        .toList(),
                adminAnalyticsMapper.findDailyUsage().stream()
                        .map(AdminDailyUsageResponse::from)
                        .toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminAnalysisFailureResponse> listFailures() {
        return adminAnalyticsMapper.findAnalysisFailures().stream()
                .map(AdminAnalysisFailureResponse::from)
                .toList();
    }

    /**
     * 품질 검수 큐. AI를 다시 호출하지 않고 저장된 최신 분석에 결정적 휴리스틱을 적용한다.
     * 사용자 원본은 수정하지 않으며, 조치는 적합도 운영 메모(REANALYSIS/QUALITY)로 남긴다.
     */
    @Override
    @Transactional(readOnly = true)
    public List<AdminQualityFlagResponse> listQualityFlags() {
        List<AdminQualityFlagResponse> flags = new java.util.ArrayList<>();
        for (AdminAnalysisSource analysis : adminAnalyticsMapper.findLatestAnalyses()) {
            int missingCount = parseList(analysis.getMissingSkills()).size();
            int certificateCount = certificateCount(analysis.getCertificateRecommendations());
            Integer score = analysis.getFitScore();

            if (score != null && score >= 85 && missingCount >= 5) {
                flags.add(flag(analysis, "SCORE_GAP_MISMATCH", "HIGH",
                        "적합도 %d점인데 부족 역량이 %d개라 점수 근거 검토가 필요합니다.".formatted(score, missingCount)));
            }
            if (score != null && score < 40 && missingCount == 0) {
                flags.add(flag(analysis, "LOW_SCORE_NO_GAPS", "MEDIUM",
                        "적합도 %d점인데 부족 역량이 비어 있어 분석 입력 확인이 필요합니다.".formatted(score)));
            }
            if (certificateCount > 3) {
                flags.add(flag(analysis, "EXCESSIVE_CERTS", "MEDIUM",
                        "자격증 추천이 %d개로 과도합니다. 실무 보완 우선 원칙과 어긋날 수 있습니다.".formatted(certificateCount)));
            }
            if ("SUCCESS".equals(analysis.getStatus())
                    && (analysis.getStrategy() == null || analysis.getStrategy().isBlank())) {
                flags.add(flag(analysis, "EMPTY_STRATEGY", "LOW",
                        "성공 상태인데 지원 전략 문구가 비어 있습니다."));
            }
            if ("LOW".equals(jsonText(analysis.getAnalysisConfidence(), "level"))) {
                flags.add(flag(analysis, "LOW_CONFIDENCE", "MEDIUM",
                        "입력 데이터 부족으로 분석 신뢰도가 낮습니다. 사용자에게 입력 보강 안내가 필요한지 확인하세요."));
            }
            if ("APPLY".equals(jsonText(analysis.getApplyDecision(), "decision"))
                    && requiredUnmetCount(analysis.getConditionMatrix()) > 0) {
                flags.add(flag(analysis, "REQUIRED_GAP_APPLY", "HIGH",
                        "필수 조건 미충족 항목이 있는데 최종 판단이 지원 가능(APPLY)으로 생성됐습니다."));
            }
            if ("SUCCESS".equals(analysis.getStatus()) && isEmptyArray(analysis.getConditionMatrix())) {
                flags.add(flag(analysis, "EMPTY_CONDITION_MATRIX", "LOW",
                        "성공 상태인데 요구조건-스펙 비교 매트릭스가 비어 있습니다."));
            }
            if (analysis.getStatus() != null && !"SUCCESS".equals(analysis.getStatus())) {
                flags.add(flag(analysis, "DEGRADED_RESULT", "HIGH",
                        "%s 상태 결과가 사용자에게 노출 중입니다. 재분석 안내가 필요할 수 있습니다.".formatted(analysis.getStatus())));
            }
        }
        return flags;
    }

    private static AdminQualityFlagResponse flag(AdminAnalysisSource analysis, String flagType, String severity, String detail) {
        return new AdminQualityFlagResponse(
                analysis.getFitAnalysisId(),
                analysis.getApplicationCaseId(),
                analysis.getUserName(),
                analysis.getUserEmail(),
                analysis.getCompanyName(),
                analysis.getJobTitle(),
                analysis.getFitScore(),
                flagType,
                severity,
                detail,
                analysis.getAnalyzedAt());
    }

    /** certificate_recommendations JSON 배열 길이. 객체 배열이므로 단순 노드 수만 센다. */
    private int certificateCount(String json) {
        if (json == null || json.isBlank()) {
            return 0;
        }
        try {
            var node = objectMapper.readTree(json);
            return node.isArray() ? node.size() : 0;
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String jsonText(String json, String field) {
        if (json == null || json.isBlank()) {
            return "";
        }
        try {
            return objectMapper.readTree(json).path(field).asText("");
        } catch (Exception ignored) {
            return "";
        }
    }

    private int requiredUnmetCount(String json) {
        if (json == null || json.isBlank()) {
            return 0;
        }
        try {
            int count = 0;
            for (var row : objectMapper.readTree(json)) {
                if ("REQUIRED".equals(row.path("conditionType").asText())
                        && "UNMET".equals(row.path("matchStatus").asText())) {
                    count++;
                }
            }
            return count;
        } catch (Exception ignored) {
            return 0;
        }
    }

    private boolean isEmptyArray(String json) {
        if (json == null || json.isBlank()) {
            return true;
        }
        try {
            var node = objectMapper.readTree(json);
            return node.isArray() && node.isEmpty();
        } catch (Exception ignored) {
            return true;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminCareerAnalysisRunResponse> listRuns(Long userId) {
        return adminAnalyticsMapper.findCareerAnalysisRuns(userId).stream()
                .map(AdminCareerAnalysisRunResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminCareerRunMemoResponse> listMemos(Long runId) {
        ensureRunExists(runId);
        return adminAnalyticsMapper.findMemosByRunId(runId).stream()
                .map(AdminCareerRunMemoResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public AdminCareerRunMemoResponse createMemo(Long runId, Long adminUserId, AdminCareerRunMemoRequest request) {
        ensureRunExists(runId);
        AdminCareerRunMemo memo = AdminCareerRunMemo.builder()
                .careerAnalysisRunId(runId)
                .adminUserId(adminUserId)
                .memoType(normalizeMemoType(request.memoType()))
                .content(request.content().trim())
                .build();
        adminAnalyticsMapper.insertMemo(memo);
        return AdminCareerRunMemoResponse.from(adminAnalyticsMapper.findMemoByIdAndRunId(memo.getId(), runId));
    }

    @Override
    @Transactional
    public AdminCareerRunMemoResponse updateMemo(Long runId, Long memoId, AdminCareerRunMemoRequest request) {
        AdminCareerRunMemo memo = adminAnalyticsMapper.findMemoByIdAndRunId(memoId, runId);
        if (memo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "운영 메모를 찾을 수 없습니다.");
        }
        memo.setMemoType(normalizeMemoType(request.memoType()));
        memo.setContent(request.content().trim());
        int updated = adminAnalyticsMapper.updateMemo(memo);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "운영 메모를 찾을 수 없습니다.");
        }
        return AdminCareerRunMemoResponse.from(adminAnalyticsMapper.findMemoByIdAndRunId(memoId, runId));
    }

    @Override
    @Transactional
    public void deleteMemo(Long runId, Long memoId) {
        int deleted = adminAnalyticsMapper.deleteMemo(memoId, runId);
        if (deleted == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "운영 메모를 찾을 수 없습니다.");
        }
    }

    private void ensureRunExists(Long runId) {
        if (adminAnalyticsMapper.findCareerAnalysisRunById(runId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "분석 실행 이력을 찾을 수 없습니다.");
        }
    }

    private static String normalizeMemoType(String value) {
        return value == null || value.isBlank() ? "GENERAL" : value.trim().toUpperCase();
    }

    private AdminAnalyticsStatsResponse stats(List<AdminAnalysisSource> analyses) {
        int averageFitScore = (int) Math.round(analyses.stream()
                .filter(analysis -> analysis.getFitScore() != null)
                .map(AdminAnalysisSource::getFitScore)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0));

        return new AdminAnalyticsStatsResponse(
                adminAnalyticsMapper.countUsers(),
                adminAnalyticsMapper.countActiveUsers(),
                adminAnalyticsMapper.countApplications(),
                analyses.size(),
                adminAnalyticsMapper.countInterviews(),
                averageFitScore,
                adminAnalyticsMapper.sumCreditsUsedThisMonth());
    }

    private List<AdminSkillGapResponse> skillGaps(List<AdminAnalysisSource> analyses) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (AdminAnalysisSource analysis : analyses) {
            for (String skill : parseList(analysis.getMissingSkills())) {
                counts.merge(skill, 1, Integer::sum);
            }
        }
        int total = analyses.size();
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(10)
                .map(entry -> new AdminSkillGapResponse(entry.getKey(), entry.getValue(), total, percentage(entry.getValue(), total)))
                .toList();
    }

    private static List<AdminFitScoreBandResponse> fitScoreBands(List<AdminAnalysisSource> analyses) {
        Map<String, Long> counts = analyses.stream()
                .filter(analysis -> analysis.getFitScore() != null)
                .collect(Collectors.groupingBy(
                        analysis -> fitScoreBand(analysis.getFitScore()),
                        LinkedHashMap::new,
                        Collectors.counting()));
        int total = counts.values().stream().mapToInt(Long::intValue).sum();

        return List.of("80점 이상", "70-79점", "50-69점", "50점 미만").stream()
                .map(label -> {
                    int count = counts.getOrDefault(label, 0L).intValue();
                    return new AdminFitScoreBandResponse(label, count, percentage(count, total));
                })
                .toList();
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

    private static String fitScoreBand(Integer score) {
        if (score >= 80) {
            return "80점 이상";
        }
        if (score >= 70) {
            return "70-79점";
        }
        if (score >= 50) {
            return "50-69점";
        }
        return "50점 미만";
    }

    private static int percentage(int count, int total) {
        if (total == 0) {
            return 0;
        }
        return (int) Math.round((count * 100.0) / total);
    }
}
