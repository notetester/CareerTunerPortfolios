import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router";
import { Award, CalendarPlus, CalendarRange, CheckCircle2, ClipboardList, Compass, GraduationCap, Info, Loader2, Target } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { getCareerRoadmap } from "../api/fitAnalysisApi";
import type { CareerRoadmap, CareerRoadmapItem } from "../types/fitAnalysis";
import { createPlannerScheduleItem } from "@/features/planner/api/plannerApi";
import type { PlannerReminderChannel } from "@/features/planner/types/planner";
import { toast } from "@/features/notification/components/toast";

/**
 * 장기 커리어 로드맵 — C 의 결정론 엔진 산출을 월 단위 타임라인으로 시각화한다.
 * 실일정(공식 확인 자격증 회차·지원 마감)과 '계획 제안'(학습 블록)을 시각적으로 구분하고,
 * 항목을 플래너 일정으로 내보낼 수 있다(연/월 단위 스펙 준비 시나리오의 진입점).
 */
export function CareerRoadmapPage() {
  const [months, setMonths] = useState(12);
  const [roadmap, setRoadmap] = useState<CareerRoadmap | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [savedKeys, setSavedKeys] = useState<Set<string>>(new Set());
  const [savingKey, setSavingKey] = useState<string | null>(null);
  const [bulkSaving, setBulkSaving] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    getCareerRoadmap(months)
      .then((data) => { if (!cancelled) setRoadmap(data); })
      .catch((requestError) => {
        if (!cancelled) setError(requestError instanceof Error ? requestError.message : "로드맵을 불러오지 못했습니다.");
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [months]);

  const monthGroups = useMemo(() => groupByMonth(roadmap?.items ?? []), [roadmap?.items]);
  // 항목별 유일 키(전역 인덱스 포함) — 동일 회사·동일 마감일 지원 건이 2건이어도 React key/반영 상태가 충돌하지 않게.
  const keyOf = useMemo(() => {
    const map = new Map<CareerRoadmapItem, string>();
    (roadmap?.items ?? []).forEach((item, index) => map.set(item, `${index}:${item.type}:${item.startDate}`));
    return map;
  }, [roadmap?.items]);

  const addToPlanner = async (item: CareerRoadmapItem) => {
    const key = keyOf.get(item)!;
    setSavingKey(key);
    try {
      await createPlannerScheduleItem(toPlannerRequest(item, key));
      setSavedKeys((prev) => new Set(prev).add(key));
      toast.success(`'${item.title}' 일정을 플래너에 추가했습니다.`);
    } catch (requestError) {
      toast.error(requestError instanceof Error ? requestError.message : "일정을 추가하지 못했습니다.");
    } finally {
      setSavingKey(null);
    }
  };

  const addAllToPlanner = async () => {
    if (!roadmap) return;
    setBulkSaving(true);
    let added = 0;
    let failed = 0;
    for (const item of roadmap.items) {
      const key = keyOf.get(item)!;
      if (savedKeys.has(key)) continue;
      try {
        // 한 항목이 실패해도(예: 제약 위반) 멈추지 않고 나머지를 계속 반영한다.
        await createPlannerScheduleItem(toPlannerRequest(item, key));
        setSavedKeys((prev) => new Set(prev).add(key));
        added += 1;
      } catch {
        failed += 1;
      }
    }
    setBulkSaving(false);
    if (failed > 0) {
      toast.error(`${added}건 반영, ${failed}건 실패 — 실패 항목은 개별로 다시 시도하세요.`);
    } else {
      toast.success(added > 0 ? `로드맵 ${added}건을 플래너에 반영했습니다.` : "이미 전부 반영되어 있습니다.");
    }
  };

  return (
    <div className="min-h-screen bg-slate-50">
      <div className="mx-auto max-w-[1100px] space-y-5 px-4 py-8 sm:px-6">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div>
            <h1 className="flex items-center gap-2 text-2xl font-black text-slate-900">
              <Compass className="size-6 text-indigo-600" />
              장기 커리어 로드맵
            </h1>
            <p className="mt-1 text-sm text-slate-500">
              희망 직무 기준으로 자격증 실일정·학습 계획·지원 마감을 연 단위로 배치합니다. 날짜는 공식 확인분만 사용합니다.
            </p>
          </div>
          <div className="flex items-center gap-2">
            <div className="flex rounded-lg border border-slate-200 bg-card p-0.5">
              {[6, 12, 24].map((horizon) => (
                <button
                  key={horizon}
                  onClick={() => setMonths(horizon)}
                  className={`rounded-md px-3 py-1.5 text-xs font-semibold ${
                    months === horizon ? "bg-indigo-600 text-white" : "text-slate-600 hover:bg-slate-50"
                  }`}
                >
                  {horizon}개월
                </button>
              ))}
            </div>
            {roadmap && roadmap.items.length > 0 && (
              <Button size="sm" className="gap-1.5" onClick={() => void addAllToPlanner()} disabled={bulkSaving}>
                {bulkSaving ? <Loader2 className="size-4 animate-spin" /> : <CalendarPlus className="size-4" />}
                전체 플래너 반영
              </Button>
            )}
          </div>
        </div>

        {roadmap?.desiredJob && (
          <div className="flex flex-wrap items-center gap-2">
            <Badge className="bg-indigo-100 text-indigo-700"><Target className="mr-1 size-3" />{roadmap.desiredJob}</Badge>
            <span className="text-xs text-slate-400">생성 {roadmap.generatedAt.slice(0, 16).replace("T", " ")}</span>
            <Link to="/planner" className="text-xs font-semibold text-blue-600 underline-offset-2 hover:underline">
              플래너에서 월/연 달력으로 보기 →
            </Link>
            <Link to="/certificates" className="text-xs font-semibold text-blue-600 underline-offset-2 hover:underline">
              자격증 검색 →
            </Link>
          </div>
        )}

        {error && <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error}</div>}
        {loading && (
          <div className="flex items-center gap-2 rounded-lg border border-slate-200 bg-card p-4 text-sm text-slate-600">
            <Loader2 className="size-4 animate-spin text-indigo-600" />
            공식 일정 근거를 확인해 로드맵을 만드는 중입니다...
          </div>
        )}

        {!loading && roadmap && !roadmap.desiredJob && (
          <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800">
            프로필에 <b>희망 직무</b>를 등록하면 직군 기준 장기 로드맵을 생성합니다.{" "}
            <Link to="/profile" className="font-semibold underline">프로필로 이동</Link>
          </div>
        )}

        {!loading && roadmap && roadmap.desiredJob && monthGroups.length === 0 && (
          <div className="rounded-lg border border-slate-200 bg-card p-4 text-sm text-slate-600">
            기간 안에 배치할 확인된 일정이 없습니다. 기간을 늘리거나, 지원 건 적합도 분석을 먼저 실행해 보세요.
          </div>
        )}

        {!loading && monthGroups.map((group) => (
          <section key={group.key} className="rounded-lg border border-slate-200 bg-card">
            <div className="flex items-center justify-between border-b border-slate-100 px-4 py-3">
              <span className="text-sm font-bold text-slate-800">{group.label}</span>
              <span className="text-xs font-semibold text-slate-400">{group.items.length}건</span>
            </div>
            <div className="divide-y divide-slate-100">
              {group.items.map((item) => {
                const key = keyOf.get(item)!;
                const meta = typeMeta(item.type);
                return (
                  <div key={key} className={`grid gap-2 p-4 md:grid-cols-[120px_minmax(0,1fr)_auto] ${item.planningBlock ? "bg-slate-50/60" : ""}`}>
                    <div className="text-xs font-semibold text-slate-500">
                      {fmtRange(item.startDate, item.endDate)}
                    </div>
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="font-bold text-slate-900">{item.title}</span>
                        <Badge className={meta.tone}><meta.icon className="mr-1 size-3" />{meta.label}</Badge>
                        {item.planningBlock && (
                          <Badge variant="outline" className="border-dashed text-slate-500">계획 제안</Badge>
                        )}
                      </div>
                      {item.detail && <p className="mt-1 text-xs leading-5 text-slate-600">{item.detail}</p>}
                      {item.sourceName && <p className="mt-0.5 text-[11px] text-slate-400">출처: {item.sourceName}</p>}
                    </div>
                    <div className="flex items-start">
                      {savedKeys.has(key) ? (
                        <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-green-700">
                          <CheckCircle2 className="size-3.5" /> 반영됨
                        </span>
                      ) : (
                        <Button
                          variant="outline"
                          size="sm"
                          className="h-7 gap-1 px-2 text-[11px]"
                          onClick={() => void addToPlanner(item)}
                          disabled={savingKey === key || bulkSaving}
                        >
                          <CalendarPlus className="size-3.5" />
                          플래너에 추가
                        </Button>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          </section>
        ))}

        {!loading && roadmap && roadmap.basisNotes.length > 0 && (
          <div className="rounded-lg border border-slate-200 bg-card p-4">
            <div className="mb-1.5 flex items-center gap-1.5 text-sm font-semibold text-slate-700">
              <Info className="size-4 text-slate-400" />
              산출 근거와 한계
            </div>
            <ul className="list-disc space-y-1 pl-5 text-xs leading-5 text-slate-500">
              {roadmap.basisNotes.map((note) => <li key={note}>{note}</li>)}
            </ul>
          </div>
        )}
      </div>
    </div>
  );
}

function groupByMonth(items: CareerRoadmapItem[]) {
  const groups = new Map<string, CareerRoadmapItem[]>();
  for (const item of items) {
    const key = item.startDate.slice(0, 7);
    groups.set(key, [...(groups.get(key) ?? []), item]);
  }
  return Array.from(groups, ([key, groupItems]) => ({
    key,
    label: `${key.slice(0, 4)}년 ${Number(key.slice(5, 7))}월`,
    items: groupItems,
  }));
}

function fmtRange(start: string, end: string | null): string {
  const fmt = (value: string) => `${Number(value.slice(5, 7))}.${Number(value.slice(8, 10))}`;
  return end && end !== start ? `${fmt(start)} ~ ${fmt(end)}` : fmt(start);
}

function typeMeta(type: string): { label: string; tone: string; icon: typeof Award } {
  switch (type) {
    case "CERT_REGISTRATION": return { label: "원서접수", tone: "bg-red-100 text-red-700", icon: ClipboardList };
    case "CERT_EXAM": return { label: "자격증 시험", tone: "bg-indigo-100 text-indigo-700", icon: Award };
    case "CERT_PRACTICAL": return { label: "실기시험", tone: "bg-indigo-100 text-indigo-700", icon: Award };
    case "SKILL_LEARNING": return { label: "학습 블록", tone: "bg-green-100 text-green-700", icon: GraduationCap };
    case "APPLICATION_DEADLINE": return { label: "지원 마감", tone: "bg-amber-100 text-amber-700", icon: CalendarRange };
    default: return { label: type, tone: "bg-slate-100 text-slate-700", icon: CalendarRange };
  }
}

/** 로드맵 항목 → 플래너 일정 요청. 학습 블록은 MONTH 정밀도(월 단위 계획), 실일정은 DAY. */
function toPlannerRequest(item: CareerRoadmapItem, key: string) {
  const kind = item.type === "CERT_REGISTRATION" || item.type === "APPLICATION_DEADLINE"
    ? "DEADLINE"
    : item.type === "SKILL_LEARNING" ? "TASK" : "EVENT";
  return {
    // 백엔드 title 은 200자 제약이라 안전하게 자른다(긴 회사명·회차명이 400 을 유발하지 않게).
    title: item.title.slice(0, 200),
    description: [item.detail, item.planningBlock ? "장기 로드맵 학습 계획(자유 조정 가능)" : null].filter(Boolean).join(" · ") || null,
    kind,
    status: "PLANNED",
    allDay: true,
    timingPrecision: item.planningBlock ? "MONTH" : "DAY",
    startAt: `${item.startDate}T00:00:00`,
    endAt: item.endDate ? `${item.endDate}T23:59:00` : null,
    timezone: "Asia/Seoul",
    sourceType: "CAREER_ROADMAP",
    sourceRef: key.slice(0, 120),
    overlayVisible: false,
    opacity: 0.96,
    pinned: false,
    clickThrough: false,
    reminders: item.planningBlock
      ? []
      : [{ remindAt: null, offsetMinutes: 1440, channels: ["WEB_TOAST"] as PlannerReminderChannel[], soundEnabled: false, vibrationEnabled: false }],
  };
}
