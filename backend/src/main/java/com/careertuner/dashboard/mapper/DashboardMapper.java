package com.careertuner.dashboard.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.dashboard.domain.DashboardActivitySource;
import com.careertuner.dashboard.domain.DashboardApplicationSource;
import com.careertuner.dashboard.domain.DashboardFitPointSource;
import com.careertuner.dashboard.domain.DashboardInterviewSource;
import com.careertuner.dashboard.domain.DashboardLearningProgressSource;
import com.careertuner.dashboard.domain.DashboardNotificationSource;
import com.careertuner.dashboard.domain.DashboardTodo;
import com.careertuner.dashboard.domain.DashboardUserSource;

@Mapper
public interface DashboardMapper {

    DashboardUserSource findUserById(Long userId);

    List<DashboardApplicationSource> findApplicationsByUserId(Long userId);

    List<DashboardActivitySource> findRecentActivitiesByUserId(Long userId);

    int countInterviewsThisWeek(Long userId);

    int sumCreditsUsedThisMonth(Long userId);

    /** 최근 면접 카드: 점수가 기록된 최근 세션 2건(최신 + 직전, 점수 변화 계산용). D 테이블 읽기 전용. */
    List<DashboardInterviewSource> findRecentScoredInterviews(Long userId);

    /** 최근 면접 세션에서 점수가 가장 낮은 답변의 피드백(핵심 개선점). D 테이블 읽기 전용. */
    String findWorstAnswerFeedbackBySessionId(Long sessionId);

    /** 최근 알림 3건. F 테이블 읽기 전용(PRODUCT_STRUCTURE가 대시보드 참조 대상으로 명시). */
    List<DashboardNotificationSource> findRecentNotifications(Long userId);

    /** 최근 변화 요약용 적합도 분석 전체 이력(지원 건·생성 순). fit_analysis는 C 소유. */
    List<DashboardFitPointSource> findFitScoreHistoryByUserId(Long userId);

    /** 준비도 게이지용 학습 로드맵 진행 현황(지원 건별 최신 분석의 체크리스트 기준). */
    DashboardLearningProgressSource findLearningProgressByUserId(Long userId);

    List<DashboardTodo> findTodosByUserId(Long userId);

    int insertTodo(DashboardTodo todo);

    int updateTodoDone(@Param("userId") Long userId, @Param("todoId") Long todoId, @Param("done") boolean done);

    /** 파생 할 일 완료 오버라이드 upsert (user_id + derived_key 유니크). */
    int upsertDerivedTodo(DashboardTodo todo);

    int deleteTodo(@Param("userId") Long userId, @Param("todoId") Long todoId);
}
