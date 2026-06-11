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
