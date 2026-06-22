import { api } from "@/app/lib/api";
import type { CreditProduct, SubscriptionPlan } from "@/features/billing/api/billingApi";

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

export interface AdminPaymentRow {
  id: number;
  userId: number;
  userEmail: string | null;
  userName: string | null;
  provider: string;
  productCode: string;
  amount: number;
  plan: string | null;
  creditAmount: number | null;
  status: string;
  paidAt: string | null;
  createdAt: string;
}

export interface AdminPaymentSummary {
  totalCount: number;
  paidCount: number;
  totalRevenue: number;
}

export interface AdminPlans {
  plans: SubscriptionPlan[];
  creditProducts: CreditProduct[];
  benefitPolicies: SubscriptionBenefitPolicy[];
  featureBenefitPolicies: AiFeatureBenefitPolicy[];
}

export type BillingPolicyTargetType =
  | "SUBSCRIPTION_PLAN"
  | "CREDIT_PRODUCT"
  | "SUBSCRIPTION_BENEFIT_POLICY"
  | "AI_FEATURE_BENEFIT_POLICY";

export interface BillingPolicyChange {
  id: number;
  targetType: BillingPolicyTargetType;
  targetCode: string;
  currentSnapshotJson: string;
  nextSnapshotJson: string;
  effectiveFrom: string;
  applyMode: string;
  status: string;
  createdBy: number | null;
  createdAt: string;
  canceledBy: number | null;
  canceledAt: string | null;
}

export interface CreateBillingPolicyChangeRequest {
  targetType: BillingPolicyTargetType;
  applyMode: string;
  effectiveFrom: string;
  nextSnapshot: Record<string, unknown>;
}

export const getAdminPayments = (status?: string) =>
  api<AdminPaymentRow[]>(`/admin/payments${status ? `?status=${encodeURIComponent(status)}` : ""}`, { method: "GET" });

export const getAdminPaymentSummary = () =>
  api<AdminPaymentSummary>("/admin/payments/summary", { method: "GET" });

export const getAdminPlans = () => api<AdminPlans>("/admin/plans", { method: "GET" });

export const getBillingPolicyChanges = () =>
  api<BillingPolicyChange[]>("/admin/plans/policy-changes", { method: "GET" });

export const createBillingPolicyChange = (request: CreateBillingPolicyChangeRequest) =>
  api<BillingPolicyChange>("/admin/plans/policy-changes", {
    method: "POST",
    body: JSON.stringify(request),
  });

export const cancelBillingPolicyChange = (id: number) =>
  api<BillingPolicyChange>(`/admin/plans/policy-changes/${id}/cancel`, { method: "POST" });
