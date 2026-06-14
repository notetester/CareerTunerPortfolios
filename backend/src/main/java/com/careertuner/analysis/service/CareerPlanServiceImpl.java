package com.careertuner.analysis.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.analysis.domain.CareerGoal;
import com.careertuner.analysis.domain.LearningPlan;
import com.careertuner.analysis.domain.LearningPlanTask;
import com.careertuner.analysis.dto.CareerGoalRequest;
import com.careertuner.analysis.dto.CareerGoalResponse;
import com.careertuner.analysis.dto.CareerPlanResponse;
import com.careertuner.analysis.dto.LearningPlanRequest;
import com.careertuner.analysis.dto.LearningPlanResponse;
import com.careertuner.analysis.dto.LearningPlanTaskRequest;
import com.careertuner.analysis.dto.LearningPlanTaskResponse;
import com.careertuner.analysis.mapper.CareerPlanMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CareerPlanServiceImpl implements CareerPlanService {

    private final CareerPlanMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public CareerPlanResponse get(Long userId) {
        return new CareerPlanResponse(
                CareerGoalResponse.from(mapper.findGoal(userId)),
                mapper.findPlans(userId).stream().map(this::response).toList());
    }

    @Override
    @Transactional
    public CareerGoalResponse updateGoal(Long userId, CareerGoalRequest request) {
        CareerGoal goal = CareerGoal.builder()
                .userId(userId)
                .targetJob(trim(request.targetJob()))
                .targetPeriod(trim(request.targetPeriod()))
                .prioritySkill(trim(request.prioritySkill()))
                .preferredCompanyType(trim(request.preferredCompanyType()))
                .build();
        mapper.upsertGoal(goal);
        return CareerGoalResponse.from(mapper.findGoal(userId));
    }

    @Override
    @Transactional
    public LearningPlanResponse createPlan(Long userId, LearningPlanRequest request) {
        if (request.startDate() != null && request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "학습 계획 종료일은 시작일보다 빠를 수 없습니다.");
        }
        LearningPlan plan = LearningPlan.builder()
                .userId(userId)
                .title(request.title().trim())
                .targetSkill(request.targetSkill().trim())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .status("ACTIVE")
                .build();
        mapper.insertPlan(plan);
        return response(plan);
    }

    @Override
    @Transactional
    public LearningPlanTaskResponse createTask(Long userId, Long planId, LearningPlanTaskRequest request) {
        ensurePlan(userId, planId);
        LearningPlanTask task = LearningPlanTask.builder()
                .learningPlanId(planId)
                .task(request.task().trim())
                .sortOrder(request.sortOrder() == null ? mapper.findTasks(planId).size() + 1 : request.sortOrder())
                .build();
        mapper.insertTask(task);
        return LearningPlanTaskResponse.from(mapper.findTask(planId, task.getId()));
    }

    @Override
    @Transactional
    public LearningPlanTaskResponse updateTask(Long userId, Long planId, Long taskId, boolean done) {
        if (mapper.updateTaskDone(userId, planId, taskId, done) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "학습 계획 과제를 찾을 수 없습니다.");
        }
        return LearningPlanTaskResponse.from(mapper.findTask(planId, taskId));
    }

    private LearningPlanResponse response(LearningPlan plan) {
        List<LearningPlanTaskResponse> tasks = mapper.findTasks(plan.getId()).stream()
                .map(LearningPlanTaskResponse::from)
                .toList();
        return LearningPlanResponse.of(plan, tasks);
    }

    private void ensurePlan(Long userId, Long planId) {
        if (mapper.findPlan(userId, planId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "학습 계획을 찾을 수 없습니다.");
        }
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
