import { api } from "@/app/lib/api";
import type { Faq, Notice, SupportTicket, TicketAttachment, TicketThread } from "../types/support";

/** 문의 첨부 업로드 → 파일 메타. /api/file/upload(kind=ATTACHMENT) 재사용(인증 필요). */
export async function uploadTicketFile(file: File): Promise<TicketAttachment> {
  const fd = new FormData();
  fd.append("file", file);
  fd.append("kind", "ATTACHMENT");
  const r = await api<{
    id: number; originalName: string; sizeBytes: number; contentType?: string; contentUrl: string;
  }>("/file/upload", { method: "POST", body: fd });
  return { id: r.id, name: r.originalName, size: r.sizeBytes, contentType: r.contentType, contentUrl: r.contentUrl };
}

/** FAQ 목록 (공개) */
export async function getFaqs(category?: string): Promise<Faq[]> {
  const params = category ? `?category=${category}` : "";
  return api<Faq[]>(`/support/faq${params}`, {}, { auth: false });
}

/** 공지 목록 (공개) */
export async function getNotices(): Promise<Notice[]> {
  const list = await api<Notice[]>("/support/notices", {}, { auth: false });
  return list.map((n) => ({ ...n, content: "" }));
}

/** 공지 상세 (공개) */
export async function getNoticeDetail(id: number): Promise<Notice | undefined> {
  return api<Notice>(`/support/notices/${id}`, {}, { auth: false });
}

/** 문의 접수 (인증 필요) */
export async function createTicket(data: {
  category: string;
  subject: string;
  content: string;
  attachmentFileIds?: number[];
}): Promise<SupportTicket> {
  const t = await api<SupportTicket>("/support/tickets", {
    method: "POST",
    body: JSON.stringify(data),
  });
  return { ...t, content: data.content };
}

/** 내 문의 목록 (인증 필요) — 백엔드 TicketResponse 에는 content 가 없어 빈 값으로 채운다. */
export async function getMyTickets(): Promise<SupportTicket[]> {
  const list = await api<Array<Omit<SupportTicket, "content">>>("/support/tickets", { method: "GET" });
  return list.map((t) => ({ ...t, content: "" }));
}

/** 내 문의 단건 (인증 필요) — 최신 관리자 답변 포함. */
export async function getMyTicket(id: number): Promise<SupportTicket> {
  const t = await api<Omit<SupportTicket, "content">>(`/support/tickets/${id}`, { method: "GET" });
  return { ...t, content: "" };
}

/** 내 문의 전체 대화(원문 + 관리자 답변 + 추가 문의). */
export function getTicketThread(id: number): Promise<TicketThread> {
  return api<TicketThread>(`/support/tickets/${id}/messages`, { method: "GET" });
}

/** 내 문의에 추가 메시지(추가 문의)를 남긴다. 갱신된 스레드를 반환. */
export function addTicketMessage(id: number, content: string, attachmentFileIds?: number[]): Promise<TicketThread> {
  return api<TicketThread>(`/support/tickets/${id}/messages`, {
    method: "POST",
    body: JSON.stringify({ content, attachmentFileIds }),
  });
}
