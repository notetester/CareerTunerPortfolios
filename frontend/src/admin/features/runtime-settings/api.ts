import { api } from "@/app/lib/api";

export interface RuntimeSetting {
  id: number;
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
  updatedBy: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface RuntimeSettingHistory {
  id: number;
  settingId: number | null;
  settingKey: string;
  versionNo: number;
  changeType: string;
  actorUserId: number | null;
  beforeValue: string | null;
  afterValue: string | null;
  reason: string | null;
  createdAt: string;
}

export interface RuntimeSettingSave {
  settingKey: string;
  settingGroup?: string;
  displayName?: string;
  settingValue?: string | null;
  fallbackValue?: string | null;
  valueType?: string;
  secret?: boolean;
  editable?: boolean;
  active?: boolean;
  description?: string | null;
  reason?: string;
}

export function getRuntimeSettings(group = "", keyword = "", includeInactive = false): Promise<RuntimeSetting[]> {
  const sp = new URLSearchParams();
  if (group) sp.set("group", group);
  if (keyword) sp.set("keyword", keyword);
  if (includeInactive) sp.set("includeInactive", "true");
  const q = sp.toString();
  return api<RuntimeSetting[]>(`/admin/runtime-settings${q ? `?${q}` : ""}`);
}

export function saveRuntimeSetting(payload: RuntimeSettingSave): Promise<RuntimeSetting> {
  return api<RuntimeSetting>("/admin/runtime-settings", { method: "POST", body: JSON.stringify(payload) });
}

export function getRuntimeSettingHistory(key = "", limit = 100): Promise<RuntimeSettingHistory[]> {
  const sp = new URLSearchParams();
  if (key) sp.set("key", key);
  sp.set("limit", String(limit));
  return api<RuntimeSettingHistory[]>(`/admin/runtime-settings/history?${sp.toString()}`);
}
