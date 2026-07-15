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

let communityGeneration = 0;
let postListSequence = 0;
let hotPostSequence = 0;
let categoryCountSequence = 0;
let detailSequence = 0;
let commentSequence = 0;
let activeDetailPostId: number | null = null;

const isCurrentGeneration = (generation: number) => generation === communityGeneration;
const isCurrentDetail = (generation: number, sequence: number, postId: number) =>
  isCurrentGeneration(generation) && sequence === detailSequence && activeDetailPostId === postId;
const isCurrentComments = (generation: number, sequence: number, postId: number) =>
  isCurrentGeneration(generation) && sequence === commentSequence && activeDetailPostId === postId;

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
    const generation = communityGeneration;
    const sequence = ++postListSequence;
    set({ loading: true, error: null });
    try {
      // 서버 페이지네이션 — 한 페이지(size)만 받고 total 로 전체 페이지를 계산한다.
      // 정렬·검색·개인화도 서버가 처리하므로 100건 상한 없이 전체 글에 접근한다(클라 재정렬 없음).
      const { posts, total } = await communityApi.getPostsPage(category, sort, page, size, keyword);
      if (!isCurrentGeneration(generation) || sequence !== postListSequence) return;
      set({ posts, total, loading: false });
    } catch (e) {
      if (!isCurrentGeneration(generation) || sequence !== postListSequence) return;
      set({ loading: false, error: (e as Error).message });
    }
  },

  fetchPostsByIds: async (ids) => {
    const generation = communityGeneration;
    const sequence = ++postListSequence;
    set({ loading: true, error: null });
    try {
      // 추천 모아보기는 서버가 매칭 전부(≤20건)를 한 번에 내려준다 — total=현재 글 수(전 건 로드).
      const posts = await communityApi.getPostsByIds(ids);
      if (!isCurrentGeneration(generation) || sequence !== postListSequence) return;
      set({ posts, total: posts.length, loading: false });
    } catch (e) {
      if (!isCurrentGeneration(generation) || sequence !== postListSequence) return;
      set({ loading: false, error: (e as Error).message });
    }
  },

  fetchHotPosts: async () => {
    const generation = communityGeneration;
    const sequence = ++hotPostSequence;
    try {
      const hotPosts = await communityApi.getHotPosts();
      if (!isCurrentGeneration(generation) || sequence !== hotPostSequence) return;
      set({ hotPosts });
    } catch { /* 인기글 실패는 무시 */ }
  },

  fetchCategoryCounts: async () => {
    const generation = communityGeneration;
    const sequence = ++categoryCountSequence;
    try {
      // 탭 뱃지는 서버 전수 집계를 쓴다 — 최신 100건 표본 집계는 오래된 카테고리 글이
      // 표본 밖으로 밀리면 뱃지가 사라지거나 수가 모자라는 문제가 있었다.
      const counts = await communityApi.getCategoryCounts();
      if (!isCurrentGeneration(generation) || sequence !== categoryCountSequence) return;
      set({ categoryCounts: counts });
    } catch { /* 카운트 실패는 무시 */ }
  },

  fetchPostDetail: async (id) => {
    const generation = communityGeneration;
    const sequence = ++detailSequence;
    activeDetailPostId = id;
    commentSequence += 1;
    // 이전 글을 먼저 비운다 — 남겨두면 조회 실패 시 직전에 보던 글(삭제한 글 포함)이 그대로 렌더된다.
    set({ currentPost: null, comments: [], detailLoading: true, detailNotFound: false, detailError: null });
    try {
      const currentPost = await communityApi.getPostDetail(id);
      if (!isCurrentDetail(generation, sequence, id)) return;
      set({ currentPost, detailLoading: false });
    } catch (e) {
      if (!isCurrentDetail(generation, sequence, id)) return;
      const notFound = e instanceof ApiError && e.status === 404;
      set({ currentPost: null, detailLoading: false, detailNotFound: notFound, detailError: (e as Error).message });
    }
  },

  fetchComments: async (postId) => {
    const generation = communityGeneration;
    const sequence = ++commentSequence;
    try {
      const comments = await communityApi.getComments(postId);
      if (!isCurrentComments(generation, sequence, postId)) return;
      set({ comments });
    } catch (e) {
      if (!isCurrentComments(generation, sequence, postId)) return;
      set({ detailError: (e as Error).message });
    }
  },

  addComment: async (postId, content, parentId, anonymous = true) => {
    const generation = communityGeneration;
    await communityApi.createComment(postId, content, parentId, anonymous);
    if (!isCurrentGeneration(generation) || activeDetailPostId !== postId) return;
    const sequence = ++commentSequence;
    const comments = await communityApi.getComments(postId);
    if (!isCurrentComments(generation, sequence, postId)) return;
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
    const generation = communityGeneration;
    await communityApi.createPost(data);
    if (!isCurrentGeneration(generation)) return;
    const sequence = ++postListSequence;
    // 작성 후 첫 페이지(최신)로 갱신 — 새 글이 상단에 온다. 화면은 제출 후 page 1 로 리셋해 정합을 맞춘다.
    const { posts, total } = await communityApi.getPostsPage();
    if (!isCurrentGeneration(generation) || sequence !== postListSequence) return;
    set({ posts, total });
  },

  updatePost: async (id, data) => {
    const generation = communityGeneration;
    await communityApi.updatePost(id, data);
    if (!isCurrentGeneration(generation)) return;
    const sequence = ++postListSequence;
    const { posts, total } = await communityApi.getPostsPage();
    if (!isCurrentGeneration(generation) || sequence !== postListSequence) return;
    set({ posts, total });
  },

  toggleReaction: async (targetType, targetId, reactionType) => {
    const generation = communityGeneration;
    const anonymous = get().reactAnonymously;
    const result = await communityApi.toggleReaction(targetType, targetId, reactionType, anonymous);
    if (!isCurrentGeneration(generation)) return result;
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
    const generation = communityGeneration;
    const anonymous = get().reactAnonymously;
    const { active, scrapCount } = await communityApi.toggleScrap(postId, anonymous);
    if (!isCurrentGeneration(generation)) return active;
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
    const generation = communityGeneration;
    const active = await communityApi.togglePostSubscription(postId);
    if (!isCurrentGeneration(generation)) return active;
    const { currentPost } = get();
    if (currentPost && currentPost.id === postId) {
      set({ currentPost: { ...currentPost, subscribed: active } });
    }
    return active;
  },

  toggleCommentSubscription: async (commentId) => {
    const generation = communityGeneration;
    const active = await communityApi.toggleCommentSubscription(commentId);
    if (!isCurrentGeneration(generation)) return active;
    const { comments } = get();
    set({
      comments: comments.map((c) => (c.id === commentId ? { ...c, subscribed: active } : c)),
    });
    return active;
  },

  reactAnonymously: false,
  setReactAnonymously: (v) => set({ reactAnonymously: v }),
}));

/** 계정 교체·로그아웃 시 viewer별 차단/반응 상태와 늦은 응답을 함께 폐기한다. */
export function resetCommunityState(): void {
  communityGeneration += 1;
  postListSequence += 1;
  hotPostSequence += 1;
  categoryCountSequence += 1;
  detailSequence += 1;
  commentSequence += 1;
  activeDetailPostId = null;
  useCommunityStore.setState({
    posts: [], hotPosts: [], categoryCounts: {}, loading: false,
    currentPost: null, comments: [], detailLoading: false,
    detailNotFound: false, detailError: null, error: null,
    reactAnonymously: false,
  });
}
