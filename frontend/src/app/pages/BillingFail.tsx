import { useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router";
import { XCircle } from "lucide-react";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";
import { cancelTossPayment } from "@/features/billing/api/paymentApi";

export function BillingFailPage() {
  const [searchParams] = useSearchParams();
  const requestedRef = useRef(false);
  const [returnTab, setReturnTab] = useState<"plans" | "credits">("credits");
  const code = searchParams.get("code");
  const orderId = searchParams.get("orderId");
  const message = searchParams.get("message") || "결제가 완료되지 않았습니다.";

  useEffect(() => {
    if (requestedRef.current || !orderId) {
      return;
    }
    requestedRef.current = true;

    cancelTossPayment(orderId)
      .then((payment) => {
        if (payment.productType === "SUBSCRIPTION") {
          setReturnTab("plans");
        }
      })
      .catch(() => {});
  }, [orderId]);

  return (
    <div className="min-h-screen bg-slate-50 px-4 py-12">
      <Card className="mx-auto max-w-xl border border-slate-200 bg-white">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-xl">
            <XCircle className="size-5 text-red-600" />
            결제 실패
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-5">
          <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
            <div className="font-semibold">{message}</div>
            {code && <div className="mt-1 text-xs text-red-500">오류 코드: {code}</div>}
          </div>
          <div className="flex gap-2">
            <Button asChild className="flex-1">
              <Link to={`/billing?tab=${returnTab}`}>다시 시도</Link>
            </Button>
            <Button asChild variant="outline" className="flex-1">
              <Link to="/billing">결제 관리</Link>
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
