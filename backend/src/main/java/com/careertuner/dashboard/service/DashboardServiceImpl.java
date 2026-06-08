package com.careertuner.dashboard.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final DashboardMapper dashboardMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary(Long userId) {
        DashboardUserSource user = dashboardMapper.findUserById(userId);
        List<DashboardApplicationSource> applications = dashboardMapper.findApplicationsByUserId(userId);
        List<DashboardApplicationSource> analyzedApplications = applications.stream()
                .filter(application -> application.getFitScore() != null)
                .toList();
        List<DashboardSkillGapResponse> skillGaps = skillGaps(analyzedApplications);
        DashboardStatsResponse stats = stats(user, applications, analyzedApplications, userId);

        return new DashboardSummaryResponse(
                DashboardUserResponse.from(user),
                stats,
                focus(applications),
                applications.stream()
                        .limit(5)
                        .map(application -> DashboardApplicationResponse.of(application, tags(application)))
                        .toList(),
                todos(applications, analyzedApplications, skillGaps),
                dashboardMapper.findRecentActivitiesByUserId(userId).stream()
                        .map(DashboardActivityResponse::from)
                        .toList(),
                skillGaps);
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
