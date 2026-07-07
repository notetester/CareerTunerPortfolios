import { useEffect, useState } from "react";
import { Minus, Plus, WalletCards } from "lucide-react";
import { Button } from "@/app/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/app/components/ui/dialog";
import { Input } from "@/app/components/ui/input";
import { Textarea } from "@/app/components/ui/textarea";
import type { AdminCreditAdjustRequest, AdminCreditAdjustResponse } from "../types";

interface CreditAdjustDialogProps {
  open: boolean;
  initialUserId: number | null;
  onOpenChange: (open: boolean) => void;
  onSubmit: (request: AdminCreditAdjustRequest) => Promise<AdminCreditAdjustResponse>;
}

export function CreditAdjustDialog({ open, initialUserId, onOpenChange, onSubmit }: CreditAdjustDialogProps) {
  const [mode, setMode] = useState<"grant" | "deduct">("grant");
  const [userId, setUserId] = useState("");
  const [amount, setAmount] = useState("");
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!open) return;
    setMode("grant");
    setUserId(initialUserId ? String(initialUserId) : "");
    setAmount("");
    setReason("");
    setError(null);
  }, [open, initialUserId]);

  const close = (nextOpen: boolean) => {
    if (!nextOpen && submitting) return;
    onOpenChange(nextOpen);
  };

  const submit = async () => {
    if (submitting) return;
    const validation = validate(userId, amount, reason);
    if (validation.error) {
      setError(validation.error);
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await onSubmit({
        userId: validation.userId,
        amount: mode === "grant" ? validation.amount : -validation.amount,
        reason: reason.trim(),
      });
      onOpenChange(false);
    } catch (cause) {
      setError(errorMessage(cause, "크레딧을 조정하지 못했습니다."));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={close}>
      <DialogContent className="border-border bg-card text-card-foreground sm:max-w-lg">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2"><WalletCards className="size-5 text-blue-600" />크레딧 수동 조정</DialogTitle>
          <DialogDescription>조정 결과는 회원 잔액과 크레딧 원장, 관리자 활동 로그에 함께 기록됩니다.</DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-2" role="group" aria-label="크레딧 조정 방식">
            <Button type="button" variant={mode === "grant" ? "default" : "outline"} onClick={() => setMode("grant")} disabled={submitting}><Plus className="size-4" />지급</Button>
            <Button type="button" variant={mode === "deduct" ? "destructive" : "outline"} onClick={() => setMode("deduct")} disabled={submitting}><Minus className="size-4" />차감</Button>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <label className="space-y-2 text-sm font-semibold">회원 ID<Input aria-label="조정 대상 회원 ID" inputMode="numeric" value={userId} onChange={(event) => setUserId(event.target.value.replace(/[^0-9]/g, ""))} placeholder="예: 9001" disabled={submitting} /></label>
            <label className="space-y-2 text-sm font-semibold">크레딧 수량<Input aria-label="조정 크레딧 수량" inputMode="numeric" value={amount} onChange={(event) => setAmount(event.target.value.replace(/[^0-9]/g, ""))} placeholder="1~1,000,000" disabled={submitting} /></label>
          </div>

          <label className="block space-y-2 text-sm font-semibold">조정 사유
            <Textarea aria-label="크레딧 조정 사유" value={reason} onChange={(event) => setReason(event.target.value)} placeholder="고객 보상, 오지급 회수 등 근거를 입력하세요." maxLength={255} disabled={submitting} className="min-h-28 resize-y" />
            <span className="block text-right text-xs font-normal text-muted-foreground">{reason.length}/255</span>
          </label>

          {mode === "deduct" && <p className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">현재 잔액보다 많이 차감하면 처리되지 않습니다.</p>}
          {error && <p className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => close(false)} disabled={submitting}>취소</Button>
          <Button variant={mode === "deduct" ? "destructive" : "default"} onClick={() => void submit()} disabled={submitting}>
            {submitting ? "처리 중..." : mode === "grant" ? "크레딧 지급" : "크레딧 차감"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function validate(userIdText: string, amountText: string, reason: string): { userId: number; amount: number; error: string | null } {
  const userId = Number(userIdText);
  const amount = Number(amountText);
  if (!userIdText || !Number.isSafeInteger(userId) || userId <= 0) return { userId: 0, amount: 0, error: "올바른 회원 ID를 입력해 주세요." };
  if (!amountText || !Number.isSafeInteger(amount) || amount <= 0) return { userId, amount: 0, error: "조정할 크레딧을 1 이상 정수로 입력해 주세요." };
  if (amount > 1_000_000) return { userId, amount, error: "한 번에 조정할 수 있는 크레딧은 1,000,000 이하입니다." };
  if (!reason.trim()) return { userId, amount, error: "조정 사유를 입력해 주세요." };
  return { userId, amount, error: null };
}

function errorMessage(reason: unknown, fallback: string) {
  return reason instanceof Error && reason.message ? reason.message : fallback;
}
