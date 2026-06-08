import { useState } from "react";
import { Avatar, AvatarFallback } from "@/app/components/ui/avatar";
import { Send } from "lucide-react";

interface CommentFormProps {
  onSubmit: (text: string) => void;
}

export function CommentForm({ onSubmit }: CommentFormProps) {
  const [draft, setDraft] = useState("");

  const submit = () => {
    const t = draft.trim();
    if (!t) return;
    onSubmit(t);
    setDraft("");
  };

  return (
    <>
      {/* Desktop form */}
      <div className="ct-cform">
        <Avatar className="w-8 h-8 shrink-0">
          <AvatarFallback className="text-xs bg-muted">나</AvatarFallback>
        </Avatar>
        <div className="ct-cform__main">
          <textarea
            placeholder="따뜻한 댓글은 큰 힘이 됩니다. 익명으로 등록돼요."
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            rows={3}
            style={{
              width: "100%",
              border: "1px solid var(--border)",
              borderRadius: 8,
              padding: "10px 14px",
              resize: "vertical",
              background: "var(--card)",
              color: "var(--foreground)",
              fontSize: 14,
              lineHeight: 1.6,
            }}
          />
          <div className="ct-cform__row">
            <button
              className="ct-btn-brand"
              disabled={!draft.trim()}
              onClick={submit}
            >
              댓글 등록 <Send />
            </button>
          </div>
        </div>
      </div>

      {/* Mobile sticky bar */}
      <div className="ct-cbar">
        <input
          placeholder="댓글을 입력하세요"
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && submit()}
        />
        <button className="ct-cbar__send" onClick={submit}>
          <Send />
        </button>
      </div>
    </>
  );
}
