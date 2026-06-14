import { PostCard } from "./PostCard";
import type { CommunityPost } from "../types/community";

interface PostListProps {
  posts: CommunityPost[];
  onPostClick?: (post: CommunityPost) => void;
}

export function PostList({ posts, onPostClick }: PostListProps) {
  return (
    <section className="av-panel">
      {posts.map((post) => (
        <PostCard
          key={post.id}
          post={post}
          onClick={() => onPostClick?.(post)}
        />
      ))}
    </section>
  );
}
