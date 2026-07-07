import { api } from "@/app/lib/api";
import type {
  AdminJobPostingFallbackSetting,
  AdminJobPostingFallbackSettingRequest,
  AdminJobPostingUploadLimitSetting,
  AdminJobPostingUploadLimitSettingRequest,
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

export function getJobPostingUploadLimitSetting(): Promise<AdminJobPostingUploadLimitSetting> {
  return api<AdminJobPostingUploadLimitSetting>("/admin/ai-settings/upload-size", { method: "GET" });
}

export function updateJobPostingUploadLimitSetting(
  request: AdminJobPostingUploadLimitSettingRequest,
): Promise<AdminJobPostingUploadLimitSetting> {
  return api<AdminJobPostingUploadLimitSetting>("/admin/ai-settings/upload-size", {
    method: "PATCH",
    body: JSON.stringify(request),
  });
}
