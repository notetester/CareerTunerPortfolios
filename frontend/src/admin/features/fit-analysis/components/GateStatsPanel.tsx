import { Badge } from "@/app/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { AlertTriangle, Loader2, ShieldCheck } from "lucide-react";
import type { AdminGateStats } from "../types/adminFitAnalysis";

interface BadgeTone {
  label: string;
  cls: string;
}

// 목록·상세의 gate 뱃지 색상 규칙과 동일하게 유지한다(REVIEW_REQUIRED=orange, PASSED=green, REJECTED=red).
const gateStatusMeta: Record<string, BadgeTone> = {
  PASSED: { label: "근거 통과", cls: "bg-green-100 text-green-700" },
  REVIEW_REQUIRED: { label: "검토 필요", cls: "bg-orange-100 text-orange-700" },
  REJECTED: { label: "반려", cls: "bg-red-100 text-red-700" },
};

// gate review workflow 처리 상태(PENDING/RESOLVED/REANALYSIS_REQUESTED) 분포용.
const reviewStatusMeta: Record<string, BadgeTone> = {
  PENDING: { label: "대기", cls: "bg-orange-100 text-orange-700" },
  RESOLVED: { label: "검토 완료", cls: "bg-green-100 text-green-700" },
  REANALYSIS_REQUESTED: { label: "재분석 요청", cls: "bg-amber-100 text-amber-700" },
};

// gate reason severity 칩과 동일한 색(critical=red, warning=amber).
const severityMeta: Record<string, BadgeTone> = {
  critical: { label: "critical", cls: "bg-red-100 text-red-700" },
  warning: { label: "warning", cls: "bg-amber-100 text-amber-700" },
};

/** 분포 Record 를 건수 내림차순 [키, 건수] 목록으로 변환. */
function toEntries(record: Record<string, number>): [string, number][] {
  return Object.entries(record).sort((a, b) => b[1] - a[1]);
}

function DistributionCard({
  title,
  record,
  meta,
  footer,
}: {
  title: string;
  record: Record<string, number>;
  meta: Record<string, BadgeTone>;
  footer?: string;
}) {
  const entries = toEntries(record);
  return (
    <div className="rounded-lg bg-slate-50 p-3">
      <div className="text-[11px] font-semibold text-slate-400">{title}</div>
      <div className="mt-1.5 flex flex-wrap gap-1.5">
        {entries.length > 0 ? (
          entries.map(([key, count]) => {
            const tone = meta[key] ?? { label: key, cls: "bg-slate-100 text-slate-600" };
            return (
              <Badge key={key} className={tone.cls}>
                {tone.label} {count}
              </Badge>
            );
          })
        ) : (
          <span className="text-xs text-slate-400">기록 없음</span>
        )}
      </div>
      {footer && <div className="mt-1.5 text-[11px] text-slate-400">{footer}</div>}
    </div>
  );
}

interface GateStatsPanelProps {
  stats: AdminGateStats | null;
  loading: boolean;
  error: string | null;
}

/** review-first evidence gate(R3) 운영 통계 요약 패널. 목록과 독립적으로 로딩되고 실패해도 목록을 막지 않는다. */
export default function GateStatsPanel({ stats, loading, error }: GateStatsPanelProps) {
  const reasonTypeEntries = stats ? toEntries(stats.byReasonType) : [];
  const reasonSeverityLine = stats
    ? toEntries(stats.byReasonSeverity)
        .map(([key, count]) => `${key} ${count}`)
        .join(" · ")
    : "";

  return (
    <Card className="border border-slate-200 bg-card">
      <CardHeader className="pb-3">
        <CardTitle className="flex items-center gap-2 text-base">
          <ShieldCheck className="size-4 text-orange-600" />
          근거 검토(gate) 통계 요약
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        {loading ? (
          <div className="flex items-center gap-2 rounded-lg bg-slate-50 p-4 text-sm text-slate-500">
            <Loader2 className="size-4 animate-spin" />
            gate 통계를 불러오는 중입니다.
          </div>
        ) : error ? (
          <p className="text-xs text-slate-400">gate 통계를 불러오지 못했습니다. ({error})</p>
        ) : stats ? (
          <>
            <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
              <div className="rounded-lg bg-slate-50 p-3">
                <div className="text-[11px] font-semibold text-slate-400">전체 gate 기록</div>
                <div className="mt-1 text-xl font-black text-slate-900">{stats.total}건</div>
              </div>
              <DistributionCard title="게이트 상태 분포" record={stats.byGateStatus} meta={gateStatusMeta} />
              <DistributionCard title="검토 상태 분포" record={stats.byReviewStatus} meta={reviewStatusMeta} />
              <DistributionCard
                title="심각도 분포"
                record={stats.byMaxSeverity}
                meta={severityMeta}
                footer={reasonSeverityLine ? `사유 단위: ${reasonSeverityLine}` : undefined}
              />
            </div>

            <div className="grid gap-3 lg:grid-cols-2">
              <div className="rounded-lg border border-slate-100 p-3">
                <div className="mb-2 text-xs font-bold text-slate-800">사유 유형 분포</div>
                <div className="flex flex-wrap gap-1.5">
                  {reasonTypeEntries.length > 0 ? (
                    reasonTypeEntries.map(([type, count]) => (
                      <span key={type} className="rounded bg-slate-100 px-2 py-1 text-xs font-semibold text-slate-600">
                        {type} {count}건
                      </span>
                    ))
                  ) : (
                    <span className="text-xs text-slate-400">집계된 사유가 없습니다.</span>
                  )}
                </div>
              </div>
              <div className="rounded-lg border border-slate-100 p-3">
                <div className="mb-2 text-xs font-bold text-slate-800">빈출 claim Top</div>
                <div className="flex flex-wrap gap-1.5">
                  {stats.topClaims.length > 0 ? (
                    stats.topClaims.map((item) => (
                      <span
                        key={item.claim}
                        className="inline-flex max-w-full items-center gap-1 rounded bg-orange-50 px-2 py-1 text-xs font-semibold text-orange-700"
                      >
                        <span className="truncate">{item.claim}</span>
                        <span className="shrink-0 rounded bg-white/70 px-1 text-[11px] text-slate-600">{item.count}건</span>
                      </span>
                    ))
                  ) : (
                    <span className="text-xs text-slate-400">집계된 claim이 없습니다.</span>
                  )}
                </div>
              </div>
            </div>

            {stats.brokenReasonsJsonCount > 0 && (
              <div className="flex items-center gap-1.5 text-xs font-semibold text-amber-700">
                <AlertTriangle className="size-3.5" />
                파싱 불가 reasons JSON {stats.brokenReasonsJsonCount}건 — 원본 데이터 확인이 필요합니다.
              </div>
            )}
          </>
        ) : (
          <p className="text-xs text-slate-400">gate 통계가 아직 없습니다.</p>
        )}
      </CardContent>
    </Card>
  );
}
