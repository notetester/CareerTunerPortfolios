import { AlertCircle, CheckCircle2, Loader2 } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import type { ApplicationCaseExtraction } from "../types/applicationCase";
import { isApplicationCaseExtractionActive } from "../types/applicationCase";

const extractionClassName: Record<ApplicationCaseExtraction["status"], string> = {
  QUEUED: "border-amber-200 bg-amber-50 text-amber-700",
  RUNNING: "border-blue-200 bg-blue-50 text-blue-700",
  SUCCEEDED: "border-emerald-200 bg-emerald-50 text-emerald-700",
  FAILED: "border-red-200 bg-red-50 text-red-700",
};

export function getApplicationExtractionStatusLabel(status: ApplicationCaseExtraction["status"]): string {
  if (status === "FAILED") return "추출 실패";
  if (status === "SUCCEEDED") return "추출 완료";
  return "추출 중";
}

export function ApplicationExtractionBadge({
  extraction,
}: {
  extraction: ApplicationCaseExtraction | null | undefined;
}) {
  if (!extraction) return null;

  const active = isApplicationCaseExtractionActive(extraction.status);
  const Icon = active ? Loader2 : extraction.status === "SUCCEEDED" ? CheckCircle2 : AlertCircle;

  return (
    <Badge variant="outline" className={extractionClassName[extraction.status]}>
      <Icon className={`size-3 ${active ? "animate-spin" : ""}`} />
      {getApplicationExtractionStatusLabel(extraction.status)}
    </Badge>
  );
}
