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

export interface ModerationDetail extends ModerationItem {
  content: string;
  model: string | null;
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
