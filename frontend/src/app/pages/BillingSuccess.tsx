import { useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router";
import { CheckCircle2, Loader2 } from "lucide-react";
import { useAuth } from "../auth/AuthContext";
import { ApiError } from "../lib/api";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";
import { confirmTossPayment } from "@/features/billing/api/paymentApi";
import type { TossPaymentConfirmResponse } from "@/features/billing/types/billing";

function messageOf(error: unknown) {
  if (error instanceof ApiError || error instanceof Error) {
    return error.message;
  }
  return "결제 승인 처리 중 오류가 발생했습니다.";
}

export function BillingSuccessPage() {
  const [searchParams] = useSearchParams();
  const { refreshMe } = useAuth();
  const requestedRef = useRef(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<TossPaymentConfirmResponse | null>(null);

  useEffect(() => {
    if (requestedRef.current) {
      return;
    }
    requestedRef.current = true;

    const paymentKey = searchParams.get("paymentKey");
    const orderId = searchParams.get("orderId");
    const amount = Number(searchParams.get("amount"));

    if (!paymentKey || !orderId || !Number.isFinite(amount) || amount <= 0) {
      setError("결제 승인에 필요한 정보가 올바르지 않습니다.");
      setLoading(false);
      return;
    }

    confirmTossPayment({ paymentKey, orderId, amount })
      .then(async (confirmed) => {
        setResult(confirmed);
        await refreshMe();
      })
      .catch((err) => setError(messageOf(err)))
      .finally(() => setLoading(false));
  }, [refreshMe, searchParams]);

  return (
    <div className="min-h-screen bg-slate-50 px-4 py-12">
      <Card className="mx-auto max-w-xl border border-slate-200 bg-white">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-xl">
            {loading ? <Loader2 className="size-5 animate-spin text-blue-600" /> : <CheckCircle2 className="size-5 text-green-600" />}
            결제 승인
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-5">
          {loading && <p className="text-sm text-slate-600">Toss 결제 승인 결과를 확인하고 크레딧을 충전하는 중입니다.</p>}

          {!loading && error && (
            <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm font-semibold text-red-700">
              {error}
            </div>
          )}

          {!loading && result && (
            <div className="space-y-3">
              <div className="rounded-lg border border-green-200 bg-green-50 px-4 py-3 text-sm font-semibold text-green-700">
                결제가 완료되어 {result.creditAmount.toLocaleString("ko-KR")} 크레딧이 충전되었습니다.
              </div>
              <div className="rounded-lg bg-slate-50 p-4 text-sm text-slate-700">
                <div className="flex justify-between">
                  <span>결제 금액</span>
                  <strong>{result.amount.toLocaleString("ko-KR")}원</strong>
                </div>
                <div className="mt-2 flex justify-between">
                  <span>현재 잔액</span>
                  <strong>{result.balance.toLocaleString("ko-KR")} 크레딧</strong>
                </div>
              </div>
            </div>
          )}

          <div className="flex gap-2">
            <Button asChild className="flex-1">
              <Link to="/billing?tab=credits">크레딧 충전</Link>
            </Button>
            <Button asChild variant="outline" className="flex-1">
              <Link to="/dashboard">대시보드</Link>
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
