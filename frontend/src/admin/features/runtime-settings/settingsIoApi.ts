import { api } from "@/app/lib/api";

/** 설정 export 봉투(요청 섹션만 채워짐). 라운드트립을 위해 구조를 그대로 보존한다. */
export interface SettingsExport {
  schemaVersion: number;
  exportedAt: string;
  runtimeSettings?: RuntimeSettingExport[];
  moderation?: ModerationExport;
}

export interface RuntimeSettingExport {
  settingKey: string;
  settingGroup: string;
  displayName: string;
  settingValue: string | null;
  fallbackValue: string | null;
  valueType: string;
  secret: boolean;
  editable: boolean;
  active: boolean;
  description: string | null;
}

export interface ModerationExport {
  strictness: string;
  hideThreshold: number;
  sanctionThreshold: number;
  blockDays: number;
  reportBlurThreshold: number;
  postRateWindowSeconds: number;
  postRateMax: number;
  commentRateWindowSeconds: number;
  commentRateMax: number;
  inquiryRateWindowSeconds: number;
  inquiryRateMax: number;
}

export interface SettingsImportResult {
  sections: { section: string; applied: number; skipped: number; messages: string[] }[];
  totalApplied: number;
  totalSkipped: number;
}

export function getSettingsSections(): Promise<string[]> {
  return api<string[]>("/admin/settings/sections");
}

export function exportSettings(sections: string[]): Promise<SettingsExport> {
  const q = sections.length ? `?sections=${encodeURIComponent(sections.join(","))}` : "";
  return api<SettingsExport>(`/admin/settings/export${q}`);
}

export function importSettings(payload: SettingsExport): Promise<SettingsImportResult> {
  return api<SettingsImportResult>("/admin/settings/import", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}
