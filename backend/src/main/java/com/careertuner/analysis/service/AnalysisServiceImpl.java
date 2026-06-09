package com.careertuner.analysis.service;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.analysis.ai.CareerTrendAiCommand;
import com.careertuner.analysis.ai.CareerTrendAiResult;
import com.careertuner.analysis.ai.CareerTrendAiService;
import com.careertuner.analysis.domain.AnalysisSource;
import com.careertuner.analysis.domain.CareerAnalysisRun;
import com.careertuner.analysis.dto.AnalysisApplicationSummaryResponse;
import com.careertuner.analysis.dto.AnalysisScorePointResponse;
import com.careertuner.analysis.dto.AnalysisStatResponse;
import com.careertuner.analysis.dto.AnalysisSummaryResponse;
import com.careertuner.analysis.dto.CareerAnalysisRunResponse;
import com.careertuner.analysis.dto.InterviewTrendResponse;
import com.careertuner.analysis.dto.JobReadinessResponse;
import com.careertuner.analysis.dto.SkillGapResponse;
import com.careertuner.analysis.mapper.AnalysisMapper;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class AnalysisServiceImpl implements AnalysisService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final DateTimeFormatter SCORE_LABEL_FORMAT = DateTimeFormatter.ofPattern("M월 d일");
    private static final String ANALYSIS_TYPE = "CAREER_TREND";
    // 명시적 재생성(사용자가 '재분석' 클릭) 시 적합도 분석과 동일하게 ai_usage_log에 차감을 남긴다.
    private static final int EXPLICIT_REFRESH_CREDIT = 1;

    private final AnalysisMapper analysisMapper;
    private final CareerTrendAiService careerTrendAiService;
    private final CareerAnalysisRunService careerAnalysisRunService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public AnalysisSummaryResponse getSummary(Long userId) {
        return buildSummary(userId, false);
    }

    @Override
    @Transactional
    public AnalysisSummaryResponse refreshSummary(Long userId) {
        return buildSummary(userId, true);
    }

    /**
     * 결정적 집계는 항상 새로 계산하고(토큰 비용 없음), 비용이 드는 장기 경향 AI 요약만 캐시한다.
     * forceRefresh=false 이면 같은 입력의 저장 결과를 재사용(AI 미실행)하고,
     * 입력이 바뀐 경우에만 1회 자동 재생성한다. forceRefresh=true 이면 항상 재실행 + 크레딧 차감.
     */
    private AnalysisSummaryResponse buildSummary(Long userId, boolean forceRefresh) {
        List<AnalysisSource> sources = analysisMapper.findSourcesByUserId(userId);
        List<AnalysisSource> analyzed = sources.stream()
                .filter(source -> source.getFitAnalysisId() != null)
                .toList();

        AnalysisStatResponse stats = stats(sources, analyzed);
        List<SkillGapResponse> skillGaps = skillGaps(analyzed);
        List<JobReadinessResponse> jobReadiness = jobReadiness(analyzed);
        List<AnalysisScorePointResponse> scoreHistory = scoreHistory(analyzed);
        InterviewTrendResponse interviewTrend = interviewTrend(sources);

        // 장기 경향 요약(16)과 다음 지원 방향(17) AI 입력. 키 주입 시 실 AI로 교체된다.
        CareerTrendAiCommand command = new CareerTrendAiCommand(
                stats,
                skillGaps,
                jobReadiness,
                scoreHistory,
                interviewTrend,
                bestStrategy(analyzed));
        String fingerprint = CareerAnalysisRunService.fingerprint(canonical(command));

        List<AnalysisApplicationSummaryResponse> applications =
                sources.stream().map(AnalysisApplicationSummaryResponse::from).toList();

        if (!forceRefresh) {
            Optional<CareerAnalysisRun> cached = careerAnalysisRunService.findFreshRun(userId, ANALYSIS_TYPE, fingerprint);
            if (cached.isPresent()) {
                CareerAnalysisRun run = cached.get();
                CachedTrend stored = parseTrend(run.getResult());
                return new AnalysisSummaryResponse(
                        stats, skillGaps, jobReadiness, scoreHistory, applications,
                        stored.recommendedDirections(), stored.trendSummary(), interviewTrend,
                        CareerAnalysisRunResponse.from(run));
            }
        }

        // 캐시 미스(데이터 변경/최초) 또는 명시적 재생성: 실제 AI 실행.
        CareerTrendAiResult ai = careerTrendAiService.generate(command);
        int creditUsed = forceRefresh && "SUCCESS".equals(ai.status()) ? EXPLICIT_REFRESH_CREDIT : 0;
        CareerAnalysisRunResponse run = careerAnalysisRunService.record(
                userId,
                ANALYSIS_TYPE,
                fingerprint,
                command,
                Map.of("trendSummary", ai.trendSummary(), "recommendedDirections", ai.recommendedDirections()),
                ai.usage(),
                ai.status(),
                ai.errorMessage(),
                ai.retryable(),
                creditUsed);

        return new AnalysisSummaryResponse(
                stats, skillGaps, jobReadiness, scoreHistory, applications,
                ai.recommendedDirections(), ai.trendSummary(), interviewTrend, run);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CareerAnalysisRunResponse> getHistory(Long userId) {
        return careerAnalysisRunService.listByUserId(userId);
    }

    private String canonical(CareerTrendAiCommand command) {
        try {
            return objectMapper.writeValueAsString(command);
        } catch (Exception ex) {
            return String.valueOf(command.hashCode());
        }
    }

    private CachedTrend parseTrend(String resultJson) {
        if (resultJson == null || resultJson.isBlank()) {
            return new CachedTrend("", List.of());
        }
        try {
            var node = objectMapper.readTree(resultJson);
            String summary = node.path("trendSummary").asText("");
            List<String> directions = objectMapper.convertValue(
                    node.path("recommendedDirections"), STRING_LIST);
            return new CachedTrend(summary, directions == null ? List.of() : directions);
        } catch (Exception ex) {
            return new CachedTrend("", List.of());
        }
    }

    private record CachedTrend(String trendSummary, List<String> recommendedDirections) {
    }

    private static InterviewTrendResponse interviewTrend(List<AnalysisSource> sources) {
        int totalSessions = sources.stream().mapToInt(AnalysisSource::getInterviewCount).sum();
        int totalAnswers = sources.stream().mapToInt(AnalysisSource::getInterviewAnswerCount).sum();
        int averageSessions = weightedAverage(
                sources,
                AnalysisSource::getInterviewCount,
                AnalysisSource::getAverageInterviewScore);
        int averageAnswers = weightedAverage(
                sources,
                AnalysisSource::getInterviewAnswerCount,
                AnalysisSource::getAverageInterviewAnswerScore);
        return new InterviewTrendResponse(totalSessions, averageSessions, totalAnswers, averageAnswers);
    }

    private static int weightedAverage(List<AnalysisSource> sources,
                                       java.util.function.ToIntFunction<AnalysisSource> count,
                                       java.util.function.Function<AnalysisSource, Integer> average) {
        int total = sources.stream().mapToInt(count).sum();
        if (total == 0) {
            return 0;
        }
        int sum = sources.stream()
                .filter(source -> average.apply(source) != null)
                .mapToInt(source -> count.applyAsInt(source) * average.apply(source))
                .sum();
        return (int) Math.round(sum / (double) total);
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

    private static String bestStrategy(List<AnalysisSource> analyzed) {
        return analyzed.stream()
                .filter(source -> source.getStrategy() != null && !source.getStrategy().isBlank())
                .max(Comparator.comparing(source -> source.getFitScore() == null ? 0 : source.getFitScore()))
                .map(AnalysisSource::getStrategy)
                .orElse(null);
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
