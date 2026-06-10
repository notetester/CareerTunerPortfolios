import { useEffect, useState } from "react";
import { BarChart3, RefreshCw } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Textarea } from "@/app/components/ui/textarea";
import { getDifficultyLabel, parseJsonArrayOrText, parseJsonStringArray } from "@/features/applications/types/analysis";
import { getAdminJobAnalyses, updateAdminJobAnalysisMemo } from "../api";
import type { AdminJobAnalysisRow } from "../types";
import AdminShell from "../../../components/AdminShell";

function formatDateTime(value: string | null | undefined): string {
  if (!value) return "-";
  return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

export function AdminJobAnalysisPage() {
  const [rows, setRows] = useState<AdminJobAnalysisRow[]>([]);
  const [memos, setMemos] = useState<Record<number, string>>({});
  const [loading, setLoading] = useState(true);
  const [savingId, setSavingId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const nextRows = await getAdminJobAnalyses();
      setRows(nextRows);
      setMemos(Object.fromEntries(nextRows.map((row) => [row.id, row.adminMemo ?? ""])));
    } catch (err) {
      setError(err instanceof Error ? err.message : "공고 분석 목록을 불러오지 못했습니다.");
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
      await updateAdminJobAnalysisMemo(analysisId, memos[analysisId] ?? "");
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "운영 메모를 저장하지 못했습니다.");
    } finally {
      setSavingId(null);
    }
  };

  return (
    <AdminShell
      active="job-analysis"
      breadcrumb="공고 분석 조회"
      title="공고 분석 조회"
      icon={BarChart3}
      desc="지원 건별 공고 분석 결과와 운영 메모를 확인합니다."
      actions={(
        <Button variant="outline" onClick={() => void load()} disabled={loading}>
          <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
          새로고침
        </Button>
      )}
    >
      <div className="space-y-5">
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
                        {row.jobPostingRevision ? ` · 공고 rev ${row.jobPostingRevision}` : ""}
                        {row.confirmedAt ? ` · 확정 ${formatDateTime(row.confirmedAt)}` : " · 미확정"}
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
                  <div className="grid gap-3 md:grid-cols-2">
                    <TextBlock title="주요 업무" value={row.duties} />
                    <TextBlock title="자격 요건" value={row.qualifications} />
                    <TextBlock title="근거" value={row.evidence} />
                    <TextBlock title="모호한 조건" value={row.ambiguousConditions} />
                  </div>
                  <div className="space-y-2 rounded-lg border border-slate-200 bg-slate-50 p-3">
                    <div className="text-xs font-semibold text-slate-500">운영 메모</div>
                    <Textarea
                      value={memos[row.id] ?? ""}
                      onChange={(event) => setMemos((current) => ({ ...current, [row.id]: event.target.value }))}
                      className="min-h-20 bg-white"
                      placeholder="분석 오류, 사용자 문의 대응, 운영 판단을 기록"
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
    </AdminShell>
  );
}

function TextBlock({ title, value }: { title: string; value: string | null }) {
  const parsed = parseJsonArrayOrText(value);

  return (
    <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
      <div className="text-xs font-semibold text-slate-500">{title}</div>
      {parsed.kind === "list" ? (
        <ul className="mt-2 space-y-1.5 text-sm leading-6 text-slate-600">
          {parsed.items.map((item, index) => (
            <li key={`${item}-${index}`} className="flex gap-2">
              <span className="mt-2 size-1.5 shrink-0 rounded-full bg-current" />
              <span className="min-w-0 break-words">{item}</span>
            </li>
          ))}
        </ul>
      ) : parsed.kind === "text" ? (
        <p className="mt-2 whitespace-pre-line text-sm leading-6 text-slate-600">{parsed.text}</p>
      ) : (
        <p className="mt-2 text-sm text-slate-400">내용 없음</p>
      )}
    </div>
  );
}

function KeywordBlock({ title, value }: { title: string; value: string | null }) {
  const items = parseJsonStringArray(value);
  return (
    <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
      <div className="text-xs font-semibold text-slate-500">{title}</div>
      <div className="mt-2 flex flex-wrap gap-1.5">
        {items.length > 0 ? (
          items.map((item) => (
            <span key={item} className="rounded-full bg-white px-2 py-1 text-xs font-semibold text-slate-700">
              {item}
            </span>
          ))
        ) : (
          <span className="text-sm text-slate-400">미정</span>
        )}
      </div>
    </div>
  );
}
