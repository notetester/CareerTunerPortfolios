import { useState } from "react";
import { Avatar, AvatarFallback } from "@/app/components/ui/avatar";
import { Heart, MessageCircle, Lock, Trash2, MessageSquareX } from "lucide-react";
import { useCommunityStore } from "../hooks/useCommunityStore";
import { useLoginDialog } from "../hooks/useLoginDialog";
import { ConfirmDialog } from "@/app/components/ui/confirm-dialog";
import * as communityApi from "../api/communityApi";
import { relTime } from "@/features/notification/types/notification";
import type { CommunityComment } from "../types/community";

interface CommentItemProps {
  comment: CommunityComment;
}

export function CommentItem({ comment: c }: CommentItemProps) {
  const { toggleReaction } = useCommunityStore();
  const { showLoginDialog, requireAuth, onLoginConfirm, onLoginCancel } = useLoginDialog();
  const [liked, setLiked] = useState(c.liked ?? false);
  const [showDeleteDialog, setShowDeleteDialog] = useState(false);
  const [deleted, setDeleted] = useState(false);

  const handleDeleteComment = async () => {
    try {
      await communityApi.deleteComment(c.id);
      setShowDeleteDialog(false);
      setDeleted(true);
    } catch {
      setShowDeleteDialog(false);
    }
  };

  if (deleted) return null;

  const handleLike = () => {
    requireAuth(() => {
      toggleReaction("COMMENT", c.id, "LIKE");
      setLiked((v) => !v);
    });
  };

  return (
    <>
      <div className={`ct-cmt ${c.isAuthor ? "is-op" : ""}`}>
        <Avatar className="w-8 h-8 shrink-0">
          <AvatarFallback className="text-xs bg-muted">
            {c.author.name[0]}
          </AvatarFallback>
        </Avatar>
        <div className="ct-cmt__body">
          <div className="ct-cmt__top">
            <span className="ct-cmt__name">{c.author.name}</span>
            {c.isAuthor && <span className="ct-cmt__op">작성자</span>}
            <span className="ct-cmt__time">{relTime(c.createdAt)}</span>
          </div>
          <div className="ct-cmt__text">{c.content}</div>
          <div className="ct-cmt__foot">
            <button
              className={`ct-cmt__act ${liked ? "is-on" : ""}`}
              onClick={handleLike}
            >
              <Heart /> {c.likeCount}
            </button>
            <button className="ct-cmt__act" onClick={() => requireAuth(() => {})}>
              <MessageCircle /> 답글
            </button>
            {c.isAuthor && (
              <button className="ct-cmt__act ct-cmt__act--del" onClick={() => setShowDeleteDialog(true)}>
                <Trash2 /> 삭제
              </button>
            )}
          </div>
        </div>
      </div>

      {showLoginDialog && (
        <ConfirmDialog
          variant="info"
          icon={<Lock />}
          title="로그인이 필요해요"
          description="이 기능은 로그인 후에 이용할 수 있어요. 30초면 시작할 수 있습니다."
          confirmLabel="로그인하기"
          cancelLabel="둘러보기"
          onConfirm={onLoginConfirm}
          onCancel={onLoginCancel}
        />
      )}

      {showDeleteDialog && (
        <ConfirmDialog
          variant="danger"
          icon={<MessageSquareX />}
          title="이 댓글을 삭제할까요?"
          description="삭제한 댓글은 복구할 수 없어요."
          confirmLabel="삭제"
          cancelLabel="취소"
          onConfirm={handleDeleteComment}
          onCancel={() => setShowDeleteDialog(false)}
        />
      )}
    </>
  );
}
