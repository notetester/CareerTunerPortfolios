export interface ModerationItem {
  postId: number;
  title: string;
  authorName: string;
  category: string;
  status: string;
  toxic: boolean;
  aiCategory: string | null;
  confidence: number;
  attemptCount: number;
  createdAt: string;
  moderatedAt: string | null;
}

/** 본문 이미지별 검열 결과(IMAGE_MODERATION). action: hide(글 숨김) | blur(블러) | allow(통과). */
export interface ModerationImageItem {
  url: string | null;
  category: string | null;
  confidence: number | null;
  action: string | null;
}

export interface ModerationDetail extends ModerationItem {
  content: string;
  model: string | null;
  /** 텍스트(toxic/aiCategory/confidence)와 별개인 본문 이미지 검열 결과. */
  images?: ModerationImageItem[];
}

export interface ModerationPage {
  items: ModerationItem[];
  total: number;
  page: number;
  size: number;
  hasNext: boolean;
}

export interface ModerationStats {
  categories: { category: string; count: number }[];
  total: number;
}
