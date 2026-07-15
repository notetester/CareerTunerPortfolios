export type PlannerTab = "schedule" | "memo" | "overlay";

export const PLANNER_SECTION_PATHS: Record<PlannerTab, string> = {
  schedule: "/planner/schedule",
  memo: "/planner/memos",
  overlay: "/planner/overlays",
};

export const PLANNER_EDIT_QUERY_PARAM = "edit";

export function parsePlannerItemId(value: string | null): number | null {
  if (!value || !/^\d+$/.test(value)) return null;
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null;
}

export function plannerEditHref(section: "schedule" | "memo", itemId: number): string {
  if (!Number.isSafeInteger(itemId) || itemId <= 0) {
    throw new Error("플래너 편집 대상 ID는 양의 정수여야 합니다.");
  }
  const search = new URLSearchParams({ [PLANNER_EDIT_QUERY_PARAM]: String(itemId) });
  return `${PLANNER_SECTION_PATHS[section]}?${search.toString()}`;
}

export function findPlannerEditTarget<T extends { id: number }>(items: T[], itemId: number | null): T | null {
  if (itemId == null) return null;
  return items.find((item) => item.id === itemId) ?? null;
}
