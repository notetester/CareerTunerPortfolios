export type CommunityCategory =
  | "job-review"
  | "interview-review"
  | "job-question"
  | "success-strategy"
  | "portfolio-feedback"
  | "certificate-review"
  | "free";

export type PostStatus = "PUBLISHED" | "HIDDEN" | "DELETED" | "PENDING";

export interface PublicAuthor {
  id: number;
  name: string;
  isAnonymous: boolean;
}

export interface PostStats {
  viewCount: number;
  commentCount: number;
  likeCount: number;
  bookmarkCount: number;
  /** 리액션 확장 축 — 구 데이터/목 호환을 위해 옵션(없으면 0 취급) */
  dislikeCount?: number;
  recommendCount?: number;
  disrecommendCount?: number;
  scrapCount?: number;
}

export interface CommunityPost {
  id: number;
  category: CommunityCategory;
  categoryLabel: string;
  title: string;
  content: string;
  tags: string[];
  author: PublicAuthor;
  stats: PostStats;
  status: PostStatus;
  createdAt: string;
  updatedAt?: string;
  companyName?: string;
  jobRole?: string;
  result?: string;
  isHot?: boolean;
  daysAgo?: number;
  interviewReview?: InterviewReviewMetadata;
  liked?: boolean;
  bookmarked?: boolean;
  disliked?: boolean;
  recommended?: boolean;
  disrecommended?: boolean;
  scrapped?: boolean;
  /** 뷰어의 글 구독 여부(새 댓글 알림) */
  subscribed?: boolean;
  /** 서버가 뷰어 기준으로 차단 처리한 글 — 톰스톤("차단한 사용자의 게시글입니다")만 렌더한다(조용한 차단). */
  blocked?: boolean;
}

export interface InterviewReviewMetadata {
  companyName: string;
  jobRole: string;
  interviewType: string;
  difficulty: number | null;
  interviewDate?: string;
  resultStatus?: "PASSED" | "FAILED" | "PENDING" | "UNKNOWN";
  stage?: string;
  questions: string[];
}

export interface CommunityComment {
  id: number;
  postId: number;
  parentId?: number | null;
  mentionLabel?: string | null;
  author: PublicAuthor;
  content: string;
  likeCount: number;
  dislikeCount?: number;
  recommendCount?: number;
  disrecommendCount?: number;
  isAuthor: boolean;   // 게시글 작성자(OP) 댓글 여부 — "작성자" 배지용
  mine?: boolean;      // 본인 댓글 여부 — 수정/삭제 버튼 게이팅용
  createdAt: string;
  liked?: boolean;
  disliked?: boolean;
  recommended?: boolean;
  disrecommended?: boolean;
  /** 뷰어의 댓글 구독 여부(새 답글 알림) */
  subscribed?: boolean;
  /** 서버가 내려주는 tombstone 플래그. 삭제/숨김이지만 살아있는 답글이 있어 골격만 유지하는 노드. */
  isDeleted?: boolean;
  /** 서버가 뷰어 기준으로 차단 처리한 댓글 — 톰스톤만 렌더하고 답글 트리는 유지한다(조용한 차단). */
  blocked?: boolean;
}

/** 리액션 축: RECOMMEND/DISRECOMMEND(추천 축 — 트렌드·인기글용), LIKE/DISLIKE(개인화용), BOOKMARK(즐겨찾기) */
export type ReactionType = "LIKE" | "DISLIKE" | "RECOMMEND" | "DISRECOMMEND" | "BOOKMARK";
export type TargetType = "POST" | "COMMENT";

/** 리액션 토글 응답 — 같은 축 교체가 있어 서버가 토글 후 카운트 전체를 내려준다. */
export interface ReactionCounts {
  likeCount: number;
  dislikeCount: number;
  recommendCount: number;
  disrecommendCount: number;
  bookmarkCount: number;
  scrapCount: number;
}

export interface ToggleReactionResult {
  active: boolean;
  reactionType: ReactionType;
  counts: ReactionCounts;
}

/** 게시글 반응자 목록 항목 — 익명 리액션은 본인 것만 내려온다(타인 시점 제외, 집계 포함). */
export interface PostReactor {
  reactionType: ReactionType;
  userId: number | null;
  name: string;
  anonymous: boolean;
  mine: boolean;
  createdAt: string;
}

/* ── 스크랩 (스냅샷 보존형 — 즐겨찾기와 별개) ── */

export interface ScrapItem {
  id: number;
  postId: number | null;
  title: string;
  content: string;
  authorLabel: string;
  category: string;
  anonymous: boolean;
  scrappedAt: string;
  /** 원본 글이 아직 열람 가능한지 — false 면 "원본이 삭제된 글" 배지 */
  originAvailable: boolean;
}

export interface ScrapPage {
  items: ScrapItem[];
  total: number;
  page: number;
  size: number;
}

/* ── 활동 목록 ── */

export type ActivityTabKey = "posts" | "comments" | "replies" | "likes" | "bookmarks" | "scraps";

export const ACTIVITY_TAB_LABELS: Record<ActivityTabKey, string> = {
  posts: "작성 글",
  comments: "작성 댓글",
  replies: "작성 답글",
  likes: "좋아요",
  bookmarks: "즐겨찾기",
  scraps: "스크랩",
};

export interface ActivityItem {
  itemType: "POST" | "COMMENT" | "REPLY" | "SCRAP";
  postId: number | null;
  commentId: number | null;
  scrapId: number | null;
  title: string;
  preview: string;
  reactionType: string | null;
  anonymous: boolean;
  createdAt: string;
}

export interface ActivityPage {
  tab: ActivityTabKey;
  /** 타인 프로필에서 이 탭이 공개인지 — false 면 잠금 표시 */
  allowed: boolean;
  items: ActivityItem[];
  total: number;
  page: number;
  size: number;
}

export interface ActivityTabs {
  userId: number;
  name: string;
  tabs: Record<ActivityTabKey, boolean>;
}

/* ── 리액션 유지/해지 설정 (게시글 수정 시) ── */

export type RetentionValue = "keep" | "release";

export type ReactionRetentionSettings = Record<
  "like" | "dislike" | "recommend" | "disrecommend" | "bookmark",
  RetentionValue
>;

export interface CategoryInfo {
  value: string;
  label: string;
  slug: CommunityCategory;
  colorClass: string;
}

export const CATEGORIES: CategoryInfo[] = [
  { value: "all", label: "전체", slug: "job-review", colorClass: "" },
  { value: "job", label: "취업후기", slug: "job-review", colorClass: "cat-job" },
  { value: "interview", label: "면접후기", slug: "interview-review", colorClass: "cat-interview" },
  { value: "role", label: "직무질문", slug: "job-question", colorClass: "cat-role" },
  { value: "pass", label: "합격전략", slug: "success-strategy", colorClass: "cat-pass" },
  { value: "portfolio", label: "포트폴리오", slug: "portfolio-feedback", colorClass: "cat-portfolio" },
  { value: "cert", label: "자격증후기", slug: "certificate-review", colorClass: "cat-cert" },
  { value: "free", label: "자유게시판", slug: "free", colorClass: "cat-free" },
];

export const CATEGORY_META: Record<string, { variant: string; value: string }> = {
  "취업후기": { variant: "cat-job", value: "job" },
  "면접후기": { variant: "cat-interview", value: "interview" },
  "직무질문": { variant: "cat-role", value: "role" },
  "합격전략": { variant: "cat-pass", value: "pass" },
  "포트폴리오": { variant: "cat-portfolio", value: "portfolio" },
  "자격증후기": { variant: "cat-cert", value: "cert" },
  "자유게시판": { variant: "cat-free", value: "free" },
};
