package com.careertuner.planner.service;

import java.time.LocalDateTime;

import com.careertuner.planner.dto.PlannerDashboardResponse;
import com.careertuner.planner.dto.PlannerMemoRequest;
import com.careertuner.planner.dto.PlannerMemoResponse;
import com.careertuner.planner.dto.PlannerScheduleItemRequest;
import com.careertuner.planner.dto.PlannerScheduleItemResponse;
import com.careertuner.planner.dto.PlannerStrategyDraftResponse;

public interface PlannerService {

    PlannerDashboardResponse getDashboard(Long userId, LocalDateTime from, LocalDateTime to);

    PlannerMemoResponse createMemo(Long userId, PlannerMemoRequest request);

    PlannerMemoResponse updateMemo(Long userId, Long memoId, PlannerMemoRequest request);

    void deleteMemo(Long userId, Long memoId);

    PlannerScheduleItemResponse createScheduleItem(Long userId, PlannerScheduleItemRequest request);

    PlannerScheduleItemResponse updateScheduleItem(Long userId, Long itemId, PlannerScheduleItemRequest request);

    void deleteScheduleItem(Long userId, Long itemId);

    PlannerStrategyDraftResponse createStrategyDraft(Long userId, Long fitAnalysisId);
}
