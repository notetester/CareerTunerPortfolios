package com.careertuner.dashboard.dto;

import com.careertuner.dashboard.domain.DashboardTodo;

/**
 * 오늘의 할 일 항목. source=DERIVED는 지원 현황에서 자동 계산된 항목(derivedKey로 완료 처리),
 * source=USER는 사용자가 직접 추가한 항목(id로 완료/삭제 처리)이다.
 */
public record DashboardTodoResponse(
        Long id,
        String derivedKey,
        String source,
        boolean done,
        String task,
        String time
) {

    public static DashboardTodoResponse derived(String derivedKey, boolean done, String task, String time) {
        return new DashboardTodoResponse(null, derivedKey, "DERIVED", done, task, time);
    }

    public static DashboardTodoResponse user(DashboardTodo todo) {
        return new DashboardTodoResponse(todo.getId(), null, "USER", todo.isDone(), todo.getTask(), todo.getTimeLabel());
    }
}
