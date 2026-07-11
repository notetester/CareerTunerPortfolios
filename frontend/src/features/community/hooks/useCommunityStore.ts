import { create } from "zustand";
import { ApiError } from "@/app/lib/api";
import * as communityApi from "../api/communityApi";
import type {
  CommunityPost, CommunityComment, CommunityCategory,
  ReactionType, TargetType, ToggleReactionResult,
} from "../types/community";

interface CommunityState {
  /* 목록 */
  posts: CommunityPost[];
  /** 서버 기준 전체 글 수(현재 필터/검색 조건) — Pager 전체 페이지 계산용. 서버 페이지네이션이라 posts 는 현재 페이지만 담는다. */
  total: number;
  hotPosts: { id: number; title: string; comments: number; views: number }[];
  /** 카테고리 slug → 게시글 수 (탭 뱃지용, 목록과 동일 소스) */
  categoryCounts: Record<string, number>;
  loading: boolean;

  /* 상세 */
  currentPost: CommunityPost | null;
  comments: CommunityComment[];
  detailLoading: boolean;
  /** 상세 조회가 404 — 삭제(DELETED)·숨김(HIDDEN)·없는 id를 백엔드가 모두 NOT_FOUND 로 응답한다. */
  detailNotFound: boolean;
  /** 상세·댓글 전용 에러. 목록 화면(error)과 섞으면 상세 실패가 목록에 에러로 남는다. */
  detailError: string | null;

  /* 에러 — 목록(fetchPosts) 전용 */
  error: string | null;

  /* 액션 */
  fetchPosts: (category?: CommunityCategory, sort?: string, keyword?: string, page?: number, size?: number) => Promise<void>;
  /** 챗봇 추천 모아보기(?ids=) — id 목록으로 정확 조회해 posts 를 채운다(추천 순서 보존). */
  fetchPostsByIds: (ids: number[]) => Promise<void>;
  fetchHotPosts: () => Promise<void>;
  fetchCategoryCounts: () => Promise<void>;
  fetchPostDetail: (id: number) => Promise<void>;
  fetchComments: (postId: number) => Promise<void>;
  addComment: (postId: number, content: string, parentId?: number, anonymous?: boolean) => Promise<void>;
  createPost: (data: {
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
  }) => Promise<void>;
  updatePost: (id: number, data: {
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
  }) => Promise<void>;
  toggleReaction: (
    targetType: TargetType,
    targetId: number,
    reactionType: ReactionType,
  ) => Promise<ToggleReactionResult>;
  toggleScrap: (postId: number) => Promise<boolean>;
  togglePostSubscription: (postId: number) => Promise<boolean>;
  toggleCommentSubscription: (commentId: number) => Promise<boolean>;

  /* 익명 반응 모드 — 리액션 바의 "익명으로" 드롭다운이 제어하고, 글/댓글 리액션·스크랩이 공유한다 */
  reactAnonymously: boolean;
  setReactAnonymously: (v: boolean) => void;
}

/** 토글 결과의 활성 상태로 뷰어 플래그를 재계산 — 같은 축 반대 리액션은 교체되므로 함께 끈다. */
function viewerFlagsAfterToggle(type: ReactionType, active: boolean) {
  const flags: Partial<Pick<CommunityPost, "liked" | "disliked" | "recommended" | "disrecommended" | "bookmarked">> = {};
  switch (type) {
    case "LIKE": flags.liked = active; if (active) flags.disliked = false; break;
    case "DISLIKE": flags.disliked = active; if (active) flags.liked = false; break;
    case "RECOMMEND": flags.recommended = active; if (active) flags.disrecommended = false; break;
    case "DISRECOMMEND": flags.disrecommended = active; if (active) flags.recommended = false; break;
    case "BOOKMARK": flags.bookmarked = active; break;
  }
  return flags;
}

export const useCommunityStore = create<CommunityState>((set, get) => ({
  posts: [],
  total: 0,
  hotPosts: [],
  categoryCounts: {},
  loading: false,
  currentPost: null,
  comments: [],
  detailLoading: false,
  detailNotFound: false,
  detailError: null,
  error: null,

  fetchPosts: async (category, sort, keyword, page = 0, size = 20) => {
    set({ loading: true, error: null });
    try {
      // 서버 페이지네이션 — 한 페이지(size)만 받고 total 로 전체 페이지를 계산한다.
      // 정렬·검색·개인화도 서버가 처리하므로 100건 상한 없이 전체 글에 접근한다(클라 재정렬 없음).
      const { posts, total } = await communityApi.getPostsPage(category, sort, page, size, keyword);
      set({ posts, total, loading: false });
    } catch (e) {
      set({ loading: false, error: (e as Error).message });
    }
  },

  fetchPostsByIds: async (ids) => {
    set({ loading: true, error: null });
    try {
      // 추천 모아보기는 서버가 매칭 전부(≤20건)를 한 번에 내려준다 — total=현재 글 수(전 건 로드).
      const posts = await communityApi.getPostsByIds(ids);
      set({ posts, total: posts.length, loading: false });
    } catch (e) {
      set({ loading: false, error: (e as Error).message });
    }
  },

  fetchHotPosts: async () => {
    try {
      const hotPosts = await communityApi.getHotPosts();
      set({ hotPosts });
    } catch { /* 인기글 실패는 무시 */ }
  },

  fetchCategoryCounts: async () => {
    try {
      // 탭 뱃지는 목록과 동일 소스(community_post)를 봐야 한다.
      // 전체 게시글을 한 번 받아 카테고리별로 집계(목록도 size 100 상한과 동일).
      const all = await communityApi.getPosts(undefined, "latest", 0, 100);
      const counts: Record<string, number> = {};
      for (const p of all) counts[p.category] = (counts[p.category] ?? 0) + 1;
      set({ categoryCounts: counts });
    } catch { /* 카운트 실패는 무시 */ }
  },

  fetchPostDetail: async (id) => {
    // 이전 글을 먼저 비운다 — 남겨두면 조회 실패 시 직전에 보던 글(삭제한 글 포함)이 그대로 렌더된다.
    set({ currentPost: null, detailLoading: true, detailNotFound: false, detailError: null });
    try {
      const currentPost = await communityApi.getPostDetail(id);
      set({ currentPost, detailLoading: false });
    } catch (e) {
      const notFound = e instanceof ApiError && e.status === 404;
      set({ currentPost: null, detailLoading: false, detailNotFound: notFound, detailError: (e as Error).message });
    }
  },

  fetchComments: async (postId) => {
    try {
      const comments = await communityApi.getComments(postId);
      set({ comments });
    } catch (e) {
      set({ detailError: (e as Error).message });
    }
  },

  addComment: async (postId, content, parentId, anonymous = true) => {
    await communityApi.createComment(postId, content, parentId, anonymous);
    const comments = await communityApi.getComments(postId);
    set({ comments });
    const { currentPost } = get();
    if (currentPost && currentPost.id === postId) {
      set({
        currentPost: {
          ...currentPost,
          stats: { ...currentPost.stats, commentCount: comments.filter((c) => !c.isDeleted).length },
        },
      });
    }
  },

  createPost: async (data) => {
    await communityApi.createPost(data);
    // 작성 후 첫 페이지(최신)로 갱신 — 새 글이 상단에 온다. 화면은 제출 후 page 1 로 리셋해 정합을 맞춘다.
    const { posts, total } = await communityApi.getPostsPage();
    set({ posts, total });
  },

  updatePost: async (id, data) => {
    await communityApi.updatePost(id, data);
    const { posts, total } = await communityApi.getPostsPage();
    set({ posts, total });
  },

  toggleReaction: async (targetType, targetId, reactionType) => {
    const anonymous = get().reactAnonymously;
    const result = await communityApi.toggleReaction(targetType, targetId, reactionType, anonymous);
    const { currentPost, comments } = get();
    const flags = viewerFlagsAfterToggle(result.reactionType, result.active);

    // 같은 축 교체가 있어 델타 계산 대신 서버가 내려준 토글 후 카운트로 갱신한다(응답 기반 계약).
    if (targetType === "POST" && currentPost && currentPost.id === targetId) {
      set({
        currentPost: {
          ...currentPost,
          ...flags,
          stats: {
            ...currentPost.stats,
            likeCount: result.counts.likeCount,
            dislikeCount: result.counts.dislikeCount,
            recommendCount: result.counts.recommendCount,
            disrecommendCount: result.counts.disrecommendCount,
            bookmarkCount: result.counts.bookmarkCount,
            scrapCount: result.counts.scrapCount,
          },
        },
      });
    }
    if (targetType === "COMMENT") {
      set({
        comments: comments.map((c) =>
          c.id === targetId
            ? {
                ...c,
                ...flags,
                likeCount: result.counts.likeCount,
                dislikeCount: result.counts.dislikeCount,
                recommendCount: result.counts.recommendCount,
                disrecommendCount: result.counts.disrecommendCount,
              }
            : c,
        ),
      });
    }
    return result;
  },

  toggleScrap: async (postId) => {
    const anonymous = get().reactAnonymously;
    const { active, scrapCount } = await communityApi.toggleScrap(postId, anonymous);
    const { currentPost } = get();
    if (currentPost && currentPost.id === postId) {
      set({
        currentPost: {
          ...currentPost,
          scrapped: active,
          stats: { ...currentPost.stats, scrapCount },
        },
      });
    }
    return active;
  },

  togglePostSubscription: async (postId) => {
    const active = await communityApi.togglePostSubscription(postId);
    const { currentPost } = get();
    if (currentPost && currentPost.id === postId) {
      set({ currentPost: { ...currentPost, subscribed: active } });
    }
    return active;
  },

  toggleCommentSubscription: async (commentId) => {
    const active = await communityApi.toggleCommentSubscription(commentId);
    const { comments } = get();
    set({
      comments: comments.map((c) => (c.id === commentId ? { ...c, subscribed: active } : c)),
    });
    return active;
  },

  reactAnonymously: false,
  setReactAnonymously: (v) => set({ reactAnonymously: v }),
}));
