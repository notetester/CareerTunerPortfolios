import { useEffect, useState } from "react";
import { ShieldCheck } from "lucide-react";
import { Button } from "@/app/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/app/components/ui/dialog";
import type { CurrentRefundPolicy } from "../api/refundPolicyApi";

interface RefundPolicyConfirmDialogProps {
  open: boolean;
  policy: CurrentRefundPolicy | null;
  busy?: boolean;
  onCancel(): void;
  onConfirm(): void;
}

export function RefundPolicyConfirmDialog({
  open,
  policy,
  busy = false,
  onCancel,
  onConfirm,
}: RefundPolicyConfirmDialogProps) {
  const [agreed, setAgreed] = useState(false);

  useEffect(() => {
    if (open) setAgreed(false);
  }, [open, policy?.id]);

  return (
    <Dialog open={open} onOpenChange={(next) => { if (!next && !busy) onCancel(); }}>
      <DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-xl">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <ShieldCheck className="size-5 text-blue-600" />
            결제 전 환불 정책 확인
          </DialogTitle>
          <DialogDescription>
            결제 당시 정책 버전이 결제 내역에 저장되며 이후 정책 변경은 소급 적용되지 않습니다.
          </DialogDescription>
        </DialogHeader>

        {policy ? (
          <div className="space-y-3">
            <div className="rounded-lg border border-blue-100 bg-blue-50 p-3 text-sm text-blue-950">
              <div className="font-semibold">{policy.title} · v{policy.version}</div>
              {policy.summary && <p className="mt-1 text-blue-800">{policy.summary}</p>}
            </div>
            <div className="max-h-64 whitespace-pre-wrap rounded-lg border border-slate-200 bg-slate-50 p-4 text-sm leading-6 text-slate-700">
              {policy.content}
            </div>
            <label className="flex cursor-pointer items-start gap-3 rounded-lg border border-slate-200 p-3 text-sm">
              <input
                className="mt-1 size-4"
                type="checkbox"
                checked={agreed}
                onChange={(event) => setAgreed(event.target.checked)}
                disabled={busy}
              />
              <span>환불 정책을 확인했으며 해당 정책이 적용되는 결제를 진행합니다.</span>
            </label>
          </div>
        ) : (
          <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
            시행 중인 환불 정책을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.
          </div>
        )}

        <DialogFooter>
          <Button variant="outline" onClick={onCancel} disabled={busy}>취소</Button>
          <Button onClick={onConfirm} disabled={!policy || !agreed || busy}>
            동의하고 결제하기
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
