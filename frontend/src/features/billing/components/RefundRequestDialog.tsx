import { useEffect, useState } from "react";
import { Button } from "@/app/components/ui/button";
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from "@/app/components/ui/dialog";
import type { Payment } from "../api/billingApi";
import type { RefundEligibility, RefundReasonCode } from "../api/refundRequestApi";
import type { CurrentRefundPolicy } from "../api/refundPolicyApi";

const won = (value: number) => `${value.toLocaleString("ko-KR")}원`;

interface Props {
  payment: Payment | null;
  policy: CurrentRefundPolicy | null;
  eligibility: RefundEligibility | null;
  error: string | null;
  busy: boolean;
  onCancel: () => void;
  onReasonChange: (reasonCode: RefundReasonCode) => void;
  onSubmit: (reasonCode: RefundReasonCode, reasonText: string) => void;
}

export function RefundRequestDialog({ payment, policy, eligibility, error, busy, onCancel, onReasonChange, onSubmit }: Props) {
  const [reasonCode, setReasonCode] = useState<RefundReasonCode>("CHANGE_OF_MIND");
  const [reasonText, setReasonText] = useState("");

  useEffect(() => {
    if (payment) {
      setReasonCode("CHANGE_OF_MIND");
      setReasonText("");
    }
  }, [payment?.id]);

  const detailRequired = reasonCode === "OTHER";
  return (
    <Dialog open={payment !== null} onOpenChange={(open) => { if (!open && !busy) onCancel(); }}>
      <DialogContent className="max-w-lg bg-white text-slate-900 dark:bg-slate-950 dark:text-slate-100">
        <DialogHeader>
          <DialogTitle>환불 신청</DialogTitle>
          <DialogDescription>신청 후 관리자가 결제 및 사용 이력을 확인하여 전액 환불 또는 환불 불가로 처리합니다.</DialogDescription>
        </DialogHeader>
        {payment && (
          <div className="space-y-4 text-sm">
            <div className="grid grid-cols-2 gap-2 rounded-lg bg-slate-50 p-4 text-slate-900 dark:bg-[#1f2937] dark:text-white">
              <span className="text-slate-600 dark:text-[#d1d5db]">주문번호</span><span className="text-right font-semibold">{payment.orderId}</span>
              <span className="text-slate-600 dark:text-[#d1d5db]">결제일</span><span className="text-right">{(payment.paidAt ?? payment.createdAt)?.slice(0, 10)}</span>
              <span className="text-slate-600 dark:text-[#d1d5db]">결제 금액</span><span className="text-right font-bold">{won(payment.amount)}</span>
            </div>
            <div className="rounded-lg border border-blue-200 bg-blue-50 p-4 text-blue-950 dark:border-[#3b82f6] dark:bg-[#1e293b] dark:text-white">
              <div className="font-semibold">적용 환불 정책 v{eligibility?.policyVersion || policy?.version || "-"}</div>
              <p className="mt-1 whitespace-pre-line text-xs leading-5 text-blue-900 dark:text-white">
                {eligibility?.policySummary ?? policy?.summary ?? policy?.content ?? "환불 정책을 불러오는 중입니다."}
              </p>
              <p className="mt-2 text-xs font-semibold text-blue-950 dark:text-white">결제 이후 크레딧 또는 사용권을 사용한 경우 환불이 제한될 수 있습니다.</p>
            </div>
            {eligibility && (
              <div className={`rounded-lg border p-3 text-sm font-semibold ${eligibilityClass(eligibility.eligibilityResult)}`}>
                {eligibility.message}
              </div>
            )}
            {error && <div className="rounded-lg border border-red-300 bg-red-50 p-3 text-sm font-semibold text-red-800 dark:border-red-800 dark:bg-red-950/50 dark:text-red-100">{error}</div>}
            <label className="block space-y-1.5">
              <span className="font-semibold text-slate-700 dark:text-slate-200">환불 사유</span>
              <select className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-slate-900 dark:border-[#6b7280] dark:bg-white dark:text-[#111827]" value={reasonCode}
                onChange={(event) => {
                  const next = event.target.value as RefundReasonCode;
                  setReasonCode(next);
                  onReasonChange(next);
                }}>
                <option value="CHANGE_OF_MIND">단순 변심</option>
                <option value="DUPLICATE_PAYMENT">중복 결제</option>
                <option value="SYSTEM_ERROR">시스템 오류</option>
                <option value="LEGAL_REQUIREMENT">기타 법적 사유</option>
                <option value="OTHER">기타</option>
              </select>
            </label>
            <label className="block space-y-1.5">
              <span className="font-semibold text-slate-700 dark:text-slate-200">상세 내용{detailRequired ? " (필수)" : ""}</span>
              <textarea className="min-h-24 w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-slate-900 dark:border-[#6b7280] dark:bg-white dark:text-[#111827]" maxLength={1000}
                value={reasonText} onChange={(event) => setReasonText(event.target.value)} />
            </label>
          </div>
        )}
        <DialogFooter>
          <Button variant="outline" onClick={onCancel} disabled={busy}>취소</Button>
          <Button onClick={() => onSubmit(reasonCode, reasonText)}
            disabled={busy || !payment || !policy || !eligibility || eligibility.eligibilityResult === "INELIGIBLE" || (detailRequired && !reasonText.trim())}>
            {busy ? "신청 중" : "환불 신청"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function eligibilityClass(result: RefundEligibility["eligibilityResult"]) {
  if (result === "ELIGIBLE") return "border-green-300 bg-green-50 text-green-800 dark:border-green-700 dark:bg-green-950/50 dark:text-white";
  if (result === "INELIGIBLE") return "border-red-300 bg-red-50 text-red-800 dark:border-red-700 dark:bg-red-950/50 dark:text-white";
  return "border-amber-300 bg-amber-50 text-amber-800 dark:border-amber-700 dark:bg-amber-950/50 dark:text-white";
}
