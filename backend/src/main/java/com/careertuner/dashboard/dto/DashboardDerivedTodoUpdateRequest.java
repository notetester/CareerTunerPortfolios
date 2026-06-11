package com.careertuner.dashboard.dto;

/**
 * 파생(자동 계산) 할 일의 완료 처리. task/time은 체크 시점의 문구 스냅샷으로 저장한다.
 */
public record DashboardDerivedTodoUpdateRequest(String derivedKey, boolean done, String task, String time) {
}
