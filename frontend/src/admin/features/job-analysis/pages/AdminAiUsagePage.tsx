import { useEffect, useState } from "react";
import { Gauge, RefreshCw } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent } from "@/app/components/ui/card";
import { getAdminBUsageLogs } from "../api";
import type { AdminAiUsageLogRow } from "../types";

function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

export function AdminAiUsagePage() {
  const [rows, setRows] = useState<AdminAiUsageLogRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      setRows(await getAdminBUsageLogs());
    } catch (err) {
      setError(err instanceof Error ? err.message : "AI 사용량 로그를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  return (
    <div className="min-h-screen bg-slate-50">
      <div className="mx-auto w-full max-w-7xl space-y-5 px-4 py-8 sm:px-6">
        <section className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <Badge className="mb-2 bg-slate-900 text-white">B 관리자</Badge>
            <h1 className="flex items-center gap-2 text-2xl font-bold text-slate-950">
              <Gauge className="size-6 text-blue-600" />
              B AI 사용량
            </h1>
            <p className="mt-1 text-sm text-slate-500">공고 OCR, 공고 분석, 기업 분석의 사용량과 실패 로그를 확인합니다.</p>
          </div>
          <Button variant="outline" onClick={() => void load()} disabled={loading}>
            <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
            새로고침
          </Button>
        </section>

        {error && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}

        <Card className="border-slate-200 bg-white">
          <CardContent className="overflow-x-auto p-0">
            {loading ? (
              <div className="h-48 animate-pulse bg-slate-100" />
            ) : rows.length === 0 ? (
              <div className="p-8 text-center text-sm text-slate-500">AI 사용량 로그가 없습니다.</div>
            ) : (
              <table className="min-w-[920px] w-full text-left text-sm">
                <thead className="border-b border-slate-200 bg-slate-50 text-xs text-slate-500">
                  <tr>
                    <th className="px-4 py-3">시간</th>
                    <th className="px-4 py-3">기능</th>
                    <th className="px-4 py-3">상태</th>
                    <th className="px-4 py-3">지원 건</th>
                    <th className="px-4 py-3">사용자</th>
                    <th className="px-4 py-3">토큰</th>
                    <th className="px-4 py-3">크레딧</th>
                    <th className="px-4 py-3">오류</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((row) => (
                    <tr key={row.id} className="border-b border-slate-100 last:border-b-0">
                      <td className="px-4 py-3 text-xs text-slate-500">{formatDateTime(row.createdAt)}</td>
                      <td className="px-4 py-3 font-semibold text-slate-800">{row.featureType}</td>
                      <td className="px-4 py-3">
                        <Badge className={row.status === "SUCCESS" ? "bg-emerald-100 text-emerald-700" : "bg-red-100 text-red-700"}>
                          {row.status}
                        </Badge>
                      </td>
                      <td className="px-4 py-3 text-slate-600">
                        {row.companyName ? `${row.companyName} · ${row.jobTitle}` : "-"}
                      </td>
                      <td className="px-4 py-3 text-slate-600">{row.userEmail}</td>
                      <td className="px-4 py-3 text-slate-600">{row.tokenUsage ?? 0}</td>
                      <td className="px-4 py-3 text-slate-600">{row.creditUsed}</td>
                      <td className="max-w-[240px] truncate px-4 py-3 text-xs text-red-600">{row.errorMessage ?? ""}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
