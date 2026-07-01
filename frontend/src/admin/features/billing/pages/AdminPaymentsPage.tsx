import { useEffect, useState } from "react";
import { CreditCard, RefreshCw, RotateCcw } from "lucide-react";
import AdminShell from "../../../components/AdminShell";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent } from "@/app/components/ui/card";
import {
  approveAdminRefundRequest, getAdminPayments, getAdminPaymentSummary, getAdminRefundRequests,
  rejectAdminRefundRequest,
  type AdminPaymentRow, type AdminPaymentSummary,
} from "../api";
import type { RefundRequestRow } from "@/features/billing/api/refundRequestApi";
import { toast } from "@/features/notification/components/toast";

const STATUS_FILTERS = [
  { value: "", label: "전체" },
  { value: "PAID", label: "결제완료" },
  { value: "READY", label: "대기" },
  { value: "CANCELED", label: "취소" },
];

const won = (n: number) => `${n.toLocaleString("ko-KR")}원`;
const fmt = (v: string | null) => (v ? new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(new Date(v)) : "-");

export function AdminPaymentsPage() {
  const [rows, setRows] = useState<AdminPaymentRow[]>([]);
  const [summary, setSummary] = useState<AdminPaymentSummary | null>(null);
  const [status, setStatus] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [refunds, setRefunds] = useState<RefundRequestRow[]>([]);
  const [refundStatus, setRefundStatus] = useState("REQUESTED");
  const [reviewingId, setReviewingId] = useState<number | null>(null);

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

  const review = async (row: RefundRequestRow, approve: boolean) => {
    const reason = window.prompt(approve ? "전액 환불 승인 사유를 입력하세요." : "환불 불가 사유를 입력하세요.");
    if (!reason?.trim()) return;
    if (!window.confirm(approve ? "이 결제 건을 전액 환불 처리하시겠습니까?" : "이 환불 신청을 거절하시겠습니까?")) return;
    setReviewingId(row.id);
    try {
      if (approve) await approveAdminRefundRequest(row.id, reason.trim());
      else await rejectAdminRefundRequest(row.id, reason.trim());
      toast.success(approve ? "전액 환불로 처리했습니다." : "환불 불가로 처리했습니다.");
      await Promise.all([load(status), loadRefunds(refundStatus)]);
    } catch (e) {
      const message = e instanceof Error ? e.message : "환불 요청 처리에 실패했습니다.";
      setError(message);
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
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-slate-200 text-left text-xs font-semibold text-slate-500">
                  <th className="px-4 py-3">회원</th>
                  <th className="px-4 py-3">상품</th>
                  <th className="px-4 py-3">금액</th>
                  <th className="px-4 py-3">상태</th>
                  <th className="px-4 py-3">결제일</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
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
                {rows.length === 0 && !loading && (
                  <tr><td colSpan={5} className="px-4 py-10 text-center text-sm text-slate-400">결제 내역이 없습니다.</td></tr>
                )}
              </tbody>
            </table>
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
                    <td className="max-w-56 px-4 py-3"><div className="font-semibold">{reasonLabel(row.reasonCode)}</div><div className="mt-1 break-words text-xs text-slate-500">{row.reasonText || "상세 내용 없음"}</div>{row.reviewedReason && <div className="mt-2 text-xs text-blue-700">처리 사유: {row.reviewedReason}</div>}</td>
                    <td className="px-4 py-3">
                      {row.status === "REQUESTED" ? <div className="flex min-w-36 gap-2">
                        <Button size="sm" disabled={reviewingId === row.id} onClick={() => void review(row, true)}>전액 환불</Button>
                        <Button size="sm" variant="outline" disabled={reviewingId === row.id} onClick={() => void review(row, false)}>환불 불가</Button>
                      </div> : <Badge className={row.status === "APPROVED" ? "bg-blue-100 text-blue-700" : "bg-slate-200 text-slate-700"}>{row.status === "APPROVED" ? "전액 환불" : "환불 불가"}</Badge>}
                    </td>
                  </tr>
                ))}
                {refunds.length === 0 && <tr><td colSpan={5} className="px-4 py-10 text-center text-sm text-slate-400">환불 요청이 없습니다.</td></tr>}
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>
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
