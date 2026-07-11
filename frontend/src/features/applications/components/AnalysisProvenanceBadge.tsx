import { Cpu } from "lucide-react";
import { cn } from "@/app/components/ui/utils";
import { parseAnalysisProvenance, type AnalysisProvenanceSource } from "../types/analysis";

interface AnalysisProvenanceBadgeProps {
  /** 분석 행(provenance 필드 포함). 실제 생성 provider 가 없으면(레거시·자동 무선택) 아무것도 렌더하지 않는다. */
  source: AnalysisProvenanceSource | null | undefined;
  className?: string;
}

/**
 * 분석이 어떤 모델로 생성됐는지 보여주는 작은 뱃지. 실제 생성 provider(actualProvider)가 기록된 분석
 * (초기 등록 preferred·strict 재분석)에만 표시하고, 폴백이 있었으면 요청 provider 도 함께 노출한다.
 * provenance 가 없는 레거시·자동 무선택 분석에는 아무것도 렌더하지 않는다.
 */
export function AnalysisProvenanceBadge({ source, className }: AnalysisProvenanceBadgeProps) {
  const provenance = parseAnalysisProvenance(source);
  if (!provenance) {
    return null;
  }

  const titleParts = [`생성 모델: ${provenance.actualProviderLabel}`];
  if (provenance.actualModel) titleParts.push(provenance.actualModel);
  if (provenance.fallbackUsed && provenance.requestedProviderLabel) {
    titleParts.push(`요청 모델(${provenance.requestedProviderLabel})이 실패해 폴백됨`);
  }
  if (provenance.runModeLabel) titleParts.push(provenance.runModeLabel);

  return (
    <span
      className={cn(
        "inline-flex items-center gap-1 rounded-full border border-slate-200 bg-slate-50 px-2 py-0.5 text-xs text-slate-600",
        className,
      )}
      title={titleParts.join(" · ")}
    >
      <Cpu className="size-3 shrink-0 text-slate-500" />
      <span className="font-medium text-slate-700">{provenance.actualProviderLabel}</span>
      {provenance.actualModel && <span className="text-slate-400">· {provenance.actualModel}</span>}
      {provenance.fallbackUsed && provenance.requestedProviderLabel && (
        <span className="text-amber-600">· 폴백(요청 {provenance.requestedProviderLabel})</span>
      )}
      {provenance.runModeLabel && <span className="text-slate-400">· {provenance.runModeLabel}</span>}
    </span>
  );
}
