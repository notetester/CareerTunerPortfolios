import { useState } from "react";
import { Avatar, AvatarFallback } from "@/app/components/ui/avatar";
import { Heart, MessageCircle } from "lucide-react";

interface CommentItemProps {
  name: string;
  time: string;
  likes: number;
  isAuthor: boolean;
  text: string;
}

export function CommentItem({ name, time, likes, isAuthor, text }: CommentItemProps) {
  const [liked, setLiked] = useState(false);

  return (
    <div className={`ct-cmt ${isAuthor ? "is-op" : ""}`}>
      <Avatar className="w-8 h-8 shrink-0">
        <AvatarFallback className="text-xs bg-muted">
          {name[0]}
        </AvatarFallback>
      </Avatar>
      <div className="ct-cmt__body">
        <div className="ct-cmt__top">
          <span className="ct-cmt__name">{name}</span>
          {isAuthor && <span className="ct-cmt__op">작성자</span>}
          <span className="ct-cmt__time">{time}</span>
        </div>
        <div className="ct-cmt__text">{text}</div>
        <div className="ct-cmt__foot">
          <button
            className={`ct-cmt__act ${liked ? "is-on" : ""}`}
            onClick={() => setLiked((v) => !v)}
          >
            <Heart /> {likes + (liked ? 1 : 0)}
          </button>
          <button className="ct-cmt__act">
            <MessageCircle /> 답글
          </button>
        </div>
      </div>
    </div>
  );
}
