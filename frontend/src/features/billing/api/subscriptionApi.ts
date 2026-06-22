import { api } from "@/app/lib/api";
import type { MyBenefits, SubscriptionPlan } from "../types/billing";

export function listSubscriptionPlans(): Promise<SubscriptionPlan[]> {
  return api<SubscriptionPlan[]>("/billing/plans", { method: "GET" }, { auth: false });
}

export function getMyBenefits(): Promise<MyBenefits> {
  return api<MyBenefits>("/billing/benefits/me", { method: "GET" });
}
