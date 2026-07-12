import { useEffect, useState } from "react";
import { CreditCard, FileText, RefreshCw, RotateCcw } from "lucide-react";
import AdminShell from "../../../components/AdminShell";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent } from "@/app/components/ui/card";
import {
  AdminListFooter,
  AdminListToolbar,
  AdminSortableHeader,
  useAdminListTools,
  type AdminListColumn,
} from "@/admin/components/AdminListTools";
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from "@/app/components/ui/dialog";
import { Textarea } from "@/app/components/ui/textarea";
import {
  approveAdminRefundRequest, getAdminPayments, getAdminPaymentSummary, getAdminRefundRequests,
  rejectAdminRefundRequest,
  type AdminPaymentRow, type AdminPaymentSummary,
} from "../api";
import type { RefundRequestRow } from "@/features/billing/api/refundRequestApi";
import { toast } from "@/features/notification/components/toast";
import { useAdminDomainAuthorization } from "@/admin/auth/useAdminAuthorization";

const STATUS_FILTERS = [
  { value: "", label: "전체" },
  { value: "PAID", label: "결제완료" },
  { value: "READY", label: "대기" },
  { value: "CANCELED", label: "취소" },
];

const won = (n: number) => `${n.toLocaleString("ko-KR")}원`;
const fmt = (v: string | null) => (v ? new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(new Date(v)) : "-");

/** 결제 상품 표기 — 구독/크레딧/기타 상품코드 순으로 사람이 읽는 라벨. */
function productLabel(row: AdminPaymentRow): string {
  if (row.plan) return `${row.plan} 구독`;
  if (row.creditAmount) return `크레딧 ${row.creditAmount}개`;
  return row.productCode;
}

const PAYMENT_COLUMNS: AdminListColumn<AdminPaymentRow>[] = [
  { id: "user", label: "회원", getText: (row) => `${row.userName ?? "-"} ${row.userEmail ?? ""}`.trim(), sortable: true },
  { id: "product", label: "상품", getText: (row) => productLabel(row), sortable: true },
  { id: "amount", label: "금액", getText: (row) => row.amount, sortable: true },
  { id: "status", label: "상태", getText: (row) => row.status, sortable: true },
  { id: "paidAt", label: "결제일", getText: (row) => fmt(row.paidAt ?? row.createdAt), sortValue: (row) => row.paidAt ?? row.createdAt, sortable: true },
];

export function AdminPaymentsPage() {
  const { canUpdate } = useAdminDomainAuthorization("BILLING");
  const [rows, setRows] = useState<AdminPaymentRow[]>([]);
  const [summary, setSummary] = useState<AdminPaymentSummary | null>(null);
  const [status, setStatus] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [refunds, setRefunds] = useState<RefundRequestRow[]>([]);
  const [refundStatus, setRefundStatus] = useState("REQUESTED");
  const [reviewingId, setReviewingId] = useState<number | null>(null);
  const [selectedRefund, setSelectedRefund] = useState<RefundRequestRow | null>(null);
  const [reviewTarget, setReviewTarget] = useState<{ row: RefundRequestRow; approve: boolean } | null>(null);
  const [reviewReason, setReviewReason] = useState("");
  const [reviewError, setReviewError] = useState<string | null>(null);

  const list = useAdminListTools(rows, {
    columns: PAYMENT_COLUMNS,
    getRowId: (row) => row.id,
    defaultSortId: "paidAt",
    defaultSortDir: "desc",
  });

  const load = async (nextStatus = status) => {
    setLoading(true);
    setError(null);
    try {
      const [list, sum, refundList] = await Promise.all([
        getAdminPayments(nextStatus || undefined), getAdminPaymentSummary(),
        getAdminRefundRequests(refundStatus || undefined),
      ]);
      setRows(list);
      setSummary(sum);
      setRefunds(refundList);
    } catch (e) {
      setError(e instanceof Error ? e.message : "결제 내역을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load("");
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const loadRefunds = async (nextStatus: string) => {
    setRefundStatus(nextStatus);
    try {
      setRefunds(await getAdminRefundRequests(nextStatus || undefined));
    } catch (e) {
      setError(e instanceof Error ? e.message : "환불 요청을 불러오지 못했습니다.");
    }
  };

  const openReview = (row: RefundRequestRow, approve: boolean) => {
    if (!canUpdate) return;
    setReviewTarget({ row, approve });
    setReviewReason("");
    setReviewError(null);
  };

  const closeReview = () => {
    if (reviewingId !== null) return;
    setReviewTarget(null);
    setReviewReason("");
    setReviewError(null);
  };

  const submitReview = async () => {
    if (!canUpdate || !reviewTarget || !reviewReason.trim()) return;
    const { row, approve } = reviewTarget;
    setReviewingId(row.id);
    setReviewError(null);
    try {
      if (approve) await approveAdminRefundRequest(row.id, reviewReason.trim());
      else await rejectAdminRefundRequest(row.id, reviewReason.trim());
      toast.success(approve ? "전액 환불로 처리했습니다." : "환불 불가로 처리했습니다.");
      await Promise.all([load(status), loadRefunds(refundStatus)]);
      setReviewTarget(null);
      setReviewReason("");
    } catch (e) {
      const message = e instanceof Error ? e.message : "환불 요청 처리에 실패했습니다.";
      setError(message);
      setReviewError(message);
      toast.error(message);
    } finally {
      setReviewingId(null);
    }
  };

  return (
    <AdminShell
      active="payments"
      breadcrumb="결제 관리"
      title="결제 관리"
      icon={CreditCard}
      desc="구독·크레딧 결제 내역과 매출을 조회합니다."
      actions={
        <Button variant="outline" onClick={() => void load()} disabled={loading}>
          <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
        </Button>
      }
    >
      <div className="mb-4 grid gap-3 sm:grid-cols-3">
        <SummaryCard label="전체 결제" value={`${summary?.totalCount ?? 0}건`} />
        <SummaryCard label="결제 완료" value={`${summary?.paidCount ?? 0}건`} />
        <SummaryCard label="누적 매출" value={won(summary?.totalRevenue ?? 0)} highlight />
      </div>

      <div className="mb-3 flex flex-wrap gap-2">
        {STATUS_FILTERS.map((f) => (
          <Button
            key={f.value}
            size="sm"
            variant={status === f.value ? "default" : "outline"}
            onClick={() => { setStatus(f.value); void load(f.value); }}
          >
            {f.label}
          </Button>
        ))}
      </div>

      {error && <div className="mb-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}

      <Card className="border-slate-200 bg-card">
        <CardContent className="p-0">
          <div className="p-4">
            <AdminListToolbar state={list} fileName="admin_payments" />
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-slate-200 text-left text-xs font-semibold text-slate-500">
                  <AdminSortableHeader state={list} columnId="user" className="px-4 py-3">회원</AdminSortableHeader>
                  <AdminSortableHeader state={list} columnId="product" className="px-4 py-3">상품</AdminSortableHeader>
                  <AdminSortableHeader state={list} columnId="amount" className="px-4 py-3">금액</AdminSortableHeader>
                  <AdminSortableHeader state={list} columnId="status" className="px-4 py-3">상태</AdminSortableHeader>
                  <AdminSortableHeader state={list} columnId="paidAt" className="px-4 py-3">결제일</AdminSortableHeader>
                </tr>
              </thead>
              <tbody>
                {list.visibleRows.map((r) => (
                  <tr key={r.id} className="border-b border-slate-100">
                    <td className="px-4 py-3">
                      <div className="font-semibold text-slate-800">{r.userName ?? "-"}</div>
                      <div className="text-xs text-slate-500">{r.userEmail ?? "-"}</div>
                    </td>
                    <td className="px-4 py-3 text-slate-700">
                      {r.plan ? `${r.plan} 구독` : r.creditAmount ? `크레딧 ${r.creditAmount}개` : r.productCode}
                    </td>
                    <td className="px-4 py-3 font-bold text-slate-900">{won(r.amount)}</td>
                    <td className="px-4 py-3">
                      <Badge className={r.status === "PAID" ? "bg-green-100 text-green-700" : r.status === "READY" ? "bg-amber-100 text-amber-700" : "bg-slate-200 text-slate-600"}>
                        {r.status}
                      </Badge>
                    </td>
                    <td className="px-4 py-3 text-xs text-slate-500">{fmt(r.paidAt ?? r.createdAt)}</td>
                  </tr>
                ))}
                {list.visibleRows.length === 0 && !loading && (
                  <tr><td colSpan={5} className="px-4 py-10 text-center text-sm text-slate-400">결제 내역이 없습니다.</td></tr>
                )}
              </tbody>
            </table>
          </div>
          <div className="p-4">
            <AdminListFooter state={list} />
          </div>
        </CardContent>
      </Card>

      <Card className="mt-5 border-slate-200 bg-card">
        <CardContent className="p-0">
          <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 p-4">
            <div>
              <div className="flex items-center gap-2 font-bold text-slate-900"><RotateCcw className="size-4" />환불 요청</div>
              <p className="mt-1 text-xs text-slate-500">결제 당시 정책과 사용 이력을 확인해 전액 환불 또는 환불 불가로 처리합니다.</p>
            </div>
            <div className="flex gap-2">
              {[["REQUESTED", "검토 중"], ["APPROVED", "승인"], ["REJECTED", "불가"], ["", "전체"]].map(([value, label]) => (
                <Button key={value || "all"} size="sm" variant={refundStatus === value ? "default" : "outline"}
                  onClick={() => void loadRefunds(value)}>{label}</Button>
              ))}
            </div>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead><tr className="border-b border-slate-200 text-left text-xs font-semibold text-slate-500">
                <th className="px-4 py-3">회원/주문</th><th className="px-4 py-3">결제</th>
                <th className="px-4 py-3">판정 근거</th><th className="px-4 py-3">신청 사유</th><th className="px-4 py-3">처리</th>
              </tr></thead>
              <tbody>
                {refunds.map((row) => (
                  <tr key={row.id} className="border-b border-slate-100 align-top">
                    <td className="px-4 py-3"><div className="font-semibold">{row.userName ?? "-"}</div><div className="text-xs text-slate-500">{row.userEmail}</div><div className="mt-1 text-xs">{row.orderId}</div></td>
                    <td className="px-4 py-3"><div>{row.plan ? `${row.plan} 구독` : row.productCode}</div><div className="font-bold">{won(row.paymentAmount)}</div><div className="text-xs text-slate-500">{fmt(row.paidAt)}</div></td>
                    <td className="px-4 py-3"><EligibilityBadge result={row.eligibilityResult} /><div className="mt-2 text-xs text-slate-600">크레딧 {row.creditUsed ? "사용" : "미사용"} · 사용권 {row.benefitUsed ? "사용" : "미사용"}</div></td>
                    <td className="min-w-64 px-4 py-3">
                      <button
                        type="button"
                        onClick={() => setSelectedRefund(row)}
                        className="w-full rounded-lg border border-border bg-surface-2 px-3 py-3 text-left transition-colors hover:border-border-strong hover:bg-muted"
                      >
                        <span className="flex items-center gap-2 font-semibold text-foreground">
                          <FileText className="size-4 shrink-0 text-accent-2" />
                          {reasonLabel(row.reasonCode)}
                        </span>
                        <span className="mt-2 block line-clamp-2 min-h-10 break-words text-sm leading-5 text-muted-foreground">
                          {row.reasonText || "상세 내용 없음"}
                        </span>
                        <span className="mt-2 block text-xs font-semibold text-accent-2">환불 요청 사유 보기</span>
                      </button>
                      {row.reviewedReason && <div className="mt-2 text-xs text-blue-700">처리 사유: {row.reviewedReason}</div>}
                    </td>
                    <td className="px-4 py-3">
                      {row.status === "REQUESTED" && canUpdate ? <div className="flex min-w-36 gap-2">
                        <Button size="sm" disabled={reviewingId === row.id} onClick={() => openReview(row, true)}>전액 환불</Button>
                        <Button size="sm" variant="outline" disabled={reviewingId === row.id} onClick={() => openReview(row, false)}>환불 불가</Button>
                      </div> : <Badge className={row.status === "APPROVED" ? "bg-blue-100 text-blue-700" : "bg-slate-200 text-slate-700"}>{row.status === "APPROVED" ? "전액 환불" : row.status === "REQUESTED" ? "검토 중" : "환불 불가"}</Badge>}
                    </td>
                  </tr>
                ))}
                {refunds.length === 0 && <tr><td colSpan={5} className="px-4 py-10 text-center text-sm text-slate-400">환불 요청이 없습니다.</td></tr>}
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>

      <Dialog open={selectedRefund !== null} onOpenChange={(open) => { if (!open) setSelectedRefund(null); }}>
        <DialogContent className="max-h-[80vh] overflow-y-auto border-border bg-card text-card-foreground sm:max-w-2xl">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-foreground">
              <FileText className="size-5 text-accent-2" />
              환불 요청 사유
            </DialogTitle>
            <DialogDescription className="text-muted-foreground">
              주문번호 {selectedRefund?.orderId ?? "-"} · {selectedRefund?.userName ?? selectedRefund?.userEmail ?? "회원"}
            </DialogDescription>
          </DialogHeader>

          {selectedRefund && (
            <div className="space-y-4">
              <div className="grid gap-3 rounded-lg border border-border bg-surface-2 p-4 sm:grid-cols-2">
                <div>
                  <div className="text-xs font-semibold text-muted-foreground">사유 구분</div>
                  <div className="mt-1 font-semibold text-foreground">{reasonLabel(selectedRefund.reasonCode)}</div>
                </div>
                <div>
                  <div className="text-xs font-semibold text-muted-foreground">결제 금액</div>
                  <div className="mt-1 font-semibold text-foreground">{won(selectedRefund.paymentAmount)}</div>
                </div>
              </div>

              <div>
                <div className="mb-2 text-sm font-semibold text-foreground">사용자가 작성한 환불 요청 사유</div>
                <div className="min-h-32 whitespace-pre-wrap break-words rounded-lg border border-border bg-background p-4 text-sm leading-7 text-foreground">
                  {selectedRefund.reasonText || "상세 사유가 입력되지 않았습니다."}
                </div>
              </div>

              {selectedRefund.reviewedReason && (
                <div>
                  <div className="mb-2 text-sm font-semibold text-foreground">관리자 처리 사유</div>
                  <div className="whitespace-pre-wrap break-words rounded-lg border border-border bg-surface-2 p-4 text-sm leading-6 text-foreground">
                    {selectedRefund.reviewedReason}
                  </div>
                </div>
              )}
            </div>
          )}
        </DialogContent>
      </Dialog>

      <Dialog open={canUpdate && reviewTarget !== null} onOpenChange={(open) => { if (!open) closeReview(); }}>
        <DialogContent className="border-border bg-card text-card-foreground sm:max-w-xl">
          <DialogHeader>
            <DialogTitle className="text-foreground">
              {reviewTarget?.approve ? "전액 환불 승인" : "환불 불가 처리"}
            </DialogTitle>
            <DialogDescription className="leading-6 text-muted-foreground">
              주문번호 {reviewTarget?.row.orderId ?? "-"} 건을
              {reviewTarget?.approve ? " 전액 환불로 승인합니다." : " 환불 불가로 처리합니다."}
            </DialogDescription>
          </DialogHeader>

          {reviewTarget && (
            <div className="space-y-4">
              <div className="grid gap-3 rounded-lg border border-border bg-surface-2 p-4 text-sm sm:grid-cols-2">
                <div>
                  <div className="text-xs font-semibold text-muted-foreground">신청 회원</div>
                  <div className="mt-1 font-semibold text-foreground">{reviewTarget.row.userName ?? reviewTarget.row.userEmail}</div>
                </div>
                <div>
                  <div className="text-xs font-semibold text-muted-foreground">환불 금액</div>
                  <div className="mt-1 font-semibold text-foreground">{won(reviewTarget.row.refundAmount)}</div>
                </div>
              </div>

              <label htmlFor="refund-review-reason" className="block text-sm font-semibold text-foreground">
                {reviewTarget.approve ? "승인 사유" : "환불 불가 사유"}
              </label>
              <Textarea
                id="refund-review-reason"
                autoFocus
                value={reviewReason}
                onChange={(event) => setReviewReason(event.target.value)}
                placeholder={reviewTarget.approve ? "미사용 확인 등 승인 근거를 입력하세요." : "환불이 불가능한 사유를 입력하세요."}
                className="min-h-32 resize-y border-border bg-background text-foreground placeholder:text-muted-foreground"
                maxLength={500}
              />
              <div className="text-right text-xs text-muted-foreground">{reviewReason.length}/500</div>

              {reviewError && (
                <div className="rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">
                  {reviewError}
                </div>
              )}
            </div>
          )}

          <DialogFooter>
            <Button variant="outline" onClick={closeReview} disabled={reviewingId !== null}>취소</Button>
            <Button
              variant={reviewTarget?.approve ? "default" : "destructive"}
              onClick={() => void submitReview()}
              disabled={!reviewReason.trim() || reviewingId !== null}
            >
              {reviewingId !== null ? "처리 중..." : reviewTarget?.approve ? "전액 환불 승인" : "환불 불가 확정"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </AdminShell>
  );
}

function EligibilityBadge({ result }: { result: RefundRequestRow["eligibilityResult"] }) {
  const label = result === "ELIGIBLE" ? "환불 가능" : result === "INELIGIBLE" ? "정책상 불가" : "예외 검토";
  const color = result === "ELIGIBLE" ? "bg-green-100 text-green-700" : result === "INELIGIBLE" ? "bg-red-100 text-red-700" : "bg-amber-100 text-amber-700";
  return <Badge className={color}>{label}</Badge>;
}

function reasonLabel(code: string) {
  return ({ CHANGE_OF_MIND: "단순 변심", DUPLICATE_PAYMENT: "중복 결제", SYSTEM_ERROR: "시스템 오류", LEGAL_REQUIREMENT: "법적 사유", OTHER: "기타" } as Record<string, string>)[code] ?? code;
}

function SummaryCard({ label, value, highlight }: { label: string; value: string; highlight?: boolean }) {
  return (
    <Card className="border-slate-200 bg-card">
      <CardContent className="p-4">
        <div className="text-xs font-semibold text-slate-500">{label}</div>
        <div className={`mt-1 text-2xl font-black ${highlight ? "text-blue-600" : "text-slate-900"}`}>{value}</div>
      </CardContent>
    </Card>
  );
}
