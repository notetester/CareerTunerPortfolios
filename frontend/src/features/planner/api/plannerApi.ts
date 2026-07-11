import { api } from "@/app/lib/api";
import { apiBase } from "@/app/lib/apiBase";
import { getAccessToken } from "@/app/lib/tokenStore";
import type {
  PlannerDashboard,
  PlannerMemo,
  PlannerMemoRequest,
  PlannerScheduleItem,
  PlannerScheduleItemRequest,
  PlannerStrategyDraft,
} from "../types/planner";

export function getPlannerDashboard(from?: string, to?: string) {
  const params = new URLSearchParams();
  if (from) params.set("from", from);
  if (to) params.set("to", to);
  const query = params.toString();
  return api<PlannerDashboard>(`/planner${query ? `?${query}` : ""}`);
}

export function createPlannerMemo(request: PlannerMemoRequest) {
  return api<PlannerMemo>("/planner/memos", {
    method: "POST",
    body: JSON.stringify(request),
  });
}

export function updatePlannerMemo(id: number, request: PlannerMemoRequest) {
  return api<PlannerMemo>(`/planner/memos/${id}`, {
    method: "PUT",
    body: JSON.stringify(request),
  });
}

export function deletePlannerMemo(id: number) {
  return api<void>(`/planner/memos/${id}`, { method: "DELETE" });
}

export function createPlannerScheduleItem(request: PlannerScheduleItemRequest) {
  return api<PlannerScheduleItem>("/planner/schedule", {
    method: "POST",
    body: JSON.stringify(request),
  });
}

export function updatePlannerScheduleItem(id: number, request: PlannerScheduleItemRequest) {
  return api<PlannerScheduleItem>(`/planner/schedule/${id}`, {
    method: "PUT",
    body: JSON.stringify(request),
  });
}

export function deletePlannerScheduleItem(id: number) {
  return api<void>(`/planner/schedule/${id}`, { method: "DELETE" });
}

export function createStrategyScheduleDraft(fitAnalysisId: number) {
  return api<PlannerStrategyDraft>(`/planner/strategy-drafts/fit-analyses/${fitAnalysisId}`, {
    method: "POST",
  });
}

/** 플래너 일정을 .ics 파일로 내려받는다(구글/애플/아웃룩 캘린더에 가져오기). 인증 헤더가 필요해 blob 다운로드로 처리. */
export async function exportPlannerIcs(): Promise<void> {
  const token = getAccessToken();
  const response = await fetch(`${apiBase()}/planner/export.ics`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  if (!response.ok) {
    throw new Error("캘린더 내보내기에 실패했습니다.");
  }
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = "careertuner-planner.ics";
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}
