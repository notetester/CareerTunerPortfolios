import { mockFaqs, mockNotices } from "../data/mockSupport";
import type { Faq, Notice, SupportTicket } from "../types/support";

// TODO: 백엔드 연동 시 mock → api() 호출로 교체
// import { api } from "@/app/lib/api";

const delay = (ms = 300) => new Promise((r) => setTimeout(r, ms));

/** FAQ 목록 */
export async function getFaqs(category?: string): Promise<Faq[]> {
  await delay();
  if (!category || category === "all") return mockFaqs;
  return mockFaqs.filter((f) => f.category === category);
}

/** 공지 목록 */
export async function getNotices(): Promise<Notice[]> {
  await delay();
  return mockNotices;
}

/** 공지 상세 */
export async function getNoticeDetail(id: number): Promise<Notice | undefined> {
  await delay();
  return mockNotices.find((n) => n.id === id);
}

/** 문의 접수 */
export async function createTicket(data: {
  category: string;
  subject: string;
  content: string;
}): Promise<SupportTicket> {
  await delay(500);
  return {
    id: Date.now(),
    subject: data.subject,
    content: data.content,
    status: "RECEIVED",
    createdAt: new Date().toISOString(),
  };
}
