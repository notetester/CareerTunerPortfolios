import { create } from "zustand";
import * as communityApi from "../api/communityApi";
import type { CommunityPost, CommunityComment, CommunityCategory } from "../types/community";

interface CommunityState {
  /* 목록 */
  posts: CommunityPost[];
  hotPosts: { title: string; comments: number; views: number }[];
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
  toggleReaction: (
    targetType: "POST" | "COMMENT",
    targetId: number,
    reactionType: "LIKE" | "BOOKMARK",
  ) => Promise<boolean>;
}

export const useCommunityStore = create<CommunityState>((set, get) => ({
  posts: [],
  hotPosts: [],
  loading: false,
  currentPost: null,
  comments: [],
  detailLoading: false,
  error: null,

  fetchPosts: async (category, sort) => {
    set({ loading: true, error: null });
    try {
      const posts = await communityApi.getPosts(category, sort);
      set({ posts, loading: false });
    } catch (e) {
      set({ loading: false, error: (e as Error).message });
    }
  },

  fetchHotPosts: async () => {
    try {
      const hotPosts = await communityApi.getHotPosts();
      set({ hotPosts });
    } catch { /* 사이드바이므로 실패 무시 */ }
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
    } catch { /* 댓글 로딩 실패 시 빈 배열 유지 */ }
  },

  addComment: async (postId, content) => {
    try {
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
    } catch (e) {
      set({ error: (e as Error).message });
      throw e;
    }
  },

  createPost: async (data) => {
    try {
      await communityApi.createPost(data);
      const posts = await communityApi.getPosts();
      set({ posts });
    } catch (e) {
      set({ error: (e as Error).message });
      throw e;
    }
  },

  toggleReaction: async (targetType, targetId, reactionType) => {
    try {
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
    } catch (e) {
      set({ error: (e as Error).message });
      throw e;
    }
  },
}));
