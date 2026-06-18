import { api } from "@/app/lib/api";
import type { CreditProduct, SubscriptionPlan } from "@/features/billing/api/billingApi";

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
}

export const getAdminPayments = (status?: string) =>
  api<AdminPaymentRow[]>(`/admin/payments${status ? `?status=${encodeURIComponent(status)}` : ""}`, { method: "GET" });

export const getAdminPaymentSummary = () =>
  api<AdminPaymentSummary>("/admin/payments/summary", { method: "GET" });

export const getAdminPlans = () => api<AdminPlans>("/admin/plans", { method: "GET" });
