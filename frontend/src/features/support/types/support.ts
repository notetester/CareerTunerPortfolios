export interface Faq {
  id: number;
  category: string;
  question: string;
  answer: string;
}

export interface Notice {
  id: number;
  title: string;
  content: string;
  isPinned: boolean;
  tag: string;
  createdAt: string;
  viewCount: number;
}

export type TicketStatus = "RECEIVED" | "IN_PROGRESS" | "ANSWERED" | "CLOSED";

export interface SupportTicket {
  id: number;
  subject: string;
  content: string;
  status: TicketStatus;
  reply?: string;
  repliedAt?: string;
  createdAt: string;
}

export const FAQ_CATEGORIES = [
  { value: "all", label: "전체" },
  { value: "general", label: "일반" },
  { value: "account", label: "계정" },
  { value: "billing", label: "결제" },
  { value: "ai", label: "AI기능" },
  { value: "interview", label: "면접" },
] as const;

export const CONTACT_CATEGORIES = ["계정", "결제", "AI기능", "기술문제", "기타"] as const;
