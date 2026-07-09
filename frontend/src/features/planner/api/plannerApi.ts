import { api } from "@/app/lib/api";
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
