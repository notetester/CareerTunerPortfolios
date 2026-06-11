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
import com.careertuner.analysis.domain.AnalysisAnswerSource;
import com.careertuner.analysis.domain.AnalysisSource;
import com.careertuner.analysis.domain.CareerAnalysisRun;
import com.careertuner.analysis.dto.AnalysisAnswerThemeResponse;
import com.careertuner.analysis.dto.AnalysisApplicationSummaryResponse;
import com.careertuner.analysis.dto.AnalysisApplicationTierResponse;
import com.careertuner.analysis.dto.AnalysisJobDistributionResponse;
import com.careertuner.analysis.dto.AnalysisMonthlyFitResponse;
import com.careertuner.analysis.dto.AnalysisPeriodResponse;
import com.careertuner.analysis.dto.AnalysisScorePointResponse;
import com.careertuner.analysis.dto.AnalysisStatResponse;
import com.careertuner.analysis.dto.AnalysisStrengthTrendResponse;
import com.careertuner.analysis.dto.AnalysisSummaryResponse;
import com.careertuner.analysis.dto.AnalysisTierItemResponse;
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
        // 결정적 집계(기획 §8.9, 디자인 분석 §6.10). AI 입력/캐시 fingerprint에는 포함하지 않아
        // 기존 저장 요약이 무효화되지 않는다.
        List<AnalysisStrengthTrendResponse> strengthTrends = strengthTrends(analyzed);
        List<AnalysisJobDistributionResponse> jobDistribution = jobDistribution(sources);
        List<AnalysisAnswerThemeResponse> answerThemes = answerThemes(analysisMapper.findAnswerSourcesByUserId(userId));
        AnalysisPeriodResponse period = period(sources, analyzed);
        List<AnalysisMonthlyFitResponse> monthlyFitTrend = monthlyFitTrend(analyzed);
        List<AnalysisApplicationTierResponse> applicationTiers = applicationTiers(analyzed);

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
                        strengthTrends, jobDistribution, answerThemes, period,
                        monthlyFitTrend, applicationTiers,
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
                ai.recommendedDirections(), ai.trendSummary(), interviewTrend,
                strengthTrends, jobDistribution, answerThemes, period,
                monthlyFitTrend, applicationTiers, run);
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

    /** 반복 강점: 적합도 분석에서 매칭된 역량(matched_skills)을 지원 건 단위로 집계한다. */
    private List<AnalysisStrengthTrendResponse> strengthTrends(List<AnalysisSource> analyzed) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (AnalysisSource source : analyzed) {
            for (String skill : parseList(source.getMatchedSkills())) {
                counts.merge(skill, 1, Integer::sum);
            }
        }
        int total = analyzed.size();
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(8)
                .map(entry -> new AnalysisStrengthTrendResponse(
                        entry.getKey(), entry.getValue(), total, percentage(entry.getValue(), total)))
                .toList();
    }

    /** 자주 지원하는 직무 분포: 전체 지원 건(미분석 포함)을 직무명으로 묶는다. */
    private static List<AnalysisJobDistributionResponse> jobDistribution(List<AnalysisSource> sources) {
        int total = sources.size();
        return sources.stream()
                .collect(Collectors.groupingBy(AnalysisSource::getJobTitle, LinkedHashMap::new, Collectors.toList()))
                .entrySet()
                .stream()
                .map(entry -> {
                    List<AnalysisSource> values = entry.getValue();
                    var averageFit = values.stream()
                            .filter(source -> source.getFitScore() != null)
                            .mapToInt(AnalysisSource::getFitScore)
                            .average();
                    return new AnalysisJobDistributionResponse(
                            entry.getKey(),
                            values.size(),
                            percentage(values.size(), total),
                            averageFit.isPresent() ? (int) Math.round(averageFit.getAsDouble()) : null);
                })
                .sorted(Comparator.comparingInt(AnalysisJobDistributionResponse::count).reversed())
                .toList();
    }

    /**
     * 자주 개선이 필요한 답변 요소: 질문 유형별 평균 점수를 낮은 순으로 정렬하고,
     * 유형별 최저점 답변의 피드백을 대표 개선 포인트로 제공한다(입력이 점수 오름차순 정렬이라 첫 행이 최저점).
     */
    private static List<AnalysisAnswerThemeResponse> answerThemes(List<AnalysisAnswerSource> answers) {
        return answers.stream()
                .collect(Collectors.groupingBy(AnalysisAnswerSource::getQuestionType, LinkedHashMap::new, Collectors.toList()))
                .entrySet()
                .stream()
                .map(entry -> {
                    List<AnalysisAnswerSource> values = entry.getValue();
                    int average = (int) Math.round(values.stream()
                            .mapToInt(AnalysisAnswerSource::getScore)
                            .average()
                            .orElse(0));
                    String sampleFeedback = values.stream()
                            .map(AnalysisAnswerSource::getFeedback)
                            .filter(feedback -> feedback != null && !feedback.isBlank())
                            .findFirst()
                            .orElse(null);
                    return new AnalysisAnswerThemeResponse(entry.getKey(), values.size(), average, sampleFeedback);
                })
                .sorted(Comparator.comparingInt(AnalysisAnswerThemeResponse::averageScore))
                .toList();
    }

    /** 월별 평균 적합도 변화: 적합도 분석 시점을 월 단위로 묶어 최근 6개월만 보여준다. */
    private static List<AnalysisMonthlyFitResponse> monthlyFitTrend(List<AnalysisSource> analyzed) {
        Map<java.time.YearMonth, List<Integer>> byMonth = new java.util.TreeMap<>();
        for (AnalysisSource source : analyzed) {
            if (source.getFitScore() == null || source.getAnalyzedAt() == null) {
                continue;
            }
            byMonth.computeIfAbsent(java.time.YearMonth.from(source.getAnalyzedAt()), key -> new java.util.ArrayList<>())
                    .add(source.getFitScore());
        }
        List<AnalysisMonthlyFitResponse> points = byMonth.entrySet().stream()
                .map(entry -> new AnalysisMonthlyFitResponse(
                        entry.getKey().toString(),
                        (int) Math.round(entry.getValue().stream().mapToInt(Integer::intValue).average().orElse(0)),
                        entry.getValue().size()))
                .toList();
        return points.size() <= 6 ? points : points.subList(points.size() - 6, points.size());
    }

    /** 상향/적정/안전 지원 분류: 최신 적합도 점수 기준의 결정적 분류. 세 구간을 항상 반환한다. */
    private static List<AnalysisApplicationTierResponse> applicationTiers(List<AnalysisSource> analyzed) {
        Map<String, List<AnalysisTierItemResponse>> buckets = new LinkedHashMap<>();
        buckets.put("SAFE", new java.util.ArrayList<>());
        buckets.put("MATCH", new java.util.ArrayList<>());
        buckets.put("CHALLENGE", new java.util.ArrayList<>());
        analyzed.stream()
                .filter(source -> source.getFitScore() != null)
                .sorted(Comparator.comparing(AnalysisSource::getFitScore).reversed())
                .forEach(source -> {
                    String tier = source.getFitScore() >= 80 ? "SAFE" : source.getFitScore() >= 60 ? "MATCH" : "CHALLENGE";
                    buckets.get(tier).add(new AnalysisTierItemResponse(
                            source.getApplicationCaseId(),
                            source.getCompanyName(),
                            source.getJobTitle(),
                            source.getFitScore()));
                });
        return List.of(
                new AnalysisApplicationTierResponse("SAFE", "안전 지원",
                        "적합도 80점 이상. 합격 가능성이 높은 지원 건입니다.", buckets.get("SAFE")),
                new AnalysisApplicationTierResponse("MATCH", "적정 지원",
                        "적합도 60~79점. 현재 스펙과 잘 맞아 보완 1~2개로 경쟁력이 생깁니다.", buckets.get("MATCH")),
                new AnalysisApplicationTierResponse("CHALLENGE", "상향 지원",
                        "적합도 60점 미만. 부족 역량 보완이 전제되는 도전 지원 건입니다.", buckets.get("CHALLENGE")));
    }

    /** 분석 대상 기간과 데이터 수: 적합도 분석 시점의 최소/최대를 기간으로 쓴다. */
    private static AnalysisPeriodResponse period(List<AnalysisSource> sources, List<AnalysisSource> analyzed) {
        var analyzedAts = analyzed.stream()
                .map(AnalysisSource::getAnalyzedAt)
                .filter(java.util.Objects::nonNull)
                .sorted()
                .toList();
        int interviewSessions = sources.stream().mapToInt(AnalysisSource::getInterviewCount).sum();
        return new AnalysisPeriodResponse(
                analyzedAts.isEmpty() ? null : analyzedAts.get(0),
                analyzedAts.isEmpty() ? null : analyzedAts.get(analyzedAts.size() - 1),
                sources.size(),
                analyzed.size(),
                interviewSessions);
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
