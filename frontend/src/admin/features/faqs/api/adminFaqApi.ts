import { api } from "@/app/lib/api";
import type { Faq, FaqCategory } from "../data/faqData";
import type { AdminFaqResponse } from "../types/adminFaq";

const DB_TO_KR: Record<string, FaqCategory> = {
  GENERAL: "일반",
  ACCOUNT: "계정",
  PAYMENT: "결제",
  AI_FEATURE: "AI기능",
  INTERVIEW: "면접",
};

const KR_TO_DB: Record<string, string> = {
  "일반": "GENERAL",
  "계정": "ACCOUNT",
  "결제": "PAYMENT",
  "AI기능": "AI_FEATURE",
  "면접": "INTERVIEW",
};

function toFaq(b: AdminFaqResponse): Faq {
  return {
    id: b.id,
    cat: (DB_TO_KR[b.category] ?? "일반") as FaqCategory,
    q: b.question,
    a: b.answer,
    on: b.published,
    images: [],
    yt: "",
  };
}

export function getFaqs(): Promise<Faq[]> {
  return api<AdminFaqResponse[]>("/admin/faq").then((list) => list.map(toFaq));
}

export function createFaq(data: {
  cat: FaqCategory;
  q: string;
  a: string;
  on: boolean;
}): Promise<Faq> {
  return api<AdminFaqResponse>("/admin/faq", {
    method: "POST",
    body: JSON.stringify({
      category: KR_TO_DB[data.cat] ?? "GENERAL",
      question: data.q,
      answer: data.a,
      isPublished: data.on,
      sortOrder: 0,
    }),
  }).then(toFaq);
}

export function updateFaq(
  id: number,
  data: { cat: FaqCategory; q: string; a: string; on: boolean },
): Promise<Faq> {
  return api<AdminFaqResponse>(`/admin/faq/${id}`, {
    method: "PUT",
    body: JSON.stringify({
      category: KR_TO_DB[data.cat] ?? "GENERAL",
      question: data.q,
      answer: data.a,
      isPublished: data.on,
      sortOrder: 0,
    }),
  }).then(toFaq);
}

export function deleteFaq(id: number): Promise<void> {
  return api<void>(`/admin/faq/${id}`, { method: "DELETE" });
}
