import { api } from "@/app/lib/api";
import type { CreditProduct, SubscriptionPlan } from "@/features/billing/api/billingApi";
import type { RefundPolicyRules } from "@/features/billing/api/refundPolicyApi";
import type { RefundRequestRow } from "@/features/billing/api/refundRequestApi";

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

export interface AdminRefundPolicy {
  id: number;
  policyCode: string;
  version: number;
  title: string;
  summary: string | null;
  content: string;
  rulesJson: string;
  status: "DRAFT" | "PUBLISHED";
  adverse: boolean;
  effectiveAt: string | null;
  publishedAt: string | null;
  noticeId: number | null;
  createdBy: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface SaveAdminRefundPolicyRequest {
  title: string;
  summary: string;
  content: string;
  rules: RefundPolicyRules;
  adverse: boolean;
  effectiveAt: string;
}

export const getAdminPayments = (status?: string) =>
  api<AdminPaymentRow[]>(`/admin/payments${status ? `?status=${encodeURIComponent(status)}` : ""}`, { method: "GET" });

export const getAdminPaymentSummary = () =>
  api<AdminPaymentSummary>("/admin/payments/summary", { method: "GET" });

export const getAdminRefundRequests = (status?: string) =>
  api<RefundRequestRow[]>(`/admin/refunds${status ? `?status=${encodeURIComponent(status)}` : ""}`, { method: "GET" });

export const approveAdminRefundRequest = (id: number, reviewedReason: string) =>
  api<RefundRequestRow>(`/admin/refunds/${id}/approve`, {
    method: "POST",
    body: JSON.stringify({ reviewedReason }),
  });

export const rejectAdminRefundRequest = (id: number, reviewedReason: string) =>
  api<RefundRequestRow>(`/admin/refunds/${id}/reject`, {
    method: "POST",
    body: JSON.stringify({ reviewedReason }),
  });

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

export const getAdminRefundPolicies = () =>
  api<AdminRefundPolicy[]>("/admin/refund-policies", { method: "GET" });

export const saveAdminRefundPolicyDraft = (request: SaveAdminRefundPolicyRequest) =>
  api<AdminRefundPolicy>("/admin/refund-policies/draft", {
    method: "PUT",
    body: JSON.stringify(request),
  });

export const publishAdminRefundPolicy = (id: number) =>
  api<AdminRefundPolicy>(`/admin/refund-policies/${id}/publish`, { method: "POST" });
