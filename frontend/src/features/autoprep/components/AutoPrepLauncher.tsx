import { useRef, useState } from "react";

import { uploadAttachment } from "../api/autoPrepApi";
import type { AutoPrepRequest } from "../types/autoPrep";

interface FileItem {
  file: File;
  id?: number;
  uploading: boolean;
  error?: boolean;
}

interface Props {
  onRun: (req: AutoPrepRequest) => void;
  busy: boolean;
}

const EXAMPLES = [
  "카카오 프론트 압박 면접까지",
  "토스 서버 직무 위주로",
  "자소서만 첨삭해줘",
];

/** 한 줄 입력 + 파일 첨부(드래그/버튼) 런처. "준비 시작"을 누르면 채팅 팝업이 즉시 뜬다(되묻기는 그 안에서). */
export function AutoPrepLauncher({ onRun, busy }: Props) {
  const [query, setQuery] = useState("");
  const [files, setFiles] = useState<FileItem[]>([]);
  const [drag, setDrag] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  const addFiles = async (list: FileList | null) => {
    if (!list || list.length === 0) return;
    const items: FileItem[] = Array.from(list).map((file) => ({ file, uploading: true }));
    setFiles((prev) => [...prev, ...items]);
    for (const item of items) {
      try {
        const res = await uploadAttachment(item.file);
        setFiles((prev) => prev.map((f) => (f === item ? { ...f, id: res.id, uploading: false } : f)));
      } catch {
        setFiles((prev) => prev.map((f) => (f === item ? { ...f, uploading: false, error: true } : f)));
      }
    }
  };

  const removeFile = (target: FileItem) => setFiles((prev) => prev.filter((f) => f !== target));

  const submit = () => {
    if (busy) return;
    const ids = files.filter((f) => f.id != null).map((f) => f.id as number);
    onRun({
      query: query.trim() || undefined,
      attachmentFileIds: ids.length ? ids : undefined,
    });
  };

  return (
    <div
      className={`rounded-xl border bg-gradient-to-br from-primary/10 to-primary/5 p-4 transition-colors ${
        drag ? "border-primary" : "border-border"
      }`}
      onDragEnter={(e) => {
        e.preventDefault();
        setDrag(true);
      }}
      onDragOver={(e) => e.preventDefault()}
      onDragLeave={(e) => {
        if (!e.currentTarget.contains(e.relatedTarget as Node)) setDrag(false);
      }}
      onDrop={(e) => {
        e.preventDefault();
        setDrag(false);
        void addFiles(e.dataTransfer.files);
      }}
    >
      <div className="flex items-center gap-2 text-sm font-semibold text-foreground">
        <span aria-hidden>⚡</span> AI 오케스트레이터
      </div>
      <p className="mb-3 mt-1 text-xs text-muted-foreground">
        회사·직무를 말하거나, 공고 캡처·PDF·자소서를 첨부하면 6개 AI가 알아서 준비해요.
      </p>

      <div className="flex gap-2">
        <button
          type="button"
          onClick={() => inputRef.current?.click()}
          className="flex-none rounded-lg border border-border bg-background px-3 text-base text-muted-foreground transition hover:text-foreground"
          title="파일 첨부"
          aria-label="파일 첨부"
        >
          📎
        </button>
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter") submit();
          }}
          placeholder="네이버 백엔드 신입 통째로 준비해줘"
          className="flex-1 rounded-lg border border-border bg-background px-3 py-2.5 text-sm text-foreground outline-none placeholder:text-muted-foreground focus:border-primary"
        />
        <button
          type="button"
          onClick={submit}
          disabled={busy}
          className="flex-none rounded-lg bg-primary px-4 text-sm font-semibold text-primary-foreground transition hover:brightness-110 disabled:opacity-50"
        >
          준비 시작
        </button>
      </div>

      {files.length > 0 && (
        <div className="mt-3 flex flex-wrap gap-2">
          {files.map((f, i) => (
            <span
              key={i}
              className={`flex items-center gap-1.5 rounded-lg border border-border bg-card px-2.5 py-1.5 text-xs ${
                f.error ? "text-destructive" : "text-foreground"
              }`}
            >
              {f.uploading ? "⏳" : f.error ? "⚠️" : "📄"} {f.file.name}
              <button
                type="button"
                onClick={() => removeFile(f)}
                className="text-muted-foreground hover:text-foreground"
                aria-label="첨부 제거"
              >
                ✕
              </button>
            </span>
          ))}
        </div>
      )}

      <div className="mt-3 flex flex-wrap gap-1.5">
        {EXAMPLES.map((ex) => (
          <button
            key={ex}
            type="button"
            onClick={() => setQuery(ex)}
            className="rounded-full border border-border bg-card px-2.5 py-1 text-[11px] text-muted-foreground transition hover:text-foreground"
          >
            {ex}
          </button>
        ))}
      </div>

      <p className="mt-2.5 text-[11px] text-muted-foreground">
        🔒 무료는 첨부 1개까지 반영돼요. 프리미엄이면 여러 개·대용량까지.
      </p>

      <input
        ref={inputRef}
        type="file"
        multiple
        className="hidden"
        onChange={(e) => {
          void addFiles(e.target.files);
          e.target.value = "";
        }}
      />
    </div>
  );
}
