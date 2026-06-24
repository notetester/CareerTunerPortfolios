/** 기간 세그(오늘/이번 주/이번 달) → from/to(YYYY-MM-DD) 환산. */

export type PeriodKey = "today" | "week" | "month";

export const PERIOD_TABS: { key: PeriodKey; label: string }[] = [
  { key: "today", label: "오늘" },
  { key: "week", label: "이번 주" },
  { key: "month", label: "이번 달" },
];

function fmt(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

/**
 * 오늘 = today~today, 이번 주 = 최근 7일(오늘 포함), 이번 달 = 이번 달 1일~오늘.
 */
export function rangeOf(key: PeriodKey): { from: string; to: string } {
  const now = new Date();
  const to = fmt(now);
  if (key === "today") return { from: to, to };
  if (key === "week") {
    const start = new Date(now);
    start.setDate(now.getDate() - 6);
    return { from: fmt(start), to };
  }
  // month
  const first = new Date(now.getFullYear(), now.getMonth(), 1);
  return { from: fmt(first), to };
}
