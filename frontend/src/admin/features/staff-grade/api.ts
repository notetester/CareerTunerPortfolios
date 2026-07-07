import { api } from "@/app/lib/api";
import { apiBase } from "@/app/lib/apiBase";
import { getAccessToken } from "@/app/lib/tokenStore";

export interface StaffGradeRow {
  id: number | null;
  userId: number;
  userEmail: string;
  userName: string;
  userRole: string;
  department: string | null;
  seniority: string | null;
  jobTier: string | null;
  payBand: string | null;
  jobGrade: string | null;
  payStep: string | null;
  baseSalary: number | null;
  currency: string | null;
  effectiveDate: string | null;
  memo: string | null;
  updatedAt: string | null;
}

export interface StaffGradePage {
  items: StaffGradeRow[];
  total: number;
  page: number;
  size: number;
}

export interface StaffGradeUpsert {
  department: string | null;
  seniority: string | null;
  jobTier: string | null;
  payBand: string | null;
  jobGrade: string | null;
  payStep: string | null;
  baseSalary: number | null;
  currency: string | null;
  effectiveDate: string | null;
  memo: string | null;
}

export interface StaffGradeHistory {
  id: number;
  userId: number;
  oldValuesJson: string | null;
  newValuesJson: string | null;
  changedBy: number | null;
  source: string;
  memo: string | null;
  createdAt: string;
}

export interface StaffCandidate {
  userId: number;
  email: string;
  name: string;
  role: string;
  hasGrade: boolean;
}

export interface ImportRow {
  rowNumber: number;
  email: string | null;
  userId: number | null;
  userName: string | null;
  department: string | null;
  seniority: string | null;
  jobTier: string | null;
  payBand: string | null;
  jobGrade: string | null;
  payStep: string | null;
  baseSalary: number | null;
  currency: string | null;
  effectiveDate: string | null;
  status: string;
  message: string | null;
}

export interface ImportPreview {
  totalRows: number;
  okCount: number;
  errorCount: number;
  rows: ImportRow[];
}

export interface ImportResult {
  appliedCount: number;
  skippedCount: number;
}

export const getStaffGrades = (keyword = "", department = "", page = 1, size = 20) => {
  const sp = new URLSearchParams();
  if (keyword) sp.set("keyword", keyword);
  if (department) sp.set("department", department);
  sp.set("page", String(page));
  sp.set("size", String(size));
  return api<StaffGradePage>(`/admin/staff-grades?${sp.toString()}`);
};

export const getStaffGrade = (userId: number) => api<StaffGradeRow>(`/admin/staff-grades/${userId}`);

export const upsertStaffGrade = (userId: number, body: StaffGradeUpsert) =>
  api<StaffGradeRow>(`/admin/staff-grades/${userId}`, { method: "PUT", body: JSON.stringify(body) });

export const getStaffGradeHistory = (userId: number) =>
  api<StaffGradeHistory[]>(`/admin/staff-grades/${userId}/history`);

export const getStaffCandidates = () => api<StaffCandidate[]>("/admin/staff-grades/candidates");

export const applyStaffImport = (rows: ImportRow[]) =>
  api<ImportResult>("/admin/staff-grades/import/apply", { method: "POST", body: JSON.stringify({ rows }) });

/** Excel/CSV 내보내기 — ApiResponse envelope 가 아닌 바이너리라 별도 fetch. */
export async function downloadStaffGradeExport(format: "excel" | "csv", keyword = "", department = ""): Promise<void> {
  const token = getAccessToken();
  const sp = new URLSearchParams();
  sp.set("format", format);
  if (keyword) sp.set("keyword", keyword);
  if (department) sp.set("department", department);
  const res = await fetch(`${apiBase()}/admin/staff-grades/export?${sp.toString()}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  if (!res.ok) throw new Error(`내보내기 실패 (${res.status})`);
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `careertuner-staff-grades.${format === "excel" ? "xlsx" : "csv"}`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

/** Excel 업로드 미리보기 — multipart 라 별도 fetch. */
export async function previewStaffImport(file: File): Promise<ImportPreview> {
  const token = getAccessToken();
  const form = new FormData();
  form.append("file", file);
  const res = await fetch(`${apiBase()}/admin/staff-grades/import/preview`, {
    method: "POST",
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: form,
  });
  const json = await res.json();
  if (!res.ok || json?.success === false) {
    throw new Error(json?.message ?? `업로드 실패 (${res.status})`);
  }
  return json.data as ImportPreview;
}
