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
  productType: "CREDIT" | "SUBSCRIPTION" | string;
  productCode: string;
  planCode?: string | null;
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
  productType: "CREDIT" | "SUBSCRIPTION" | string;
  productCode: string;
  planCode?: string | null;
  amount: number;
  creditAmount: number;
  status: string;
  balance: number;
}

export interface TossPaymentCancelResponse {
  orderId: string;
  productType: "CREDIT" | "SUBSCRIPTION" | string;
  productCode: string;
  planCode?: string | null;
  status: string;
}

export interface SubscriptionBenefitPolicy {
  planCode: string;
  benefitCode: string;
  benefitName: string;
  benefitType: string;
  quantity: number;
  resetCycle: string;
  overagePolicy: string;
  creditCost: number;
  sortOrder: number;
}

export interface SubscriptionPlan {
  code: "FREE" | "BASIC" | "PRO" | "PREMIUM" | string;
  name: string;
  monthlyPrice: number;
  yearlyPrice?: number | null;
  description?: string | null;
  sortOrder: number;
  benefits: SubscriptionBenefitPolicy[];
}

export interface UserBenefitBalance {
  benefitCode: string;
  benefitName: string;
  grantedQuantity: number;
  usedQuantity: number;
  remainingQuantity: number;
  sourcePlanCode: string;
  periodStart: string;
  periodEnd: string;
}

export interface MyBenefits {
  planCode: string;
  periodStart: string;
  periodEnd: string;
  benefits: UserBenefitBalance[];
}
