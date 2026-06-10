export interface CreditProduct {
  code: string;
  name: string;
  price: number;
  creditAmount: number;
  description?: string | null;
  badge?: string | null;
  sortOrder: number;
}

export interface TossPaymentReadyResponse {
  orderId: string;
  orderName: string;
  amount: number;
  creditAmount: number;
  customerEmail?: string | null;
  successUrl: string;
  failUrl: string;
}

export interface TossPaymentConfirmResponse {
  orderId: string;
  paymentKey: string;
  amount: number;
  creditAmount: number;
  status: string;
  balance: number;
}
