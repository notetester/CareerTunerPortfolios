import { useEffect, useState } from "react";
import { BarChart3, RefreshCw } from "lucide-react";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Badge } from "@/app/components/ui/badge";
import { getDifficultyLabel, parseJsonStringArray } from "@/features/applications/types/analysis";
import { getAdminJobAnalyses } from "../api";
import type { AdminJobAnalysisRow } from "../types";

function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

export function AdminJobAnalysisPage() {
  const [rows, setRows] = useState<AdminJobAnalysisRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      setRows(await getAdminJobAnalyses());
    } catch (err) {
      setError(err instanceof Error ? err.message : "공고 분석 목록을 불러오지 못했습니다.");
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
              <BarChart3 className="size-6 text-blue-600" />
              공고 분석 조회
            </h1>
            <p className="mt-1 text-sm text-slate-500">지원 건별 공고 분석 결과와 생성 시점을 확인합니다.</p>
          </div>
          <Button variant="outline" onClick={() => void load()} disabled={loading}>
            <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
            새로고침
          </Button>
        </section>

        {error && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}

        <div className="grid gap-3">
          {loading ? (
            <div className="h-48 animate-pulse rounded-lg bg-slate-200" />
          ) : rows.length === 0 ? (
            <Card className="border-slate-200 bg-white">
              <CardContent className="p-8 text-center text-sm text-slate-500">공고 분석 결과가 없습니다.</CardContent>
            </Card>
          ) : (
            rows.map((row) => (
              <Card key={row.id} className="border-slate-200 bg-white">
                <CardHeader className="pb-3">
                  <div className="flex flex-col gap-2 md:flex-row md:items-start md:justify-between">
                    <div>
                      <CardTitle className="text-base text-slate-950">
                        {row.companyName} · {row.jobTitle}
                      </CardTitle>
                      <p className="mt-1 text-xs text-slate-500">
                        #{row.applicationCaseId} · {row.userEmail} · {formatDateTime(row.createdAt)}
                      </p>
                    </div>
                    <Badge className="bg-blue-100 text-blue-700">{getDifficultyLabel(row.difficulty)}</Badge>
                  </div>
                </CardHeader>
                <CardContent className="space-y-3">
                  <p className="text-sm leading-6 text-slate-600">{row.summary ?? "요약 없음"}</p>
                  <div className="grid gap-2 md:grid-cols-2">
                    <KeywordBlock title="필수 역량" value={row.requiredSkills} />
                    <KeywordBlock title="우대 역량" value={row.preferredSkills} />
                  </div>
                </CardContent>
              </Card>
            ))
          )}
        </div>
      </div>
    </div>
  );
}

function KeywordBlock({ title, value }: { title: string; value: string | null }) {
  const items = parseJsonStringArray(value);
  return (
    <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
      <div className="text-xs font-semibold text-slate-500">{title}</div>
      <div className="mt-2 flex flex-wrap gap-1.5">
        {items.length > 0 ? items.map((item) => (
          <span key={item} className="rounded-full bg-white px-2 py-1 text-xs font-semibold text-slate-700">
            {item}
          </span>
        )) : <span className="text-sm text-slate-400">미정</span>}
      </div>
    </div>
  );
}
