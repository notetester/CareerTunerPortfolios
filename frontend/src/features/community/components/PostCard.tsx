import { MessageCircle, Heart, UserX } from "lucide-react";
// 개인 차단 진입점 — 익명 글은 작성자 id 가 클라이언트에 없어 게시글 id 로 차단한다(익명성 유지).
import { blockUser, blockUserByContent } from "@/features/privacy/api/privacyApi";
import { showBlockManageToast } from "@/features/privacy/components/blockToast";
import { toast } from "@/features/notification/components/toast";
import { useAuth } from "@/app/auth/AuthContext";
import { useCommunityStore } from "../hooks/useCommunityStore";
import { relTime } from "@/features/notification/types/notification";
import type { CommunityPost } from "../types/community";

interface PostCardProps {
  post: CommunityPost;
  onClick?: () => void;
}

export function PostCard({ post, onClick }: PostCardProps) {
  const { user } = useAuth();
  const { fetchPosts } = useCommunityStore();

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

  // 작성자 차단 진입점 — 로그인 상태에서만 노출, 본인 글(비익명)은 제외.
  // 익명 글은 author.id 가 없어 게시글 id 로 차단한다(서버가 작성자를 알므로 동작, 익명성 유지).
  const canBlockAuthor =
    !!user && (post.author.isAnonymous || (!!post.author.id && user.id !== post.author.id));

  const handleBlockAuthor = async (event: React.MouseEvent) => {
    event.stopPropagation(); // 카드 클릭(상세 진입)과 분리
    try {
      if (post.author.isAnonymous || !post.author.id) {
        await blockUserByContent({ contentType: "POST", contentId: post.id });
        showBlockManageToast("이 작성자를 차단했습니다.", "작성자가 누구인지는 표시되지 않습니다.");
      } else {
        await blockUser({ targetUserId: post.author.id });
        showBlockManageToast(`${post.author.name}님을 차단했습니다.`);
      }
      // 목록을 다시 받아 이 작성자의 글이 톰스톤으로 바뀌도록 한다.
      await fetchPosts();
    } catch (err) {
      // 본인 콘텐츠/운영자 차단 등 서버 검증 메시지는 그대로 보여준다.
      toast.error(err instanceof Error && err.message ? err.message : "차단 처리에 실패했습니다.");
    }
  };

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
          {canBlockAuthor && (
            <button
              type="button"
              onClick={(event) => void handleBlockAuthor(event)}
              title={
                post.author.isAnonymous
                  ? "이 작성자 차단 — 작성자가 누구인지는 표시되지 않습니다."
                  : "이 사용자 차단 — 차단 사실은 상대에게 알려지지 않습니다."
              }
              aria-label={post.author.isAnonymous ? "이 작성자 차단" : "이 사용자 차단"}
              style={{
                background: "none",
                border: "none",
                padding: 0,
                marginLeft: 8,
                cursor: "pointer",
                color: "var(--muted-foreground)",
                display: "inline-flex",
                alignItems: "center",
                verticalAlign: "middle",
              }}
            >
              <UserX style={{ width: 14, height: 14 }} />
            </button>
          )}
        </div>
      </div>
      <div className="cv-post__stats num">
        <span><MessageCircle />{post.stats.commentCount}</span>
        <span><Heart />{post.stats.likeCount}</span>
      </div>
    </article>
  );
}
