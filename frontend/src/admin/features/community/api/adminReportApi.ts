import { api } from "@/app/lib/api";
import type { Report } from "../data/reportsData";
import type {
  AdminReportListResponse,
  AdminReportDetailResponse,
} from "../types/adminReport";

function toActionLabel(action: string | null): string | undefined {
  if (!action || action === "NONE") return undefined;
  switch (action) {
    case "HIDDEN":  return "숨김 처리됨";
    case "DELETED": return "삭제됨";
    default:        return action;
  }
}

function listToReport(b: AdminReportListResponse): Report {
  return {
    id: b.id,
    reason: b.reason ?? "",
    type: b.type as "게시글" | "댓글",
    cnt: b.cnt,
    title: b.title ?? "",
    excerpt: b.excerpt ?? "",
    cat: b.cat ?? "",
    catKey: b.catKey ?? "free",
    author: b.author ?? "",
    time: formatTime(b.time),
    status: b.status === "pending" ? "pending" : "resolved",
    action: toActionLabel(b.action),
    reasons: [],
  };
}

function detailToReport(b: AdminReportDetailResponse): Report {
  return {
    ...listToReport(b),
    reasons: b.reasons ?? [],
    aiOpinion: b.aiOpinion ?? null,
  };
}

/** 백엔드에서 time은 분 단위 숫자로 온다 (TIMESTAMPDIFF MINUTE) */
function formatTime(time: string | number): string {
  const mins = Number(time);
  if (isNaN(mins)) return String(time);
  if (mins < 1) return "방금 전";
  if (mins < 60) return `${mins}분 전`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours}시간 전`;
  return `${Math.floor(hours / 24)}일 전`;
}

export function getReports(status?: "pending" | "resolved"): Promise<Report[]> {
  const q = status ? `?status=${status}` : "";
  return api<AdminReportListResponse[]>(`/admin/community/reports${q}`).then(
    (list) => list.map(listToReport),
  );
}

export function getReportDetail(id: number): Promise<Report> {
  return api<AdminReportDetailResponse>(`/admin/community/reports/${id}`).then(
    detailToReport,
  );
}

export function takeAction(
  id: number,
  action: "HIDDEN" | "DELETED" | "DISMISSED",
): Promise<Report> {
  return api<AdminReportDetailResponse>(`/admin/community/reports/${id}/action`, {
    method: "POST",
    body: JSON.stringify({ action }),
  }).then(detailToReport);
}

export function reclassify(id: number): Promise<Report> {
  return api<AdminReportDetailResponse>(`/admin/community/reports/${id}/reclassify`, {
    method: "POST",
  }).then(detailToReport);
}
