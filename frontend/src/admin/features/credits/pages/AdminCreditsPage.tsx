import { type FormEvent, useEffect, useRef, useState } from "react";
import { AlertTriangle, Coins, RefreshCw, Search, SlidersHorizontal } from "lucide-react";
import AdminShell from "@/admin/components/AdminShell";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent } from "@/app/components/ui/card";
import { Input } from "@/app/components/ui/input";
import { toast } from "@/features/notification/components/toast";
import { adjustAdminCredit, getAdminCredits, getAdminCreditSummary } from "../api";
import { CreditAdjustDialog } from "../components/CreditAdjustDialog";
import type {
  AdminCreditAdjustRequest,
  AdminCreditAdjustResponse,
  AdminCreditFilters,
  AdminCreditPage,
  AdminCreditSummary,
  AdminCreditTransactionType,
} from "../types";

const EMPTY_PAGE: AdminCreditPage = { items: [], total: 0, page: 1, size: 20 };

export function AdminCreditsPage() {
  const [pageData, setPageData] = useState<AdminCreditPage>(EMPTY_PAGE);
  const [summary, setSummary] = useState<AdminCreditSummary | null>(null);
  const [filters, setFilters] = useState<AdminCreditFilters>({ page: 1, size: 20 });
  const [keyword, setKeyword] = useState("");
  const [userId, setUserId] = useState("");
  const [type, setType] = useState<"" | AdminCreditTransactionType>("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filterError, setFilterError] = useState<string | null>(null);
  const [adjustOpen, setAdjustOpen] = useState(false);
  const [adjustUserId, setAdjustUserId] = useState<number | null>(null);
  const requestSequence = useRef(0);

  const load = async (nextFilters: AdminCreditFilters) => {
    const requestId = ++requestSequence.current;
    setLoading(true);
    setError(null);
    const [pageResult, summaryResult] = await Promise.allSettled([
      getAdminCredits(nextFilters),
      getAdminCreditSummary(),
    ]);
    if (requestId !== requestSequence.current) return;
    if (pageResult.status === "fulfilled") setPageData(pageResult.value);
    if (summaryResult.status === "fulfilled") setSummary(summaryResult.value);
    const rejected = [pageResult, summaryResult].find((result) => result.status === "rejected");
    if (rejected?.status === "rejected") setError(errorMessage(rejected.reason, "크레딧 운영 데이터를 불러오지 못했습니다."));
    setLoading(false);
  };

  useEffect(() => {
    void load(filters);
    return () => { requestSequence.current += 1; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const submitFilters = (event: FormEvent) => {
    event.preventDefault();
    const parsedUserId = userId ? Number(userId) : undefined;
    if (parsedUserId !== undefined && (!Number.isSafeInteger(parsedUserId) || parsedUserId <= 0)) {
      setFilterError("회원 ID는 1 이상의 정수로 입력해 주세요.");
      return;
    }
    setFilterError(null);
    const next: AdminCreditFilters = {
      keyword: keyword.trim() || undefined,
      userId: parsedUserId,
      type: type || undefined,
      page: 1,
      size: 20,
    };
    setFilters(next);
    void load(next);
  };

  const movePage = (page: number) => {
    const next = { ...filters, page };
    setFilters(next);
    void load(next);
  };

  const openAdjust = (targetUserId: number | null = null) => {
    setAdjustUserId(targetUserId);
    setAdjustOpen(true);
  };

  const submitAdjustment = async (request: AdminCreditAdjustRequest): Promise<AdminCreditAdjustResponse> => {
    const response = await adjustAdminCredit(request);
    toast.success(`${request.amount > 0 ? "지급" : "차감"}을 완료했습니다. 잔액 ${response.balanceAfter.toLocaleString("ko-KR")} 크레딧`);
    await load(filters);
    return response;
  };

  const totalPages = Math.max(1, Math.ceil(pageData.total / pageData.size));

  return (
    <AdminShell
      active="credits"
      breadcrumb="크레딧 관리"
      title="크레딧 관리"
      icon={Coins}
      desc="크레딧 변동 원장을 조회하고 사유가 있는 수동 조정을 처리합니다."
      actions={<div className="flex gap-2"><Button variant="outline" size="icon" title="새로고침" onClick={() => void load(filters)} disabled={loading}><RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} /></Button><Button onClick={() => openAdjust()}><SlidersHorizontal className="size-4" />수동 조정</Button></div>}
    >
      <div className="mb-5 grid grid-cols-2 gap-3 xl:grid-cols-5">
        <SummaryCard label="전체 변동" value={summary?.totalTransactions ?? 0} />
        <SummaryCard label="관리자 조정" value={summary?.adminAdjustmentCount ?? 0} />
        <SummaryCard label="누적 지급" value={summary?.totalGranted ?? 0} tone="text-emerald-600" suffix=" C" />
        <SummaryCard label="누적 차감" value={summary?.totalDeducted ?? 0} tone="text-rose-600" suffix=" C" />
        <SummaryCard label="현재 총 잔액" value={summary?.totalUserBalance ?? 0} tone="text-blue-600" suffix=" C" />
      </div>

      <form onSubmit={submitFilters} className="mb-4 grid gap-3 rounded-lg border border-border bg-card p-4 md:grid-cols-[minmax(220px,1fr)_150px_180px_auto]">
        <div className="relative"><Search className="pointer-events-none absolute left-3 top-2.5 size-4 text-muted-foreground" /><Input aria-label="크레딧 원장 검색어" value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="회원, 사유, 기능 검색" className="pl-9" maxLength={100} /></div>
        <Input aria-label="회원 ID 필터" inputMode="numeric" value={userId} onChange={(event) => setUserId(event.target.value.replace(/[^0-9]/g, ""))} placeholder="회원 ID" />
        <select aria-label="크레딧 변동 유형" value={type} onChange={(event) => setType(event.target.value as "" | AdminCreditTransactionType)} className="h-9 rounded-md border border-input bg-input-background px-3 text-sm">
          <option value="">전체 유형</option><option value="AI_USAGE">AI 사용</option><option value="CHARGE">충전</option><option value="REFUND">환불</option><option value="ADMIN_ADJUST">관리자 조정</option>
        </select>
        <Button type="submit" disabled={loading}><Search className="size-4" />검색</Button>
      </form>

      {filterError && <div className="mb-4 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">{filterError}</div>}
      {error && <ErrorBanner message={error} onRetry={() => void load(filters)} />}

      <Card className="border-border bg-card"><CardContent className="p-0"><div className="overflow-x-auto"><table className="w-full min-w-[980px] text-sm"><thead><tr className="border-b border-border text-left text-xs font-semibold text-muted-foreground"><th className="px-4 py-3">회원</th><th className="px-4 py-3">유형</th><th className="px-4 py-3 text-right">변동</th><th className="px-4 py-3 text-right">변동 후 잔액</th><th className="px-4 py-3">기능/사유</th><th className="px-4 py-3">처리 시각</th><th className="px-4 py-3 text-right">작업</th></tr></thead><tbody>
        {pageData.items.map((row) => <tr key={row.id} className="border-b border-border/60 align-top"><td className="px-4 py-3"><div className="font-semibold">{row.userName}</div><div className="text-xs text-muted-foreground">{row.userEmail} · #{row.userId}</div></td><td className="px-4 py-3"><Badge className={typeClass(row.type)}>{typeLabel(row.type)}</Badge></td><td className={`px-4 py-3 text-right font-bold ${row.amount > 0 ? "text-emerald-600" : "text-rose-600"}`}>{row.amount > 0 ? "+" : ""}{row.amount.toLocaleString("ko-KR")}</td><td className="px-4 py-3 text-right font-semibold">{row.balanceAfter.toLocaleString("ko-KR")}</td><td className="max-w-sm px-4 py-3"><div className="text-xs font-semibold text-muted-foreground">{row.featureType ?? "일반"}</div><div className="mt-1 break-words leading-5">{row.reason ?? "사유 기록 없음"}</div></td><td className="px-4 py-3 text-xs text-muted-foreground">{formatDate(row.createdAt)}</td><td className="px-4 py-3 text-right"><Button size="sm" variant="outline" onClick={() => openAdjust(row.userId)}>조정</Button></td></tr>)}
        {!loading && pageData.items.length === 0 && <EmptyRow message="조건에 맞는 크레딧 변동 내역이 없습니다." />}{loading && pageData.items.length === 0 && <EmptyRow message="크레딧 원장을 불러오는 중입니다." />}
      </tbody></table></div></CardContent></Card>

      {pageData.total > 0 && <div className="mt-4 flex items-center justify-between gap-3 text-sm text-muted-foreground"><span>총 {pageData.total.toLocaleString("ko-KR")}건</span><div className="flex items-center gap-2"><Button variant="outline" size="sm" disabled={loading || pageData.page <= 1} onClick={() => movePage(pageData.page - 1)}>이전</Button><span>{pageData.page} / {totalPages}</span><Button variant="outline" size="sm" disabled={loading || pageData.page >= totalPages} onClick={() => movePage(pageData.page + 1)}>다음</Button></div></div>}

      <CreditAdjustDialog open={adjustOpen} initialUserId={adjustUserId} onOpenChange={setAdjustOpen} onSubmit={submitAdjustment} />
    </AdminShell>
  );
}

function SummaryCard({ label, value, tone = "text-foreground", suffix = "" }: { label: string; value: number; tone?: string; suffix?: string }) { return <Card className="border-border bg-card"><CardContent className="p-4"><div className="text-xs font-semibold text-muted-foreground">{label}</div><div className={`mt-1 text-2xl font-black ${tone}`}>{value.toLocaleString("ko-KR")}{suffix}</div></CardContent></Card>; }
function EmptyRow({ message }: { message: string }) { return <tr><td colSpan={7} className="px-4 py-12 text-center text-sm text-muted-foreground">{message}</td></tr>; }
function ErrorBanner({ message, onRetry }: { message: string; onRetry: () => void }) { return <div className="mb-4 flex flex-wrap items-center justify-between gap-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700"><span className="flex items-center gap-2"><AlertTriangle className="size-4" />{message}</span><Button size="sm" variant="outline" onClick={onRetry}>다시 시도</Button></div>; }
function typeLabel(type: string) { return ({ AI_USAGE: "AI 사용", CHARGE: "충전", REFUND: "환불", ADMIN_ADJUST: "관리자 조정" } as Record<string, string>)[type] ?? type; }
function typeClass(type: AdminCreditTransactionType) { return type === "ADMIN_ADJUST" ? "bg-blue-100 text-blue-700" : type === "AI_USAGE" ? "bg-rose-100 text-rose-700" : type === "REFUND" ? "bg-amber-100 text-amber-700" : "bg-emerald-100 text-emerald-700"; }
function formatDate(value: string) { const date = new Date(value); return Number.isNaN(date.getTime()) ? "-" : new Intl.DateTimeFormat("ko-KR", { dateStyle: "short", timeStyle: "short" }).format(date); }
function errorMessage(reason: unknown, fallback: string) { return reason instanceof Error && reason.message ? reason.message : fallback; }
