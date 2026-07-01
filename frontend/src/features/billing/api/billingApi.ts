import { api } from "@/app/lib/api";

export interface SubscriptionPlan {
  id?: number;
  code: string;
  name: string;
  monthlyPrice: number;
  yearlyPrice: number | null;
  description: string;
  active?: boolean;
  sortOrder: number;
  benefits?: SubscriptionBenefitPolicy[];
}

export interface CreditProduct {
  id: number;
  code: string;
  name: string;
  price: number;
  creditAmount: number;
  description?: string | null;
  badge?: string | null;
  enabled: boolean;
  sortOrder: number;
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
  active: boolean;
  sortOrder: number;
}

export interface AiFeatureBenefitPolicy {
  featureType: string;
  benefitCode: string;
  chargeUnit: string;
  includedInTicket: boolean;
  defaultCreditCost: number;
  active: boolean;
}

export interface MyBilling {
  currentPlanCode: string;
  currentPlanName: string;
  subscriptionStatus: string;
  periodEnd: string | null;
  creditBalance: number;
}

export interface Payment {
  id: number;
  provider: string;
  productType: string;
  productCode: string;
  orderId: string;
  amount: number;
  plan: string | null;
  creditAmount: number | null;
  status: string;
  paidAt: string | null;
  createdAt: string;
}

export interface UsageRow {
  featureType: string;
  used: number;
  creditUsed: number;
}

export interface CreditTransaction {
  id: number;
  type: string;
  amount: number;
  balanceAfter: number;
  reason: string | null;
  createdAt: string;
}

export type BillingCycle = "MONTHLY" | "YEARLY";

export const getPlans = () => api<SubscriptionPlan[]>("/billing/plans", {}, { auth: false });
export const getCreditProducts = () => api<CreditProduct[]>("/billing/credit-products", {}, { auth: false });
export const getFeatureBenefitPolicies = () =>
  api<AiFeatureBenefitPolicy[]>("/billing/feature-benefit-policies", {}, { auth: false });
export const getMyBilling = () => api<MyBilling>("/billing/me");
export const getMyPayments = () => api<Payment[]>("/billing/payments");
export const getMonthlyUsage = () => api<UsageRow[]>("/billing/usage");
export const getCreditTransactions = () => api<CreditTransaction[]>("/billing/credit-transactions");

export const subscribe = (planCode: string, cycle: BillingCycle = "MONTHLY") =>
  api<MyBilling>("/billing/subscribe", { method: "POST", body: JSON.stringify({ planCode, cycle }) });

export const purchaseCredits = (productCode: string) =>
  api<MyBilling>("/billing/credits/purchase", { method: "POST", body: JSON.stringify({ productCode }) });

export const cancelSubscription = () =>
  api<MyBilling>("/billing/subscription/cancel", { method: "POST" });
