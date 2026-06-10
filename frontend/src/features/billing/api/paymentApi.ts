import { api } from "@/app/lib/api";
import type { TossPaymentConfirmResponse, TossPaymentReadyResponse } from "../types/billing";

/** 선택한 상품 코드로 Toss 결제창 호출에 필요한 서버 결제 건을 만든다. */
export function readyTossPayment(productCode: string): Promise<TossPaymentReadyResponse> {
  return api<TossPaymentReadyResponse>("/payments/toss/ready", {
    method: "POST",
    body: JSON.stringify({ productCode }),
  });
}

/** Toss 성공 리다이렉트 값을 백엔드 승인 API로 전달해 크레딧 충전을 확정한다. */
export function confirmTossPayment(params: {
  paymentKey: string;
  orderId: string;
  amount: number;
}): Promise<TossPaymentConfirmResponse> {
  return api<TossPaymentConfirmResponse>("/payments/toss/confirm", {
    method: "POST",
    body: JSON.stringify(params),
  });
}
