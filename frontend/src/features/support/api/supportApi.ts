import { api } from "@/app/lib/api";
import type { Faq, Notice, SupportTicket } from "../types/support";

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
