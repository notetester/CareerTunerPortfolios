import { api } from "@/app/lib/api";

export type CorrectionWarmupStatus =
  | "STARTED"
  | "IN_PROGRESS"
  | "ALREADY_WARM"
  | "COOLDOWN"
  | "SKIPPED";

export interface CorrectionWarmupResponse {
  status: CorrectionWarmupStatus;
  model: string;
}

export function warmupCorrectionModel() {
  return api<CorrectionWarmupResponse>("/corrections/warmup", {
    method: "POST",
  });
}
