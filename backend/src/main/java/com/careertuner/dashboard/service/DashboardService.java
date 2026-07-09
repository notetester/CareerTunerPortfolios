package com.careertuner.dashboard.service;

import java.util.List;

import com.careertuner.dashboard.dto.DashboardSummaryResponse;
import com.careertuner.dashboard.dto.DashboardTodoResponse;

public interface DashboardService {

    /** 조회용. 저장된 요약을 재사용하고, 입력이 바뀐 경우에만 1회 자동 재생성한다(크레딧 미차감). */
    DashboardSummaryResponse getSummary(Long userId);

    /** 사용자가 명시적으로 요청한 재생성. 항상 AI를 실행하고 크레딧을 차감한다. */
    DashboardSummaryResponse refreshSummary(Long userId);

    /** 오늘의 할 일 목록(파생 + 사용자 추가)만 다시 계산한다. AI는 실행하지 않는다. */
    List<DashboardTodoResponse> getTodos(Long userId);

    /** 사용자 할 일 추가 후 전체 목록 반환. */
    List<DashboardTodoResponse> addTodo(Long userId, String task, String time);

    /** 사용자 할 일 완료/해제 후 전체 목록 반환. */
    List<DashboardTodoResponse> updateUserTodo(Long userId, Long todoId, boolean done);

    /** 파생(자동 계산) 할 일 완료/해제 오버라이드 후 전체 목록 반환. */
    List<DashboardTodoResponse> updateDerivedTodo(Long userId, String derivedKey, boolean done, String task, String time);

    /** 사용자 할 일 삭제 후 전체 목록 반환. */
    List<DashboardTodoResponse> deleteTodo(Long userId, Long todoId);
}
