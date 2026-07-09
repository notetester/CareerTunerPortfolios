import { ArrowDown, ArrowUp, Gauge } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Progress } from "@/app/components/ui/progress";
import type { DashboardChange, DashboardReadiness } from "../types/dashboardSummary";

interface ReadinessGaugeCardProps {
  readiness: DashboardReadiness;
  recentChange: DashboardChange;
}

/**
 * C 담당: 전체 취업 준비도 게이지 + 최근 변화 요약.
 * 분석 실행률·평균 적합도·학습 완료율·면접 연습률의 가중 평균(결정적 집계, AI 미사용).
 */
export function ReadinessGaugeCard({ readiness, recentChange }: ReadinessGaugeCardProps) {
  const tone = readiness.overall >= 70 ? "text-green-600" : readiness.overall >= 50 ? "text-amber-600" : "text-red-500";

  return (
    <Card className="border border-slate-200 bg-card">
      <CardHeader className="pb-3">
        <CardTitle className="text-base flex items-center gap-2">
          <Gauge className="size-4 text-blue-600" />
          전체 취업 준비도
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        <div className="text-center">
          <span className={`text-4xl font-black ${tone}`}>{readiness.overall}%</span>
          {recentChange.averageScoreDelta != null && (
            <div
              className={`mt-1 inline-flex w-full items-center justify-center gap-1 text-xs font-semibold ${
                recentChange.averageScoreDelta >= 0 ? "text-green-600" : "text-red-500"
              }`}
            >
              {recentChange.averageScoreDelta >= 0 ? <ArrowUp className="size-3" /> : <ArrowDown className="size-3" />}
              재분석 {recentChange.reanalyzedApplications}건 평균 {recentChange.averageScoreDelta >= 0 ? "+" : ""}
              {recentChange.averageScoreDelta}점 (상승 {recentChange.improvedApplications} · 하락 {recentChange.declinedApplications})
            </div>
          )}
          {(recentChange.weeklyFitScoreDelta != null || recentChange.weeklyGapCountDelta != null || recentChange.weeklyInterviewScoreDelta != null) && (
            <div className="mt-2 flex flex-wrap gap-2 text-[11px] text-slate-500">
              {recentChange.weeklyFitScoreDelta != null && <span>지난주 대비 적합도 {recentChange.weeklyFitScoreDelta >= 0 ? "+" : ""}{recentChange.weeklyFitScoreDelta}점</span>}
              {recentChange.weeklyGapCountDelta != null && <span>부족 역량 {recentChange.weeklyGapCountDelta >= 0 ? "+" : ""}{recentChange.weeklyGapCountDelta}개</span>}
              {recentChange.weeklyInterviewScoreDelta != null && <span>면접 {recentChange.weeklyInterviewScoreDelta >= 0 ? "+" : ""}{recentChange.weeklyInterviewScoreDelta}점</span>}
            </div>
          )}
        </div>
        <div className="space-y-2.5">
          {readiness.components.map((component) => (
            <div key={component.key} className="space-y-1">
              <div className="flex items-center justify-between text-xs">
                <span className="font-medium text-slate-700">{component.label}</span>
                <span className="font-semibold text-slate-500">{component.score}%</span>
              </div>
              <Progress value={component.score} className="h-1.5" />
              <div className="text-[10px] text-slate-400">{component.description}</div>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}
