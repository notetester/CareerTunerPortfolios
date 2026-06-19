import { useState } from "react";
import { Avatar, AvatarFallback } from "@/app/components/ui/avatar";
import { Send } from "lucide-react";

interface CommentFormProps {
  onSubmit: (text: string, anonymous: boolean) => void;
  /** 답글용 컴팩트 폼 (모바일 고정 바 없이 인라인 표시) */
  compact?: boolean;
  placeholder?: string;
  autoFocus?: boolean;
  onCancel?: () => void;
}

export function CommentForm({ onSubmit, compact, placeholder, autoFocus, onCancel }: CommentFormProps) {
  const [draft, setDraft] = useState("");
  const [anon, setAnon] = useState(true);

  const submit = () => {
    const t = draft.trim();
    if (!t) return;
    onSubmit(t, anon);
    setDraft("");
  };

  const anonToggle = (
    <label className="ct-cform__anon">
      <input type="checkbox" checked={anon} onChange={(e) => setAnon(e.target.checked)} />
      익명으로 작성
    </label>
  );

  // 답글용 컴팩트 폼: 인라인 textarea + 등록/취소 (모바일 고정 바 미렌더)
  if (compact) {
    return (
      <div className="ct-cform ct-cform--reply">
        <Avatar className="w-7 h-7 shrink-0">
          <AvatarFallback className="text-xs bg-muted">나</AvatarFallback>
        </Avatar>
        <div className="ct-cform__main">
          <textarea
            placeholder={placeholder ?? "답글을 입력하세요. 익명으로 등록돼요."}
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            rows={2}
            autoFocus={autoFocus}
            style={{
              width: "100%",
              border: "1px solid var(--border)",
              borderRadius: 8,
              padding: "8px 12px",
              resize: "vertical",
              background: "var(--card)",
              color: "var(--foreground)",
              fontSize: 13.5,
              lineHeight: 1.6,
            }}
          />
          <div className="ct-cform__row" style={{ gap: 8 }}>
            {anonToggle}
            <button className="ct-btn-brand" disabled={!draft.trim()} onClick={submit}>
              답글 등록 <Send />
            </button>
            {onCancel && (
              <button className="ct-cmt__act" onClick={onCancel}>취소</button>
            )}
          </div>
        </div>
      </div>
    );
  }

  return (
    <>
      {/* Desktop form */}
      <div className="ct-cform">
        <Avatar className="w-8 h-8 shrink-0">
          <AvatarFallback className="text-xs bg-muted">나</AvatarFallback>
        </Avatar>
        <div className="ct-cform__main">
          <textarea
            placeholder={placeholder ?? "따뜻한 댓글은 큰 힘이 됩니다."}
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
            {anonToggle}
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
