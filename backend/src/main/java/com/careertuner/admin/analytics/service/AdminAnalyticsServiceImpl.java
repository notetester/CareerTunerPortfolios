package com.careertuner.admin.analytics.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.analytics.domain.AdminAnalysisSource;
import com.careertuner.admin.analytics.dto.AdminAnalyticsStatsResponse;
import com.careertuner.admin.analytics.dto.AdminAnalyticsSummaryResponse;
import com.careertuner.admin.analytics.dto.AdminCareerAnalysisRunResponse;
import com.careertuner.admin.analytics.dto.AdminCountResponse;
import com.careertuner.admin.analytics.dto.AdminDailyUsageResponse;
import com.careertuner.admin.analytics.dto.AdminFitScoreBandResponse;
import com.careertuner.admin.analytics.dto.AdminRecentAnalysisResponse;
import com.careertuner.admin.analytics.dto.AdminSkillGapResponse;
import com.careertuner.admin.analytics.mapper.AdminAnalyticsMapper;
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
    public List<AdminCareerAnalysisRunResponse> listRuns(Long userId) {
        return adminAnalyticsMapper.findCareerAnalysisRuns(userId).stream()
                .map(AdminCareerAnalysisRunResponse::from)
                .toList();
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
