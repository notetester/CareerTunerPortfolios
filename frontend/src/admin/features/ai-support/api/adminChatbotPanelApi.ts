import { api } from "@/app/lib/api";
import type {
  ChatbotMetrics,
  ReferencePage,
  ThresholdPreview,
  UnansweredConversation,
} from "../types/adminChatbotPanel";

/** from/to 가 있으면 쿼리스트링으로 직렬화(YYYY-MM-DD). */
function range(from?: string, to?: string): string {
  const qs = new URLSearchParams();
  if (from) qs.set("from", from);
  if (to) qs.set("to", to);
  const s = qs.toString();
  return s ? `?${s}` : "";
}

/** 메트릭 밴드 4지표(기본 최근 7일). 카드가 null 이면 "수집 중". */
export function getMetrics(from?: string, to?: string): Promise<ChatbotMetrics> {
  return api<ChatbotMetrics>(`/admin/chatbot/metrics${range(from, to)}`);
}

/** 참조 대화(답한 대화 로그) 페이지 조회. */
export function getReferences(
  from: string | undefined,
  to: string | undefined,
  page = 0,
  size = 10,
): Promise<ReferencePage> {
  const qs = new URLSearchParams();
  if (from) qs.set("from", from);
  if (to) qs.set("to", to);
  qs.set("page", String(page));
  qs.set("size", String(size));
  return api<ReferencePage>(`/admin/chatbot/references?${qs.toString()}`);
}

/** 임계값 미리보기(읽기 전용 — 실 챗봇 매칭 불변). threshold 는 0~1 분수. */
export function previewThreshold(threshold: number): Promise<ThresholdPreview> {
  return api<ThresholdPreview>(
    `/admin/chatbot/threshold/preview?threshold=${threshold}`,
  );
}

/** 공백 질문이 나온 대화 맥락(원문 + 폴백 + 주변 턴). */
export function getUnansweredConversation(id: number): Promise<UnansweredConversation> {
  return api<UnansweredConversation>(`/admin/chatbot/unanswered/${id}/conversation`);
}
