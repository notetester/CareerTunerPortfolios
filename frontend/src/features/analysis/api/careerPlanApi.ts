import { api } from "@/app/lib/api";
import type { CareerGoal, CareerPlan, LearningPlan, LearningPlanTask } from "../types/careerPlan";

export function getCareerPlan() {
  return api<CareerPlan>("/analysis/plan");
}

export function updateCareerGoal(request: Omit<CareerGoal, "id" | "updatedAt">) {
  return api<CareerGoal>("/analysis/plan/goal", { method: "PUT", body: JSON.stringify(request) });
}

export function createLearningPlan(request: { title: string; targetSkill: string; startDate: string | null; endDate: string | null }) {
  return api<LearningPlan>("/analysis/plan/learning-plans", { method: "POST", body: JSON.stringify(request) });
}

export function createLearningPlanTask(planId: number, task: string) {
  return api<LearningPlanTask>(`/analysis/plan/learning-plans/${planId}/tasks`, {
    method: "POST",
    body: JSON.stringify({ task }),
  });
}

export function updateLearningPlanTask(planId: number, taskId: number, done: boolean) {
  return api<LearningPlanTask>(`/analysis/plan/learning-plans/${planId}/tasks/${taskId}`, {
    method: "PATCH",
    body: JSON.stringify({ done }),
  });
}
