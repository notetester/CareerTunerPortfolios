import { api } from "@/app/lib/api";

export type RefundReasonCode =
  | "CHANGE_OF_MIND"
  | "DUPLICATE_PAYMENT"
  | "SYSTEM_ERROR"
  | "LEGAL_REQUIREMENT"
  | "OTHER";

export interface RefundRequestRow {
  id: number;
  paymentId: number;
  userId: number;
  userEmail: string | null;
  userName: string | null;
  orderId: string;
  productType: string;
  productCode: string;
  plan: string | null;
  paymentAmount: number;
  paidAt: string | null;
  paymentStatus: string;
  status: "REQUESTED" | "APPROVED" | "REJECTED";
  reasonCode: RefundReasonCode;
  reasonText: string | null;
  eligibilityResult: "ELIGIBLE" | "INELIGIBLE" | "REVIEW_REQUIRED";
  creditUsed: boolean;
  benefitUsed: boolean;
  refundAmount: number;
  decisionBasisJson: string;
  reviewedBy: number | null;
  reviewedReason: string | null;
  requestedAt: string;
  reviewedAt: string | null;
}

export const getMyRefundRequests = () => api<RefundRequestRow[]>("/billing/refunds");

export const createRefundRequest = (
  paymentId: number,
  reasonCode: RefundReasonCode,
  reasonText: string,
) => api<RefundRequestRow>("/billing/refunds", {
  method: "POST",
  body: JSON.stringify({ paymentId, reasonCode, reasonText: reasonText.trim() || null }),
});
