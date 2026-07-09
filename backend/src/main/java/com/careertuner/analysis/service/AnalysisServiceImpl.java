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
import com.careertuner.analysis.domain.AnalysisFitPointSource;
import com.careertuner.analysis.domain.AnalysisSource;
import com.careertuner.analysis.domain.AnalysisWeeklyMetricSource;
import com.careertuner.analysis.domain.CareerAnalysisRun;
import com.careertuner.analysis.dto.AnalysisAnswerThemeResponse;
import com.careertuner.analysis.dto.AnalysisApplicationPriorityResponse;
import com.careertuner.analysis.dto.AnalysisApplicationSummaryResponse;
import com.careertuner.analysis.dto.AnalysisApplicationTierResponse;
import com.careertuner.analysis.dto.AnalysisCareerRiskResponse;
import com.careertuner.analysis.dto.AnalysisCompanyTypeResponse;
import com.careertuner.analysis.dto.AnalysisCorrectionCorrelationResponse;
import com.careertuner.analysis.dto.AnalysisFitInterviewBandResponse;
import com.careertuner.analysis.dto.AnalysisJobDistributionResponse;
import com.careertuner.analysis.dto.AnalysisMonthlyFitResponse;
import com.careertuner.analysis.dto.AnalysisPeriodResponse;
import com.careertuner.analysis.dto.AnalysisScorePointResponse;
import com.careertuner.analysis.dto.AnalysisSkillFitResponse;
import com.careertuner.analysis.dto.AnalysisStatResponse;
import com.careertuner.analysis.dto.AnalysisStrengthTrendResponse;
import com.careertuner.analysis.dto.AnalysisSummaryResponse;
import com.careertuner.analysis.dto.AnalysisTierItemResponse;
import com.careertuner.analysis.dto.AnalysisToneStrategyResponse;
import com.careertuner.analysis.dto.AnalysisWeeklyChangeResponse;
import com.careertuner.analysis.dto.CareerAnalysisRunResponse;
import com.careertuner.analysis.dto.InterviewTrendResponse;
import com.careertuner.analysis.dto.JobReadinessResponse;
import com.careertuner.analysis.dto.SkillGapResponse;
import com.careertuner.analysis.mapper.AnalysisMapper;
import com.careertuner.notification.domain.Notification;
import com.careertuner.notification.service.NotificationService;
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
    private final NotificationService notificationService;
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
        List<AnalysisFitPointSource> fitHistory = analysisMapper.findFitScoreHistoryByUserId(userId);

        AnalysisStatResponse stats = stats(sources, analyzed);
        List<SkillGapResponse> skillGaps = skillGaps(analyzed);
        List<JobReadinessResponse> jobReadiness = jobReadiness(analyzed);
        List<AnalysisScorePointResponse> scoreHistory = scoreHistory(fitHistory);
        InterviewTrendResponse interviewTrend = interviewTrend(sources);
        // 결정적 집계(기획 §8.9, 디자인 분석 §6.10). AI 입력/캐시 fingerprint에는 포함하지 않아
        // 기존 저장 요약이 무효화되지 않는다.
        List<AnalysisStrengthTrendResponse> strengthTrends = strengthTrends(analyzed);
        List<AnalysisJobDistributionResponse> jobDistribution = jobDistribution(sources);
        List<AnalysisAnswerThemeResponse> answerThemes = answerThemes(analysisMapper.findAnswerSourcesByUserId(userId));
        AnalysisPeriodResponse period = period(sources, analyzed);
        List<AnalysisMonthlyFitResponse> monthlyFitTrend = monthlyFitTrend(fitHistory);
        List<AnalysisApplicationTierResponse> applicationTiers = applicationTiers(analyzed);
        List<AnalysisSkillFitResponse> skillFitAverages = skillFitAverages(analyzed);
        List<AnalysisFitInterviewBandResponse> fitInterviewBands = fitInterviewBands(analyzed);
        List<AnalysisApplicationPriorityResponse> applicationPriorities = applicationPriorities(sources);
        List<AnalysisCareerRiskResponse> careerRisks = careerRisks(sources, analyzed, skillGaps, interviewTrend);
        List<AnalysisCompanyTypeResponse> companyTypeFits = companyTypeFits(analyzed);
        AnalysisCorrectionCorrelationResponse correctionCorrelation = correctionCorrelation(analyzed);
        AnalysisWeeklyChangeResponse weeklyChange = weeklyChange(analysisMapper.findWeeklyMetricsByUserId(userId));
        List<String> avoidJobTypes = avoidJobTypes(skillGaps);
        List<String> next24HourActions = next24HourActions(applicationPriorities, skillGaps);
        List<AnalysisToneStrategyResponse> toneStrategies = toneStrategies(stats, skillGaps);
        List<String> threeLineSummary = threeLineSummary(applicationPriorities, skillGaps, next24HourActions);

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
                        monthlyFitTrend, applicationTiers, skillFitAverages, fitInterviewBands,
                        applicationPriorities, careerRisks,
                        companyTypeFits, correctionCorrelation, weeklyChange, avoidJobTypes,
                        next24HourActions, toneStrategies, threeLineSummary,
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

        // 캐시 재사용이 아닌 실제 AI 실행이 성공한 경우에만 완료 알림을 남긴다.
        if ("SUCCESS".equals(ai.status())) {
            notificationService.notify(Notification.builder()
                    .userId(userId)
                    .type("CAREER_TREND_COMPLETE")
                    .targetType("CAREER_ANALYSIS_RUN")
                    .targetId(run.id())
                    .title("커리어 트렌드 분석이 완료되었습니다")
                    .message("장기 취업 경향 요약과 다음 지원 방향 추천이 준비되었습니다.")
                    .link("/analysis")
                    .build());
        }

        return new AnalysisSummaryResponse(
                stats, skillGaps, jobReadiness, scoreHistory, applications,
                ai.recommendedDirections(), ai.trendSummary(), interviewTrend,
                strengthTrends, jobDistribution, answerThemes, period,
                monthlyFitTrend, applicationTiers, skillFitAverages, fitInterviewBands,
                applicationPriorities, careerRisks,
                companyTypeFits, correctionCorrelation, weeklyChange, avoidJobTypes,
                next24HourActions, toneStrategies, threeLineSummary, run);
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
                AnalysisSource::getScoredInterviewCount,
                AnalysisSource::getAverageInterviewScore);
        int averageAnswers = weightedAverage(
                sources,
                AnalysisSource::getScoredInterviewAnswerCount,
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

    private static List<AnalysisScorePointResponse> scoreHistory(List<AnalysisFitPointSource> history) {
        return history.stream()
                .filter(point -> point.getFitScore() != null && point.getAnalyzedAt() != null)
                .sorted(Comparator.comparing(AnalysisFitPointSource::getAnalyzedAt))
                .map(point -> new AnalysisScorePointResponse(point.getAnalyzedAt().format(SCORE_LABEL_FORMAT), point.getFitScore()))
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
    private static List<AnalysisMonthlyFitResponse> monthlyFitTrend(List<AnalysisFitPointSource> history) {
        Map<java.time.YearMonth, List<Integer>> byMonth = new java.util.TreeMap<>();
        for (AnalysisFitPointSource point : history) {
            if (point.getFitScore() == null || point.getAnalyzedAt() == null) {
                continue;
            }
            byMonth.computeIfAbsent(java.time.YearMonth.from(point.getAnalyzedAt()), key -> new java.util.ArrayList<>())
                    .add(point.getFitScore());
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

    /**
     * 기술스택별 평균 적합도: 해당 기술이 등장한(매칭/부족) 분석들의 평균 점수.
     * 등장 횟수가 많은 순으로 상위 8개만 보여준다. mostlyMatched 는 매칭 비율이 절반 이상인지 여부.
     */
    private List<AnalysisSkillFitResponse> skillFitAverages(List<AnalysisSource> analyzed) {
        Map<String, List<Integer>> scoresBySkill = new LinkedHashMap<>();
        Map<String, Integer> matchedCounts = new LinkedHashMap<>();
        for (AnalysisSource source : analyzed) {
            if (source.getFitScore() == null) {
                continue;
            }
            List<String> matched = parseList(source.getMatchedSkills());
            List<String> missing = parseList(source.getMissingSkills());
            for (String skill : matched) {
                scoresBySkill.computeIfAbsent(skill, key -> new java.util.ArrayList<>()).add(source.getFitScore());
                matchedCounts.merge(skill, 1, Integer::sum);
            }
            for (String skill : missing) {
                if (!containsIgnoreCase(matched, skill)) {
                    scoresBySkill.computeIfAbsent(skill, key -> new java.util.ArrayList<>()).add(source.getFitScore());
                }
            }
        }
        return scoresBySkill.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, List<Integer>>>comparingInt(entry -> entry.getValue().size()).reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(8)
                .map(entry -> new AnalysisSkillFitResponse(
                        entry.getKey(),
                        entry.getValue().size(),
                        (int) Math.round(entry.getValue().stream().mapToInt(Integer::intValue).average().orElse(0)),
                        matchedCounts.getOrDefault(entry.getKey(), 0) * 2 >= entry.getValue().size()))
                .toList();
    }

    /** 적합도 구간별 면접 평균 점수: 면접 세션이 있는 지원 건만 묶어 구간별 상관을 보여준다. */
    private static List<AnalysisFitInterviewBandResponse> fitInterviewBands(List<AnalysisSource> analyzed) {
        record Band(String key, String label, int min, int max) {
        }
        List<Band> bands = List.of(
                new Band("HIGH", "적합도 70점 이상", 70, 100),
                new Band("MID", "적합도 50~69점", 50, 69),
                new Band("LOW", "적합도 50점 미만", 0, 49));
        List<AnalysisSource> withInterview = analyzed.stream()
                .filter(source -> source.getFitScore() != null
                        && source.getInterviewCount() > 0
                        && source.getAverageInterviewScore() != null)
                .toList();
        return bands.stream()
                .map(band -> {
                    List<AnalysisSource> members = withInterview.stream()
                            .filter(source -> source.getFitScore() >= band.min() && source.getFitScore() <= band.max())
                            .toList();
                    Integer averageFit = members.isEmpty() ? null
                            : (int) Math.round(members.stream().mapToInt(AnalysisSource::getFitScore).average().orElse(0));
                    Integer averageInterview = members.isEmpty() ? null
                            : (int) Math.round(members.stream().mapToInt(AnalysisSource::getAverageInterviewScore).average().orElse(0));
                    return new AnalysisFitInterviewBandResponse(band.key(), band.label(), members.size(), averageFit, averageInterview);
                })
                .toList();
    }

    /**
     * 지원 우선순위: 적합도 점수를 중심으로 관심 표시와 현재 준비 상태를 보정한다.
     * CLOSED/APPLIED는 이미 행동이 끝난 건이므로 추천 대상에서 제외한다.
     */
    private static List<AnalysisApplicationPriorityResponse> applicationPriorities(List<AnalysisSource> sources) {
        return sources.stream()
                .filter(source -> source.getFitScore() != null)
                .filter(source -> !"CLOSED".equals(source.getStatus()) && !"APPLIED".equals(source.getStatus()))
                .map(source -> {
                    List<String> reasons = new java.util.ArrayList<>();
                    int priority = source.getFitScore();
                    reasons.add("현재 적합도 %d점".formatted(source.getFitScore()));

                    if (source.isFavorite()) {
                        priority += 5;
                        reasons.add("관심 지원 건으로 표시됨");
                    }
                    if ("READY".equals(source.getStatus())) {
                        priority += 8;
                        reasons.add("지원 준비가 완료된 상태");
                    } else if ("DRAFT".equals(source.getStatus())) {
                        priority -= 8;
                        reasons.add("공고·지원 정보 보완 필요");
                    } else if ("ANALYZING".equals(source.getStatus())) {
                        priority -= 4;
                        reasons.add("분석 진행 중");
                    }

                    int boundedPriority = Math.max(0, Math.min(100, priority));
                    String urgency = boundedPriority >= 80 ? "NOW" : boundedPriority >= 60 ? "PREPARE" : "HOLD";
                    return new AnalysisApplicationPriorityResponse(
                            source.getApplicationCaseId(),
                            source.getCompanyName(),
                            source.getJobTitle(),
                            source.getFitScore(),
                            boundedPriority,
                            urgency,
                            List.copyOf(reasons));
                })
                .sorted(Comparator.comparingInt(AnalysisApplicationPriorityResponse::priorityScore).reversed())
                .limit(5)
                .toList();
    }

    /** 데이터 축적 상태, 반복 부족 역량, 지원 방향 분산, 면접 준비 상태를 리스크로 요약한다. */
    private static List<AnalysisCareerRiskResponse> careerRisks(List<AnalysisSource> sources,
                                                               List<AnalysisSource> analyzed,
                                                               List<SkillGapResponse> skillGaps,
                                                               InterviewTrendResponse interviewTrend) {
        List<AnalysisCareerRiskResponse> risks = new java.util.ArrayList<>();

        if (!sources.isEmpty() && analyzed.size() * 100 / sources.size() < 60) {
            risks.add(new AnalysisCareerRiskResponse(
                    "ANALYSIS_COVERAGE",
                    "HIGH",
                    "적합도 분석 데이터가 부족합니다.",
                    "전체 지원 %d건 중 %d건만 분석되어 장기 경향의 신뢰도가 낮을 수 있습니다."
                            .formatted(sources.size(), analyzed.size()),
                    "미분석 지원 건의 공고 분석과 적합도 분석을 먼저 완료하세요."));
        }

        skillGaps.stream()
                .filter(gap -> gap.total() >= 2 && gap.percentage() >= 50)
                .findFirst()
                .ifPresent(gap -> risks.add(new AnalysisCareerRiskResponse(
                        "REPEATED_SKILL_GAP",
                        gap.percentage() >= 70 ? "HIGH" : "MEDIUM",
                        "%s 부족이 반복되고 있습니다.".formatted(gap.skill()),
                        "최근 분석 %d건 중 %d건(%d%%)에서 같은 부족 역량이 확인됐습니다."
                                .formatted(gap.total(), gap.count(), gap.percentage()),
                        "이번 주 학습 로드맵에서 %s 과제를 최우선으로 완료하세요.".formatted(gap.skill()))));

        long distinctJobs = sources.stream()
                .map(AnalysisSource::getJobTitle)
                .filter(job -> job != null && !job.isBlank())
                .distinct()
                .count();
        if (sources.size() >= 4 && distinctJobs >= 4) {
            risks.add(new AnalysisCareerRiskResponse(
                    "SCATTERED_DIRECTION",
                    "MEDIUM",
                    "지원 직무가 넓게 분산되어 있습니다.",
                    "지원 %d건이 %d개 직무로 나뉘어 반복 강점과 학습 우선순위가 흐려질 수 있습니다."
                            .formatted(sources.size(), distinctJobs),
                    "직무별 준비도 상위 1~2개 직무를 다음 지원의 중심으로 정하세요."));
        }

        if (sources.size() >= 2 && interviewTrend.totalSessions() == 0) {
            risks.add(new AnalysisCareerRiskResponse(
                    "NO_INTERVIEW_PRACTICE",
                    "MEDIUM",
                    "지원 건 대비 모의면접 기록이 없습니다.",
                    "적합도가 높아도 강점 경험을 말로 설명하는 연습이 없으면 면접 전환이 어렵습니다.",
                    "우선순위가 가장 높은 지원 건으로 모의면접을 1회 진행하세요."));
        }

        return risks.stream().limit(4).toList();
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

    /** 기업 분석의 산업 분류를 C의 기업 유형 신호로 읽어 적합도를 비교한다. */
    private static List<AnalysisCompanyTypeResponse> companyTypeFits(List<AnalysisSource> analyzed) {
        return analyzed.stream()
                .filter(source -> source.getFitScore() != null)
                .collect(Collectors.groupingBy(
                        source -> source.getCompanyIndustry() == null || source.getCompanyIndustry().isBlank()
                                ? "미분류 기업"
                                : source.getCompanyIndustry().trim(),
                        LinkedHashMap::new,
                        Collectors.toList()))
                .entrySet().stream()
                .map(entry -> new AnalysisCompanyTypeResponse(
                        entry.getKey(),
                        entry.getValue().size(),
                        (int) Math.round(entry.getValue().stream().mapToInt(AnalysisSource::getFitScore).average().orElse(0))))
                .sorted(Comparator.comparingInt(AnalysisCompanyTypeResponse::averageFitScore).reversed())
                .toList();
    }

    /** improved_answer가 저장된 지원 건을 답변 첨삭 완료 신호로 보고 적합도 차이를 읽기 전용 집계한다. */
    private static AnalysisCorrectionCorrelationResponse correctionCorrelation(List<AnalysisSource> analyzed) {
        List<AnalysisSource> corrected = analyzed.stream()
                .filter(source -> source.getFitScore() != null && source.getCorrectedAnswerCount() > 0)
                .toList();
        List<AnalysisSource> uncorrected = analyzed.stream()
                .filter(source -> source.getFitScore() != null && source.getCorrectedAnswerCount() == 0)
                .toList();
        Integer correctedAverage = averageFitOrNull(corrected);
        Integer uncorrectedAverage = averageFitOrNull(uncorrected);
        Integer delta = correctedAverage == null || uncorrectedAverage == null ? null : correctedAverage - uncorrectedAverage;
        return new AnalysisCorrectionCorrelationResponse(
                corrected.size(), uncorrected.size(), correctedAverage, uncorrectedAverage, delta);
    }

    private static Integer averageFitOrNull(List<AnalysisSource> values) {
        return values.isEmpty() ? null
                : (int) Math.round(values.stream().mapToInt(AnalysisSource::getFitScore).average().orElse(0));
    }

    private static AnalysisWeeklyChangeResponse weeklyChange(AnalysisWeeklyMetricSource source) {
        if (source == null) {
            return new AnalysisWeeklyChangeResponse(null, null, null, "비교할 주간 데이터가 아직 없습니다.");
        }
        Integer fitDelta = delta(source.getCurrentFitAverage(), source.getPreviousFitAverage());
        Integer gapDelta = delta(source.getCurrentGapCount(), source.getPreviousGapCount());
        Integer interviewDelta = delta(source.getCurrentInterviewAverage(), source.getPreviousInterviewAverage());
        String summary = "지난 7일 변화: 적합도 %s · 부족 역량 %s · 면접 점수 %s"
                .formatted(deltaLabel(fitDelta, "점"), deltaLabel(gapDelta, "개"), deltaLabel(interviewDelta, "점"));
        return new AnalysisWeeklyChangeResponse(fitDelta, gapDelta, interviewDelta, summary);
    }

    private static Integer delta(Integer current, Integer previous) {
        return current == null || previous == null ? null : current - previous;
    }

    private static String deltaLabel(Integer delta, String unit) {
        return delta == null ? "비교 데이터 없음" : (delta > 0 ? "+" : "") + delta + unit;
    }

    private static List<String> avoidJobTypes(List<SkillGapResponse> gaps) {
        return gaps.stream()
                .filter(gap -> gap.percentage() >= 50)
                .limit(4)
                .map(gap -> "%s을(를) 필수로 요구하는 공고 — 최근 분석의 %d%%에서 부족"
                        .formatted(gap.skill(), gap.percentage()))
                .toList();
    }

    private static List<String> next24HourActions(
            List<AnalysisApplicationPriorityResponse> priorities,
            List<SkillGapResponse> gaps) {
        java.util.LinkedHashSet<String> actions = new java.util.LinkedHashSet<>();
        priorities.stream().findFirst().ifPresent(priority ->
                actions.add("%s %s 지원 자료를 최종 점검하고 제출 일정을 확정합니다."
                        .formatted(priority.companyName(), priority.jobTitle())));
        gaps.stream().limit(2).forEach(gap ->
                actions.add("%s 60분 실습을 완료하고 결과를 학습 기록에 남깁니다.".formatted(gap.skill())));
        actions.add("현재 프로필과 포트폴리오에 최신 보완 결과가 반영됐는지 확인합니다.");
        return actions.stream().limit(3).toList();
    }

    private static List<AnalysisToneStrategyResponse> toneStrategies(
            AnalysisStatResponse stats,
            List<SkillGapResponse> gaps) {
        String topGap = gaps.isEmpty() ? "핵심 부족 역량" : gaps.get(0).skill();
        return List.of(
                new AnalysisToneStrategyResponse("DIRECT", "냉정한 평가",
                        "평균 적합도 %d점입니다. %s 보완이 반복해서 미뤄지면 지원 우선순위를 낮춰야 합니다."
                                .formatted(stats.averageFitScore(), topGap)),
                new AnalysisToneStrategyResponse("ENCOURAGING", "격려형 평가",
                        "지금까지 쌓인 강점을 유지하면서 %s부터 해결하면 다음 지원의 경쟁력을 높일 수 있습니다."
                                .formatted(topGap)),
                new AnalysisToneStrategyResponse("ACTION", "실행 중심 평가",
                        "24시간 액션 3개를 완료하고, 결과를 프로필에 반영한 뒤 적합도를 다시 확인하세요."));
    }

    private static List<String> threeLineSummary(
            List<AnalysisApplicationPriorityResponse> priorities,
            List<SkillGapResponse> gaps,
            List<String> actions) {
        String best = priorities.isEmpty()
                ? "현재 즉시 지원을 추천할 지원 건은 없습니다."
                : "현재 가장 먼저 검토할 지원 건은 %s %s입니다."
                        .formatted(priorities.get(0).companyName(), priorities.get(0).jobTitle());
        String gap = gaps.isEmpty()
                ? "반복 부족 역량은 아직 확인되지 않았습니다."
                : "가장 반복되는 부족 역량은 %s입니다.".formatted(gaps.get(0).skill());
        String action = actions.isEmpty() ? "이번 주 실행 계획을 등록하세요." : actions.get(0);
        return List.of(best, gap, action);
    }

    private static boolean containsIgnoreCase(List<String> values, String target) {
        return values.stream().anyMatch(value -> value.equalsIgnoreCase(target));
    }
}
