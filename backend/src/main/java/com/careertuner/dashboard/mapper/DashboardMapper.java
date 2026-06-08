package com.careertuner.dashboard.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.careertuner.dashboard.domain.DashboardActivitySource;
import com.careertuner.dashboard.domain.DashboardApplicationSource;
import com.careertuner.dashboard.domain.DashboardUserSource;

@Mapper
public interface DashboardMapper {

    DashboardUserSource findUserById(Long userId);

    List<DashboardApplicationSource> findApplicationsByUserId(Long userId);

    List<DashboardActivitySource> findRecentActivitiesByUserId(Long userId);

    int countInterviewsThisWeek(Long userId);

    int sumCreditsUsedThisMonth(Long userId);
}
