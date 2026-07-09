import { useState } from "react";
import { Link } from "react-router";
import { AlertTriangle, CalendarPlus, CheckCircle2, Loader2 } from "lucide-react";
import { Button } from "@/app/components/ui/button";
import { Badge } from "@/app/components/ui/badge";
import { toast } from "@/features/notification/components/toast";
import { createPlannerScheduleItem, createStrategyScheduleDraft } from "../api/plannerApi";
import type { PlannerScheduleItemRequest, PlannerStrategyDraft, PlannerStrategyDraftItem } from "../types/planner";

export function StrategyScheduleButton({ fitAnalysisId }: { fitAnalysisId: number }) {
  const [draft, setDraft] = useState<PlannerStrategyDraft | null>(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [savedRefs, setSavedRefs] = useState<Set<string>>(new Set());
  const [error, setError] = useState<string | null>(null);

  const loadDraft = async () => {
    setLoading(true);
    setError(null);
    try {
      setDraft(await createStrategyScheduleDraft(fitAnalysisId));
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "전략 일정 초안을 만들지 못했습니다.");
    } finally {
      setLoading(false);
    }
  };

  const addItem = async (item: PlannerStrategyDraftItem) => {
    setSaving(true);
    try {
      await createPlannerScheduleItem(toRequest(item));
      setSavedRefs((current) => new Set(current).add(item.sourceRef ?? item.title));
      toast.success("전략 일정을 플래너에 추가했습니다.");
    } catch (requestError) {
      toast.error(requestError instanceof Error ? requestError.message : "전략 일정을 추가하지 못했습니다.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="rounded-lg border border-blue-100 bg-blue-50/60 p-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="text-sm font-bold text-blue-900">전략 일정을 플래너에 추가</div>
        <Button variant="outline" className="h-8 bg-card text-blue-700" onClick={() => void loadDraft()} disabled={loading}>
          {loading ? <Loader2 className="size-3.5 animate-spin" /> : <CalendarPlus className="size-3.5" />}
          최신 조건으로 초안 만들기
        </Button>
      </div>
      {error && <div className="mt-2 text-xs text-red-600">{error}</div>}
      {draft && (
        <div className="mt-3 space-y-2">
          {draft.staleReasons.length > 0 && (
            <div className="rounded-md border border-amber-200 bg-amber-50 p-2 text-xs leading-5 text-amber-800">
              <AlertTriangle className="mr-1 inline size-3.5 align-[-2px]" />
              {draft.staleReasons.join(" ")}
            </div>
          )}
          {draft.items.map((item) => {
            const saved = savedRefs.has(item.sourceRef ?? item.title);
            return (
              <div key={item.sourceRef ?? item.title} className="rounded-md border border-slate-100 bg-card p-2.5">
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <div className="truncate text-sm font-semibold text-slate-900">{item.title}</div>
                    <div className="mt-0.5 text-xs text-slate-500">{formatRange(item.startAt, item.endAt)}</div>
                  </div>
                  {item.overlapCount > 0 && <Badge className="bg-amber-100 text-amber-700">겹침 {item.overlapCount}</Badge>}
                </div>
                <p className="mt-1 text-xs leading-5 text-slate-600">{item.description}</p>
                <div className="mt-2 flex items-center justify-between gap-2">
                  <span className="text-[11px] text-slate-400">알림 {item.reminders.length}개</span>
                  <Button size="sm" className="h-8" disabled={saving || saved} onClick={() => void addItem(item)}>
                    {saved ? <CheckCircle2 className="size-3.5" /> : <CalendarPlus className="size-3.5" />}
                    {saved ? "추가됨" : "추가"}
                  </Button>
                </div>
              </div>
            );
          })}
          <Link to="/planner" className="inline-flex text-xs font-semibold text-blue-700 hover:text-blue-800">
            플래너에서 세부 수정
          </Link>
        </div>
      )}
    </div>
  );
}

function toRequest(item: PlannerStrategyDraftItem): PlannerScheduleItemRequest {
  return {
    title: item.title,
    description: item.description,
    kind: item.kind,
    status: "PLANNED",
    allDay: item.allDay,
    timingPrecision: item.timingPrecision,
    startAt: toInputValue(item.startAt),
    endAt: item.endAt ? toInputValue(item.endAt) : null,
    timezone: item.timezone,
    applicationCaseId: item.applicationCaseId,
    fitAnalysisId: item.fitAnalysisId,
    sourceType: item.sourceType,
    sourceRef: item.sourceRef,
    sourceSnapshotJson: item.sourceSnapshotJson,
    overlayVisible: true,
    opacity: 0.96,
    pinned: false,
    clickThrough: false,
    reminders: item.reminders.map((reminder) => ({
      ...reminder,
      remindAt: reminder.remindAt ? toInputValue(reminder.remindAt) : null,
    })),
  };
}

function formatRange(startAt: string, endAt: string | null) {
  const start = new Date(startAt).toLocaleString("ko-KR", {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
  if (!endAt) return start;
  const end = new Date(endAt).toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" });
  return `${start} - ${end}`;
}

function toInputValue(value: string) {
  const date = new Date(value);
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function pad(value: number) {
  return String(value).padStart(2, "0");
}
