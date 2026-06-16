package com.careertuner.home.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.dashboard.dto.DashboardReadinessComponentResponse;
import com.careertuner.dashboard.dto.DashboardSummaryResponse;
import com.careertuner.dashboard.service.DashboardService;
import com.careertuner.home.dto.HomeOnboardingStepResponse;
import com.careertuner.home.dto.HomeSummaryResponse;

import lombok.RequiredArgsConstructor;

/**
 * 홈 요약 서비스. 대시보드 집계(C 동일 담당)를 재사용해 홈에 맞는 가벼운 형태로 투영한다.
 * 별도 매퍼/쿼리를 두지 않아 집계 로직 중복을 피한다.
 */
@Service
@RequiredArgsConstructor
public class HomeServiceImpl implements HomeService {

    private static final int RECENT_APPLICATION_LIMIT = 3;
    private static final int NEXT_ACTION_LIMIT = 4;
    private static final int RECENT_ACTIVITY_LIMIT = 3;

    private final DashboardService dashboardService;

    @Override
    @Transactional
    public HomeSummaryResponse getSummary(Long userId) {
        DashboardSummaryResponse dashboard = dashboardService.getSummary(userId);
        return new HomeSummaryResponse(
                dashboard.user(),
                dashboard.focus(),
                dashboard.aiSummary(),
                dashboard.recentApplications().stream().limit(RECENT_APPLICATION_LIMIT).toList(),
                dashboard.todos().stream().filter(todo -> !todo.done()).limit(NEXT_ACTION_LIMIT).toList(),
                dashboard.activities().stream().limit(RECENT_ACTIVITY_LIMIT).toList(),
                onboardingSteps(dashboard));
    }

    /**
     * 시작 준비(온보딩) 단계. 대시보드 결정적 집계에서 파생하며 추가 쿼리는 쓰지 않는다.
     * 단계는 제품 핵심 흐름(공고 등록 → 적합도 분석 → 학습 실행 → 면접 연습)을 따른다.
     */
    private static List<HomeOnboardingStepResponse> onboardingSteps(DashboardSummaryResponse dashboard) {
        boolean hasApplication = !dashboard.statusCounts().isEmpty();
        boolean analyzed = readinessScore(dashboard, "analysis") > 0;
        boolean learningStarted = readinessScore(dashboard, "learning") > 0;
        boolean interviewed = dashboard.stats().totalInterviews() > 0;
        return List.of(
                new HomeOnboardingStepResponse("signup", "회원가입", true),
                new HomeOnboardingStepResponse("application", "공고(지원 건) 등록", hasApplication),
                new HomeOnboardingStepResponse("fit-analysis", "적합도 분석 실행", analyzed),
                new HomeOnboardingStepResponse("learning", "학습 과제 완료", learningStarted),
                new HomeOnboardingStepResponse("interview", "모의면접 연습", interviewed));
    }

    private static int readinessScore(DashboardSummaryResponse dashboard, String key) {
        return dashboard.readiness().components().stream()
                .filter(component -> key.equals(component.key()))
                .mapToInt(DashboardReadinessComponentResponse::score)
                .findFirst()
                .orElse(0);
    }
}
