import { useState } from "react";
import {
  ThumbsUp, ThumbsDown, Heart, HeartCrack, Star, ClipboardList,
  Bell, BellRing, Flag, Lock, VenetianMask,
} from "lucide-react";
import { ReportDialog } from "./ReportDialog";
import { useCommunityStore } from "../hooks/useCommunityStore";
import { useLoginDialog } from "../hooks/useLoginDialog";
import { ConfirmDialog } from "@/app/components/ui/confirm-dialog";
import { toast } from "@/features/notification/components/toast";
import type { CommunityPost, ReactionType } from "../types/community";

interface ReactionButtonsProps {
  post: CommunityPost;
}

/**
 * 게시글 리액션 바 — 축 2개(추천/비추천 · 좋아요/싫어요) + 즐겨찾기 + 스크랩 + 구독.
 * 같은 축 반대 클릭 시 교체, 같은 것 재클릭 시 취소(서버 응답 기반으로 store 가 갱신).
 * "익명으로" 드롭다운은 이 페이지의 글/댓글 리액션·스크랩에 공통 적용된다.
 */
export function ReactionButtons({ post }: ReactionButtonsProps) {
  const {
    toggleReaction, toggleScrap, togglePostSubscription,
    reactAnonymously, setReactAnonymously,
  } = useCommunityStore();
  const { showLoginDialog, requireAuth, onLoginConfirm, onLoginCancel } = useLoginDialog();
  const [showReport, setShowReport] = useState(false);
  const [busy, setBusy] = useState(false);

  const stats = post.stats;

  const handleReaction = (type: ReactionType) => {
    requireAuth(async () => {
      if (busy) return;
      setBusy(true);
      try {
        await toggleReaction("POST", post.id, type);
      } catch (err) {
        toast.error(err instanceof Error && err.message ? err.message : "리액션 처리에 실패했습니다.");
      } finally {
        setBusy(false);
      }
    });
  };

  const handleScrap = () => {
    requireAuth(async () => {
      if (busy) return;
      setBusy(true);
      try {
        const active = await toggleScrap(post.id);
        toast.success(active ? "스크랩했습니다. 원본이 바뀌어도 지금 내용이 보존됩니다." : "스크랩을 취소했습니다.");
      } catch {
        toast.error("스크랩 처리에 실패했습니다.");
      } finally {
        setBusy(false);
      }
    });
  };

  const handleSubscribe = () => {
    requireAuth(async () => {
      if (busy) return;
      setBusy(true);
      try {
        const active = await togglePostSubscription(post.id);
        toast.success(active ? "이 글을 구독합니다. 새 댓글이 달리면 알려드릴게요." : "글 구독을 해지했습니다.");
      } catch {
        toast.error("구독 처리에 실패했습니다.");
      } finally {
        setBusy(false);
      }
    });
  };

  return (
    <>
      <div className="ct-actions" style={{ flexWrap: "wrap" }}>
        <div className="ct-actbtns" style={{ flexWrap: "wrap" }}>
          {/* 추천 축 — 트렌드·인기글 산정용 */}
          <button
            className={`ct-act ct-act--reco ${post.recommended ? "is-on" : ""}`}
            onClick={() => handleReaction("RECOMMEND")}
            title="추천 — 인기글·트렌드에 반영됩니다"
          >
            <ThumbsUp /> 추천 {stats.recommendCount ?? 0}
          </button>
          <button
            className={`ct-act ct-act--downvote ${post.disrecommended ? "is-on" : ""}`}
            onClick={() => handleReaction("DISRECOMMEND")}
            title="비추천"
          >
            <ThumbsDown /> {stats.disrecommendCount ?? 0}
          </button>
          {/* 개인화 축 */}
          <button
            className={`ct-act ct-act--like ${post.liked ? "is-on" : ""}`}
            onClick={() => handleReaction("LIKE")}
            title="좋아요 — 내 취향(개인화)에 반영됩니다"
          >
            <Heart /> 좋아요 {stats.likeCount}
          </button>
          <button
            className={`ct-act ct-act--dislike ${post.disliked ? "is-on" : ""}`}
            onClick={() => handleReaction("DISLIKE")}
            title="싫어요"
          >
            <HeartCrack /> {stats.dislikeCount ?? 0}
          </button>
          {/* 즐겨찾기(링크형) / 스크랩(스냅샷 보존형) */}
          <button
            className={`ct-act ct-act--save ${post.bookmarked ? "is-on" : ""}`}
            onClick={() => handleReaction("BOOKMARK")}
            title="즐겨찾기 — 원본이 삭제되면 함께 사라집니다"
          >
            <Star /> {post.bookmarked ? "즐겨찾는 글" : "즐겨찾기"} {stats.bookmarkCount}
          </button>
          <button
            className={`ct-act ct-act--scrap ${post.scrapped ? "is-on" : ""}`}
            onClick={handleScrap}
            title="스크랩 — 지금 내용을 스냅샷으로 보존합니다"
          >
            <ClipboardList /> {post.scrapped ? "스크랩됨" : "스크랩"} {stats.scrapCount ?? 0}
          </button>
          {/* 글 구독 — 새 댓글 알림 */}
          <button
            className={`ct-act ct-act--watch ${post.subscribed ? "is-on" : ""}`}
            onClick={handleSubscribe}
            title="구독 — 새 댓글이 달리면 알림을 받습니다"
          >
            {post.subscribed ? <BellRing /> : <Bell />} {post.subscribed ? "구독 중" : "구독"}
          </button>
        </div>

        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          {/* 익명 반응 모드 — 이 페이지의 글/댓글 리액션·스크랩에 공통 적용 */}
          <label className="ct-anonsel" title="익명으로 반응하면 작성자 알림·반응자 목록에 이름이 표시되지 않습니다 (집계에는 포함)">
            <VenetianMask />
            <select
              value={reactAnonymously ? "anon" : "public"}
              onChange={(e) => setReactAnonymously(e.target.value === "anon")}
            >
              <option value="public">공개로 반응</option>
              <option value="anon">익명으로 반응</option>
            </select>
          </label>
          <button className="ct-act ct-act--report" onClick={() => requireAuth(() => setShowReport(true))}>
            <Flag /> 신고
          </button>
        </div>
      </div>

      {showReport && (
        <ReportDialog
          targetType="POST"
          targetId={post.id}
          target="게시글"
          onClose={() => setShowReport(false)}
        />
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
    </>
  );
}
