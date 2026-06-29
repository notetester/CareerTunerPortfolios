import { useState } from "react";
import { Avatar, AvatarFallback } from "@/app/components/ui/avatar";
import { Heart, MessageCircle, Lock, Trash2, MessageSquareX, Pencil } from "lucide-react";
import { useCommunityStore } from "../hooks/useCommunityStore";
import { useLoginDialog } from "../hooks/useLoginDialog";
import { ConfirmDialog } from "@/app/components/ui/confirm-dialog";
import { CommentForm } from "./CommentForm";
import { toast } from "@/features/notification/components/toast";
import * as communityApi from "../api/communityApi";
import { relTime } from "@/features/notification/types/notification";
import type { CommunityComment } from "../types/community";

interface CommentItemProps {
  comment: CommunityComment;
  /** parentId → 자식 답글 목록 (재귀 렌더링용) */
  childrenMap: Map<number, CommunityComment[]>;
  depth: number;
  /** 답글 등록 (부모 댓글 id, 본문, 익명 여부) */
  onReply: (parentId: number, text: string, anonymous: boolean) => void;
}

// 시각적 들여쓰기 상한 (디시인사이드식 2단계라 사실상 1단계만 들어감)
const MAX_INDENT_DEPTH = 5;

export function CommentItem({ comment: c, childrenMap, depth, onReply }: CommentItemProps) {
  const { toggleReaction } = useCommunityStore();
  const { showLoginDialog, requireAuth, onLoginConfirm, onLoginCancel } = useLoginDialog();
  const [liked, setLiked] = useState(c.liked ?? false);
  const [showDeleteDialog, setShowDeleteDialog] = useState(false);
  const [deleted, setDeleted] = useState(false);
  const [replyOpen, setReplyOpen] = useState(false);
  const [editing, setEditing] = useState(false);
  const [editText, setEditText] = useState(c.content);
  const [contentOverride, setContentOverride] = useState<string | null>(null); // 낙관적 수정 표시(삭제 패턴과 동형)
  const [saving, setSaving] = useState(false);

  const replies = childrenMap.get(c.id) ?? [];
  // 서버 tombstone(c.isDeleted) 또는 이번 세션 낙관적 자삭(deleted) 둘 다 삭제 표시.
  // → 재조회·타 사용자·관리자 숨김에도 placeholder가 유지된다.
  const isDeleted = deleted || !!c.isDeleted;

  const handleDeleteComment = async () => {
    try {
      await communityApi.deleteComment(c.id);
      setShowDeleteDialog(false);
      setDeleted(true);
      toast.success("댓글이 삭제되었습니다.");
    } catch {
      setShowDeleteDialog(false);
      toast.error("댓글 삭제에 실패했습니다.");
    }
  };

  const handleEditSave = async () => {
    const text = editText.trim();
    if (!text || saving) return;
    setSaving(true);
    try {
      await communityApi.updateComment(c.id, text);
      setContentOverride(text);
      setEditing(false);
      toast.success("댓글이 수정되었습니다.");
    } catch {
      toast.error("댓글 수정에 실패했습니다.");
    } finally {
      setSaving(false);
    }
  };

  const handleLike = () => {
    requireAuth(async () => {
      setLiked((v) => !v);
      try {
        await toggleReaction("COMMENT", c.id, "LIKE");
      } catch {
        setLiked((v) => !v);
        toast.error("좋아요 처리에 실패했습니다.");
      }
    });
  };

  const handleReplyClick = () => {
    requireAuth(() => setReplyOpen((v) => !v));
  };

  const handleReplySubmit = (text: string, anonymous: boolean) => {
    // 멘션(@대상) 부여는 서버에서 처리한다. 클라이언트는 클릭한 댓글 id와 본문만 전달.
    onReply(c.id, text, anonymous);
    setReplyOpen(false);
  };

  // 삭제됐어도 자식 답글은 보존해야 하므로, 본문만 "삭제된 댓글"로 대체하고 트리는 유지
  return (
    <>
      <div className={`ct-cmt ${c.isAuthor ? "is-op" : ""}`}>
        <Avatar className="w-8 h-8 shrink-0">
          <AvatarFallback className="text-xs bg-muted">
            {isDeleted ? "-" : c.author.name[0]}
          </AvatarFallback>
        </Avatar>
        <div className="ct-cmt__body">
          {isDeleted ? (
            <div className="ct-cmt__text" style={{ color: "var(--muted-foreground)", fontStyle: "italic" }}>
              삭제된 댓글입니다.
            </div>
          ) : (
            <>
              <div className="ct-cmt__top">
                <span className="ct-cmt__name">{c.author.name}</span>
                {c.isAuthor && <span className="ct-cmt__op">작성자</span>}
                <span className="ct-cmt__time">{relTime(c.createdAt)}</span>
              </div>
              {editing ? (
                <div style={{ marginTop: 4 }}>
                  <textarea
                    className="ct-cmt__editbox"
                    value={editText}
                    onChange={(e) => setEditText(e.target.value)}
                    maxLength={5000}
                    rows={3}
                    style={{ width: "100%", resize: "vertical", padding: 8, borderRadius: 8, border: "1px solid var(--border)" }}
                    autoFocus
                  />
                  <div style={{ display: "flex", gap: 6, justifyContent: "flex-end", marginTop: 6 }}>
                    <button className="ct-cmt__act" onClick={() => { setEditing(false); setEditText(contentOverride ?? c.content); }}>취소</button>
                    <button className="ct-cmt__act" disabled={saving || !editText.trim()} onClick={handleEditSave}>저장</button>
                  </div>
                </div>
              ) : (
                <div className="ct-cmt__text">
                  {c.mentionLabel && <span className="ct-cmt__mention">@{c.mentionLabel}</span>}
                  {c.mentionLabel ? " " : ""}{contentOverride ?? c.content}
                </div>
              )}
              <div className="ct-cmt__foot">
                <button
                  className={`ct-cmt__act ${liked ? "is-on" : ""}`}
                  onClick={handleLike}
                >
                  <Heart /> {c.likeCount}
                </button>
                <button className="ct-cmt__act" onClick={handleReplyClick}>
                  <MessageCircle /> 답글
                </button>
                {c.mine && !editing && (
                  <button className="ct-cmt__act" onClick={() => { setEditText(contentOverride ?? c.content); setEditing(true); }}>
                    <Pencil /> 수정
                  </button>
                )}
                {c.mine && (
                  <button className="ct-cmt__act ct-cmt__act--del" onClick={() => setShowDeleteDialog(true)}>
                    <Trash2 /> 삭제
                  </button>
                )}
              </div>
            </>
          )}

          {replyOpen && (
            <div style={{ marginTop: 8 }}>
              <CommentForm
                compact
                autoFocus
                placeholder={`${c.author.name}님에게 답글 남기기…`}
                onSubmit={handleReplySubmit}
                onCancel={() => setReplyOpen(false)}
              />
            </div>
          )}
        </div>
      </div>

      {/* 자식 답글 — 재귀. 들여쓰기는 상한까지만 적용 */}
      {replies.length > 0 && (
        <div
          className="ct-cmt__replies"
          style={depth < MAX_INDENT_DEPTH ? undefined : { marginLeft: 0 }}
        >
          {replies.map((r) => (
            <CommentItem
              key={r.id}
              comment={r}
              childrenMap={childrenMap}
              depth={depth + 1}
              onReply={onReply}
            />
          ))}
        </div>
      )}

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
