import { useState } from "react";
import { Avatar, AvatarFallback } from "@/app/components/ui/avatar";

interface CommentFormProps {
  onSubmit: (text: string, anonymous: boolean) => void;
  /** 답글용 컴팩트 폼 (들여쓴 인라인 표시) */
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

  return (
    <div className={"dv-cform" + (compact ? " dv-cform--reply" : "")}>
      <Avatar className={compact ? "w-7 h-7 shrink-0" : "w-8 h-8 shrink-0"}>
        <AvatarFallback className="text-xs bg-muted">나</AvatarFallback>
      </Avatar>
      <div className="dv-cform__main">
        <textarea
          className="av-textarea"
          style={{ minHeight: compact ? 64 : 84 }}
          placeholder={placeholder ?? (compact ? "답글을 입력하세요. 익명으로 등록돼요." : "면접 준비에 도움이 되는 질문·경험을 남겨주세요.")}
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          autoFocus={autoFocus}
        />
        <div className="dv-cform__row">
          <label className="dv-cform__anon">
            <input type="checkbox" checked={anon} onChange={(e) => setAnon(e.target.checked)} />
            익명으로 작성
          </label>
          <div className="right">
            {onCancel && (
              <button className="av-btn" style={{ height: 32 }} onClick={onCancel}>취소</button>
            )}
            <button
              className="av-btn av-btn--ink"
              style={{ height: 32, padding: "0 14px", opacity: draft.trim() ? 1 : 0.45 }}
              disabled={!draft.trim()}
              onClick={submit}
            >
              {compact ? "답글 등록" : "댓글 등록"}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
