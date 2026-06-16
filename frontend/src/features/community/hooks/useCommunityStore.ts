import { create } from "zustand";
// TODO: 백엔드 연동 시 주석 해제
// import * as communityApi from "../api/communityApi";
import { mockPosts, mockHotPosts } from "../data/mockCommunity";
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

  fetchPosts: async (category, _sort) => {
    set({ loading: true, error: null });
    // TODO: 백엔드 연동 시 아래 mock → communityApi.getPosts(category, sort) 로 교체
    const filtered = category
      ? mockPosts.filter((p) => p.category === category)
      : mockPosts;
    set({ posts: filtered, loading: false });
  },

  fetchHotPosts: async () => {
    // TODO: 백엔드 연동 시 communityApi.getHotPosts() 로 교체
    set({ hotPosts: mockHotPosts });
  },

  fetchPostDetail: async (id) => {
    set({ detailLoading: true, error: null });
    // TODO: 백엔드 연동 시 communityApi.getPostDetail(id) 로 교체
    const found = mockPosts.find((p) => p.id === id) ?? null;
    set({ currentPost: found, detailLoading: false });
  },

  fetchComments: async (_postId) => {
    // TODO: 백엔드 연동 시 communityApi.getComments(postId) 로 교체
    set({ comments: [] });
  },

  addComment: async (postId, content) => {
    // TODO: 백엔드 연동 시 communityApi.createComment + getComments 로 교체
    const newComment: CommunityComment = {
      id: Date.now(),
      postId,
      content,
      author: { id: 0, name: "익명", isAnonymous: true },
      likeCount: 0,
      isAuthor: true,
      createdAt: new Date().toISOString(),
    };
    const comments = [...get().comments, newComment];
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
    // TODO: 백엔드 연동 시 communityApi.createPost + getPosts 로 교체
    const CATEGORY_LABELS: Record<string, string> = {
      interview_review: "면접후기", job_review: "취업후기", free: "자유게시판",
      pass_strategy: "합격전략", portfolio: "포트폴리오", qna: "Q&A",
    };
    const newPost: CommunityPost = {
      id: Date.now(),
      category: data.category,
      categoryLabel: CATEGORY_LABELS[data.category] ?? data.category,
      title: data.title,
      content: data.content,
      tags: data.tags,
      author: { id: 0, name: "익명", isAnonymous: true },
      stats: { viewCount: 0, commentCount: 0, likeCount: 0, bookmarkCount: 0 },
      status: "PUBLISHED",
      createdAt: "방금",
      daysAgo: 0,
    };
    set({ posts: [newPost, ...get().posts] });
  },

  toggleReaction: async (targetType, targetId, reactionType) => {
    // TODO: 백엔드 연동 시 communityApi.toggleReaction 으로 교체
    const active = true;
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
