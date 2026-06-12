package com.careertuner.admin.home.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.home.dto.AdminHomeShortcutResponse;
import com.careertuner.admin.home.dto.AdminHomeSummaryResponse;
import com.careertuner.admin.home.mapper.AdminHomeMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminHomeServiceImpl implements AdminHomeService {

    // 운영자 바로가기는 정적이지만, 적합도 분석 운영 동선을 우선 노출한다.
    private static final List<AdminHomeShortcutResponse> SHORTCUTS = List.of(
            new AdminHomeShortcutResponse("운영 종합 대시보드", "/admin/dashboard", "도메인 횡단 현황 카운트"),
            new AdminHomeShortcutResponse("분석 통계", "/admin/analytics", "분석·AI 집계 지표"),
            new AdminHomeShortcutResponse("적합도 분석 운영", "/admin/fit-analysis", "적합도 분석 결과/운영 메모"),
            new AdminHomeShortcutResponse("분석 프롬프트", "/admin/prompts", "적합도·통계 프롬프트 운영"));

    private final AdminHomeMapper adminHomeMapper;

    @Override
    @Transactional(readOnly = true)
    public AdminHomeSummaryResponse getSummary() {
        return new AdminHomeSummaryResponse(
                adminHomeMapper.countFitAnalysisFailures(),
                adminHomeMapper.countUnanalyzedApplications(),
                adminHomeMapper.countNewAnalysesLast7Days(),
                adminHomeMapper.countDegradedLatestAnalyses(),
                adminHomeMapper.countReanalysisRequests(),
                adminHomeMapper.countCareerRunFailures(),
                SHORTCUTS);
    }
}
