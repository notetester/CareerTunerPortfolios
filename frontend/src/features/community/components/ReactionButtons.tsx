import { useState } from "react";
import { Heart, Bookmark, Flag } from "lucide-react";
import { ReportDialog } from "./ReportDialog";

interface ReactionButtonsProps {
  likes: number;
}

export function ReactionButtons({ likes }: ReactionButtonsProps) {
  const [liked, setLiked] = useState(false);
  const [saved, setSaved] = useState(false);
  const [showReport, setShowReport] = useState(false);

  return (
    <>
      <div className="ct-actions">
        <div className="ct-actbtns">
          <button
            className={`ct-act ct-act--like ${liked ? "is-on" : ""}`}
            onClick={() => setLiked(!liked)}
          >
            <Heart /> 좋아요 {likes + (liked ? 1 : 0)}
          </button>
          <button
            className={`ct-act ct-act--save ${saved ? "is-on" : ""}`}
            onClick={() => setSaved(!saved)}
          >
            <Bookmark /> {saved ? "저장됨" : "북마크"}
          </button>
        </div>
        <button className="ct-act ct-act--report" onClick={() => setShowReport(true)}>
          <Flag /> 신고
        </button>
      </div>

      {showReport && (
        <ReportDialog target="게시글" onClose={() => setShowReport(false)} />
      )}
    </>
  );
}
