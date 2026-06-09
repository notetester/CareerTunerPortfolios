package com.careertuner.dashboard.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.analysis.domain.CareerAnalysisRun;
import com.careertuner.analysis.dto.CareerAnalysisRunResponse;
import com.careertuner.analysis.service.CareerAnalysisRunService;
import com.careertuner.dashboard.ai.DashboardInsightAiCommand;
import com.careertuner.dashboard.ai.DashboardInsightAiResult;
import com.careertuner.dashboard.ai.DashboardInsightAiService;
import com.careertuner.dashboard.domain.DashboardApplicationSource;
import com.careertuner.dashboard.domain.DashboardUserSource;
import com.careertuner.dashboard.dto.DashboardActivityResponse;
import com.careertuner.dashboard.dto.DashboardApplicationResponse;
import com.careertuner.dashboard.dto.DashboardFocusResponse;
import com.careertuner.dashboard.dto.DashboardSkillGapResponse;
import com.careertuner.dashboard.dto.DashboardStatsResponse;
import com.careertuner.dashboard.dto.DashboardSummaryResponse;
import com.careertuner.dashboard.dto.DashboardTodoResponse;
import com.careertuner.dashboard.dto.DashboardUserResponse;
import com.careertuner.dashboard.mapper.DashboardMapper;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final String ANALYSIS_TYPE = "DASHBOARD_SUMMARY";
    // 명시적 재생성 시 적합도 분석과 동일하게 ai_usage_log에 차감을 남긴다.
    private static final int EXPLICIT_REFRESH_CREDIT = 1;

    private final DashboardMapper dashboardMapper;
    private final DashboardInsightAiService dashboardInsightAiService;
    private final CareerAnalysisRunService careerAnalysisRunService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public DashboardSummaryResponse getSummary(Long userId) {
        return buildSummary(userId, false);
    }

    @Override
    @Transactional
    public DashboardSummaryResponse refreshSummary(Long userId) {
        return buildSummary(userId, true);
    }

    /**
     * 결정적 집계는 항상 새로 계산하고(토큰 비용 없음), 비용이 드는 대시보드 AI 요약(18)만 캐시한다.
     * forceRefresh=false 이면 같은 입력의 저장 요약을 재사용(AI 미실행)하고,
     * 입력이 바뀐 경우에만 1회 자동 재생성한다. forceRefresh=true 이면 항상 재실행 + 크레딧 차감.
     */
    private DashboardSummaryResponse buildSummary(Long userId, boolean forceRefresh) {
        DashboardUserSource user = dashboardMapper.findUserById(userId);
        List<DashboardApplicationSource> applications = dashboardMapper.findApplicationsByUserId(userId);
        List<DashboardApplicationSource> analyzedApplications = applications.stream()
                .filter(application -> application.getFitScore() != null)
                .toList();
        List<DashboardSkillGapResponse> skillGaps = skillGaps(analyzedApplications);
        DashboardStatsResponse stats = stats(user, applications, analyzedApplications, userId);
        DashboardFocusResponse focus = focus(applications);

        // 대시보드 AI 요약(18) 입력. 키 주입 시 실 AI로 교체된다.
        DashboardInsightAiCommand command = new DashboardInsightAiCommand(stats, focus, skillGaps);
        String fingerprint = CareerAnalysisRunService.fingerprint(canonical(stats, focus, skillGaps));

        String summary;
        CareerAnalysisRunResponse run;
        Optional<CareerAnalysisRun> cached = forceRefresh
                ? Optional.empty()
                : careerAnalysisRunService.findFreshRun(userId, ANALYSIS_TYPE, fingerprint);
        if (cached.isPresent()) {
            summary = parseSummary(cached.get().getResult());
            run = CareerAnalysisRunResponse.from(cached.get());
        } else {
            DashboardInsightAiResult insight = dashboardInsightAiService.summarize(command);
            int creditUsed = forceRefresh && "SUCCESS".equals(insight.status()) ? EXPLICIT_REFRESH_CREDIT : 0;
            summary = insight.summary();
            run = careerAnalysisRunService.record(
                    userId,
                    ANALYSIS_TYPE,
                    fingerprint,
                    command,
                    Map.of("summary", insight.summary()),
                    insight.usage(),
                    insight.status(),
                    insight.errorMessage(),
                    insight.retryable(),
                    creditUsed);
        }

        return new DashboardSummaryResponse(
                DashboardUserResponse.from(user),
                stats,
                focus,
                applications.stream()
                        .limit(5)
                        .map(application -> DashboardApplicationResponse.of(application, tags(application)))
                        .toList(),
                todos(applications, analyzedApplications, skillGaps),
                dashboardMapper.findRecentActivitiesByUserId(userId).stream()
                        .map(DashboardActivityResponse::from)
                        .toList(),
                skillGaps,
                summary,
                run);
    }

    /**
     * 대시보드 요약 캐시 키: 요약 문구를 실제로 좌우하는 안정 필드만 사용한다.
     * 크레딧 잔액·이번 달 사용량처럼 요약 내용과 무관한 값은 제외해 불필요한 재생성을 막는다.
     */
    private String canonical(DashboardStatsResponse stats,
                             DashboardFocusResponse focus,
                             List<DashboardSkillGapResponse> skillGaps) {
        String gaps = skillGaps.stream()
                .map(gap -> gap.skill() + ":" + gap.count())
                .collect(Collectors.joining(","));
        return String.join("|",
                String.valueOf(stats.activeApplications()),
                String.valueOf(stats.averageFitScore()),
                String.valueOf(stats.interviewsThisWeek()),
                focus.headline() == null ? "" : focus.headline(),
                gaps);
    }

    private String parseSummary(String resultJson) {
        if (resultJson == null || resultJson.isBlank()) {
            return "";
        }
        try {
            return objectMapper.readTree(resultJson).path("summary").asText("");
        } catch (Exception ex) {
            return "";
        }
    }

    private DashboardStatsResponse stats(DashboardUserSource user,
                                         List<DashboardApplicationSource> applications,
                                         List<DashboardApplicationSource> analyzedApplications,
                                         Long userId) {
        LocalDate firstDayOfMonth = LocalDate.now().withDayOfMonth(1);
        int activeApplications = (int) applications.stream()
                .filter(application -> !"CLOSED".equals(application.getStatus()))
                .count();
        int newApplicationsThisMonth = (int) applications.stream()
                .filter(application -> application.getCreatedAt() != null)
                .filter(application -> !application.getCreatedAt().toLocalDate().isBefore(firstDayOfMonth))
                .count();
        int totalInterviews = applications.stream().mapToInt(DashboardApplicationSource::getInterviewCount).sum();
        int averageFitScore = (int) Math.round(analyzedApplications.stream()
                .map(DashboardApplicationSource::getFitScore)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0));

        return new DashboardStatsResponse(
                activeApplications,
                newApplicationsThisMonth,
                totalInterviews,
                dashboardMapper.countInterviewsThisWeek(userId),
                user.getCredit(),
                creditLimit(user.getPlan()),
                dashboardMapper.sumCreditsUsedThisMonth(userId),
                averageFitScore);
    }

    private static DashboardFocusResponse focus(List<DashboardApplicationSource> applications) {
        return applications.stream()
                .filter(application -> application.getFitScore() != null)
                .max(Comparator.comparing(DashboardApplicationSource::getFitScore))
                .map(application -> new DashboardFocusResponse(
                        "%s %s 준비가 %d%% 완료됐습니다."
                                .formatted(application.getCompanyName(), application.getJobTitle(), application.getFitScore()),
                        "가장 점수가 높은 지원 건을 우선 점검하고, 부족 역량 보완과 모의면접을 이어가세요.",
                        application.getFitScore()))
                .orElseGet(() -> new DashboardFocusResponse(
                        "첫 지원 건을 등록하고 AI 분석을 시작해보세요.",
                        "공고를 등록하면 적합도, 부족 역량, 다음 행동이 대시보드에 자동으로 정리됩니다.",
                        null));
    }

    private List<DashboardSkillGapResponse> skillGaps(List<DashboardApplicationSource> analyzedApplications) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (DashboardApplicationSource application : analyzedApplications) {
            for (String skill : parseList(application.getMissingSkills())) {
                counts.merge(skill, 1, Integer::sum);
            }
        }

        int total = analyzedApplications.size();
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(5)
                .map(entry -> new DashboardSkillGapResponse(entry.getKey(), entry.getValue(), total, percentage(entry.getValue(), total)))
                .toList();
    }

    private List<DashboardTodoResponse> todos(List<DashboardApplicationSource> applications,
                                              List<DashboardApplicationSource> analyzedApplications,
                                              List<DashboardSkillGapResponse> skillGaps) {
        List<DashboardTodoResponse> todos = new ArrayList<>();
        todos.add(new DashboardTodoResponse(!applications.isEmpty(), "지원 건 1개 이상 등록", "오늘"));
        todos.add(new DashboardTodoResponse(!analyzedApplications.isEmpty(), "AI 적합도 분석 결과 확인", "오늘"));

        skillGaps.stream()
                .findFirst()
                .ifPresentOrElse(
                        gap -> todos.add(new DashboardTodoResponse(false, "%s 보완 학습 시작".formatted(gap.skill()), "이번 주")),
                        () -> todos.add(new DashboardTodoResponse(false, "관심 공고 요구 역량 3개 정리", "이번 주")));

        applications.stream()
                .filter(application -> application.getInterviewCount() == 0)
                .findFirst()
                .ifPresentOrElse(
                        application -> todos.add(new DashboardTodoResponse(false, "%s 모의면접 1회 진행".formatted(application.getCompanyName()), "이번 주")),
                        () -> todos.add(new DashboardTodoResponse(true, "등록된 지원 건 모의면접 시작", "이번 주")));

        applications.stream()
                .filter(application -> application.getFitScore() != null && application.getFitScore() >= 70)
                .findFirst()
                .ifPresentOrElse(
                        application -> todos.add(new DashboardTodoResponse(false, "%s 지원 서류 최종 점검".formatted(application.getCompanyName()), "이번 주")),
                        () -> todos.add(new DashboardTodoResponse(false, "70점 이상 적합도 후보 만들기", "이번 달")));

        return todos.stream().limit(6).toList();
    }

    private List<String> tags(DashboardApplicationSource application) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        tags.addAll(parseList(application.getRequiredSkills()));
        tags.addAll(parseList(application.getMissingSkills()));
        if (tags.isEmpty()) {
            tags.add(application.getStatus());
        }
        return tags.stream().limit(3).toList();
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

    private static int creditLimit(String plan) {
        return switch (plan) {
            case "BASIC" -> 200;
            case "PRO" -> 500;
            case "PREMIUM" -> 1000;
            default -> 50;
        };
    }

    private static int percentage(int count, int total) {
        if (total == 0) {
            return 0;
        }
        return (int) Math.round((count * 100.0) / total);
    }
}
