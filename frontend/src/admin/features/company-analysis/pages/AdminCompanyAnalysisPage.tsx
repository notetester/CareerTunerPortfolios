import { useEffect, useState } from "react";
import { Building2, RefreshCw } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Input } from "@/app/components/ui/input";
import { Textarea } from "@/app/components/ui/textarea";
import { parseJsonArrayOrText, parseJsonStringArray } from "@/features/applications/types/analysis";
import { getAdminCompanyAnalyses, updateAdminCompanyAnalysisMemo, updateAdminCompanyAnalysisMetadata } from "../api";
import type { AdminCompanyAnalysisRow } from "../types";
import AdminShell from "../../../components/AdminShell";

type MetadataForm = {
  sourceType: string;
  checkedAt: string;
  refreshRecommendedAt: string;
};

function formatDateTime(value: string | null | undefined): string {
  if (!value) return "-";
  return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

function toDateTimeLocalValue(value: string | null): string {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const pad = (part: number) => String(part).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function metadataFormFromRow(row: AdminCompanyAnalysisRow): MetadataForm {
  return {
    sourceType: row.sourceType ?? "",
    checkedAt: toDateTimeLocalValue(row.checkedAt),
    refreshRecommendedAt: toDateTimeLocalValue(row.refreshRecommendedAt),
  };
}

function blankToNull(value: string): string | null {
  const trimmed = value.trim();
  return trimmed ? trimmed : null;
}

export function AdminCompanyAnalysisPage() {
  const [rows, setRows] = useState<AdminCompanyAnalysisRow[]>([]);
  const [memos, setMemos] = useState<Record<number, string>>({});
  const [metadataForms, setMetadataForms] = useState<Record<number, MetadataForm>>({});
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
      setMetadataForms(Object.fromEntries(nextRows.map((row) => [row.id, metadataFormFromRow(row)])));
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

  const saveMetadata = async (analysisId: number) => {
    const metadata = metadataForms[analysisId] ?? { sourceType: "", checkedAt: "", refreshRecommendedAt: "" };
    const original = rows.find((row) => row.id === analysisId);
    const sourceType = blankToNull(metadata.sourceType);
    if (!sourceType) {
      setError("출처 유형은 비워둘 수 없습니다.");
      return;
    }
    const clearCheckedAt = !metadata.checkedAt.trim() && Boolean(original?.checkedAt);
    const clearRefreshRecommendedAt = !metadata.refreshRecommendedAt.trim() && Boolean(original?.refreshRecommendedAt);
    if (
      (clearCheckedAt || clearRefreshRecommendedAt) &&
      !window.confirm("비어 있는 날짜 필드는 저장하면 기존 값이 삭제됩니다. 계속할까요?")
    ) {
      return;
    }

    setSavingId(analysisId);
    setError(null);
    try {
      await updateAdminCompanyAnalysisMetadata(analysisId, {
        sourceType,
        checkedAt: blankToNull(metadata.checkedAt),
        refreshRecommendedAt: blankToNull(metadata.refreshRecommendedAt),
        clearCheckedAt,
        clearRefreshRecommendedAt,
      });
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "출처 메타데이터를 저장하지 못했습니다.");
    } finally {
      setSavingId(null);
    }
  };

  const setMetadataField = (analysisId: number, field: keyof MetadataForm, value: string) => {
    setMetadataForms((current) => {
      const base = current[analysisId] ?? { sourceType: "", checkedAt: "", refreshRecommendedAt: "" };

      return {
        ...current,
        [analysisId]: {
          ...base,
          [field]: value,
        },
      };
    });
  };

  return (
    <AdminShell
      active="company-analysis"
      breadcrumb="기업 분석 조회"
      title="기업 분석 조회"
      icon={Building2}
      desc="지원 건별 기업 분석 결과와 출처, 운영 메모를 확인합니다."
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
              <CardContent className="p-8 text-center text-sm text-slate-500">기업 분석 결과가 없습니다.</CardContent>
            </Card>
          ) : (
            rows.map((row) => (
              <Card key={row.id} className="min-w-0 overflow-hidden border-slate-200 bg-white">
                <CardHeader className="pb-3">
                  <div className="flex flex-col gap-2 md:flex-row md:items-start md:justify-between">
                    <div className="min-w-0">
                      <CardTitle className="break-words text-base text-slate-950">
                        {row.companyName} · {row.jobTitle}
                      </CardTitle>
                      <p className="mt-1 break-words text-xs leading-5 text-slate-500">
                        #{row.applicationCaseId} · {row.userEmail} · {formatDateTime(row.createdAt)}
                        {row.jobPostingRevision ? ` · 공고 rev ${row.jobPostingRevision}` : ""}
                        {row.confirmedAt ? ` · 확정 ${formatDateTime(row.confirmedAt)}` : " · 미확정"}
                      </p>
                    </div>
                    <Badge className="max-w-full shrink whitespace-normal break-words bg-blue-100 text-left leading-5 text-blue-700 md:max-w-sm">
                      {row.industry ?? "산업 미정"}
                    </Badge>
                  </div>
                </CardHeader>
                <CardContent className="space-y-3">
                  <div className="grid gap-2 md:grid-cols-3">
                    <MetaBlock label="출처 유형" value={row.sourceType ?? "-"} />
                    <MetaBlock label="확인 시각" value={formatDateTime(row.checkedAt)} />
                    <MetaBlock label="갱신 권장" value={formatDateTime(row.refreshRecommendedAt)} />
                  </div>
                  <div className="space-y-2 rounded-lg border border-slate-200 bg-slate-50 p-3">
                    <div className="text-xs font-semibold text-slate-500">출처 메타데이터</div>
                    <div className="grid gap-2 md:grid-cols-3">
                      <label className="grid gap-1 text-xs font-semibold text-slate-500">
                        출처 유형
                        <Input
                          value={metadataForms[row.id]?.sourceType ?? ""}
                          onChange={(event) => setMetadataField(row.id, "sourceType", event.target.value)}
                          className="bg-white text-sm font-normal text-slate-900"
                          maxLength={30}
                        />
                      </label>
                      <label className="grid gap-1 text-xs font-semibold text-slate-500">
                        확인 시각
                        <Input
                          type="datetime-local"
                          value={metadataForms[row.id]?.checkedAt ?? ""}
                          onChange={(event) => setMetadataField(row.id, "checkedAt", event.target.value)}
                          className="bg-white text-sm font-normal text-slate-900"
                        />
                      </label>
                      <label className="grid gap-1 text-xs font-semibold text-slate-500">
                        갱신 권장
                        <Input
                          type="datetime-local"
                          value={metadataForms[row.id]?.refreshRecommendedAt ?? ""}
                          onChange={(event) => setMetadataField(row.id, "refreshRecommendedAt", event.target.value)}
                          className="bg-white text-sm font-normal text-slate-900"
                        />
                      </label>
                    </div>
                    <Button size="sm" variant="outline" onClick={() => void saveMetadata(row.id)} disabled={savingId === row.id}>
                      메타데이터 저장
                    </Button>
                  </div>
                  <TextBlock title="기업 요약" value={row.companySummary} />
                  <TextBlock title="최근 이슈" value={row.recentIssues} />
                  <TextBlock title="검증된 사실" value={row.verifiedFacts} />
                  <TextBlock title="AI 추론" value={row.aiInferences} />
                  <TextBlock title="면접 포인트" value={row.interviewPoints} />
                  <div className="flex min-w-0 flex-wrap gap-1.5">
                    {parseJsonStringArray(row.sources).map((source) => (
                      <span key={source} className="max-w-full break-words rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold leading-5 text-slate-600">
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
    </AdminShell>
  );
}

function TextBlock({ title, value }: { title: string; value: string | null }) {
  const parsed = parseJsonArrayOrText(value);

  return (
    <div className="min-w-0 rounded-lg border border-slate-200 bg-slate-50 p-3">
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
        <p className="mt-2 whitespace-pre-line break-words text-sm leading-6 text-slate-600">{parsed.text}</p>
      ) : (
        <p className="mt-2 text-sm text-slate-400">내용 없음</p>
      )}
    </div>
  );
}

function MetaBlock({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
      <div className="text-xs font-semibold text-slate-500">{label}</div>
      <div className="mt-1 truncate text-sm font-bold text-slate-900">{value}</div>
    </div>
  );
}
