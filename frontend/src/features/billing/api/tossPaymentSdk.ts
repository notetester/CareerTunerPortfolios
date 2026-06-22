import type { TossPaymentReadyResponse } from "../types/billing";

const TOSS_PAYMENT_SDK_URL = "https://js.tosspayments.com/v1/payment";
const TOSS_CLIENT_KEY = import.meta.env.VITE_TOSS_CLIENT_KEY as string | undefined;

interface TossPaymentsV1 {
  requestPayment(
    method: "카드",
    params: {
      amount: number;
      orderId: string;
      orderName: string;
      customerEmail?: string;
      successUrl: string;
      failUrl: string;
    },
  ): Promise<void>;
}

declare global {
  interface Window {
    TossPayments?: (clientKey: string) => TossPaymentsV1;
  }
}

let sdkLoadingPromise: Promise<void> | null = null;

/** Toss Payments 브라우저 SDK 스크립트를 한 번만 로드한다. */
function loadTossPaymentSdk(): Promise<void> {
  if (window.TossPayments) {
    return Promise.resolve();
  }
  if (sdkLoadingPromise) {
    return sdkLoadingPromise;
  }

  sdkLoadingPromise = new Promise((resolve, reject) => {
    const script = document.createElement("script");
    script.src = TOSS_PAYMENT_SDK_URL;
    script.async = true;
    script.onload = () => resolve();
    script.onerror = () => reject(new Error("Toss Payments SDK를 불러오지 못했습니다."));
    document.head.appendChild(script);
  });

  return sdkLoadingPromise;
}

/** 백엔드가 확정한 결제 정보로 Toss 결제창을 연다. */
export async function requestTossCardPayment(ready: TossPaymentReadyResponse): Promise<void> {
  if (!TOSS_CLIENT_KEY) {
    throw new Error("Toss 클라이언트 키가 설정되어 있지 않습니다.");
  }

  await loadTossPaymentSdk();
  if (!window.TossPayments) {
    throw new Error("Toss Payments SDK를 사용할 수 없습니다.");
  }

  const tossPayments = window.TossPayments(TOSS_CLIENT_KEY);
  await tossPayments.requestPayment("카드", {
    amount: ready.amount,
    orderId: ready.orderId,
    orderName: ready.orderName,
    customerEmail: ready.customerEmail ?? undefined,
    successUrl: ready.successUrl,
    failUrl: ready.failUrl,
  });
}
