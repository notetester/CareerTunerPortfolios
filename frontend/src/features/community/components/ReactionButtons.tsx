import { useState } from "react";
import { ThumbsUp, ThumbsDown, Heart, EyeOff, Star, ClipboardList, Lock } from "lucide-react";
import { useCommunityStore } from "../hooks/useCommunityStore";
import { useLoginDialog } from "../hooks/useLoginDialog";
import { ConfirmDialog } from "@/app/components/ui/confirm-dialog";
import { toast } from "@/features/notification/components/toast";
import type { CommunityPost, ReactionType } from "../types/community";

interface ReactionButtonsProps {
  post: CommunityPost;
}

/**
 * 리액션 성공 토스트 문구. 추천/비추천은 공개 카운트가 즉시 갱신돼 피드백이 명확하므로 토스트 없음.
 * 효과가 눈에 덜 보이는 개인화(LIKE/DISLIKE)·개인 저장(BOOKMARK)만 토스트로 확인해준다.
 */
const REACTION_TOAST: Partial<Record<ReactionType, { on: string; off: string }>> = {
  LIKE: { on: "맞춤 탭에서 비슷한 글을 더 추천해드릴게요.", off: "‘이런 글 더 보기’를 취소했습니다." },
  DISLIKE: { on: "맞춤 탭에서 비슷한 글을 덜 보여드릴게요.", off: "‘이런 글 그만 보기’를 취소했습니다." },
  BOOKMARK: { on: "즐겨찾기에 추가했습니다.", off: "즐겨찾기를 해제했습니다." },
};

/**
 * 게시글 반응 바 (본문 하단, 댓글 위).
 * 성격별로 분리 배치:
 *  - 상단 독립: 익명으로 반응(상태 토글).
 *  - 좌측(공개 집계, 접근성): 추천/비추천 캡슐 — 라벨·카운트 항상 노출.
 *  - 개인화 쌍: "이런 글 더 보기"(LIKE, 카운트=작성자 만족) / "이런 글 그만 보기"(DISLIKE).
 *  - 우측(개인 저장): 즐겨찾기·스크랩 — 아이콘+카운트+툴팁.
 * 구독(🔔)·신고·공유는 상단 byline(PostDetailView)로 이동했다.
 */
export function ReactionButtons({ post }: ReactionButtonsProps) {
  const { toggleReaction, toggleScrap, reactAnonymously, setReactAnonymously } = useCommunityStore();
  const { showLoginDialog, requireAuth, onLoginConfirm, onLoginCancel } = useLoginDialog();
  const [busy, setBusy] = useState(false);

  const stats = post.stats;

  const handleReaction = (type: ReactionType) => {
    requireAuth(async () => {
      if (busy) return;
      setBusy(true);
      try {
        const result = await toggleReaction("POST", post.id, type);
        const msg = REACTION_TOAST[type];
        if (msg) toast.success(result.active ? msg.on : msg.off);
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

  return (
    <>
      <div className="rx-area" aria-label="글 반응">
        {/* 익명으로 반응 — 상태 토글(액션 버튼과 시각 분리) */}
        <button
          type="button"
          className={"rx-anon" + (reactAnonymously ? " on" : "")}
          onClick={() => setReactAnonymously(!reactAnonymously)}
          aria-pressed={reactAnonymously}
          data-tip="반응 시 이름 노출 여부예요 — 집계에는 똑같이 포함돼요"
        >
          <span className="av-switch" />
          익명으로 반응
        </button>

        <div className="rx-bar">
          {/* 추천·비추천 — 공개 집계(인기순). 평소 아이콘+카운트, hover 시 라벨 펼침 */}
          <div className="rx-vote">
            <button
              className={`rx-vote__b ${post.recommended ? "on" : ""}`}
              onClick={() => handleReaction("RECOMMEND")}
              disabled={busy}
              aria-label="추천"
              data-tip="인기순 정렬에 반영되는 공개 집계예요"
            >
              <ThumbsUp /><span className="rx-vote__lbl">추천</span><b className="num">{stats.recommendCount ?? 0}</b>
            </button>
            <span className="rx-vote__div" />
            <button
              className={`rx-vote__b down ${post.disrecommended ? "on" : ""}`}
              onClick={() => handleReaction("DISRECOMMEND")}
              disabled={busy}
              aria-label="비추천"
              data-tip="공개 집계 — 인기순에 반영돼요"
            >
              <ThumbsDown /><span className="rx-vote__lbl">비추천</span><b className="num">{stats.disrecommendCount ?? 0}</b>
            </button>
          </div>

          <span className="rx-sep" />

          {/* 개인화 캡슐 — 추천/비추천처럼 묶고, 평소엔 아이콘(좋아요는 +카운트)만, hover 시 라벨 펼침. 툴팁 유지 */}
          <div className="rx-vote">
            <button
              className={`rx-vote__b ${post.liked ? "on" : ""}`}
              onClick={() => handleReaction("LIKE")}
              aria-label="이런 글 더 보기"
              data-tip="비슷한 글을 맞춤 탭에서 더 추천해요"
            >
              <Heart /><span className="rx-vote__lbl">이런 글 더 보기</span><b className="num">{stats.likeCount}</b>
            </button>
            <span className="rx-vote__div" />
            <button
              className={`rx-vote__b down ${post.disliked ? "on" : ""}`}
              onClick={() => handleReaction("DISLIKE")}
              aria-label="이런 글 그만 보기"
              data-tip="맞춤 탭에서 비슷한 글을 덜 보여줘요"
            >
              <EyeOff /><span className="rx-vote__lbl">이런 글 그만 보기</span>
            </button>
          </div>

          {/* 개인 저장 — 우측, 아이콘+카운트+툴팁 */}
          <div className="right">
            <button
              className={`rx-act ${post.bookmarked ? "on" : ""}`}
              onClick={() => handleReaction("BOOKMARK")}
              aria-label="즐겨찾기"
              data-tip="즐겨찾기 — 원본 글 링크를 저장해요"
            >
              <Star /> <b className="num">{stats.bookmarkCount}</b>
            </button>
            <button
              className={`rx-act ${post.scrapped ? "on" : ""}`}
              onClick={handleScrap}
              disabled={busy}
              aria-label="스크랩"
              data-tip="스크랩 — 지금 내용 그대로 보관해요"
            >
              <ClipboardList /> <b className="num">{stats.scrapCount ?? 0}</b>
            </button>
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
    </>
  );
}
