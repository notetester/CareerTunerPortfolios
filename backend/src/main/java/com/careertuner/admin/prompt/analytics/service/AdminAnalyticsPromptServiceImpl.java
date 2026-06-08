package com.careertuner.admin.prompt.analytics.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import com.careertuner.admin.prompt.analytics.dto.AdminAnalyticsPromptResponse;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;

@Service
public class AdminAnalyticsPromptServiceImpl implements AdminAnalyticsPromptService {

    private static final List<AdminAnalyticsPromptResponse> TEMPLATES = List.of(
            new AdminAnalyticsPromptResponse(
                    "LONG_TERM_TREND_SUMMARY",
                    "장기 취업 경향 요약",
                    "v0.1",
                    "DRAFT",
                    "여러 지원 건의 적합도 변화, 직무 패턴, 반복 부족 역량을 요약한다.",
                    List.of("applicationSummaries", "scoreHistory", "skillGaps", "jobReadiness"),
                    List.of("trendSummary", "riskSignals", "recommendedDirections"),
                    List.of("반복 부족 역량과 추천 방향이 같은 근거를 공유하는지 확인", "지원 직무가 적은 경우 과도한 일반화를 피하는지 확인", "점수 변화 해석에 기간과 표본 수를 함께 표시하는지 확인"),
                    List.of("소수 지원 건으로 장기 경향을 확정하지 않음", "특정 기업 합격 가능성을 보장하지 않음"),
                    LocalDate.of(2026, 6, 8)),
            new AdminAnalyticsPromptResponse(
                    "DASHBOARD_ACTION_SUMMARY",
                    "대시보드 다음 액션 요약",
                    "v0.1",
                    "DRAFT",
                    "홈과 대시보드에서 바로 읽을 수 있는 오늘의 준비 상태와 다음 행동을 생성한다.",
                    List.of("recentApplications", "fitScores", "todoCandidates", "creditStatus"),
                    List.of("headline", "description", "todos", "focusApplication"),
                    List.of("오늘 할 일은 3~6개로 제한되는지 확인", "가장 높은 우선순위 지원 건이 명확한지 확인", "크레딧/면접/학습 액션이 섞여도 문맥이 자연스러운지 확인"),
                    List.of("긴 설명보다 실행 가능한 행동을 우선", "사용자에게 보이지 않아야 할 운영 정보를 포함하지 않음"),
                    LocalDate.of(2026, 6, 8)),
            new AdminAnalyticsPromptResponse(
                    "JOB_READINESS_GROUPING",
                    "직무별 준비도 해석",
                    "v0.1",
                    "DRAFT",
                    "지원 건을 직무군별로 묶어 준비도와 지원 방향을 해석한다.",
                    List.of("jobTitle", "fitScore", "applicationStatus", "missingSkills"),
                    List.of("jobReadiness", "trend", "careerFocus"),
                    List.of("직무명이 다르지만 같은 직무군인 경우 해석이 일관적인지 확인", "낮은 준비도에도 보완 방향이 포함되는지 확인", "상태값 READY/APPLIED가 준비도 설명에 반영되는지 확인"),
                    List.of("직무 전환을 부정적으로 단정하지 않음", "단일 점수만으로 진로 변경을 권하지 않음"),
                    LocalDate.of(2026, 6, 8)));

    @Override
    public List<AdminAnalyticsPromptResponse> list() {
        return TEMPLATES;
    }

    @Override
    public AdminAnalyticsPromptResponse get(String key) {
        return TEMPLATES.stream()
                .filter(template -> template.key().equalsIgnoreCase(key))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "분석 프롬프트를 찾을 수 없습니다."));
    }
}
