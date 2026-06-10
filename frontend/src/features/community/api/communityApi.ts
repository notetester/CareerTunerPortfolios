import { api } from "@/app/lib/api";
import type { CommunityPost, CommunityComment, CommunityCategory, InterviewReviewMetadata } from "../types/community";

/* ── 백엔드 응답 타입 ── */

interface BackendPost {
  id: number;
  category: string;
  categoryLabel: string;
  title: string;
  content: string;
  tags: string[];
  author: { id: number; name: string; isAnonymous: boolean };
  stats: { viewCount: number; commentCount: number; likeCount: number; bookmarkCount: number };
  status: string;
  createdAt: string;
  updatedAt?: string;
  companyName?: string;
  jobRole?: string;
  interviewReview?: InterviewReviewMetadata;
  liked?: boolean;
  bookmarked?: boolean;
}

interface PostPageData {
  posts: BackendPost[];
  total: number;
  page: number;
  size: number;
}

/* ── 변환 유틸 ── */

/** 프론트 slug("job-review") → 백엔드 enum("JOB_REVIEW") */
function categoryToEnum(slug: CommunityCategory): string {
  return slug.toUpperCase().replace(/-/g, "_");
}

/** 백엔드 enum("JOB_REVIEW") → 프론트 slug("job-review") */
function enumToSlug(enumStr: string): CommunityCategory {
  return enumStr.toLowerCase().replace(/_/g, "-") as CommunityCategory;
}

const RESULT_LABELS: Record<string, string> = {
  PASSED: "합격", FAILED: "불합격", PENDING: "대기중", UNKNOWN: "비공개",
};

function mapPost(p: BackendPost): CommunityPost {
  return {
    ...p,
    category: enumToSlug(p.category),
    status: p.status as CommunityPost["status"],
    daysAgo: Math.floor((Date.now() - new Date(p.createdAt).getTime()) / 86400000),
    result: p.interviewReview?.resultStatus
      ? RESULT_LABELS[p.interviewReview.resultStatus]
      : undefined,
    liked: p.liked,
    bookmarked: p.bookmarked,
  };
}

/* ── 게시글 ── */

export async function getPosts(
  category?: CommunityCategory,
  sort = "latest",
  page = 0,
  size = 20,
) {
  const params = new URLSearchParams({ sort, page: String(page), size: String(size) });
  if (category) params.set("category", categoryToEnum(category));
  const data = await api<PostPageData>(`/community/posts?${params}`, {}, { auth: false });
  return data.posts.map(mapPost);
}

export async function getPostDetail(id: number) {
  const data = await api<BackendPost>(`/community/posts/${id}`);
  return mapPost(data);
}

export async function getHotPosts() {
  return api<{ title: string; comments: number; views: number }[]>(
    "/community/posts/hot", {}, { auth: false },
  );
}

export async function createPost(data: {
  category: CommunityCategory;
  title: string;
  content: string;
  tags: string[];
  anonymous?: boolean;
  interviewReview?: {
    companyName: string;
    jobRole: string;
    interviewType?: string;
    difficulty?: number | null;
    interviewDate?: string;
    resultStatus?: string;
    questions?: string[];
  };
}) {
  return api<{ postId: number }>("/community/posts", {
    method: "POST",
    body: JSON.stringify({
      category: categoryToEnum(data.category),
      title: data.title,
      content: data.content,
      anonymous: data.anonymous ?? true,
      tags: data.tags,
      interviewReview: data.interviewReview,
    }),
  });
}

export async function deletePost(id: number) {
  return api<void>(`/community/posts/${id}`, { method: "DELETE" });
}

/* ── 댓글 ── */

export async function getComments(postId: number) {
  return api<CommunityComment[]>(`/community/posts/${postId}/comments`);
}

export async function createComment(postId: number, content: string, parentId?: number) {
  return api<CommunityComment>(`/community/posts/${postId}/comments`, {
    method: "POST",
    body: JSON.stringify({ content, parentId: parentId ?? null }),
  });
}

export async function deleteComment(commentId: number) {
  return api<void>(`/community/comments/${commentId}`, { method: "DELETE" });
}

/* ── 좋아요/북마크 ── */

export async function toggleReaction(
  targetType: "POST" | "COMMENT",
  targetId: number,
  reactionType: "LIKE" | "BOOKMARK",
) {
  const data = await api<{ active: boolean }>("/community/reactions", {
    method: "POST",
    body: JSON.stringify({ targetType, targetId, reactionType }),
  });
  return data.active;
}

/* ── 신고 ── */

export async function createReport(
  targetType: "POST" | "COMMENT",
  targetId: number,
  reason: string,
  detail?: string,
) {
  return api<void>("/community/reports", {
    method: "POST",
    body: JSON.stringify({ targetType, targetId, reason, detail }),
  });
}
