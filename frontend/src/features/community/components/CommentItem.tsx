import { useState } from "react";
import { Avatar, AvatarFallback } from "@/app/components/ui/avatar";
import { Heart, MessageCircle } from "lucide-react";
import { useNavigate } from "react-router";
import { useCommunityStore } from "../hooks/useCommunityStore";
import { useAuth } from "@/app/auth/AuthContext";
import { relTime } from "@/features/notification/types/notification";
import type { CommunityComment } from "../types/community";

interface CommentItemProps {
  comment: CommunityComment;
}

export function CommentItem({ comment: c }: CommentItemProps) {
  const { toggleReaction } = useCommunityStore();
  const { isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const [liked, setLiked] = useState(c.liked ?? false);

  const requireAuth = () => {
    alert("로그인이 필요합니다.");
    navigate("/login");
  };

  const handleLike = () => {
    if (!isAuthenticated) return requireAuth();
    toggleReaction("COMMENT", c.id, "LIKE");
    setLiked((v) => !v);
  };

  return (
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
          <button className="ct-cmt__act" onClick={() => !isAuthenticated && requireAuth()}>
            <MessageCircle /> 답글
          </button>
        </div>
      </div>
    </div>
  );
}
