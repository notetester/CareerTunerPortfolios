package com.careertuner.analysis.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.analysis.domain.CareerGoal;
import com.careertuner.analysis.domain.LearningPlan;
import com.careertuner.analysis.domain.LearningPlanTask;

@Mapper
public interface CareerPlanMapper {
    CareerGoal findGoal(Long userId);
    void upsertGoal(CareerGoal goal);
    List<LearningPlan> findPlans(Long userId);
    LearningPlan findPlan(@Param("userId") Long userId, @Param("planId") Long planId);
    void insertPlan(LearningPlan plan);
    List<LearningPlanTask> findTasks(Long planId);
    void insertTask(LearningPlanTask task);
    int updateTaskDone(@Param("userId") Long userId, @Param("planId") Long planId,
                       @Param("taskId") Long taskId, @Param("done") boolean done);
    LearningPlanTask findTask(@Param("planId") Long planId, @Param("taskId") Long taskId);
}
