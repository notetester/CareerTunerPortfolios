import { FormEvent, useEffect, useRef, useState } from "react";
import { AlertTriangle, Eye, FilePenLine, RefreshCw, Search } from "lucide-react";
import AdminShell from "@/admin/components/AdminShell";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent } from "@/app/components/ui/card";
import { Input } from "@/app/components/ui/input";
import { toast } from "@/features/notification/components/toast";
import {
  getAdminCorrection,
  getAdminCorrectionFailures,
  getAdminCorrections,
  getAdminCorrectionSummary,
  updateAdminCorrectionMemo,
} from "../api";
import { CorrectionDetailDialog } from "../components/CorrectionDetailDialog";
import type {
  AdminCorrectionDetail,
  AdminCorrectionFailureRow,
  AdminCorrectionFilters,
  AdminCorrectionPage,
  AdminCorrectionSummary,
  AdminCorrectionType,
} from "../types";

const EMPTY_PAGE: AdminCorrectionPage = { items: [], total: 0, page: 1, size: 20 };

export function AdminCorrectionsPage() {
  const [tab, setTab] = useState<"success" | "failure">("success");
  const [pageData, setPageData] = useState<AdminCorrectionPage>(EMPTY_PAGE);
  const [failures, setFailures] = useState<AdminCorrectionFailureRow[]>([]);
  const [summary, setSummary] = useState<AdminCorrectionSummary | null>(null);
  const [filters, setFilters] = useState<AdminCorrectionFilters>({ page: 1, size: 20 });
  const [keyword, setKeyword] = useState("");
  const [correctionType, setCorrectionType] = useState<"" | AdminCorrectionType>("");
  const [memoState, setMemoState] = useState<"" | "HAS_MEMO" | "NO_MEMO">("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [detail, setDetail] = useState<AdminCorrectionDetail | null>(null);
  const [detailId, setDetailId] = useState<number | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState<string | null>(null);
  const listRequest = useRef(0);
  const detailRequest = useRef(0);

  const loadSuccess = async (nextFilters: AdminCorrectionFilters) => {
    const requestId = ++listRequest.current;
    setLoading(true);
    setError(null);
    const [pageResult, summaryResult] = await Promise.allSettled([
      getAdminCorrections(nextFilters),
      getAdminCorrectionSummary(),
    ]);
    if (requestId !== listRequest.current) return;
    if (pageResult.status === "fulfilled") setPageData(pageResult.value);
    if (summaryResult.status === "fulfilled") setSummary(summaryResult.value);
    const rejected = [pageResult, summaryResult].find((result) => result.status === "rejected");
    if (rejected?.status === "rejected") setError(errorMessage(rejected.reason, "첨삭 운영 데이터를 불러오지 못했습니다."));
    setLoading(false);
  };

  const loadFailures = async () => {
    const requestId = ++listRequest.current;
    setLoading(true);
    setError(null);
    const [failureResult, summaryResult] = await Promise.allSettled([
      getAdminCorrectionFailures(200),
      getAdminCorrectionSummary(),
    ]);
    if (requestId !== listRequest.current) return;
    if (failureResult.status === "fulfilled") setFailures(failureResult.value);
    if (summaryResult.status === "fulfilled") setSummary(summaryResult.value);
    const rejected = [failureResult, summaryResult].find((result) => result.status === "rejected");
    if (rejected?.status === "rejected") setError(errorMessage(rejected.reason, "첨삭 실패 로그를 불러오지 못했습니다."));
    setLoading(false);
  };

  useEffect(() => {
    void loadSuccess(filters);
    return () => { listRequest.current += 1; detailRequest.current += 1; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const submitFilters = (event: FormEvent) => {
    event.preventDefault();
    const next: AdminCorrectionFilters = {
      keyword: keyword.trim() || undefined,
      correctionType: correctionType || undefined,
      memoState: memoState || undefined,
      page: 1,
      size: 20,
    };
    setFilters(next);
    void loadSuccess(next);
  };

  const movePage = (page: number) => {
    const next = { ...filters, page };
    setFilters(next);
    void loadSuccess(next);
  };

  const changeTab = (next: "success" | "failure") => {
    setTab(next);
    if (next === "success") void loadSuccess(filters);
    else void loadFailures();
  };

  const loadDetail = async (id: number) => {
    const requestId = ++detailRequest.current;
    setDetailId(id);
    setDetailOpen(true);
    setDetail(null);
    setDetailLoading(true);
    setDetailError(null);
    try {
      const next = await getAdminCorrection(id);
      if (requestId === detailRequest.current) setDetail(next);
    } catch (reason) {
      if (requestId === detailRequest.current) setDetailError(errorMessage(reason, "첨삭 상세를 불러오지 못했습니다."));
    } finally {
      if (requestId === detailRequest.current) setDetailLoading(false);
    }
  };

  const closeDetail = (open: boolean) => {
    setDetailOpen(open);
    if (!open) {
      detailRequest.current += 1;
      setDetailId(null);
      setDetail(null);
      setDetailError(null);
    }
  };

  const saveMemo = async (memo: string | null) => {
    if (!detail) throw new Error("첨삭 상세 정보가 없습니다.");
    await updateAdminCorrectionMemo(detail.id, memo);
    setDetail({ ...detail, adminMemo: memo });
    setPageData((current) => ({
      ...current,
      items: current.items.map((row) => row.id === detail.id ? { ...row, hasMemo: Boolean(memo) } : row),
    }));
    try {
      setSummary(await getAdminCorrectionSummary());
    } catch {
      // 메모 저장은 성공했으므로 집계 갱신 실패를 저장 실패로 취급하지 않는다.
    }
    toast.success(memo ? "운영 메모를 저장했습니다." : "운영 메모를 삭제했습니다.");
  };

  const totalPages = Math.max(1, Math.ceil(pageData.total / pageData.size));

  return (
    <AdminShell
      active="corrections"
      breadcrumb="첨삭 모니터링"
      title="첨삭 모니터링"
      icon={FilePenLine}
      desc="첨삭 결과와 AI 실패를 확인하고 운영 메모를 관리합니다."
      actions={<Button variant="outline" size="icon" title="새로고침" onClick={() => tab === "success" ? void loadSuccess(filters) : void loadFailures()} disabled={loading}><RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} /></Button>}
    >
      <div className="mb-5 grid grid-cols-2 gap-3 xl:grid-cols-5">
        <SummaryCard label="전체 성공 이력" value={summary?.totalRequests ?? 0} />
        <SummaryCard label="성공" value={summary?.successCount ?? 0} tone="text-emerald-600" />
        <SummaryCard label="AI 실패" value={summary?.failureCount ?? 0} tone="text-rose-600" />
        <SummaryCard label="메모 등록" value={summary?.memoCount ?? 0} />
        <SummaryCard label="오늘 요청" value={summary?.todayCount ?? 0} tone="text-blue-600" />
      </div>

      <div className="mb-4 flex gap-2" role="tablist" aria-label="첨삭 운영 데이터 구분">
        <Button role="tab" aria-selected={tab === "success"} variant={tab === "success" ? "default" : "outline"} onClick={() => changeTab("success")}>성공 이력</Button>
        <Button role="tab" aria-selected={tab === "failure"} variant={tab === "failure" ? "default" : "outline"} onClick={() => changeTab("failure")}>AI 실패 로그</Button>
      </div>

      {tab === "success" && (
        <form onSubmit={submitFilters} className="mb-4 grid gap-3 rounded-lg border border-border bg-card p-4 md:grid-cols-[minmax(220px,1fr)_180px_160px_auto]">
          <div className="relative"><Search className="pointer-events-none absolute left-3 top-2.5 size-4 text-muted-foreground" /><Input aria-label="첨삭 검색어" value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="회원, 기업, 직무 검색" className="pl-9" maxLength={100} /></div>
          <select aria-label="첨삭 유형" value={correctionType} onChange={(event) => setCorrectionType(event.target.value as "" | AdminCorrectionType)} className="h-9 rounded-md border border-input bg-input-background px-3 text-sm">
            <option value="">전체 유형</option><option value="SELF_INTRO">자기소개서</option><option value="INTERVIEW_ANSWER">면접 답변</option><option value="RESUME">이력서</option><option value="PORTFOLIO">포트폴리오</option>
          </select>
          <select aria-label="메모 상태" value={memoState} onChange={(event) => setMemoState(event.target.value as "" | "HAS_MEMO" | "NO_MEMO")} className="h-9 rounded-md border border-input bg-input-background px-3 text-sm">
            <option value="">메모 전체</option><option value="HAS_MEMO">메모 있음</option><option value="NO_MEMO">메모 없음</option>
          </select>
          <Button type="submit" disabled={loading}><Search className="size-4" />검색</Button>
        </form>
      )}

      {error && <ErrorBanner message={error} onRetry={() => tab === "success" ? void loadSuccess(filters) : void loadFailures()} />}

      <Card className="border-border bg-card">
        <CardContent className="p-0">
          {tab === "success" ? (
            <SuccessTable loading={loading} page={pageData} onDetail={(id) => void loadDetail(id)} />
          ) : (
            <FailureTable loading={loading} rows={failures} />
          )}
        </CardContent>
      </Card>

      {tab === "success" && pageData.total > 0 && (
        <div className="mt-4 flex items-center justify-between gap-3 text-sm text-muted-foreground">
          <span>총 {pageData.total.toLocaleString("ko-KR")}건</span>
          <div className="flex items-center gap-2"><Button variant="outline" size="sm" disabled={loading || pageData.page <= 1} onClick={() => movePage(pageData.page - 1)}>이전</Button><span>{pageData.page} / {totalPages}</span><Button variant="outline" size="sm" disabled={loading || pageData.page >= totalPages} onClick={() => movePage(pageData.page + 1)}>다음</Button></div>
        </div>
      )}

      <CorrectionDetailDialog
        open={detailOpen}
        detail={detail}
        loading={detailLoading}
        error={detailError}
        onOpenChange={closeDetail}
        onRetry={() => { if (detailId) void loadDetail(detailId); }}
        onSaveMemo={saveMemo}
      />
    </AdminShell>
  );
}

function SuccessTable({ loading, page, onDetail }: { loading: boolean; page: AdminCorrectionPage; onDetail: (id: number) => void }) {
  return <div className="overflow-x-auto"><table className="w-full min-w-[900px] text-sm"><thead><tr className="border-b border-border text-left text-xs font-semibold text-muted-foreground"><th className="px-4 py-3">회원</th><th className="px-4 py-3">지원 건</th><th className="px-4 py-3">유형</th><th className="px-4 py-3">모델/사용량</th><th className="px-4 py-3">메모</th><th className="px-4 py-3">요청 시각</th><th className="px-4 py-3 text-right">상세</th></tr></thead><tbody>
    {page.items.map((row) => <tr key={row.id} className="border-b border-border/60 align-top"><td className="px-4 py-3"><div className="font-semibold">{row.userName}</div><div className="text-xs text-muted-foreground">{row.userEmail}</div></td><td className="px-4 py-3"><div>{row.companyName ?? "직접 입력"}</div><div className="text-xs text-muted-foreground">{row.jobTitle ?? (row.applicationCaseId ? `#${row.applicationCaseId}` : "지원 건 없음")}</div></td><td className="px-4 py-3"><Badge variant="secondary">{typeLabel(row.correctionType)}</Badge></td><td className="px-4 py-3"><div>{row.model ?? "-"}</div><div className="text-xs text-muted-foreground">{row.totalTokens?.toLocaleString("ko-KR") ?? 0} tokens · {row.creditUsed ?? 0} credit</div></td><td className="px-4 py-3"><Badge className={row.hasMemo ? "bg-blue-100 text-blue-700" : "bg-slate-100 text-slate-600"}>{row.hasMemo ? "등록" : "없음"}</Badge></td><td className="px-4 py-3 text-xs text-muted-foreground">{formatDate(row.createdAt)}</td><td className="px-4 py-3 text-right"><Button size="icon" variant="ghost" title="첨삭 상세 보기" onClick={() => onDetail(row.id)}><Eye className="size-4" /></Button></td></tr>)}
    {!loading && page.items.length === 0 && <EmptyRow colSpan={7} message="조건에 맞는 첨삭 이력이 없습니다." />}{loading && page.items.length === 0 && <EmptyRow colSpan={7} message="첨삭 이력을 불러오는 중입니다." />}
  </tbody></table></div>;
}

function FailureTable({ loading, rows }: { loading: boolean; rows: AdminCorrectionFailureRow[] }) {
  return <div className="overflow-x-auto"><table className="w-full min-w-[900px] text-sm"><thead><tr className="border-b border-border text-left text-xs font-semibold text-muted-foreground"><th className="px-4 py-3">회원</th><th className="px-4 py-3">지원 건</th><th className="px-4 py-3">기능</th><th className="px-4 py-3">모델/사용량</th><th className="px-4 py-3">오류</th><th className="px-4 py-3">발생 시각</th></tr></thead><tbody>
    {rows.map((row) => <tr key={row.id} className="border-b border-border/60 align-top"><td className="px-4 py-3"><div className="font-semibold">{row.userName}</div><div className="text-xs text-muted-foreground">{row.userEmail}</div></td><td className="px-4 py-3"><div>{row.companyName ?? "직접 입력"}</div><div className="text-xs text-muted-foreground">{row.jobTitle ?? "-"}</div></td><td className="px-4 py-3"><Badge className="bg-rose-100 text-rose-700">{failureTypeLabel(row.featureType)}</Badge></td><td className="px-4 py-3"><div>{row.model ?? "-"}</div><div className="text-xs text-muted-foreground">{row.totalTokens?.toLocaleString("ko-KR") ?? 0} tokens</div></td><td className="max-w-md px-4 py-3"><p className="break-words text-sm leading-6 text-rose-700">{row.errorMessage || "오류 메시지가 기록되지 않았습니다."}</p></td><td className="px-4 py-3 text-xs text-muted-foreground">{formatDate(row.createdAt)}</td></tr>)}
    {!loading && rows.length === 0 && <EmptyRow colSpan={6} message="첨삭 AI 실패 로그가 없습니다." />}{loading && rows.length === 0 && <EmptyRow colSpan={6} message="실패 로그를 불러오는 중입니다." />}
  </tbody></table></div>;
}

function SummaryCard({ label, value, tone = "text-foreground" }: { label: string; value: number; tone?: string }) { return <Card className="border-border bg-card"><CardContent className="p-4"><div className="text-xs font-semibold text-muted-foreground">{label}</div><div className={`mt-1 text-2xl font-black ${tone}`}>{value.toLocaleString("ko-KR")}</div></CardContent></Card>; }
function EmptyRow({ colSpan, message }: { colSpan: number; message: string }) { return <tr><td colSpan={colSpan} className="px-4 py-12 text-center text-sm text-muted-foreground">{message}</td></tr>; }
function ErrorBanner({ message, onRetry }: { message: string; onRetry: () => void }) { return <div className="mb-4 flex flex-wrap items-center justify-between gap-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700"><span className="flex items-center gap-2"><AlertTriangle className="size-4" />{message}</span><Button size="sm" variant="outline" onClick={onRetry}>다시 시도</Button></div>; }
function typeLabel(type: string) { return ({ SELF_INTRO: "자기소개서", INTERVIEW_ANSWER: "면접 답변", RESUME: "이력서", PORTFOLIO: "포트폴리오" } as Record<string, string>)[type] ?? type; }
function failureTypeLabel(type: string) { return typeLabel(type.replace("CORRECTION_", "")); }
function formatDate(value: string) { const date = new Date(value); return Number.isNaN(date.getTime()) ? "-" : new Intl.DateTimeFormat("ko-KR", { dateStyle: "short", timeStyle: "short" }).format(date); }
function errorMessage(reason: unknown, fallback: string) { return reason instanceof Error && reason.message ? reason.message : fallback; }
