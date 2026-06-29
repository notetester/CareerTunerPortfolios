import { api } from "@/app/lib/api";
import type { Faq, FaqCategory } from "../data/faqData";
import type { AdminFaqResponse } from "../types/adminFaq";

const DB_TO_KR: Record<string, FaqCategory> = {
  GENERAL: "일반",
  general: "일반",
  ACCOUNT: "계정",
  account: "계정",
  PAYMENT: "결제",
  payment: "결제",
  AI_FEATURE: "AI기능",
  ai_feature: "AI기능",
  INTERVIEW: "면접",
  interview: "면접",
};

const KR_TO_DB: Record<string, string> = {
  "일반": "general",
  "계정": "account",
  "결제": "payment",
  "AI기능": "ai_feature",
  "면접": "interview",
};

function toFaq(b: AdminFaqResponse): Faq {
  return {
    id: b.id,
    cat: (DB_TO_KR[b.category] ?? "일반") as FaqCategory,
    q: b.question,
    a: b.answer,
    on: b.published,
    sortOrder: b.sortOrder,
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
  data: { cat: FaqCategory; q: string; a: string; on: boolean; sortOrder?: number },
): Promise<Faq> {
  // sortOrder 는 줄 때만 전송 — 생략 시 BE 가 기존값 유지(coalesce). 토글·편집이 순서를 0으로 리셋하던 것 방지.
  const body: {
    category: string; question: string; answer: string; isPublished: boolean; sortOrder?: number;
  } = {
    category: KR_TO_DB[data.cat] ?? "GENERAL",
    question: data.q,
    answer: data.a,
    isPublished: data.on,
  };
  if (data.sortOrder !== undefined) body.sortOrder = data.sortOrder;
  return api<AdminFaqResponse>(`/admin/faq/${id}`, {
    method: "PUT",
    body: JSON.stringify(body),
  }).then(toFaq);
}

export function deleteFaq(id: number): Promise<void> {
  return api<void>(`/admin/faq/${id}`, { method: "DELETE" });
}

export function embedAllFaqs(forceAll = false): Promise<{ embeddedCount: number }> {
  return api<{ embeddedCount: number }>(
    `/admin/faq/embed-all?forceAll=${forceAll}`,
    { method: "POST" },
  );
}
