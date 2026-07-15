import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router";
import { ArrowRight, CalendarArrowDown, CalendarClock, Layers, Loader2, RefreshCw, StickyNote } from "lucide-react";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { exportPlannerIcs, getPlannerDashboard } from "../api/plannerApi";
import type { PlannerDashboard } from "../types/planner";
import { PLANNER_SECTION_PATHS } from "./PlannerPage";

function formatScheduleTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("ko-KR", {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}

export function PlannerHubPage() {
  const [data, setData] = useState<PlannerDashboard | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [exporting, setExporting] = useState(false);
  const [exportNotice, setExportNotice] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setData(await getPlannerDashboard());
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "플래너 요약을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const upcoming = useMemo(() => (data?.scheduleItems ?? [])
    .filter((item) => item.status !== "DONE" && item.status !== "CANCELED")
    .sort((left, right) => new Date(left.startAt).getTime() - new Date(right.startAt).getTime())
    .slice(0, 3), [data]);
  const overlayCount = (data?.memos.filter((memo) => memo.overlayVisible).length ?? 0)
    + (data?.scheduleItems.filter((item) => item.overlayVisible).length ?? 0);

  const handleExport = async () => {
    setExporting(true);
    setExportNotice(null);
    try {
      await exportPlannerIcs();
      setExportNotice("캘린더 파일을 내려받았습니다.");
    } catch (requestError) {
      setExportNotice(requestError instanceof Error ? requestError.message : "캘린더를 내보내지 못했습니다.");
    } finally {
      setExporting(false);
    }
  };

  return (
    <main className="min-h-screen bg-slate-50">
      <div className="mx-auto w-full max-w-[1400px] space-y-6 px-4 py-8 sm:px-6 lg:px-8">
        <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <h1 className="flex items-center gap-2 text-2xl font-black text-slate-900">
              <CalendarClock className="size-6 text-blue-600" />
              플래너
            </h1>
            <p className="mt-1 text-sm text-slate-500">일정, 메모, 화면 오버레이를 목적별 작업 공간에서 관리하세요.</p>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button type="button" variant="outline" onClick={() => void load()} disabled={loading}>
              {loading ? <Loader2 className="size-4 animate-spin" /> : <RefreshCw className="size-4" />}
              새로고침
            </Button>
            <Button type="button" variant="outline" onClick={() => void handleExport()} disabled={exporting}>
              {exporting ? <Loader2 className="size-4 animate-spin" /> : <CalendarArrowDown className="size-4" />}
              캘린더 내보내기
            </Button>
          </div>
        </header>

        {error && (
          <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
            <span>{error}</span>
            <Button type="button" size="sm" variant="outline" onClick={() => void load()}>다시 시도</Button>
          </div>
        )}
        {exportNotice && <div role="status" className="rounded-lg border border-blue-200 bg-blue-50 px-4 py-3 text-sm text-blue-800">{exportNotice}</div>}

        <section aria-label="플래너 기능" className="grid gap-4 md:grid-cols-3">
          <PlannerHubCard
            href={PLANNER_SECTION_PATHS.schedule}
            icon={CalendarClock}
            title="일정 관리"
            description="지원 마감, 면접과 학습 계획을 시간순으로 관리합니다."
            value={loading ? "불러오는 중" : `${data?.scheduleItems.length ?? 0}개 일정`}
          />
          <PlannerHubCard
            href={PLANNER_SECTION_PATHS.memo}
            icon={StickyNote}
            title="메모 관리"
            description="지원 전략과 할 일을 고정 메모로 저장하고 수정합니다."
            value={loading ? "불러오는 중" : `${data?.memos.length ?? 0}개 메모`}
          />
          <PlannerHubCard
            href={PLANNER_SECTION_PATHS.overlay}
            icon={Layers}
            title="오버레이 관리"
            description="다른 화면 위에 띄울 일정과 메모, 투명도를 설정합니다."
            value={loading ? "불러오는 중" : `${overlayCount}개 표시 중`}
          />
        </section>

        <Card className="border-slate-200 bg-card">
          <CardHeader className="flex-row items-center justify-between gap-3">
            <CardTitle className="text-base">다가오는 일정</CardTitle>
            <Link to={PLANNER_SECTION_PATHS.schedule} className="inline-flex items-center gap-1 text-sm font-bold text-blue-700">
              전체 일정 <ArrowRight className="size-4" />
            </Link>
          </CardHeader>
          <CardContent className="space-y-2">
            {loading ? (
              <div className="flex items-center gap-2 py-6 text-sm text-slate-500"><Loader2 className="size-4 animate-spin" /> 일정을 확인하고 있습니다.</div>
            ) : upcoming.length === 0 ? (
              <div className="rounded-lg border border-dashed border-slate-200 p-5 text-sm text-slate-500">
                예정된 일정이 없습니다. 일정 관리에서 면접이나 학습 계획을 추가해 보세요.
              </div>
            ) : upcoming.map((item) => (
              <Link
                key={item.id}
                to={`${PLANNER_SECTION_PATHS.schedule}?item=${item.id}`}
                className="flex flex-col gap-1 rounded-lg border border-slate-200 px-4 py-3 hover:bg-slate-50 sm:flex-row sm:items-center sm:justify-between"
              >
                <span className="font-semibold text-slate-800">{item.title}</span>
                <span className="text-xs text-slate-500">{formatScheduleTime(item.startAt)}</span>
              </Link>
            ))}
          </CardContent>
        </Card>
      </div>
    </main>
  );
}

function PlannerHubCard({
  href,
  icon: Icon,
  title,
  description,
  value,
}: {
  href: string;
  icon: typeof CalendarClock;
  title: string;
  description: string;
  value: string;
}) {
  return (
    <Card className="border-slate-200 bg-card transition-shadow hover:shadow-md">
      <CardHeader className="pb-3">
        <div className="flex size-10 items-center justify-center rounded-lg bg-blue-50 text-blue-700"><Icon className="size-5" /></div>
        <CardTitle className="text-base">{title}</CardTitle>
      </CardHeader>
      <CardContent className="flex h-full flex-col gap-4">
        <p className="text-sm leading-6 text-slate-500">{description}</p>
        <div className="rounded-lg bg-slate-50 px-3 py-2 text-sm font-semibold text-slate-700">{value}</div>
        <Link to={href} className="mt-auto inline-flex items-center gap-1 text-sm font-bold text-blue-700 hover:text-blue-800">
          열기 <ArrowRight className="size-4" />
        </Link>
      </CardContent>
    </Card>
  );
}
