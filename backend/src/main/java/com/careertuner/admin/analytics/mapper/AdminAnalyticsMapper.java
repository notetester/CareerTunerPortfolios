package com.careertuner.admin.analytics.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.admin.analytics.domain.AdminAnalysisSource;
import com.careertuner.admin.analytics.domain.AdminCareerAnalysisRun;
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
}
