import { api } from "@/app/lib/api";
import { publishCreditBalanceChanged } from "@/app/lib/creditBalanceEvents";
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
  minimumCreditCost: number;
  maximumCreditCost: number;
  creditUnitTokens: number;
  usageBased: boolean;
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
    const required = preview.usageBased ? preview.maximumCreditCost : preview.chargeAmount;
    toast.error(`크레딧이 부족합니다. 최대 결제 가능액 ${required}크레딧 확보 필요 · 보유 ${preview.currentCredit}`, { duration: 7000 });
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

export interface ActualAiCharge {
  chargeType?: string | null;
  chargedCredit?: number;
  totalTokens?: number;
  remainingCredit?: number;
}

export const aiChargeAcknowledgementHeaders = (featureType: string, policyAcknowledgementKey: string) => ({
  "X-AI-Charge-Acknowledgement": policyAcknowledgementKey,
  "X-AI-Charge-Feature": featureType,
});

export async function runWithAiCharge<T>(
  featureType: string,
  operation: (headers: Record<string, string>) => Promise<T>,
): Promise<T> {
  const acknowledged = await notifyAndAcknowledgeAiCharge(featureType);
  const result = await operation(aiChargeAcknowledgementHeaders(featureType, acknowledged.policyAcknowledgementKey));
  toastAiChargeCompleted(acknowledged.preview);
  return result;
}

export function toastAiChargeCompleted(preview: AiChargePreview, actual?: ActualAiCharge) {
  const actualType = actual?.chargeType ?? preview.chargeType;
  if (actualType === "TICKET") {
    publishCreditBalanceChanged();
    toast.success("사용권 1회 차감이 완료되었습니다.");
  } else if (actualType === "CREDIT") {
    const actualRemainingCredit = actual?.remainingCredit;
    const remainingCredit = actualRemainingCredit !== undefined
      && Number.isSafeInteger(actualRemainingCredit)
      && actualRemainingCredit >= 0
      ? actualRemainingCredit
      : undefined;
    publishCreditBalanceChanged(remainingCredit);
    if (preview.usageBased && actual?.chargedCredit == null) {
      toast.success("실제 사용량 기준 크레딧 정산이 완료되었습니다.");
      return;
    }
    const charged = actual?.chargedCredit ?? preview.chargeAmount;
    const tokens = actual?.totalTokens ?? 0;
    const usage = tokens > 0 ? `${tokens.toLocaleString("ko-KR")}토큰 사용 · ` : "";
    toast.success(`${usage}${charged}크레딧 차감이 완료되었습니다.`);
  }
}

function chargeNotice(preview: AiChargePreview) {
  const charge = preview.chargeType === "TICKET"
    ? preview.maximumCreditCost > 0
      ? `사용권 1회가 우선 차감됩니다. 차감 전 잔여 ${preview.remainingTicket}회 · 사용권 소진 시 최소 ${preview.minimumCreditCost}크레딧, 실제 사용량에 따라 최대 ${preview.maximumCreditCost}크레딧이 차감됩니다.`
      : `사용권 1회가 우선 차감됩니다. 차감 전 잔여 ${preview.remainingTicket}회`
    : preview.chargeType === "CREDIT"
      ? preview.usageBased
        ? `최소 ${preview.minimumCreditCost}크레딧이 차감되며 실제 사용량에 따라 최대 ${preview.maximumCreditCost}크레딧까지 사용될 수 있습니다. 현재 보유 ${preview.currentCredit}크레딧`
        : `${preview.chargeAmount}크레딧이 차감됩니다. 현재 보유 ${preview.currentCredit}크레딧`
      : "무료 이용으로 차감되지 않습니다.";
  const variableChargeNotice = preview.chargeType === "TICKET" || preview.chargeType === "CREDIT"
    ? " · 사용권이 우선 사용되며, 기능별 실제 사용량에 따라 차감되는 크레딧은 달라질 수 있습니다."
    : "";
  const summary = preview.refundPolicySummary?.trim() || preview.refundPolicyTitle;
  return `${charge}${variableChargeNotice} · 환불정책 v${preview.refundPolicyVersion}: ${summary}`;
}
