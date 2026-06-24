import { api } from "@/app/lib/api";
import type { FaqDraft, UnansweredCluster, UnansweredStatus } from "../types/adminUnanswered";

/** 답 못한 질문 군집 목록(상태별, 빈도 desc/최신 desc). */
export function getUnanswered(
  status: UnansweredStatus = "NEW",
  page = 0,
  size = 50,
): Promise<UnansweredCluster[]> {
  return api<UnansweredCluster[]>(
    `/admin/chatbot/unanswered?status=${status}&page=${page}&size=${size}`,
  );
}

/** 군집 상태변경(검토중/무시). 토픽 단위로 일괄 처리된다. */
export function updateStatus(id: number, status: "REVIEWED" | "DISMISSED"): Promise<void> {
  return api<void>(`/admin/chatbot/unanswered/${id}/status`, {
    method: "PATCH",
    body: JSON.stringify({ status }),
  });
}

/** FAQ 답변 초안 생성(저장 안 함). */
export function generateDraft(id: number): Promise<FaqDraft> {
  return api<FaqDraft>(`/admin/chatbot/unanswered/${id}/draft`, { method: "POST" });
}

/** 초안을 FAQ로 등록 + 원 군집 CONVERTED 처리. */
export function convertToFaq(
  id: number,
  data: { category: string; question: string; answer: string },
): Promise<unknown> {
  return api<unknown>(`/admin/chatbot/unanswered/${id}/convert`, {
    method: "POST",
    body: JSON.stringify({
      category: data.category,
      question: data.question,
      answer: data.answer,
      isPublished: true,
      sortOrder: 0,
    }),
  });
}

/** FAQ 임베딩 인덱스 갱신(등록한 FAQ가 챗봇 검색에 반영되도록). */
export function embedAllFaqs(forceAll = false): Promise<{ embeddedCount: number }> {
  return api<{ embeddedCount: number }>(`/admin/faq/embed-all?forceAll=${forceAll}`, {
    method: "POST",
  });
}
