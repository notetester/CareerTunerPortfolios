import { api } from "@/app/lib/api";
import type {
  AdminJobPostingFallbackSetting,
  AdminJobPostingFallbackSettingRequest,
} from "./types";

export function getJobPostingFallbackSetting(): Promise<AdminJobPostingFallbackSetting> {
  return api<AdminJobPostingFallbackSetting>("/admin/ai-settings/job-posting-fallback", { method: "GET" });
}

export function updateJobPostingFallbackSetting(
  request: AdminJobPostingFallbackSettingRequest,
): Promise<AdminJobPostingFallbackSetting> {
  return api<AdminJobPostingFallbackSetting>("/admin/ai-settings/job-posting-fallback", {
    method: "PATCH",
    body: JSON.stringify(request),
  });
}
