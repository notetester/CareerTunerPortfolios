import { useEffect, useState } from "react";
import { Building2, RefreshCw } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Textarea } from "@/app/components/ui/textarea";
import { parseJsonStringArray } from "@/features/applications/types/analysis";
import { getAdminCompanyAnalyses, updateAdminCompanyAnalysisMemo } from "../api";
import type { AdminCompanyAnalysisRow } from "../types";

function formatDateTime(value: string | null | undefined): string {
  if (!value) return "-";
  return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

export function AdminCompanyAnalysisPage() {
  const [rows, setRows] = useState<AdminCompanyAnalysisRow[]>([]);
  const [memos, setMemos] = useState<Record<number, string>>({});
  const [loading, setLoading] = useState(true);
  const [savingId, setSavingId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const nextRows = await getAdminCompanyAnalyses();
      setRows(nextRows);
      setMemos(Object.fromEntries(nextRows.map((row) => [row.id, row.adminMemo ?? ""])));
    } catch (err) {
      setError(err instanceof Error ? err.message : "기업 분석 목록을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const saveMemo = async (analysisId: number) => {
    setSavingId(analysisId);
    setError(null);
    try {
      await updateAdminCompanyAnalysisMemo(analysisId, memos[analysisId] ?? "");
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "운영 메모를 저장하지 못했습니다.");
    } finally {
      setSavingId(null);
    }
  };

  return (
    <div className="min-h-screen bg-slate-50">
      <div className="mx-auto w-full max-w-7xl space-y-5 px-4 py-8 sm:px-6">
        <section className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <Badge className="mb-2 bg-slate-900 text-white">B 관리자</Badge>
            <h1 className="flex items-center gap-2 text-2xl font-bold text-slate-950">
              <Building2 className="size-6 text-blue-600" />
              기업 분석 조회
            </h1>
            <p className="mt-1 text-sm text-slate-500">지원 건별 기업 분석 결과와 출처, 운영 메모를 확인합니다.</p>
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
              <CardContent className="p-8 text-center text-sm text-slate-500">기업 분석 결과가 없습니다.</CardContent>
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
                        {row.jobPostingRevision ? ` · 공고 rev ${row.jobPostingRevision}` : ""}
                        {row.confirmedAt ? ` · 확정 ${formatDateTime(row.confirmedAt)}` : " · 미확정"}
                      </p>
                    </div>
                    <Badge className="bg-blue-100 text-blue-700">{row.industry ?? "산업 미정"}</Badge>
                  </div>
                </CardHeader>
                <CardContent className="space-y-3">
                  <TextBlock title="기업 요약" value={row.companySummary} />
                  <TextBlock title="최근 이슈" value={row.recentIssues} />
                  <TextBlock title="면접 포인트" value={row.interviewPoints} />
                  <div className="flex flex-wrap gap-1.5">
                    {parseJsonStringArray(row.sources).map((source) => (
                      <span key={source} className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-600">
                        {source}
                      </span>
                    ))}
                  </div>
                  <div className="space-y-2 rounded-lg border border-slate-200 bg-slate-50 p-3">
                    <div className="text-xs font-semibold text-slate-500">운영 메모</div>
                    <Textarea
                      value={memos[row.id] ?? ""}
                      onChange={(event) => setMemos((current) => ({ ...current, [row.id]: event.target.value }))}
                      className="min-h-20 bg-white"
                      placeholder="분석 오류, 출처 검증, 사용자 문의 대응을 기록"
                    />
                    <Button size="sm" variant="outline" onClick={() => void saveMemo(row.id)} disabled={savingId === row.id}>
                      메모 저장
                    </Button>
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

function TextBlock({ title, value }: { title: string; value: string | null }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
      <div className="text-xs font-semibold text-slate-500">{title}</div>
      <p className="mt-2 whitespace-pre-line text-sm leading-6 text-slate-600">{value ?? "내용 없음"}</p>
    </div>
  );
}
