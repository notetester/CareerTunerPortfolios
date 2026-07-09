import { Award, LoaderCircle } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { cn } from "@/app/components/ui/utils";
import { useAiChargePreview } from "../hooks/useAiChargePreview";
import type { AiChargePreview } from "../api/aiChargePreviewApi";

interface AiChargeCostBadgeProps {
  featureType: string;
  className?: string;
  enabled?: boolean;
  prefix?: string;
}

function creditRange(preview: AiChargePreview): string {
  const minimum = preview.minimumCreditCost;
  const maximum = preview.maximumCreditCost;
  if (minimum === maximum) return `${maximum}크레딧`;
  return `${minimum}~${maximum}크레딧`;
}

export function aiChargePreviewLabel(preview: AiChargePreview): string {
  if (preview.chargeType === "BLOCKED") return "사용 가능한 사용권 없음";
  if (preview.chargeType === "FREE") return "무료";
  if (preview.chargeType === "TICKET") {
    const fallback = preview.maximumCreditCost > 0 ? ` · 소진 시 ${creditRange(preview)}` : "";
    return `사용권 1회 · 잔여 ${preview.remainingTicket}회${fallback}`;
  }
  if (!preview.sufficient) return `크레딧 부족 · 최대 ${preview.maximumCreditCost} 필요`;
  return `예상 ${creditRange(preview)} · 실제 사용량 기준`;
}

export function AiChargeCostBadge({ featureType, className, enabled = true, prefix }: AiChargeCostBadgeProps) {
  const { preview, loading, error } = useAiChargePreview(featureType, enabled);
  const blocked = preview?.chargeType === "BLOCKED" || (preview?.chargeType === "CREDIT" && !preview.sufficient);
  const costLabel = loading
    ? "비용 확인 중"
    : error || !preview
      ? "실행 전 비용 확인"
      : aiChargePreviewLabel(preview);
  const label = prefix ? `${prefix} · ${costLabel}` : costLabel;

  return (
    <Badge
      className={cn(
        "w-fit whitespace-normal text-left",
        blocked ? "bg-red-100 text-red-700" : "bg-amber-100 text-amber-700",
        className,
      )}
      title="현재 기준 예상 비용이며 실행 직전에 정책을 다시 확인합니다."
    >
      {loading ? <LoaderCircle className="mr-1 size-3 animate-spin" /> : <Award className="mr-1 size-3" />}
      {label}
    </Badge>
  );
}
