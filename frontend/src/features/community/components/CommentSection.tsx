import { useNavigate } from "react-router";
import { CommentItem } from "./CommentItem";
import { CommentForm } from "./CommentForm";
import { useCommunityStore } from "../hooks/useCommunityStore";
import { useAuth } from "@/app/auth/AuthContext";
import type { CommunityComment } from "../types/community";

interface CommentSectionProps {
  postId: number;
  comments: CommunityComment[];
}

export function CommentSection({ postId, comments }: CommentSectionProps) {
  const { addComment } = useCommunityStore();
  const { isAuthenticated } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = (text: string) => {
    if (!isAuthenticated) {
      alert("로그인이 필요합니다.");
      navigate("/login");
      return;
    }
    addComment(postId, text);
  };

  return (
    <div className="ct-comments">
      <h3 className="ct-comments__h">
        댓글 <b>{comments.length}</b>
      </h3>

      <div className="ct-clist">
        {comments.map((c) => (
          <CommentItem key={c.id} comment={c} />
        ))}
      </div>

      <CommentForm onSubmit={handleSubmit} />
    </div>
  );
}
