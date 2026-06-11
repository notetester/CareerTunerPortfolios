import { useState } from "react";
import { Heart, Bookmark, Flag, Lock } from "lucide-react";
import { ReportDialog } from "./ReportDialog";
import { useCommunityStore } from "../hooks/useCommunityStore";
import { useLoginDialog } from "../hooks/useLoginDialog";
import { ConfirmDialog } from "@/app/components/ui/confirm-dialog";
import { toast } from "@/features/notification/components/toast";

interface ReactionButtonsProps {
  postId: number;
  likeCount: number;
  bookmarkCount: number;
  initialLiked?: boolean;
  initialBookmarked?: boolean;
}

export function ReactionButtons({ postId, likeCount, bookmarkCount, initialLiked = false, initialBookmarked = false }: ReactionButtonsProps) {
  const { toggleReaction } = useCommunityStore();
  const { showLoginDialog, requireAuth, onLoginConfirm, onLoginCancel } = useLoginDialog();
  const [liked, setLiked] = useState(initialLiked);
  const [saved, setSaved] = useState(initialBookmarked);
  const [showReport, setShowReport] = useState(false);

  const handleLike = () => {
    requireAuth(async () => {
      setLiked((v) => !v);
      try {
        await toggleReaction("POST", postId, "LIKE");
      } catch {
        setLiked((v) => !v);
        toast.error("좋아요 처리에 실패했습니다.");
      }
    });
  };

  const handleBookmark = () => {
    requireAuth(async () => {
      setSaved((v) => !v);
      try {
        await toggleReaction("POST", postId, "BOOKMARK");
      } catch {
        setSaved((v) => !v);
        toast.error("북마크 처리에 실패했습니다.");
      }
    });
  };

  return (
    <>
      <div className="ct-actions">
        <div className="ct-actbtns">
          <button
            className={`ct-act ct-act--like ${liked ? "is-on" : ""}`}
            onClick={handleLike}
          >
            <Heart /> 좋아요 {likeCount}
          </button>
          <button
            className={`ct-act ct-act--save ${saved ? "is-on" : ""}`}
            onClick={handleBookmark}
          >
            <Bookmark /> {saved ? "저장됨" : "북마크"} {bookmarkCount}
          </button>
        </div>
        <button className="ct-act ct-act--report" onClick={() => requireAuth(() => setShowReport(true))}>
          <Flag /> 신고
        </button>
      </div>

      {showReport && (
        <ReportDialog
          targetType="POST"
          targetId={postId}
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
