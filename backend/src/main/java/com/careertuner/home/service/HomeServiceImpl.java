package com.careertuner.home.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.dashboard.dto.DashboardSummaryResponse;
import com.careertuner.dashboard.service.DashboardService;
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
                dashboard.activities().stream().limit(RECENT_ACTIVITY_LIMIT).toList());
    }
}
