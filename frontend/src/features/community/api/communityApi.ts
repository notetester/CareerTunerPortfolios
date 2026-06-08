import { mockPosts, mockHotPosts, mockPostDetail } from "../data/mockCommunity";
import type { CommunityPost, CommunityComment, CommunityCategory } from "../types/community";

// TODO: 백엔드 연동 시 mock → api() 호출로 교체
// import { api } from "@/app/lib/api";

/** 네트워크 지연 시뮬레이션 */
const delay = (ms = 300) => new Promise((r) => setTimeout(r, ms));

/** 게시글 목록 */
export async function getPosts(category?: CommunityCategory) {
  await delay();
  if (!category) return mockPosts;
  return mockPosts.filter((p) => p.category === category);
}

/** 게시글 상세 */
export async function getPostDetail(_id: number) {
  await delay();
  return mockPostDetail;
}

/** 인기글 */
export async function getHotPosts() {
  await delay(200);
  return mockHotPosts;
}

/** 게시글 작성 */
export async function createPost(data: {
  category: CommunityCategory;
  title: string;
  content: string;
  tags: string[];
}) {
  await delay(500);
  const newPost: CommunityPost = {
    id: Date.now(),
    category: data.category,
    categoryLabel: data.category,
    title: data.title,
    content: data.content,
    tags: data.tags,
    author: { id: 0, name: "익명", isAnonymous: true },
    stats: { viewCount: 0, commentCount: 0, likeCount: 0, bookmarkCount: 0 },
    status: "PUBLISHED",
    createdAt: "방금",
  };
  return newPost;
}

/** 게시글 삭제 */
export async function deletePost(_id: number) {
  await delay(300);
}

/** 댓글 목록 */
export async function getComments(_postId: number): Promise<CommunityComment[]> {
  await delay(200);
  return mockPostDetail.comments.map((c, i) => ({
    id: i + 1,
    postId: _postId,
    author: { id: i + 1, name: c.name, isAnonymous: c.name === "익명" },
    content: c.text,
    likeCount: c.likes,
    isAuthor: c.isAuthor,
    createdAt: c.time,
  }));
}

/** 댓글 작성 */
export async function createComment(_postId: number, content: string): Promise<CommunityComment> {
  await delay(300);
  return {
    id: Date.now(),
    postId: _postId,
    author: { id: 0, name: "익명", isAnonymous: true },
    content,
    likeCount: 0,
    isAuthor: false,
    createdAt: "방금",
  };
}

/** 좋아요/북마크 토글 */
export async function toggleReaction(
  _targetType: "POST" | "COMMENT",
  _targetId: number,
  _reactionType: "LIKE" | "BOOKMARK",
) {
  await delay(200);
  return true;
}
