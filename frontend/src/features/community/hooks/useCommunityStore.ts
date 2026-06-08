import { create } from "zustand";
import * as communityApi from "../api/communityApi";
import type { CommunityPost, CommunityComment, CommunityCategory } from "../types/community";

interface CommunityState {
  /* 목록 */
  posts: CommunityPost[];
  hotPosts: { title: string; comments: number; views: number }[];
  loading: boolean;

  /* 상세 */
  currentPost: typeof import("../data/mockCommunity").mockPostDetail | null;
  comments: CommunityComment[];
  detailLoading: boolean;

  /* 액션 */
  fetchPosts: (category?: CommunityCategory) => Promise<void>;
  fetchHotPosts: () => Promise<void>;
  fetchPostDetail: (id: number) => Promise<void>;
  fetchComments: (postId: number) => Promise<void>;
  addComment: (postId: number, content: string) => Promise<void>;
  createPost: (data: {
    category: CommunityCategory;
    title: string;
    content: string;
    tags: string[];
  }) => Promise<void>;
  toggleReaction: (
    targetType: "POST" | "COMMENT",
    targetId: number,
    reactionType: "LIKE" | "BOOKMARK",
  ) => Promise<void>;
}

export const useCommunityStore = create<CommunityState>((set, get) => ({
  posts: [],
  hotPosts: [],
  loading: false,
  currentPost: null,
  comments: [],
  detailLoading: false,

  fetchPosts: async (category) => {
    set({ loading: true });
    const posts = await communityApi.getPosts(category);
    set({ posts, loading: false });
  },

  fetchHotPosts: async () => {
    const hotPosts = await communityApi.getHotPosts();
    set({ hotPosts });
  },

  fetchPostDetail: async (id) => {
    set({ detailLoading: true });
    const currentPost = await communityApi.getPostDetail(id);
    set({ currentPost, detailLoading: false });
  },

  fetchComments: async (postId) => {
    const comments = await communityApi.getComments(postId);
    set({ comments });
  },

  addComment: async (postId, content) => {
    const newComment = await communityApi.createComment(postId, content);
    set({ comments: [...get().comments, newComment] });
  },

  createPost: async (data) => {
    const newPost = await communityApi.createPost(data);
    set({ posts: [newPost, ...get().posts] });
  },

  toggleReaction: async (targetType, targetId, reactionType) => {
    await communityApi.toggleReaction(targetType, targetId, reactionType);
  },
}));
