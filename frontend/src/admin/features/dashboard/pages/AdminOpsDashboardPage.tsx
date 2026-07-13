import { useEffect, useState } from "react";
import { BarChart3, Briefcase, RefreshCw, ShieldAlert, Sparkles, Target, Users, Video } from "lucide-react";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent } from "@/app/components/ui/card";
import AdminShell from "../../../components/AdminShell";
import { getAdminDashboardOverview } from "../api/adminDashboardApi";
import type { AdminDashboardOverview } from "../types/adminDashboard";

/**
 * 관리자 운영 종합 대시보드(C 담당). 도메인 횡단 운영 현황 카운트를 보여준다.
 * 분석·AI 깊은 통계는 분석 통계 대시보드(/admin)를 참고.
 */
export function AdminOpsDashboardPage() {
  const [overview, setOverview] = useState<AdminDashboardOverview | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      setOverview(await getAdminDashboardOverview());
    } catch (err) {
      setError(err instanceof Error ? err.message : "운영 현황을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const cards = overview
    ? [
        { label: "전체 회원", value: overview.totalUsers, sub: `활성 ${overview.activeUsers.toLocaleString()}명`, icon: Users },
        { label: "지원 건", value: overview.totalApplications, sub: "삭제 제외", icon: Briefcase },
        { label: "적합도 분석", value: overview.totalFitAnalyses, sub: "누적 생성", icon: Target },
        { label: "면접 세션", value: overview.totalInterviewSessions, sub: "누적", icon: Video },
        { label: "이번 달 AI 호출", value: overview.aiCallsThisMonth, sub: "ai_usage_log 기준", icon: Sparkles },
        { label: "근거 검토 대기", value: overview.reviewRequiredAnalyses, sub: "최신 지원 건 기준", icon: ShieldAlert },
      ]
    : [];

  return (
    <AdminShell
      active="ops-dashboard"
      breadcrumb="운영 대시보드"
      title="운영 종합 대시보드"
      icon={BarChart3}
      desc="도메인 횡단 운영 현황 카운트입니다. 분석·AI 통계는 분석 통계 대시보드를 참고하세요."
      actions={(
        <Button variant="outline" onClick={() => void load()} disabled={loading}>
          <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
          새로고침
        </Button>
      )}
    >
      <div className="space-y-5">
        {error && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}

        {loading ? (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {Array.from({ length: 6 }).map((_, index) => (
              <div key={index} className="h-28 animate-pulse rounded-lg bg-slate-200" />
            ))}
          </div>
        ) : (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {cards.map((card) => (
              <Card key={card.label} className="border-slate-200 bg-card">
                <CardContent className="p-5">
                  <div className="flex items-center justify-between">
                    <span className="text-sm font-semibold text-slate-500">{card.label}</span>
                    <card.icon className="size-5 text-blue-600" />
                  </div>
                  <div className="mt-2 text-3xl font-black text-slate-900">{card.value.toLocaleString()}</div>
                  <div className="mt-1 text-xs text-slate-400">{card.sub}</div>
                </CardContent>
              </Card>
            ))}
          </div>
        )}
      </div>
    </AdminShell>
  );
}
