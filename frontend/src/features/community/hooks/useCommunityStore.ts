import { create } from "zustand";
import * as communityApi from "../api/communityApi";
import type { CommunityPost, CommunityComment, CommunityCategory } from "../types/community";

interface CommunityState {
  /* 목록 */
  posts: CommunityPost[];
  hotPosts: { id: number; title: string; comments: number; views: number }[];
  /** 카테고리 slug → 게시글 수 (탭 뱃지용, 목록과 동일 소스) */
  categoryCounts: Record<string, number>;
  loading: boolean;

  /* 상세 */
  currentPost: CommunityPost | null;
  comments: CommunityComment[];
  detailLoading: boolean;

  /* 에러 */
  error: string | null;

  /* 액션 */
  fetchPosts: (category?: CommunityCategory, sort?: string) => Promise<void>;
  fetchHotPosts: () => Promise<void>;
  fetchCategoryCounts: () => Promise<void>;
  fetchPostDetail: (id: number) => Promise<void>;
  fetchComments: (postId: number) => Promise<void>;
  addComment: (postId: number, content: string) => Promise<void>;
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
    targetType: "POST" | "COMMENT",
    targetId: number,
    reactionType: "LIKE" | "BOOKMARK",
  ) => Promise<boolean>;
}

export const useCommunityStore = create<CommunityState>((set, get) => ({
  posts: [],
  hotPosts: [],
  categoryCounts: {},
  loading: false,
  currentPost: null,
  comments: [],
  detailLoading: false,
  error: null,

  fetchPosts: async (category, sort) => {
    set({ loading: true, error: null });
    try {
      // 클라이언트 페이지네이션을 쓰므로 한 번에 충분히 받아온다 (서버 size 상한 100).
      const posts = await communityApi.getPosts(category, sort, 0, 100);
      set({ posts, loading: false });
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
    set({ detailLoading: true, error: null });
    try {
      const currentPost = await communityApi.getPostDetail(id);
      set({ currentPost, detailLoading: false });
    } catch (e) {
      set({ detailLoading: false, error: (e as Error).message });
    }
  },

  fetchComments: async (postId) => {
    try {
      const comments = await communityApi.getComments(postId);
      set({ comments });
    } catch (e) {
      set({ error: (e as Error).message });
    }
  },

  addComment: async (postId, content) => {
    await communityApi.createComment(postId, content);
    const comments = await communityApi.getComments(postId);
    set({ comments });
    const { currentPost } = get();
    if (currentPost && currentPost.id === postId) {
      set({
        currentPost: {
          ...currentPost,
          stats: { ...currentPost.stats, commentCount: comments.length },
        },
      });
    }
  },

  createPost: async (data) => {
    await communityApi.createPost(data);
    const posts = await communityApi.getPosts();
    set({ posts });
  },

  updatePost: async (id, data) => {
    await communityApi.updatePost(id, data);
    const posts = await communityApi.getPosts();
    set({ posts });
  },

  toggleReaction: async (targetType, targetId, reactionType) => {
    const active = await communityApi.toggleReaction(targetType, targetId, reactionType);
    const { currentPost, comments } = get();
    const delta = active ? 1 : -1;

    if (targetType === "POST" && currentPost && currentPost.id === targetId) {
      const key = reactionType === "LIKE" ? "likeCount" : "bookmarkCount";
      set({
        currentPost: {
          ...currentPost,
          stats: { ...currentPost.stats, [key]: Math.max(0, currentPost.stats[key] + delta) },
        },
      });
    }
    if (targetType === "COMMENT") {
      set({
        comments: comments.map((c) =>
          c.id === targetId
            ? { ...c, likeCount: Math.max(0, c.likeCount + delta) }
            : c,
        ),
      });
    }
    return active;
  },
}));
