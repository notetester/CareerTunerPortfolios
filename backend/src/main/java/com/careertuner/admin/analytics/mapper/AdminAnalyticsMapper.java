package com.careertuner.admin.analytics.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.admin.analytics.domain.AdminAnalysisFailureSource;
import com.careertuner.admin.analytics.domain.AdminAnalysisSource;
import com.careertuner.admin.analytics.domain.AdminCareerAnalysisRun;
import com.careertuner.admin.analytics.domain.AdminCareerRunMemo;
import com.careertuner.admin.analytics.domain.AdminCountSource;
import com.careertuner.admin.analytics.domain.AdminDailyUsageSource;
import com.careertuner.admin.analytics.domain.AdminUserTimelineSource;
import com.careertuner.admin.analytics.domain.AdminPromptPerformanceSource;

@Mapper
public interface AdminAnalyticsMapper {

    int countUsers();

    int countActiveUsers();

    int countApplications();

    int countInterviews();

    int sumCreditsUsedThisMonth();

    List<AdminCountSource> countUsersByPlan();

    List<AdminCountSource> countApplicationsByStatus();

    List<AdminAnalysisSource> findLatestAnalyses();

    /** 분석 실패 큐: fit_analysis + career_analysis_run 의 비정상 결과 최신순 100건. */
    List<AdminAnalysisFailureSource> findAnalysisFailures();

    void upsertQualityFlag(@Param("targetType") String targetType,
                           @Param("targetId") Long targetId,
                           @Param("flagType") String flagType,
                           @Param("severity") String severity,
                           @Param("memo") String memo);

    boolean isQualityFlagResolved(@Param("targetType") String targetType,
                                  @Param("targetId") Long targetId,
                                  @Param("flagType") String flagType);

    int resolveQualityFlag(@Param("targetType") String targetType,
                           @Param("targetId") Long targetId,
                           @Param("flagType") String flagType);

    List<AdminDailyUsageSource> findDailyUsage();

    List<AdminCareerAnalysisRun> findCareerAnalysisRuns(@Param("userId") Long userId);

    List<AdminUserTimelineSource> findUserTimeline(Long userId);

    List<AdminPromptPerformanceSource> findPromptPerformance();

    // ── 실행 이력 운영 메모 (career_analysis_run 단위) ──
    AdminCareerAnalysisRun findCareerAnalysisRunById(@Param("runId") Long runId);

    List<AdminCareerRunMemo> findMemosByRunId(@Param("runId") Long runId);

    AdminCareerRunMemo findMemoByIdAndRunId(@Param("id") Long id, @Param("runId") Long runId);

    void insertMemo(AdminCareerRunMemo memo);

    int updateMemo(AdminCareerRunMemo memo);

    int deleteMemo(@Param("id") Long id, @Param("runId") Long runId);
}
