package com.careertuner.admin.analytics.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.admin.analytics.domain.AdminAnalysisSource;
import com.careertuner.admin.analytics.domain.AdminCareerAnalysisRun;
import com.careertuner.admin.analytics.domain.AdminCareerRunMemo;
import com.careertuner.admin.analytics.domain.AdminCountSource;
import com.careertuner.admin.analytics.domain.AdminDailyUsageSource;

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

    List<AdminDailyUsageSource> findDailyUsage();

    List<AdminCareerAnalysisRun> findCareerAnalysisRuns(@Param("userId") Long userId);

    // ── 실행 이력 운영 메모 (career_analysis_run 단위) ──
    AdminCareerAnalysisRun findCareerAnalysisRunById(@Param("runId") Long runId);

    List<AdminCareerRunMemo> findMemosByRunId(@Param("runId") Long runId);

    AdminCareerRunMemo findMemoByIdAndRunId(@Param("id") Long id, @Param("runId") Long runId);

    void insertMemo(AdminCareerRunMemo memo);

    int updateMemo(AdminCareerRunMemo memo);

    int deleteMemo(@Param("id") Long id, @Param("runId") Long runId);
}
