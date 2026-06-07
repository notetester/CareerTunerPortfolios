import { Badge } from "@/app/components/ui/badge";
import type { ApplicationStatus } from "../types/applicationCase";
import { getApplicationStatusLabel } from "../types/applicationCase";

const statusClassName: Record<ApplicationStatus, string> = {
  DRAFT: "border-slate-200 bg-slate-100 text-slate-700",
  ANALYZING: "border-violet-200 bg-violet-50 text-violet-700",
  READY: "border-blue-200 bg-blue-50 text-blue-700",
  APPLIED: "border-emerald-200 bg-emerald-50 text-emerald-700",
  CLOSED: "border-zinc-200 bg-zinc-100 text-zinc-600",
};

export function ApplicationStatusBadge({ status }: { status: ApplicationStatus }) {
  return (
    <Badge variant="outline" className={statusClassName[status]}>
      {getApplicationStatusLabel(status)}
    </Badge>
  );
}
