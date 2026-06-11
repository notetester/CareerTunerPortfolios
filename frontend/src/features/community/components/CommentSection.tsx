import { Lock } from "lucide-react";
import { CommentItem } from "./CommentItem";
import { CommentForm } from "./CommentForm";
import { useCommunityStore } from "../hooks/useCommunityStore";
import { useLoginDialog } from "../hooks/useLoginDialog";
import { ConfirmDialog } from "@/app/components/ui/confirm-dialog";
import type { CommunityComment } from "../types/community";

interface CommentSectionProps {
  postId: number;
  comments: CommunityComment[];
}

export function CommentSection({ postId, comments }: CommentSectionProps) {
  const { addComment } = useCommunityStore();
  const { showLoginDialog, requireAuth, onLoginConfirm, onLoginCancel } = useLoginDialog();

  const handleSubmit = (text: string) => {
    requireAuth(() => addComment(postId, text));
  };

  return (
    <>
      <div className="ct-comments">
        <h3 className="ct-comments__h">
          댓글 <b>{comments.length}</b>
        </h3>

        <div className="ct-clist">
          {comments.map((c) => (
            <CommentItem key={c.id} comment={c} />
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
