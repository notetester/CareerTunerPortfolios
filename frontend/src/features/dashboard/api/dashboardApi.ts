import { api } from "@/app/lib/api";
import { runWithAiCharge } from "@/features/billing/api/aiChargePreviewApi";
import type { DashboardSummary, DashboardTodo } from "../types/dashboardSummary";

export function getDashboardSummary() {
  return api<DashboardSummary>("/dashboard/summary");
}

/** 사용자가 명시적으로 요청한 대시보드 요약 재생성. AI를 강제 실행하며 크레딧이 차감된다. */
export function refreshDashboardSummary() {
  return runWithAiCharge("DASHBOARD_SUMMARY", (headers) =>
    api<DashboardSummary>("/dashboard/summary/refresh", { method: "POST", headers }));
}

// ----- 오늘의 할 일 (완료 처리/추가/삭제 — 디자인 분석 §6.4). 모든 호출이 갱신된 전체 목록을 반환한다. -----

export function addDashboardTodo(task: string, time: string) {
  return api<DashboardTodo[]>("/dashboard/todos", {
    method: "POST",
    body: JSON.stringify({ task, time }),
  });
}

export function updateDashboardTodo(todoId: number, done: boolean) {
  return api<DashboardTodo[]>(`/dashboard/todos/${todoId}`, {
    method: "PATCH",
    body: JSON.stringify({ done }),
  });
}

/** 파생(자동 계산) 할 일의 완료 처리. 체크 시점 문구를 스냅샷으로 함께 보낸다. */
export function updateDerivedDashboardTodo(todo: Pick<DashboardTodo, "derivedKey" | "task" | "time">, done: boolean) {
  return api<DashboardTodo[]>("/dashboard/todos/derived", {
    method: "PATCH",
    body: JSON.stringify({ derivedKey: todo.derivedKey, done, task: todo.task, time: todo.time }),
  });
}

export function deleteDashboardTodo(todoId: number) {
  return api<DashboardTodo[]>(`/dashboard/todos/${todoId}`, { method: "DELETE" });
}
