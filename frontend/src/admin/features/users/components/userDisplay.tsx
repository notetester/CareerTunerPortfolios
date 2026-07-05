import type { ReactNode } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import type { AdminUserStatus } from "../types";

/** 회원 화면 공용 표시 유틸 — 상태 옵션/배지 톤/포맷터/소형 표시 컴포넌트. */

export const STATUS_OPTIONS: Array<{ value: AdminUserStatus; label: string }> = [
  { value: "ACTIVE", label: "활성" },
  { value: "DORMANT", label: "휴면" },
  { value: "BLOCKED", label: "차단" },
  { value: "DELETED", label: "탈퇴/삭제" },
];

export const statusTone: Record<AdminUserStatus, string> = {
  ACTIVE: "bg-green-100 text-green-700",
  DORMANT: "bg-amber-100 text-amber-700",
  BLOCKED: "bg-red-100 text-red-700",
  DELETED: "bg-slate-200 text-slate-700",
};

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(date);
}

export function toLocalInputValue(value: string | null): string {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const offset = date.getTimezoneOffset() * 60000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 16);
}

export function toIsoOrNull(value: string): string | null {
  return value ? `${value}:00` : null;
}

export function statusLabel(status: AdminUserStatus): string {
  return STATUS_OPTIONS.find((option) => option.value === status)?.label ?? status;
}

export function summarizeJson(value: string | null | undefined, emptyText = "미입력"): string {
  if (!value || value === "[]" || value === "{}") return emptyText;
  try {
    const parsed: unknown = JSON.parse(value);
    if (Array.isArray(parsed)) return parsed.length > 0 ? `${parsed.length}개 입력` : emptyText;
    if (parsed && typeof parsed === "object") return `${Object.keys(parsed).length}개 항목`;
    return "입력됨";
  } catch {
    return value.trim() ? "입력됨" : emptyText;
  }
}

export function Info({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg bg-slate-50 px-3 py-2">
      <div className="text-[11px] font-semibold uppercase text-slate-400">{label}</div>
      <div className="mt-1 break-words text-sm font-semibold text-slate-800">{value}</div>
    </div>
  );
}

export function HistoryCard({ title, children }: { title: string; children: ReactNode }) {
  return (
    <Card className="border-slate-200 bg-card">
      <CardHeader className="pb-3">
        <CardTitle className="text-base">{title}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-2">{children}</CardContent>
    </Card>
  );
}

export function EmptyText({ text }: { text: string }) {
  return <div className="rounded-lg bg-slate-50 p-4 text-center text-sm text-slate-500">{text}</div>;
}
