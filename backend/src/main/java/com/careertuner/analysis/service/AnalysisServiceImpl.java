package com.careertuner.analysis.service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.analysis.domain.AnalysisSource;
import com.careertuner.analysis.dto.AnalysisApplicationSummaryResponse;
import com.careertuner.analysis.dto.AnalysisScorePointResponse;
import com.careertuner.analysis.dto.AnalysisStatResponse;
import com.careertuner.analysis.dto.AnalysisSummaryResponse;
import com.careertuner.analysis.dto.JobReadinessResponse;
import com.careertuner.analysis.dto.SkillGapResponse;
import com.careertuner.analysis.mapper.AnalysisMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AnalysisServiceImpl implements AnalysisService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final DateTimeFormatter SCORE_LABEL_FORMAT = DateTimeFormatter.ofPattern("M월 d일");

    private final AnalysisMapper analysisMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional(readOnly = true)
    public AnalysisSummaryResponse getSummary(Long userId) {
        List<AnalysisSource> sources = analysisMapper.findSourcesByUserId(userId);
        List<AnalysisSource> analyzed = sources.stream()
                .filter(source -> source.getFitAnalysisId() != null)
                .toList();

        return new AnalysisSummaryResponse(
                stats(sources, analyzed),
                skillGaps(analyzed),
                jobReadiness(analyzed),
                scoreHistory(analyzed),
                sources.stream().map(AnalysisApplicationSummaryResponse::from).toList(),
                recommendedDirections(analyzed));
    }

    private static AnalysisStatResponse stats(List<AnalysisSource> sources, List<AnalysisSource> analyzed) {
        int average = (int) Math.round(analyzed.stream()
                .filter(source -> source.getFitScore() != null)
                .mapToInt(AnalysisSource::getFitScore)
                .average()
                .orElse(0));
        int highFit = (int) analyzed.stream()
                .filter(source -> source.getFitScore() != null && source.getFitScore() >= 70)
                .count();
        int ready = (int) sources.stream()
                .filter(source -> "READY".equals(source.getStatus()) || "APPLIED".equals(source.getStatus()))
                .count();
        return new AnalysisStatResponse(sources.size(), analyzed.size(), average, highFit, ready);
    }

    private List<SkillGapResponse> skillGaps(List<AnalysisSource> analyzed) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (AnalysisSource source : analyzed) {
            for (String skill : parseList(source.getMissingSkills())) {
                counts.merge(skill, 1, Integer::sum);
            }
        }
        int total = analyzed.size();
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(8)
                .map(entry -> new SkillGapResponse(entry.getKey(), entry.getValue(), total, percentage(entry.getValue(), total)))
                .toList();
    }

    private static List<JobReadinessResponse> jobReadiness(List<AnalysisSource> analyzed) {
        return analyzed.stream()
                .collect(Collectors.groupingBy(AnalysisSource::getJobTitle, LinkedHashMap::new, Collectors.toList()))
                .entrySet()
                .stream()
                .map(entry -> {
                    List<AnalysisSource> values = entry.getValue();
                    int readiness = (int) Math.round(values.stream()
                            .filter(source -> source.getFitScore() != null)
                            .mapToInt(AnalysisSource::getFitScore)
                            .average()
                            .orElse(0));
                    String trend = readiness >= 70 ? "up" : readiness >= 50 ? "neutral" : "down";
                    return new JobReadinessResponse(entry.getKey(), readiness, values.size(), trend);
                })
                .sorted(Comparator.comparingInt(JobReadinessResponse::readiness).reversed())
                .toList();
    }

    private static List<AnalysisScorePointResponse> scoreHistory(List<AnalysisSource> analyzed) {
        return analyzed.stream()
                .filter(source -> source.getFitScore() != null && source.getAnalyzedAt() != null)
                .sorted(Comparator.comparing(AnalysisSource::getAnalyzedAt))
                .map(source -> new AnalysisScorePointResponse(source.getAnalyzedAt().format(SCORE_LABEL_FORMAT), source.getFitScore()))
                .toList();
    }

    private List<String> recommendedDirections(List<AnalysisSource> analyzed) {
        List<String> directions = new ArrayList<>();
        skillGaps(analyzed).stream()
                .limit(3)
                .forEach(gap -> directions.add("%s 보완을 우선 과제로 잡으세요. 최근 분석 %d건 중 %d건에서 부족 역량으로 나타났습니다."
                        .formatted(gap.skill(), gap.total(), gap.count())));

        analyzed.stream()
                .filter(source -> source.getStrategy() != null && !source.getStrategy().isBlank())
                .max(Comparator.comparing(source -> source.getFitScore() == null ? 0 : source.getFitScore()))
                .ifPresent(source -> directions.add("%s %s 지원 전략: %s"
                        .formatted(source.getCompanyName(), source.getJobTitle(), source.getStrategy())));

        if (directions.isEmpty()) {
            directions.add("분석 결과가 쌓이면 반복 부족 역량과 다음 지원 방향을 추천합니다.");
        }
        return directions.stream().limit(5).toList();
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

    private static int percentage(int count, int total) {
        if (total == 0) {
            return 0;
        }
        return (int) Math.round((count * 100.0) / total);
    }
}
