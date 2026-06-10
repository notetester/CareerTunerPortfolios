import { useState } from "react";
import { Heart, Bookmark, Flag } from "lucide-react";
import { useNavigate } from "react-router";
import { ReportDialog } from "./ReportDialog";
import { useCommunityStore } from "../hooks/useCommunityStore";
import { useAuth } from "@/app/auth/AuthContext";

interface ReactionButtonsProps {
  postId: number;
  likeCount: number;
  bookmarkCount: number;
  initialLiked?: boolean;
  initialBookmarked?: boolean;
}

export function ReactionButtons({ postId, likeCount, bookmarkCount, initialLiked = false, initialBookmarked = false }: ReactionButtonsProps) {
  const { toggleReaction } = useCommunityStore();
  const { isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const [liked, setLiked] = useState(initialLiked);
  const [saved, setSaved] = useState(initialBookmarked);
  const [showReport, setShowReport] = useState(false);

  const requireAuth = () => {
    alert("로그인이 필요합니다.");
    navigate("/login");
  };

  const handleLike = () => {
    if (!isAuthenticated) return requireAuth();
    toggleReaction("POST", postId, "LIKE");
    setLiked((v) => !v);
  };

  const handleBookmark = () => {
    if (!isAuthenticated) return requireAuth();
    toggleReaction("POST", postId, "BOOKMARK");
    setSaved((v) => !v);
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
        <button className="ct-act ct-act--report" onClick={() => isAuthenticated ? setShowReport(true) : requireAuth()}>
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
    </>
  );
}
