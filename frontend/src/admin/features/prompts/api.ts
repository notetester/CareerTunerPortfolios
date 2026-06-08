import { api } from "@/app/lib/api";
import type { AdminPromptView } from "./types";

export async function getBPromptViews(): Promise<AdminPromptView[]> {
  const [jobAnalysis, companyAnalysis] = await Promise.all([
    api<AdminPromptView>("/admin/prompts/job-analysis", { method: "GET" }),
    api<AdminPromptView>("/admin/prompts/company-analysis", { method: "GET" }),
  ]);
  return [jobAnalysis, companyAnalysis];
}
