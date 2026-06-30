import { api } from "@/app/lib/api";
import { toast } from "@/features/notification/components/toast";
import {
  acknowledgeRefundPolicy,
  createPolicyActionKey,
  type RefundPolicyTrigger,
} from "./refundPolicyApi";

export type AiChargePreviewType = "TICKET" | "CREDIT" | "FREE" | "BLOCKED";

export interface AiChargePreview {
  featureType: string;
  chargeType: AiChargePreviewType;
  benefitCode: string | null;
  chargeAmount: number;
  remainingTicket: number;
  currentCredit: number;
  sufficient: boolean;
  triggerType: RefundPolicyTrigger | null;
  actionKey: string;
  refundPolicyId: number;
  refundPolicyVersion: number;
  refundPolicyTitle: string;
  refundPolicySummary: string | null;
  refundPolicyRulesJson: string;
}

export interface AcknowledgedChargePreview {
  preview: AiChargePreview;
  policyAcknowledgementKey: string;
}

export const previewAiCharge = (
  featureType: string,
  creditCost?: number,
  actionKey = createPolicyActionKey("AI_USAGE"),
) => api<AiChargePreview>("/billing/charge-preview", {
  method: "POST",
  body: JSON.stringify({ featureType, creditCost, actionKey }),
});

/**
 * 실제 AI 요청 직전에 호출한다. 서버가 계산한 사용권/크레딧 차감량과
 * 환불정책 버전을 토스트로 고지한 뒤 동일 actionKey로 확인 이력을 남긴다.
 */
export async function notifyAndAcknowledgeAiCharge(
  featureType: string,
  creditCost?: number,
): Promise<AcknowledgedChargePreview> {
  const actionKey = createPolicyActionKey("AI_USAGE");
  const preview = await previewAiCharge(featureType, creditCost, actionKey);

  if (preview.chargeType === "BLOCKED") {
    toast.error("사용 가능한 사용권이 없고 크레딧 대체 차감도 허용되지 않습니다.", { duration: 7000 });
    throw new Error("사용 가능한 사용권이 없습니다.");
  }
  if (preview.chargeType === "CREDIT" && !preview.sufficient) {
    toast.error(`크레딧이 부족합니다. 필요 ${preview.chargeAmount}, 보유 ${preview.currentCredit}`, { duration: 7000 });
    throw new Error("크레딧이 부족합니다.");
  }

  toast.info(chargeNotice(preview), { duration: 8000 });
  if (preview.triggerType) {
    await acknowledgeRefundPolicy(
      preview.refundPolicyId,
      preview.triggerType,
      preview.actionKey,
    );
  }
  return { preview, policyAcknowledgementKey: preview.actionKey };
}

export function toastAiChargeCompleted(preview: AiChargePreview) {
  if (preview.chargeType === "TICKET") {
    toast.success("사용권 1회 차감이 완료되었습니다.");
  } else if (preview.chargeType === "CREDIT") {
    toast.success(`${preview.chargeAmount}크레딧 차감이 완료되었습니다.`);
  }
}

function chargeNotice(preview: AiChargePreview) {
  const charge = preview.chargeType === "TICKET"
    ? `사용권 1회가 차감됩니다. 차감 전 잔여 ${preview.remainingTicket}회`
    : preview.chargeType === "CREDIT"
      ? `${preview.chargeAmount}크레딧이 차감됩니다. 현재 보유 ${preview.currentCredit}크레딧`
      : "무료 이용으로 차감되지 않습니다.";
  const summary = preview.refundPolicySummary?.trim() || preview.refundPolicyTitle;
  return `${charge} · 환불정책 v${preview.refundPolicyVersion}: ${summary}`;
}
