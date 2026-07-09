/**
 * AI 상담 운영 콘솔 — 3단계-2(메트릭 밴드 · 임계값 슬라이더 · 참조 대화) 응답 타입.
 * 백엔드 계약(`/api/admin/chatbot/{metrics,references,threshold/preview,unanswered/{id}/conversation}`)과 1:1.
 */

/** 시계열 1점(스파크라인 소스). */
export interface MetricPoint {
  date: string;
  count: number;
}

/**
 * 메트릭 카드 1개. 데이터가 없으면 카드 전체가 null(→ "수집 중" placeholder).
 * value/deltaVsPrev 가 null 이면 해당 칸만 "—".
 */
export interface MetricCard {
  /** 비율 카드(자동해결률·전환율)는 0~1 분수, 건수 카드는 정수. */
  value: number | null;
  /** 직전 동일 길이 기간 대비 변화량(같은 단위). */
  deltaVsPrev: number | null;
  series: MetricPoint[];
}

/** 메트릭 밴드 4지표. 각 지표는 Card 또는 null(수집 중). */
export interface ChatbotMetrics {
  /** 자동 해결률(0~1 분수). */
  autoResolveRate: MetricCard | null;
  /** FAQ 참조 응답 건수. */
  faqReferenceCount: MetricCard | null;
  /** FAQ 공백 군집 수(빨강 강조). */
  faqGap: MetricCard | null;
  /** 상담사 전환율(0~1 분수). */
  handoffRate: MetricCard | null;
}

/** 참조 대화(답한 대화 로그) 1행. */
export interface ReferenceRow {
  createdAt: string;
  question: string;
  faqQuestion: string | null;
  similarity: number | null;
  result: "해결" | "상담 전환";
}

/** 참조 대화 페이지 응답. */
export interface ReferencePage {
  content: ReferenceRow[];
  total: number;
  page: number;
  size: number;
}

/** 임계값 미리보기 히스토그램 1구간. */
export interface ThresholdBucket {
  from: number;
  to: number;
  count: number;
}

/** 임계값 미리보기 응답(읽기 전용 — 실 챗봇 매칭 불변). */
export interface ThresholdPreview {
  threshold: number;
  /** threshold 미만 = 공백으로 잡히는 수. */
  gapCount: number;
  total: number;
  histogram: ThresholdBucket[];
}

/** 발생 대화 드릴 1턴(말풍선). */
export interface ConversationTurn {
  role: string;
  text: string;
}

/** 공백 질문이 나온 대화 맥락. */
export interface UnansweredConversation {
  conversationId: number | null;
  question: string;
  fallbackMessage: string;
  contextTurns: ConversationTurn[];
}
