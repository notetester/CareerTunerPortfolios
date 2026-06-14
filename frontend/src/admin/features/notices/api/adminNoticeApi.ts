import { api } from "@/app/lib/api";
import type { Notice, NoticeStatus } from "../data/noticesData";
import type { AdminNoticeResponse } from "../types/adminNotice";

function toNotice(b: AdminNoticeResponse): Notice {
  const status: NoticeStatus =
    b.status === "PUBLISHED" ? "published" : b.status === "DRAFT" ? "draft" : "draft";
  let date: string;
  if (status === "draft") {
    date = "임시저장";
  } else if (b.publishedAt) {
    date = b.publishedAt.slice(0, 10).replace(/-/g, ".");
  } else {
    date = b.createdAt.slice(0, 10).replace(/-/g, ".");
  }
  return {
    id: b.id,
    title: b.title,
    body: b.content,
    status,
    pinned: b.pinned,
    date,
    views: b.viewCount,
    cover: b.thumbnailUrl ?? null,
    images: [],
  };
}

export function getNotices(): Promise<Notice[]> {
  return api<AdminNoticeResponse[]>("/admin/notices").then((list) =>
    list.map(toNotice),
  );
}

export function createNotice(data: {
  title: string;
  content: string;
  status: string;
  isPinned: boolean;
  thumbnailUrl: string | null;
}): Promise<Notice> {
  return api<AdminNoticeResponse>("/admin/notices", {
    method: "POST",
    body: JSON.stringify(data),
  }).then(toNotice);
}

export function updateNotice(
  id: number,
  data: {
    title?: string;
    content?: string;
    status?: string;
    isPinned?: boolean;
    thumbnailUrl?: string | null;
  },
): Promise<Notice> {
  return api<AdminNoticeResponse>(`/admin/notices/${id}`, {
    method: "PUT",
    body: JSON.stringify(data),
  }).then(toNotice);
}

export function deleteNotice(id: number): Promise<void> {
  return api<void>(`/admin/notices/${id}`, { method: "DELETE" });
}
