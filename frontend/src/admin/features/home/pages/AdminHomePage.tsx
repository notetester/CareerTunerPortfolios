import { useEffect, useState } from "react";
import { Link } from "react-router";
import { AlertTriangle, ArrowRight, ClipboardList, House, RefreshCw, Sparkles } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent } from "@/app/components/ui/card";
import { getAdminHomeSummary } from "../api/adminHomeApi";
import type { AdminHomeSummary } from "../types/adminHome";

/**
 * 관리자 홈(C 담당). 운영자가 지금 처리할 적합도 분석 대기 큐와 운영 바로가기를 보여준다.
 * 현황 숫자 중심의 운영 종합 대시보드(/admin/dashboard)와 역할이 다르다.
 */
export function AdminHomePage() {
  const [summary, setSummary] = useState<AdminHomeSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      setSummary(await getAdminHomeSummary());
    } catch (err) {
      setError(err instanceof Error ? err.message : "관리자 홈 정보를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const queue = summary
    ? [
        { label: "적합도 분석 실패", value: summary.fitAnalysisFailures, tone: "text-red-600", icon: AlertTriangle, hint: "AI 호출 실패 건 재시도 확인" },
        { label: "강등 결과 노출", value: summary.degradedLatestAnalyses, tone: "text-red-600", icon: AlertTriangle, hint: "최신 분석이 FALLBACK/FAILED 상태" },
        { label: "재분석 요청", value: summary.reanalysisRequests, tone: "text-amber-600", icon: ClipboardList, hint: "재분석 필요 메모가 달린 분석" },
        { label: "미분석 지원 건", value: summary.unanalyzedApplications, tone: "text-amber-600", icon: ClipboardList, hint: "적합도 분석 미실행" },
        { label: "장기 분석 실패", value: summary.careerRunFailures, tone: "text-rose-600", icon: AlertTriangle, hint: "장기 경향·대시보드 요약 실행 실패" },
        { label: "최근 7일 신규 분석", value: summary.newAnalysesLast7Days, tone: "text-emerald-600", icon: Sparkles, hint: "최근 생성된 적합도 분석" },
      ]
    : [];

  return (
    <div className="min-h-screen bg-slate-50">
      <div className="mx-auto w-full max-w-7xl space-y-5 px-4 py-8 sm:px-6">
        <section className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <Badge className="mb-2 bg-slate-900 text-white">C 관리자</Badge>
            <h1 className="flex items-center gap-2 text-2xl font-bold text-slate-950">
              <House className="size-6 text-blue-600" />
              관리자 홈
            </h1>
            <p className="mt-1 text-sm text-slate-500">지금 처리할 적합도 분석 운영 항목과 바로가기입니다.</p>
          </div>
          <Button variant="outline" onClick={() => void load()} disabled={loading}>
            <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
            새로고침
          </Button>
        </section>

        {error && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}

        {loading ? (
          <div className="grid gap-4 sm:grid-cols-3">
            {Array.from({ length: 6 }).map((_, index) => (
              <div key={index} className="h-28 animate-pulse rounded-lg bg-slate-200" />
            ))}
          </div>
        ) : (
          <>
            <div className="grid gap-4 sm:grid-cols-3">
              {queue.map((item) => (
                <Card key={item.label} className="border-slate-200 bg-white">
                  <CardContent className="p-5">
                    <div className="flex items-center justify-between">
                      <span className="text-sm font-semibold text-slate-500">{item.label}</span>
                      <item.icon className={`size-5 ${item.tone}`} />
                    </div>
                    <div className={`mt-2 text-3xl font-black ${item.tone}`}>{item.value.toLocaleString()}</div>
                    <div className="mt-1 text-xs text-slate-400">{item.hint}</div>
                  </CardContent>
                </Card>
              ))}
            </div>

            <div className="grid gap-3 sm:grid-cols-2">
              {(summary?.shortcuts ?? []).map((shortcut) => (
                <Link
                  key={shortcut.path}
                  to={shortcut.path}
                  className="group rounded-lg border border-slate-200 bg-white p-4 transition-colors hover:border-blue-300 hover:bg-blue-50"
                >
                  <div className="flex items-center justify-between">
                    <span className="font-semibold text-slate-800">{shortcut.label}</span>
                    <ArrowRight className="size-4 text-slate-400 transition-colors group-hover:text-blue-600" />
                  </div>
                  <div className="mt-1 text-xs text-slate-500">{shortcut.description}</div>
                </Link>
              ))}
            </div>
          </>
        )}
      </div>
    </div>
  );
}
