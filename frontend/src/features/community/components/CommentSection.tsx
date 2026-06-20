import { useMemo } from "react";
import { Lock } from "lucide-react";
import { CommentItem } from "./CommentItem";
import { CommentForm } from "./CommentForm";
import { useCommunityStore } from "../hooks/useCommunityStore";
import { useLoginDialog } from "../hooks/useLoginDialog";
import { ConfirmDialog } from "@/app/components/ui/confirm-dialog";
import { toast } from "@/features/notification/components/toast";
import type { CommunityComment } from "../types/community";

interface CommentSectionProps {
  postId: number;
  comments: CommunityComment[];
}

export function CommentSection({ postId, comments }: CommentSectionProps) {
  const { addComment } = useCommunityStore();
  const { showLoginDialog, requireAuth, onLoginConfirm, onLoginCancel } = useLoginDialog();

  // 평면 목록 → parentId 기준 트리(부모별 자식 맵 + 최상위 댓글)
  const { roots, childrenMap } = useMemo(() => {
    const childrenMap = new Map<number, CommunityComment[]>();
    const roots: CommunityComment[] = [];
    for (const c of comments) {
      if (c.parentId == null) {
        roots.push(c);
      } else {
        const arr = childrenMap.get(c.parentId) ?? [];
        arr.push(c);
        childrenMap.set(c.parentId, arr);
      }
    }
    return { roots, childrenMap };
  }, [comments]);

  const handleSubmit = (text: string, anonymous: boolean) => {
    requireAuth(async () => {
      try {
        await addComment(postId, text, undefined, anonymous);
        toast.success("댓글이 등록되었습니다.");
      } catch {
        toast.error("댓글 등록에 실패했습니다.");
      }
    });
  };

  const handleReply = (parentId: number, text: string, anonymous: boolean) => {
    requireAuth(async () => {
      try {
        await addComment(postId, text, parentId, anonymous);
        toast.success("답글이 등록되었습니다.");
      } catch {
        toast.error("답글 등록에 실패했습니다.");
      }
    });
  };

  return (
    <>
      <div className="ct-comments">
        <h3 className="ct-comments__h">
          댓글 <b>{comments.length}</b>
        </h3>

        <div className="ct-clist">
          {roots.map((c) => (
            <CommentItem
              key={c.id}
              comment={c}
              childrenMap={childrenMap}
              depth={0}
              onReply={handleReply}
            />
          ))}
        </div>

        <CommentForm onSubmit={handleSubmit} />
      </div>

      {showLoginDialog && (
        <ConfirmDialog
          variant="info"
          icon={<Lock />}
          title="로그인이 필요해요"
          description="댓글을 작성하려면 로그인이 필요합니다. 30초면 시작할 수 있어요."
          confirmLabel="로그인하기"
          cancelLabel="둘러보기"
          onConfirm={onLoginConfirm}
          onCancel={onLoginCancel}
        />
      )}
    </>
  );
}
