import { useEffect, useMemo, useState } from "react";
import type React from "react";
import { Briefcase, RefreshCw, Search } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Checkbox } from "@/app/components/ui/checkbox";
import { Input } from "@/app/components/ui/input";
import { Textarea } from "@/app/components/ui/textarea";
import {
  APPLICATION_STATUS_OPTIONS,
  type ApplicationStatus,
  getApplicationStatusLabel,
} from "@/features/applications/types/applicationCase";
import { parseJsonStringArray } from "@/features/applications/types/analysis";
import {
  getAdminApplicationCaseDetail,
  getAdminApplicationCases,
  updateAdminApplicationCaseStatus,
} from "../api";
import type { AdminApplicationCaseDetail, AdminApplicationCaseRow } from "../types";
import AdminShell from "../../../components/AdminShell";

function formatDateTime(value: string | null): string {
  if (!value) return "-";
  return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

function formatDate(value: string | null): string {
  if (!value) return "-";
  return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium" }).format(new Date(value));
}

export function AdminApplicationCasesPage() {
  const [rows, setRows] = useState<AdminApplicationCaseRow[]>([]);
  const [detail, setDetail] = useState<AdminApplicationCaseDetail | null>(null);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [keyword, setKeyword] = useState("");
  const [status, setStatus] = useState("");
  const [includeArchived, setIncludeArchived] = useState(true);
  const [includeDeleted, setIncludeDeleted] = useState(false);
  const [memo, setMemo] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const selected = useMemo(() => rows.find((row) => row.id === selectedId) ?? rows[0] ?? null, [rows, selectedId]);

  const loadRows = async () => {
    setLoading(true);
    setError(null);
    try {
      const nextRows = await getAdminApplicationCases({ keyword, status, includeArchived, includeDeleted });
      setRows(nextRows);
      if (!selectedId && nextRows[0]) setSelectedId(nextRows[0].id);
    } catch (err) {
      setError(err instanceof Error ? err.message : "지원 건 목록을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  };

  const loadDetail = async (id: number) => {
    setError(null);
    try {
      setDetail(await getAdminApplicationCaseDetail(id));
    } catch (err) {
      setError(err instanceof Error ? err.message : "지원 건 상세를 불러오지 못했습니다.");
    }
  };

  useEffect(() => {
    void loadRows();
  }, []);

  useEffect(() => {
    if (selected?.id) void loadDetail(selected.id);
  }, [selected?.id]);

  const handleStatus = async (nextStatus: ApplicationStatus) => {
    if (!selected) return;
    try {
      const updated = await updateAdminApplicationCaseStatus(selected.id, nextStatus, memo);
      setRows((items) => items.map((item) => (item.id === updated.id ? updated : item)));
      setMemo("");
      await loadDetail(selected.id);
    } catch (err) {
      setError(err instanceof Error ? err.message : "상태를 변경하지 못했습니다.");
    }
  };

  return (
    <AdminShell
      active="application-cases"
      breadcrumb="지원 건 관리"
      title="지원 건 관리"
      icon={Briefcase}
      desc="지원 건별 상태, 공고 revision, 분석 이력과 B AI 사용 로그를 확인합니다."
      actions={(
        <Button variant="outline" onClick={() => void loadRows()} disabled={loading}>
          <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
          새로고침
        </Button>
      )}
    >
      <div className="grid gap-5 lg:grid-cols-[360px_minmax(0,1fr)]">
        <section className="space-y-4">
          <Card className="border-slate-200 bg-white">
            <CardContent className="space-y-3 p-4">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
                <Input value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="기업, 직무, 이메일 검색" className="pl-9" />
              </div>
              <div className="flex flex-wrap gap-2">
                <Button size="sm" variant={status === "" ? "default" : "outline"} onClick={() => setStatus("")}>전체</Button>
                {APPLICATION_STATUS_OPTIONS.map((option) => (
                  <Button key={option.value} size="sm" variant={status === option.value ? "default" : "outline"} onClick={() => setStatus(option.value)}>
                    {option.label}
                  </Button>
                ))}
              </div>
              <label className="flex items-center gap-2 text-xs font-semibold text-slate-600">
                <Checkbox checked={includeArchived} onCheckedChange={(checked) => setIncludeArchived(Boolean(checked))} />
                보관 포함
              </label>
              <label className="flex items-center gap-2 text-xs font-semibold text-slate-600">
                <Checkbox checked={includeDeleted} onCheckedChange={(checked) => setIncludeDeleted(Boolean(checked))} />
                삭제 포함
              </label>
              <Button className="w-full bg-blue-600 text-white hover:bg-blue-700" onClick={() => void loadRows()}>
                필터 적용
              </Button>
            </CardContent>
          </Card>

          {error && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}

          <div className="space-y-2">
            {rows.map((row) => (
              <button
                key={row.id}
                type="button"
                className={`w-full rounded-lg border bg-white p-3 text-left transition-colors ${
                  selected?.id === row.id ? "border-blue-300 ring-2 ring-blue-100" : "border-slate-200 hover:border-blue-200"
                }`}
                onClick={() => setSelectedId(row.id)}
              >
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <div className="truncate text-sm font-bold text-slate-950">{row.companyName} · {row.jobTitle}</div>
                    <div className="truncate text-xs text-slate-500">{row.userEmail}</div>
                  </div>
                  <Badge variant="outline">{getApplicationStatusLabel(row.status)}</Badge>
                </div>
                <div className="mt-2 flex flex-wrap gap-1.5 text-[11px] text-slate-500">
                  <span>공고 rev {row.latestPostingRevision ?? "-"}</span>
                  <span>마감 {formatDate(row.deadlineDate)}</span>
                  {row.archivedAt && <span>보관</span>}
                  {row.deletedAt && <span>삭제</span>}
                </div>
              </button>
            ))}
          </div>
        </section>

        <section className="min-w-0 space-y-4">
          {!detail ? (
            <Card className="border-slate-200 bg-white">
              <CardContent className="p-8 text-center text-sm text-slate-500">지원 건을 선택하세요.</CardContent>
            </Card>
          ) : (
            <>
              <Card className="border-slate-200 bg-white">
                <CardHeader>
                  <CardTitle className="text-lg font-bold text-slate-950">
                    {detail.applicationCase.companyName} · {detail.applicationCase.jobTitle}
                  </CardTitle>
                </CardHeader>
                <CardContent className="space-y-4">
                  <div className="grid gap-3 md:grid-cols-5">
                    <Info label="사용자" value={detail.applicationCase.userEmail} />
                    <Info label="상태" value={getApplicationStatusLabel(detail.applicationCase.status)} />
                    <Info label="마감일" value={formatDate(detail.applicationCase.deadlineDate)} />
                    <Info label="생성" value={formatDateTime(detail.applicationCase.createdAt)} />
                    <Info label="수정" value={formatDateTime(detail.applicationCase.updatedAt)} />
                  </div>
                  <div className="space-y-2">
                    <div className="text-sm font-semibold text-slate-900">상태 변경</div>
                    <div className="flex flex-wrap gap-2">
                      {APPLICATION_STATUS_OPTIONS.map((option) => (
                        <Button key={option.value} size="sm" variant="outline" onClick={() => void handleStatus(option.value)}>
                          {option.label}
                        </Button>
                      ))}
                    </div>
                    <Textarea value={memo} onChange={(event) => setMemo(event.target.value)} className="min-h-20 bg-white" placeholder="상태 변경 메모" />
                  </div>
                </CardContent>
              </Card>

              <GridSection title="공고문 revision">
                {detail.jobPostings.map((posting) => (
                  <Card key={posting.id} className="border-slate-200 bg-white">
                    <CardContent className="space-y-2 p-4 text-sm">
                      <div className="font-semibold text-slate-900">rev {posting.revision} · {posting.sourceType}</div>
                      <div className="text-xs text-slate-500">{formatDateTime(posting.createdAt)}</div>
                      <p className="line-clamp-3 whitespace-pre-line text-slate-600">{posting.extractedText ?? posting.originalText ?? posting.uploadedFileUrl ?? "내용 없음"}</p>
                    </CardContent>
                  </Card>
                ))}
              </GridSection>

              <GridSection title="공고 분석 이력">
                {detail.jobAnalyses.map((analysis) => (
                  <Card key={analysis.id} className="border-slate-200 bg-white">
                    <CardContent className="space-y-2 p-4 text-sm">
                      <div className="font-semibold text-slate-900">#{analysis.id} · 공고 rev {analysis.jobPostingRevision ?? "-"}</div>
                      <div className="text-xs text-slate-500">{formatDateTime(analysis.createdAt)} · {analysis.confirmedAt ? "확정" : "미확정"}</div>
                      <p className="line-clamp-3 text-slate-600">{analysis.summary ?? "요약 없음"}</p>
                      <div className="flex flex-wrap gap-1">
                        {parseJsonStringArray(analysis.requiredSkills).map((skill) => <Badge key={skill} variant="outline">{skill}</Badge>)}
                      </div>
                      <AnalysisText label="근거" value={analysis.evidence} />
                      <AnalysisText label="모호한 조건" value={analysis.ambiguousConditions} />
                    </CardContent>
                  </Card>
                ))}
              </GridSection>

              <GridSection title="기업 분석 이력">
                {detail.companyAnalyses.map((analysis) => (
                  <Card key={analysis.id} className="border-slate-200 bg-white">
                    <CardContent className="space-y-2 p-4 text-sm">
                      <div className="font-semibold text-slate-900">#{analysis.id} · 공고 rev {analysis.jobPostingRevision ?? "-"}</div>
                      <div className="text-xs text-slate-500">{formatDateTime(analysis.createdAt)} · {analysis.confirmedAt ? "확정" : "미확정"}</div>
                      <p className="line-clamp-3 text-slate-600">{analysis.companySummary ?? "요약 없음"}</p>
                      <div className="grid gap-2 sm:grid-cols-3">
                        <Info label="출처 유형" value={analysis.sourceType ?? "-"} />
                        <Info label="확인 시각" value={formatDateTime(analysis.checkedAt)} />
                        <Info label="갱신 권장" value={formatDateTime(analysis.refreshRecommendedAt)} />
                      </div>
                      <AnalysisText label="검증된 사실" value={analysis.verifiedFacts} />
                      <AnalysisText label="AI 추론" value={analysis.aiInferences} />
                    </CardContent>
                  </Card>
                ))}
              </GridSection>

              <GridSection title="B AI 사용량/실패 로그">
                {detail.usageLogs.map((log) => (
                  <Card key={log.id} className={log.status === "FAILED" ? "border-red-200 bg-red-50" : "border-slate-200 bg-white"}>
                    <CardContent className="space-y-1 p-4 text-sm">
                      <div className={log.status === "FAILED" ? "font-semibold text-red-800" : "font-semibold text-slate-900"}>
                        {log.featureType} · {log.status}
                      </div>
                      <div className="text-xs text-slate-500">{formatDateTime(log.createdAt)} · token {log.tokenUsage ?? 0}</div>
                      {log.errorMessage && (
                        <div className="whitespace-pre-wrap rounded-md border border-red-200 bg-white px-2 py-1 text-xs font-medium text-red-700">
                          {log.errorMessage}
                        </div>
                      )}
                    </CardContent>
                  </Card>
                ))}
              </GridSection>
            </>
          )}
        </section>
      </div>
    </AdminShell>
  );
}

function Info({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
      <div className="text-xs font-semibold text-slate-500">{label}</div>
      <div className="mt-1 truncate text-sm font-bold text-slate-900">{value}</div>
    </div>
  );
}

function AnalysisText({ label, value }: { label: string; value: string | null }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
      <div className="text-xs font-semibold text-slate-500">{label}</div>
      <p className="mt-1 whitespace-pre-line text-sm leading-6 text-slate-600">{value ?? "-"}</p>
    </div>
  );
}

function GridSection({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="space-y-2">
      <h2 className="text-sm font-bold text-slate-900">{title}</h2>
      <div className="grid gap-3 md:grid-cols-2">{children}</div>
    </section>
  );
}
