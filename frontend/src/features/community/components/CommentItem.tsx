import { useState } from "react";
import { useNavigate } from "react-router";
import { Avatar, AvatarFallback } from "@/app/components/ui/avatar";
import {
  Heart, HeartCrack, ThumbsUp, ThumbsDown, Bell, BellRing,
  MessageCircle, Lock, Trash2, MessageSquareX, Pencil, UserX,
} from "lucide-react";
// 개인 차단 진입점 — 익명 댓글은 작성자 id 가 클라이언트에 없어 콘텐츠 id 로 차단한다(익명성 유지).
import { blockUser, blockUserByContent } from "@/features/privacy/api/privacyApi";
import { showBlockManageToast } from "@/features/privacy/components/blockToast";
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

export function CommentItem({ comment: c, childrenMap, depth, onReply }: CommentItemProps) {
  const { toggleReaction, toggleCommentSubscription, fetchComments } = useCommunityStore();
  const { showLoginDialog, requireAuth, onLoginConfirm, onLoginCancel } = useLoginDialog();
  const navigate = useNavigate();
  const [showDeleteDialog, setShowDeleteDialog] = useState(false);
  const [deleted, setDeleted] = useState(false);
  const [replyOpen, setReplyOpen] = useState(false);
  const [editing, setEditing] = useState(false);
  const [editText, setEditText] = useState(c.content);
  const [contentOverride, setContentOverride] = useState<string | null>(null); // 낙관적 수정 표시(삭제 패턴과 동형)
  const [saving, setSaving] = useState(false);

  const replies = childrenMap.get(c.id) ?? [];
  // 서버 tombstone(c.isDeleted) 또는 이번 세션 낙관적 자삭(deleted) 둘 다 삭제 표시.
  const isDeleted = deleted || !!c.isDeleted;
  // 뷰어가 차단한 작성자의 댓글 — 톰스톤만 렌더하고 답글 트리는 유지(조용한 차단, "한 번 보기" 없음).
  const isBlocked = !!c.blocked;
  const canNameLink = !c.author.isAnonymous && !!c.author.id;

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

  // 리액션 상태·카운트는 store 가 서버 응답(토글 후 카운트)으로 comments 배열을 갱신해 props 로 내려온다.
  const handleReaction = (type: "LIKE" | "DISLIKE" | "RECOMMEND" | "DISRECOMMEND") => {
    requireAuth(async () => {
      try {
        await toggleReaction("COMMENT", c.id, type);
      } catch (err) {
        toast.error(err instanceof Error && err.message ? err.message : "리액션 처리에 실패했습니다.");
      }
    });
  };

  // 댓글 구독 — 새 답글이 달리면 알림(작성자가 아니어도 가능, 토글)
  const handleSubscribe = () => {
    requireAuth(async () => {
      try {
        const active = await toggleCommentSubscription(c.id);
        toast.success(active ? "이 댓글을 구독합니다. 새 답글이 달리면 알려드릴게요." : "댓글 구독을 해지했습니다.");
      } catch {
        toast.error("구독 처리에 실패했습니다.");
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

  // 작성자 차단 — 조용한 차단. 차단 후 목록을 다시 받아 이 작성자의 댓글이 톰스톤으로 바뀐다.
  const handleBlockAuthor = () => {
    requireAuth(async () => {
      try {
        if (c.author.isAnonymous || !c.author.id) {
          await blockUserByContent({ contentType: "COMMENT", contentId: c.id });
          showBlockManageToast("이 작성자를 차단했습니다.", "작성자가 누구인지는 표시되지 않습니다.");
        } else {
          await blockUser({ targetUserId: c.author.id });
          showBlockManageToast(`${c.author.name}님을 차단했습니다.`);
        }
        await fetchComments(c.postId);
      } catch (err) {
        toast.error(err instanceof Error && err.message ? err.message : "차단 처리에 실패했습니다.");
      }
    });
  };

  return (
    <>
      <div className={"dv-cmt" + (depth > 0 ? " dv-cmt--reply" : "")}>
        <Avatar className="w-7 h-7 shrink-0">
          <AvatarFallback className="text-xs bg-muted">
            {isDeleted || isBlocked ? "-" : c.author.name[0]}
          </AvatarFallback>
        </Avatar>
        <div className="dv-cmt__b">
          {isDeleted || isBlocked ? (
            <div className="dv-cmt__x muted">
              {isDeleted ? "삭제된 댓글입니다." : "차단한 사용자의 댓글입니다."}
            </div>
          ) : (
            <>
              <div className="dv-cmt__top">
                {/* 비익명 작성자 이름 클릭 → 프로필 활동 탭 */}
                <span
                  className={"dv-cmt__n" + (canNameLink ? " link" : "")}
                  onClick={() => { if (canNameLink) navigate(`/community/users/${c.author.id}/activity`); }}
                  title={canNameLink ? "작성자의 활동 보기" : undefined}
                >
                  {c.author.name}
                </span>
                {c.isAuthor && <span className="dv-cmt__op">작성자</span>}
                <span className="dv-cmt__t">{relTime(c.createdAt)}</span>
              </div>
              {editing ? (
                <div style={{ marginTop: 6 }}>
                  <textarea
                    className="av-textarea"
                    style={{ minHeight: 70 }}
                    value={editText}
                    onChange={(e) => setEditText(e.target.value)}
                    maxLength={5000}
                    autoFocus
                  />
                  <div style={{ display: "flex", gap: 12, justifyContent: "flex-end", marginTop: 6 }}>
                    <button className="dv-cmt__act" onClick={() => { setEditing(false); setEditText(contentOverride ?? c.content); }}>취소</button>
                    <button className="dv-cmt__act" disabled={saving || !editText.trim()} onClick={handleEditSave}>저장</button>
                  </div>
                </div>
              ) : (
                <div className="dv-cmt__x">
                  {c.mentionLabel && <span className="dv-cmt__mention">@{c.mentionLabel}</span>}
                  {c.mentionLabel ? " " : ""}{contentOverride ?? c.content}
                </div>
              )}
              <div className="dv-cmt__foot">
                {/* 추천 축(트렌드용) */}
                <button className={"dv-cmt__act" + (c.recommended ? " on" : "")} onClick={() => handleReaction("RECOMMEND")} title="추천">
                  <ThumbsUp /> {c.recommendCount ?? 0}
                </button>
                <button className={"dv-cmt__act" + (c.disrecommended ? " on-muted" : "")} onClick={() => handleReaction("DISRECOMMEND")} title="비추천">
                  <ThumbsDown /> {c.disrecommendCount ?? 0}
                </button>
                {/* 개인화 축 */}
                <button className={"dv-cmt__act" + (c.liked ? " on" : "")} onClick={() => handleReaction("LIKE")} title="좋아요 — 맞춤 추천에 반영">
                  <Heart /> {c.likeCount}
                </button>
                <button className={"dv-cmt__act" + (c.disliked ? " on-muted" : "")} onClick={() => handleReaction("DISLIKE")} title="싫어요">
                  <HeartCrack /> {c.dislikeCount ?? 0}
                </button>
                {/* 댓글 구독 — 새 답글 알림 */}
                <button className={"dv-cmt__act" + (c.subscribed ? " on" : "")} onClick={handleSubscribe} title="구독 — 새 답글이 달리면 알림을 받습니다">
                  {c.subscribed ? <BellRing /> : <Bell />}
                </button>
                <button className="dv-cmt__act" onClick={handleReplyClick}>
                  <MessageCircle /> 답글
                </button>
                {c.mine && !editing && (
                  <button className="dv-cmt__act" onClick={() => { setEditText(contentOverride ?? c.content); setEditing(true); }}>
                    <Pencil /> 수정
                  </button>
                )}
                {c.mine && (
                  <button className="dv-cmt__act dv-cmt__act--del" onClick={() => setShowDeleteDialog(true)}>
                    <Trash2 /> 삭제
                  </button>
                )}
                {/* 작성자 메뉴 — 익명 댓글도 노출: 댓글 id 로 차단(서버가 작성자를 알므로 동작, 익명성 유지) */}
                {!c.mine && (!!c.author.id || c.author.isAnonymous) && (
                  <button
                    className="dv-cmt__act dv-cmt__act--del"
                    onClick={handleBlockAuthor}
                    title={
                      c.author.isAnonymous
                        ? "작성자가 누구인지는 표시되지 않습니다. 차단 사실은 상대에게 알려지지 않습니다."
                        : "차단 사실은 상대에게 알려지지 않습니다."
                    }
                  >
                    <UserX /> {c.author.isAnonymous ? "이 작성자 차단" : "차단"}
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

      {/* 자식 답글 — 재귀. dv-cmt--reply(depth>0) 로 한 단계 들여쓰기, 더 깊어도 평평하게 유지 */}
      {replies.length > 0 && (
        <div>
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
