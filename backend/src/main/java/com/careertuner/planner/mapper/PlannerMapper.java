package com.careertuner.planner.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.planner.domain.PlannerMemo;
import com.careertuner.planner.domain.PlannerScheduleItem;
import com.careertuner.planner.domain.PlannerScheduleReminder;
import com.careertuner.planner.domain.PlannerStrategyAnalysis;

@Mapper
public interface PlannerMapper {

    List<PlannerMemo> findMemos(@Param("userId") Long userId);

    PlannerMemo findMemo(@Param("userId") Long userId, @Param("memoId") Long memoId);

    void insertMemo(PlannerMemo memo);

    int updateMemo(PlannerMemo memo);

    int deleteMemo(@Param("userId") Long userId, @Param("memoId") Long memoId);

    List<PlannerScheduleItem> findScheduleItems(
            @Param("userId") Long userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    PlannerScheduleItem findScheduleItem(@Param("userId") Long userId, @Param("itemId") Long itemId);

    PlannerScheduleItem findScheduleItemByReminderId(@Param("reminderId") Long reminderId);

    void insertScheduleItem(PlannerScheduleItem item);

    int updateScheduleItem(PlannerScheduleItem item);

    int deleteScheduleItem(@Param("userId") Long userId, @Param("itemId") Long itemId);

    List<PlannerScheduleReminder> findRemindersByScheduleItemIds(@Param("itemIds") List<Long> itemIds);

    List<PlannerScheduleReminder> findDueReminders(@Param("now") LocalDateTime now, @Param("limit") int limit);

    void insertReminder(PlannerScheduleReminder reminder);

    int deleteRemindersByItem(@Param("userId") Long userId, @Param("itemId") Long itemId);

    int markReminderSent(@Param("reminderId") Long reminderId);

    int countScheduleOverlaps(
            @Param("userId") Long userId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt,
            @Param("excludeItemId") Long excludeItemId);

    int countApplicationCase(@Param("userId") Long userId, @Param("applicationCaseId") Long applicationCaseId);

    int countFitAnalysis(@Param("userId") Long userId, @Param("fitAnalysisId") Long fitAnalysisId);

    PlannerStrategyAnalysis findStrategyAnalysis(
            @Param("userId") Long userId,
            @Param("fitAnalysisId") Long fitAnalysisId);
}
