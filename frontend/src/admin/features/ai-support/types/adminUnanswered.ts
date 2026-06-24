/** 백엔드 /api/admin/chatbot/unanswered 응답(군집 1개) 1:1 매핑. */
export interface QuestionVariant {
  question: string;
  count: number;
}

export interface UnansweredCluster {
  /** 대표 행 id — 상태변경·초안·전환 대상. */
  id: number;
  /** 추천 카테고리(가장 가까웠던 FAQ의 DB 카테고리). null 가능. */
  category: string | null;
  /** 대표 질문. */
  question: string;
  /** 묶인 총 문의 수. */
  frequency: number;
  /** FAQ 최고 유사도(0~1). null 가능. */
  topSimilarity: number | null;
  /** 가장 가까웠던 기존 FAQ 질문. null 가능. */
  bestFaqQuestion: string | null;
  /** 상태(NEW/REVIEWED/CONVERTED/DISMISSED). */
  status: string;
  /** 마지막 발생 시각(ISO). */
  lastSeen: string;
  /** 묶인 변형 질문(표현별 건수). */
  variants: QuestionVariant[];
}

/** 초안 생성 결과. */
export interface FaqDraft {
  question: string;
  answer: string;
  frequency: number;
}

export type UnansweredStatus = "NEW" | "REVIEWED" | "DISMISSED" | "CONVERTED";
