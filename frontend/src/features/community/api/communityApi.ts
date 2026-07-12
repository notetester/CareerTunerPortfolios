import { api } from "@/app/lib/api";
import type {
  CommunityPost, CommunityComment, CommunityCategory, InterviewReviewMetadata,
  ReactionType, TargetType, ToggleReactionResult, PostReactor,
  ScrapItem, ScrapPage, ActivityPage, ActivityTabs, ActivityTabKey,
  ReactionRetentionSettings,
} from "../types/community";

/* ── 백엔드 응답 타입 ── */

interface BackendPost {
  id: number;
  category: string;
  categoryLabel: string;
  title: string;
  content: string;
  tags: string[];
  author: { id: number | null; name: string; nicknameProfileId?: number | null; isAnonymous: boolean };
  stats: {
    viewCount: number; commentCount: number;
    likeCount: number; dislikeCount?: number;
    recommendCount?: number; disrecommendCount?: number;
    bookmarkCount: number; scrapCount?: number;
  };
  status: string;
  createdAt: string;
  updatedAt?: string;
  companyName?: string;
  jobRole?: string;
  interviewReview?: InterviewReviewMetadata;
  liked?: boolean;
  disliked?: boolean;
  recommended?: boolean;
  disrecommended?: boolean;
  bookmarked?: boolean;
  scrapped?: boolean;
  subscribed?: boolean;
  /** 뷰어가 차단한 작성자의 글이면 true — 톰스톤 렌더용(조용한 차단). */
  blocked?: boolean;
  /** 신고 누적으로 가려진 글(비작성자에게 블러). */
  blurred?: boolean;
  reportCount?: number;
  /** AI 이미지 검열에서 블러 대상으로 판정된 본문 이미지 + 사유(상세 응답에만 포함). */
  blurredImages?: { url: string; category: string | null }[];
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
    disliked: p.disliked,
    recommended: p.recommended,
    disrecommended: p.disrecommended,
    bookmarked: p.bookmarked,
    scrapped: p.scrapped,
    subscribed: p.subscribed,
  };
}

/* ── 게시글 ── */

export async function getPosts(
  category?: CommunityCategory,
  sort = "latest",
  page = 0,
  size = 20,
  keyword?: string,
) {
  const params = new URLSearchParams({ sort, page: String(page), size: String(size) });
  if (category) params.set("category", categoryToEnum(category));
  if (keyword && keyword.trim()) params.set("keyword", keyword.trim());
  // 로그인 사용자면 토큰을 붙여 서버가 뷰어 컨텍스트를 갖게 한다(개인화 sort='personalized'와
  // 차단 작성자 필터가 뷰어 없이는 동작하지 않는다). 엔드포인트는 permitAll 이라 비로그인은 헤더만 생략된다.
  const data = await api<PostPageData>(`/community/posts?${params}`, {}, { auth: true });
  return data.posts.map(mapPost);
}

/** 챗봇 추천 모아보기 — id 목록으로 정확 조회(입력 순서 보존, 서버가 차단·블라인드 필터 적용). */
export async function getPostsByIds(ids: number[]) {
  const params = new URLSearchParams({ ids: ids.join(",") });
  const data = await api<PostPageData>(`/community/posts?${params}`, {}, { auth: true });
  return data.posts.map(mapPost);
}

export async function getPostDetail(id: number) {
  const data = await api<BackendPost>(`/community/posts/${id}`);
  return mapPost(data);
}

export async function getHotPosts() {
  return api<{ id: number; title: string; comments: number; views: number }[]>(
    "/community/posts/hot", {}, { auth: false },
  );
}

export async function createPost(data: {
  category: CommunityCategory;
  title: string;
  content: string;
  tags: string[];
  anonymous?: boolean;
  /** 비익명 작성 시 표시할 닉네임 프로필 id(선택). 없으면 계정 기본 프로필/계정명으로 표시. */
  nicknameProfileId?: number | null;
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
      nicknameProfileId: data.nicknameProfileId ?? null,
      tags: data.tags,
      interviewReview: data.interviewReview,
    }),
  });
}

export async function updatePost(id: number, data: {
  title: string;
  content: string;
  tags: string[];
  anonymous?: boolean;
  /** 비익명 작성 시 표시할 닉네임 프로필 id(선택). 없으면 계정 기본 프로필/계정명으로 표시. */
  nicknameProfileId?: number | null;
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
  return api<void>(`/community/posts/${id}`, {
    method: "PUT",
    body: JSON.stringify({
      title: data.title,
      content: data.content,
      anonymous: data.anonymous ?? true,
      nicknameProfileId: data.nicknameProfileId ?? null,
      tags: data.tags,
      interviewReview: data.interviewReview,
    }),
  });
}

export async function deletePost(id: number) {
  return api<void>(`/community/posts/${id}`, { method: "DELETE" });
}

export async function adminUpdatePostStatus(id: number, status: "PUBLISHED" | "HIDDEN" | "DELETED", reason?: string) {
  if (status === "DELETED") {
    return api<void>(`/admin/community/posts/${id}`, {
      method: "DELETE",
      body: JSON.stringify({ status, reason }),
    });
  }
  return api<void>(`/admin/community/posts/${id}/status`, {
    method: "PATCH",
    body: JSON.stringify({ status, reason }),
  });
}

/* ── AI 태그 ── */

export interface AiTagResult {
  postId: number;
  taskType: string;
  status: string;
  resultJson: string | null;
}

export interface ParsedAiTags {
  tags: string[];
  confidence: number;
  applied: boolean;
}

export async function getAiTags(postId: number): Promise<ParsedAiTags | null> {
  try {
    const result = await api<AiTagResult | null>(`/community/posts/${postId}/ai-tags`);
    if (!result?.resultJson) return null;
    return JSON.parse(result.resultJson) as ParsedAiTags;
  } catch {
    return null;
  }
}

/* ── 댓글 ── */

export async function getComments(postId: number) {
  return api<CommunityComment[]>(`/community/posts/${postId}/comments`);
}

export async function createComment(
  postId: number,
  content: string,
  parentId?: number,
  anonymous = true,
  nicknameProfileId?: number | null,
) {
  return api<CommunityComment>(`/community/posts/${postId}/comments`, {
    method: "POST",
    body: JSON.stringify({
      content,
      parentId: parentId ?? null,
      anonymous,
      nicknameProfileId: nicknameProfileId ?? null,
    }),
  });
}

export async function updateComment(commentId: number, content: string) {
  return api<CommunityComment>(`/community/comments/${commentId}`, {
    method: "PUT",
    body: JSON.stringify({ content }),
  });
}

export async function deleteComment(commentId: number) {
  return api<void>(`/community/comments/${commentId}`, { method: "DELETE" });
}

/* ── 리액션 (추천/비추천 · 좋아요/싫어요 · 즐겨찾기) ── */

/**
 * 리액션 토글 — 같은 축(추천↔비추천, 좋아요↔싫어요)에서 반대 클릭 시 교체,
 * 같은 것 재클릭 시 취소. 서버가 토글 후 카운트 전체를 내려준다(응답 기반 UI 갱신).
 */
export async function toggleReaction(
  targetType: TargetType,
  targetId: number,
  reactionType: ReactionType,
  anonymous = false,
) {
  return api<ToggleReactionResult>("/community/reactions", {
    method: "POST",
    body: JSON.stringify({ targetType, targetId, reactionType, anonymous }),
  });
}

/** 게시글 반응자 목록 — 익명 리액션은 본인 것만 내려온다(타인 시점 제외). */
export async function getPostReactors(postId: number) {
  return api<PostReactor[]>(`/community/posts/${postId}/reactions`);
}

/* ── 스크랩 (스냅샷 보존형 — 즐겨찾기와 별개) ── */

export async function toggleScrap(postId: number, anonymous = false) {
  return api<{ active: boolean; scrapCount: number }>(`/community/posts/${postId}/scrap`, {
    method: "POST",
    body: JSON.stringify({ anonymous }),
  });
}

export async function getMyScraps(page = 0, size = 20) {
  return api<ScrapPage>(`/community/scraps?page=${page}&size=${size}`);
}

export async function getScrapDetail(scrapId: number) {
  return api<ScrapItem>(`/community/scraps/${scrapId}`);
}

export async function deleteScrap(scrapId: number) {
  return api<void>(`/community/scraps/${scrapId}`, { method: "DELETE" });
}

/* ── 구독 (글: 새 댓글 알림 / 댓글: 새 답글 알림) ── */

export async function togglePostSubscription(postId: number) {
  const data = await api<{ active: boolean }>(`/community/posts/${postId}/subscription`, { method: "POST" });
  return data.active;
}

export async function toggleCommentSubscription(commentId: number) {
  const data = await api<{ active: boolean }>(`/community/comments/${commentId}/subscription`, { method: "POST" });
  return data.active;
}

/* ── 리액션 유지/해지 설정 (게시글 수정 시 keep/release) ── */

export async function getReactionSettings() {
  return api<ReactionRetentionSettings>("/community/reaction-settings");
}

export async function updateReactionSettings(settings: Partial<ReactionRetentionSettings>) {
  return api<ReactionRetentionSettings>("/community/reaction-settings", {
    method: "PUT",
    body: JSON.stringify(settings),
  });
}

/* ── 활동 목록 (내 활동 / 타인 프로필 활동) ── */

export async function getMyActivity(tab: ActivityTabKey, page = 0, size = 20) {
  return api<ActivityPage>(`/community/me/activity?tab=${tab}&page=${page}&size=${size}`);
}

export async function getUserActivity(userId: number, tab: ActivityTabKey, page = 0, size = 20) {
  return api<ActivityPage>(`/community/users/${userId}/activity?tab=${tab}&page=${page}&size=${size}`);
}

/** 타인 프로필 활동 탭 헤더 — 탭별 공개 여부(비공개 탭은 잠금 표시). */
export async function getUserActivityTabs(userId: number) {
  return api<ActivityTabs>(`/community/users/${userId}/activity-tabs`);
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
