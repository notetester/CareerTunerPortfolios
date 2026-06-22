import { AlertCircle, AlertTriangle, CheckCircle2, Loader2, ShieldCheck } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import type { ApplicationCaseExtraction, ApplicationCaseExtractionQualityStatus } from "../types/applicationCase";
import { isApplicationCaseExtractionActive } from "../types/applicationCase";

const extractionClassName: Record<ApplicationCaseExtraction["status"], string> = {
  QUEUED: "border-amber-200 bg-amber-50 text-amber-700",
  RUNNING: "border-blue-200 bg-blue-50 text-blue-700",
  SUCCEEDED: "border-emerald-200 bg-emerald-50 text-emerald-700",
  FAILED: "border-red-200 bg-red-50 text-red-700",
};

const qualityClassName: Record<ApplicationCaseExtractionQualityStatus, string> = {
  PASS: "border-emerald-200 bg-emerald-50 text-emerald-700",
  REVIEW_REQUIRED: "border-amber-200 bg-amber-50 text-amber-700",
  FAILED: "border-red-200 bg-red-50 text-red-700",
};

export function getApplicationExtractionStatusLabel(status: ApplicationCaseExtraction["status"]): string {
  if (status === "FAILED") return "추출 실패";
  if (status === "SUCCEEDED") return "추출 완료";
  if (status === "RUNNING") return "추출 중";
  return "추출 대기";
}

function getQualityStatusLabel(status: ApplicationCaseExtractionQualityStatus): string {
  if (status === "PASS") return "품질 통과";
  if (status === "REVIEW_REQUIRED") return "검수 필요";
  return "품질 실패";
}

export function ApplicationExtractionBadge({
  extraction,
}: {
  extraction: ApplicationCaseExtraction | null | undefined;
}) {
  if (!extraction) return null;

  const active = isApplicationCaseExtractionActive(extraction.status);
  const Icon = active ? Loader2 : extraction.status === "SUCCEEDED" ? CheckCircle2 : AlertCircle;
  const qualityStatus = extraction.qualityStatus;
  const QualityIcon = qualityStatus === "PASS" ? ShieldCheck : AlertTriangle;

  return (
    <>
      <Badge variant="outline" className={extractionClassName[extraction.status]}>
        <Icon className={`size-3 ${active ? "animate-spin" : ""}`} />
        {getApplicationExtractionStatusLabel(extraction.status)}
      </Badge>
      {qualityStatus && (
        <Badge variant="outline" className={qualityClassName[qualityStatus]}>
          <QualityIcon className="size-3" />
          {getQualityStatusLabel(qualityStatus)}
          {typeof extraction.qualityScore === "number" ? ` ${extraction.qualityScore}` : ""}
        </Badge>
      )}
    </>
  );
}
