import { useState } from "react";
import { CommentItem } from "./CommentItem";
import { CommentForm } from "./CommentForm";

interface Comment {
  name: string;
  time: string;
  likes: number;
  isAuthor: boolean;
  text: string;
}

interface CommentSectionProps {
  comments: Comment[];
}

export function CommentSection({ comments: initial }: CommentSectionProps) {
  const [comments, setComments] = useState(initial);

  const handleSubmit = (text: string) => {
    setComments((c) => [
      ...c,
      { name: "나", time: "방금 전", likes: 0, isAuthor: false, text },
    ]);
  };

  return (
    <div className="ct-comments">
      <h3 className="ct-comments__h">
        댓글 <b>{comments.length}</b>
      </h3>

      <div className="ct-clist">
        {comments.map((c, idx) => (
          <CommentItem key={idx} {...c} />
        ))}
      </div>

      <CommentForm onSubmit={handleSubmit} />
    </div>
  );
}
