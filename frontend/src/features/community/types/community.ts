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
  isAuthor: boolean;   // 게시글 작성자(OP) 댓글 여부 — "작성자" 배지용
  mine?: boolean;      // 본인 댓글 여부 — 수정/삭제 버튼 게이팅용
  createdAt: string;
  liked?: boolean;
  /** 서버가 내려주는 tombstone 플래그. 삭제/숨김이지만 살아있는 답글이 있어 골격만 유지하는 노드. */
  isDeleted?: boolean;
}

export type ReactionType = "LIKE" | "BOOKMARK";
export type TargetType = "POST" | "COMMENT";

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
