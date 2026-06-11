import { ArrowDown, ArrowUp, History, Minus } from "lucide-react";
import { Card, CardContent } from "@/app/components/ui/card";
import { AiResultBadge } from "@/features/analysis/components/AiResultBadge";
import { useFitAnalysisHistory } from "@/features/analysis/hooks/useFitAnalysisHistory";
import type { FitAnalysisHistoryEntry } from "@/features/analysis/types/fitAnalysis";

interface FitAnalysisHistoryPanelProps {
  applicationCaseId: number | null;
  enabled: boolean;
  /** 최신 분석 id. 재분석 직후 히스토리를 다시 불러오는 트리거. */
  refreshKey?: number | null;
}

/**
 * C 담당: 적합도 재분석 히스토리. 분석을 다시 실행할 때마다 점수 변화와
 * 매칭/부족 역량 변화를 타임라인으로 보여줘 사용자의 성장 추적을 돕는다.
 */
export function FitAnalysisHistoryPanel({ applicationCaseId, enabled, refreshKey }: FitAnalysisHistoryPanelProps) {
  const { entries, loading } = useFitAnalysisHistory(applicationCaseId, enabled, refreshKey);

  if (!enabled || loading || entries.length < 2) {
    // 분석이 2회 이상 쌓여야 변화를 비교할 수 있다. 그 전에는 패널을 숨겨 소음을 줄인다.
    return null;
  }

  return (
    <div className="space-y-5">
      <div>
        <h2 className="flex items-center gap-2 text-lg font-bold text-slate-900">
          <History className="size-5 text-indigo-600" />
          재분석 히스토리
        </h2>
        <p className="mt-1 text-sm text-slate-500">분석을 다시 실행할 때마다 점수와 역량 변화가 누적됩니다.</p>
      </div>

      <div className="space-y-3">
        {entries.map((entry, index) => (
          <HistoryEntryCard key={entry.id} entry={entry} latest={index === 0} />
        ))}
      </div>
    </div>
  );
}

function HistoryEntryCard({ entry, latest }: { entry: FitAnalysisHistoryEntry; latest: boolean }) {
  return (
    <Card className={`border ${latest ? "border-indigo-200 bg-indigo-50/40" : "border-slate-200 bg-white"}`}>
      <CardContent className="space-y-2.5 p-4">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="flex items-center gap-2">
            <span className="text-xl font-black text-slate-900">{entry.fitScore ?? 0}점</span>
            <ScoreDelta delta={entry.scoreDelta} previous={entry.previousScore} />
            {latest && <span className="rounded bg-indigo-100 px-1.5 py-0.5 text-[10px] font-bold text-indigo-700">최신</span>}
          </div>
          <div className="flex items-center gap-2 text-xs text-slate-400">
            <AiResultBadge status={entry.status} />
            {entry.model || "mock"} · {entry.createdAt ? new Date(entry.createdAt).toLocaleString("ko-KR") : "생성 시각 없음"}
          </div>
        </div>

        <ChangeChips label="새로 매칭" tone="bg-green-50 text-green-700" items={entry.gainedSkills} />
        <ChangeChips label="해소된 부족 역량" tone="bg-blue-50 text-blue-700" items={entry.resolvedGaps} />
        <ChangeChips label="새 부족 역량" tone="bg-red-50 text-red-600" items={entry.newGaps} />
      </CardContent>
    </Card>
  );
}

function ScoreDelta({ delta, previous }: { delta: number | null; previous: number | null }) {
  if (delta === null || previous === null) {
    return <span className="text-xs text-slate-400">첫 분석</span>;
  }
  if (delta === 0) {
    return (
      <span className="inline-flex items-center gap-0.5 text-xs font-semibold text-slate-500">
        <Minus className="size-3" />
        변화 없음 ({previous}점 → 동일)
      </span>
    );
  }
  const up = delta > 0;
  return (
    <span className={`inline-flex items-center gap-0.5 text-xs font-semibold ${up ? "text-green-600" : "text-red-500"}`}>
      {up ? <ArrowUp className="size-3" /> : <ArrowDown className="size-3" />}
      {up ? "+" : ""}{delta}점 ({previous}점 대비)
    </span>
  );
}

function ChangeChips({ label, tone, items }: { label: string; tone: string; items: string[] }) {
  if (items.length === 0) return null;
  return (
    <div className="flex flex-wrap items-center gap-1.5">
      <span className="text-xs font-semibold text-slate-600">{label}</span>
      {items.map((item) => (
        <span key={item} className={`rounded-full px-2 py-0.5 text-xs font-medium ${tone}`}>
          {item}
        </span>
      ))}
    </div>
  );
}
