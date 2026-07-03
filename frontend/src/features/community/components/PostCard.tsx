import { MessageCircle, Heart } from "lucide-react";
import { relTime } from "@/features/notification/types/notification";
import type { CommunityPost } from "../types/community";

interface PostCardProps {
  post: CommunityPost;
  onClick?: () => void;
}

export function PostCard({ post, onClick }: PostCardProps) {
  // 차단한 작성자의 글 — 톰스톤만 렌더하고 "한 번 보기" 없이 유지한다(조용한 차단, 클릭 진입 없음).
  if (post.blocked) {
    return (
      <article className="cv-post" aria-disabled="true">
        <div className="cv-post__b">
          <div className="cv-post__x" style={{ color: "var(--muted-foreground)", fontStyle: "italic" }}>
            차단한 사용자의 게시글입니다.
          </div>
        </div>
      </article>
    );
  }

  return (
    <article className="cv-post" onClick={onClick}>
      <div className="cv-post__b">
        <div className="cv-post__cat">
          {post.categoryLabel}
          {post.isHot && <span className="cv-post__hot">인기</span>}
        </div>
        <h3 className="cv-post__t">{post.title}</h3>
        <div className="cv-post__x">{post.content}</div>
        {post.tags?.length > 0 && (
          <div className="cv-post__tags">
            {post.tags.slice(0, 5).map((tag) => (
              <span key={tag} className="cv-post__tag">#{tag}</span>
            ))}
          </div>
        )}
        <div className="cv-post__meta num">
          {post.author.name} · {relTime(post.createdAt)} · 조회 {post.stats.viewCount.toLocaleString()}
        </div>
      </div>
      <div className="cv-post__stats num">
        <span><MessageCircle />{post.stats.commentCount}</span>
        <span><Heart />{post.stats.likeCount}</span>
      </div>
    </article>
  );
}
