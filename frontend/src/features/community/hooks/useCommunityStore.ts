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
    } catch (error) {
      set({
        posts: [],
        loading: false,
        error: error instanceof Error ? error.message : "게시글을 불러오지 못했습니다.",
      });
    }
  },

  fetchHotPosts: async () => {
    try {
      const hotPosts = await communityApi.getHotPosts();
      set({ hotPosts });
    } catch (error) {
      set({ hotPosts: [], error: error instanceof Error ? error.message : "인기글을 불러오지 못했습니다." });
    }
  },

  fetchPostDetail: async (id) => {
    set({ detailLoading: true, error: null });
    try {
      const currentPost = await communityApi.getPostDetail(id);
      set({ currentPost, detailLoading: false });
    } catch (error) {
      set({
        currentPost: null,
        detailLoading: false,
        error: error instanceof Error ? error.message : "게시글을 불러오지 못했습니다.",
      });
    }
  },

  fetchComments: async (postId) => {
    try {
      const comments = await communityApi.getComments(postId);
      set({ comments });
    } catch (error) {
      set({ comments: [], error: error instanceof Error ? error.message : "댓글을 불러오지 못했습니다." });
    }
  },

  addComment: async (postId, content) => {
    const newComment = await communityApi.createComment(postId, content);
    const comments = [...get().comments, newComment];
    set({ comments, error: null });
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
    await get().fetchPosts(undefined, "latest");
  },

  toggleReaction: async (targetType, targetId, reactionType) => {
    const active = await communityApi.toggleReaction(targetType, targetId, reactionType);
    const { currentPost, comments } = get();
    const delta = active ? 1 : -1;
    const applyPostReaction = (post: CommunityPost): CommunityPost => {
      const key = reactionType === "LIKE" ? "likeCount" : "bookmarkCount";
      const flag = reactionType === "LIKE" ? "liked" : "bookmarked";
      return {
        ...post,
        [flag]: active,
        stats: { ...post.stats, [key]: Math.max(0, post.stats[key] + delta) },
      };
    };

    if (targetType === "POST" && currentPost && currentPost.id === targetId) {
      set({ currentPost: applyPostReaction(currentPost) });
    }
    if (targetType === "POST") {
      set({ posts: get().posts.map((post) => (post.id === targetId ? applyPostReaction(post) : post)) });
    }
    if (targetType === "COMMENT") {
      set({
        comments: comments.map((c) =>
          c.id === targetId
            ? { ...c, liked: active, likeCount: Math.max(0, c.likeCount + delta) }
            : c,
        ),
      });
    }
    return active;
  },
}));
