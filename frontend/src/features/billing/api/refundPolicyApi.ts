import { api } from "@/app/lib/api";

export type RefundPolicyTrigger = "NOTICE" | "PAYMENT" | "CREDIT_USE" | "BENEFIT_USE";

export interface RefundPolicyRules {
  legalBasis: string;
  withdrawalDays: number;
  unusedPolicy: "FULL_REFUND" | "MANUAL_REVIEW";
  usedPolicy: "MANUAL_REVIEW" | "PRORATED_REFUND" | "NO_REFUND";
  exceptionCodes: string[];
  noticeScopes: Array<"PAYMENT" | "CREDIT_USE" | "BENEFIT_USE">;
}

export interface CurrentRefundPolicy {
  id: number;
  policyCode: string;
  version: number;
  title: string;
  summary: string | null;
  content: string;
  rulesJson: string;
  effectiveAt: string;
  noticeId: number | null;
  acknowledgedTriggers: RefundPolicyTrigger[];
}

export const defaultRefundPolicyRules: RefundPolicyRules = {
  legalBasis: "E_COMMERCE_ACT",
  withdrawalDays: 7,
  unusedPolicy: "FULL_REFUND",
  usedPolicy: "NO_REFUND",
  exceptionCodes: ["DUPLICATE_PAYMENT", "SYSTEM_ERROR", "LEGAL_REQUIREMENT"],
  noticeScopes: ["PAYMENT", "CREDIT_USE", "BENEFIT_USE"],
};

export function parseRefundPolicyRules(value: string): RefundPolicyRules {
  try {
    const parsed = JSON.parse(value) as Partial<RefundPolicyRules>;
    return {
      ...defaultRefundPolicyRules,
      ...parsed,
      exceptionCodes: Array.isArray(parsed.exceptionCodes)
        ? parsed.exceptionCodes.map(String)
        : defaultRefundPolicyRules.exceptionCodes,
      noticeScopes: Array.isArray(parsed.noticeScopes)
        ? parsed.noticeScopes as RefundPolicyRules["noticeScopes"]
        : defaultRefundPolicyRules.noticeScopes,
    };
  } catch {
    return defaultRefundPolicyRules;
  }
}

export const getCurrentRefundPolicy = () =>
  api<CurrentRefundPolicy>("/billing/refund-policy/current", { method: "GET" });

export const acknowledgeRefundPolicy = (
  policyId: number,
  triggerType: RefundPolicyTrigger,
  actionKey = "GLOBAL",
) =>
  api<CurrentRefundPolicy>("/billing/refund-policy/acknowledgements", {
    method: "POST",
    body: JSON.stringify({ policyId, triggerType, actionKey }),
  });

export function createPolicyActionKey(prefix: string) {
  const random = typeof crypto !== "undefined" && "randomUUID" in crypto
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  return `${prefix}:${random}`.replace(/[^A-Za-z0-9:_-]/g, "-").slice(0, 120);
}
