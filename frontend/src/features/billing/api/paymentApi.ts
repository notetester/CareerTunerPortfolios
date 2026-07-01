import { api } from "@/app/lib/api";
import type { TossPaymentCancelResponse, TossPaymentConfirmResponse, TossPaymentReadyResponse } from "../types/billing";

export type PaymentProductType = "CREDIT" | "SUBSCRIPTION";

export function readyTossPayment(
  productCode: string,
  productType: PaymentProductType = "CREDIT",
  refundPolicyId: number,
  policyAcknowledgementKey: string,
): Promise<TossPaymentReadyResponse> {
  return api<TossPaymentReadyResponse>("/payments/toss/ready", {
    method: "POST",
    body: JSON.stringify({ productType, productCode, refundPolicyId, policyAcknowledgementKey }),
  });
}

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

export function cancelTossPayment(orderId: string): Promise<TossPaymentCancelResponse> {
  return api<TossPaymentCancelResponse>("/payments/toss/cancel", {
    method: "POST",
    body: JSON.stringify({ orderId }),
  });
}
