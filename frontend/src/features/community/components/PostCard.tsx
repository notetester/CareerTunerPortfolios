import { useState } from "react";
import { EyeOff, MessageCircle, Heart, UserX } from "lucide-react";
// 개인 차단 진입점 — 익명 글은 작성자 id 가 클라이언트에 없어 게시글 id 로 차단한다(익명성 유지).
import { blockUser, blockUserByContent } from "@/features/privacy/api/privacyApi";
import { showBlockManageToast } from "@/features/privacy/components/blockToast";
import { toast } from "@/features/notification/components/toast";
import { useAuth } from "@/app/auth/AuthContext";
import { ConfirmDialog } from "@/app/components/ui/confirm-dialog";
import { useCommunityStore } from "../hooks/useCommunityStore";
import { relTime } from "@/features/notification/types/notification";
import { toPlainPreview } from "../lib/postContent";
import type { CommunityPost } from "../types/community";

interface PostCardProps {
  post: CommunityPost;
  onClick?: () => void;
}

export function PostCard({ post, onClick }: PostCardProps) {
  const { user } = useAuth();
  const { fetchPosts } = useCommunityStore();
  const [revealed, setRevealed] = useState(false);
  // 실수 클릭 방지 — 차단은 확인 다이얼로그를 거친다.
  const [confirmBlock, setConfirmBlock] = useState(false);
  const isBlurred = !!post.blurred && !revealed;

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

  // 확인 다이얼로그에서 "차단하기"를 눌렀을 때만 실제 차단을 실행한다.
  const doBlock = async () => {
    setConfirmBlock(false);
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
        {isBlurred ? (
          <div
            className="cv-post__blur"
            onClick={(e) => { e.stopPropagation(); setRevealed(true); }}
            style={{ position: "relative", cursor: "pointer", borderRadius: 8, overflow: "hidden" }}
          >
            <div style={{ filter: "blur(6px)", pointerEvents: "none", userSelect: "none" }}>
              <h3 className="cv-post__t">{post.title}</h3>
              <div className="cv-post__x">{toPlainPreview(post.content)}</div>
            </div>
            <div
              style={{
                position: "absolute", inset: 0, display: "flex", flexDirection: "column",
                alignItems: "center", justifyContent: "center", gap: 4, textAlign: "center",
                background: "color-mix(in srgb, var(--background) 55%, transparent)",
                color: "var(--muted-foreground)", fontSize: 13, fontWeight: 600,
              }}
            >
              <EyeOff size={18} />
              신고 누적으로 가려진 글입니다{post.reportCount ? ` (신고 ${post.reportCount}회)` : ""}
              <span style={{ fontWeight: 400, fontSize: 12 }}>클릭하면 표시합니다</span>
            </div>
          </div>
        ) : (
          <>
            <h3 className="cv-post__t">{post.title}</h3>
            <div className="cv-post__x">{toPlainPreview(post.content)}</div>
          </>
        )}
        <div className="cv-post__meta num">
          {post.author.name} · {relTime(post.createdAt)} · 조회 {post.stats.viewCount.toLocaleString()}
          {canBlockAuthor && (
            <button
              type="button"
              onClick={(event) => { event.stopPropagation(); setConfirmBlock(true); }}
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

      {confirmBlock && (
        // 다이얼로그 클릭이 카드(onClick=상세 진입)로 버블되지 않게 감싼다.
        <div onClick={(e) => e.stopPropagation()}>
          <ConfirmDialog
            variant="warning"
            icon={<UserX />}
            title={post.author.isAnonymous ? "이 작성자를 차단할까요?" : `${post.author.name}님을 차단할까요?`}
            description={
              post.author.isAnonymous
                ? "이 작성자의 글이 목록에서 숨겨집니다. 작성자가 누구인지는 표시되지 않으며, 차단 사실은 상대에게 알려지지 않아요. 차단 관리에서 언제든 해제할 수 있어요."
                : "이 사용자의 글이 목록에서 숨겨집니다. 차단 사실은 상대에게 알려지지 않으며, 차단 관리에서 언제든 해제할 수 있어요."
            }
            confirmLabel="차단하기"
            cancelLabel="취소"
            onConfirm={() => void doBlock()}
            onCancel={() => setConfirmBlock(false)}
          />
        </div>
      )}
    </article>
  );
}
