package com.careertuner.analysis.service;

import com.careertuner.analysis.dto.CareerGoalRequest;
import com.careertuner.analysis.dto.CareerGoalResponse;
import com.careertuner.analysis.dto.CareerPlanResponse;
import com.careertuner.analysis.dto.LearningPlanRequest;
import com.careertuner.analysis.dto.LearningPlanResponse;
import com.careertuner.analysis.dto.LearningPlanTaskRequest;
import com.careertuner.analysis.dto.LearningPlanTaskResponse;

public interface CareerPlanService {
    CareerPlanResponse get(Long userId);
    CareerGoalResponse updateGoal(Long userId, CareerGoalRequest request);
    LearningPlanResponse createPlan(Long userId, LearningPlanRequest request);
    LearningPlanTaskResponse createTask(Long userId, Long planId, LearningPlanTaskRequest request);
    LearningPlanTaskResponse updateTask(Long userId, Long planId, Long taskId, boolean done);
}
