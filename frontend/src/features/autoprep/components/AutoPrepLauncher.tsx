import { useRef, useState } from "react";
import { AlertTriangle, FileText, History, Loader2, Lock, Paperclip, X, Zap } from "lucide-react";

import { createJobPostingCaseFromFile, uploadAttachment } from "../api/autoPrepApi";
import { deletePendingAutoPrepFile } from "@/app/lib/pendingAutoPrepFiles";
import { ApiError } from "@/app/lib/api";
import type { AutoPrepRequest } from "../types/autoPrep";

/** 첨부 파일 역할 — 공고(지원 건 생성용) / 자소서(WRITE 교정용). */
type FileRole = "posting" | "resume";

interface FileItem {
  key: string;
  file: File;
  id?: number;
  uploading: boolean;
  error?: boolean;
  role: FileRole;
}

let nextFileItemKey = 0;

function createFileItemKey(): string {
  nextFileItemKey += 1;
  return `autoprep-file-${nextFileItemKey}`;
}

interface Props {
  onRun: (req: AutoPrepRequest) => void;
  /** 지난 준비 기록 열기(요청 없이 창만) — 미전달이면 버튼을 숨긴다. */
  onHistory?: () => void;
  busy: boolean;
}

const EXAMPLES = [
  "카카오 프론트 압박 면접까지",
  "토스 서버 직무 위주로",
  "자소서만 첨삭해줘",
];

/** 파일 타입으로 역할 초기값 추정 — 이미지·PDF·'공고/채용/jd' 이름은 공고, 그 외는 자소서. 사용자가 토글로 바꾼다. */
function defaultRole(file: File): FileRole {
  const type = file.type || "";
  const name = file.name.toLowerCase();
  if (type.startsWith("image/") || type === "application/pdf" || name.endsWith(".pdf")) return "posting";
  if (name.includes("공고") || name.includes("채용") || name.includes("jd")) return "posting";
  return "resume";
}

/** 공고 파일 중 이미지/PDF 는 B 지원건 생성 엔드포인트로, 텍스트/docx 는 백엔드 본문 추출로 라우팅한다. */
function isBinaryPosting(file: File): boolean {
  const type = file.type || "";
  return type.startsWith("image/") || type === "application/pdf" || file.name.toLowerCase().endsWith(".pdf");
}

/** 한 줄 입력 + 파일 첨부(드래그/버튼) 런처. "준비 시작"을 누르면 채팅 팝업이 즉시 뜬다(되묻기는 그 안에서). */
export function AutoPrepLauncher({ onRun, onHistory, busy }: Props) {
  const [query, setQuery] = useState("");
  const [files, setFiles] = useState<FileItem[]>([]);
  const [drag, setDrag] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const removedUploadingKeysRef = useRef(new Set<string>());

  const addFiles = async (list: FileList | null) => {
    if (!list || list.length === 0) return;
    const items: FileItem[] = Array.from(list).map((file) => ({
      key: createFileItemKey(),
      file,
      uploading: true,
      role: defaultRole(file),
    }));
    setFiles((prev) => [...prev, ...items]);
    for (const item of items) {
      try {
        const res = await uploadAttachment(item.file);
        if (removedUploadingKeysRef.current.delete(item.key)) {
          await deletePendingAutoPrepFile(res.id).catch(() => undefined);
          continue;
        }
        setFiles((prev) => prev.map((f) => (f.key === item.key ? { ...f, id: res.id, uploading: false } : f)));
      } catch {
        removedUploadingKeysRef.current.delete(item.key);
        setFiles((prev) => prev.map((f) => (f.key === item.key ? { ...f, uploading: false, error: true } : f)));
      }
    }
  };

  const removeFile = async (target: FileItem) => {
    if (target.uploading) {
      removedUploadingKeysRef.current.add(target.key);
      setFiles((prev) => prev.filter((file) => file.key !== target.key));
      return;
    }
    if (target.id != null) {
      try {
        await deletePendingAutoPrepFile(target.id);
      } catch {
        setFiles((prev) => prev.map((file) => (file.key === target.key ? { ...file, error: true } : file)));
        return;
      }
    }
    setFiles((prev) => prev.filter((file) => file.key !== target.key));
  };

  const setRole = (target: FileItem, role: FileRole) => {
    setFiles((prev) => prev.map((f) => (f.key === target.key ? { ...f, role } : f)));
  };

  const submit = async () => {
    if (busy || submitting) return;
    setSubmitError(null);
    const ready = files.filter((f) => f.id != null && !f.error && !f.uploading);
    const resumeIds = ready.filter((f) => f.role === "resume").map((f) => f.id as number);
    const postings = ready.filter((f) => f.role === "posting");
    if (postings.length > 1) {
      setSubmitError("공고는 한 번에 1개만 선택해 주세요. 자소서는 함께 첨부할 수 있어요.");
      return;
    }
    const postingTextIds = postings.filter((f) => !isBinaryPosting(f.file)).map((f) => f.id as number);
    const binaryPostings = postings.filter((f) => isBinaryPosting(f.file));

    setSubmitting(true);
    try {
      // 이미지/PDF 공고는 B 엔드포인트로 지원 건을 먼저 만든다(첫 건만 — 오케는 단일 지원 건). 나머지는 무시.
      let applicationCaseId: number | undefined;
      const first = binaryPostings[0];
      if (first) {
        const sourceType = (first.file.type || "").startsWith("image/") ? "IMAGE" : "PDF";
        applicationCaseId = await createJobPostingCaseFromFile(
          first.file,
          sourceType,
          first.id as number,
          resumeIds,
        );
        // ATTACHMENT 로 이미 올라간 pending 파일은 회수한다(공고 원본은 B 저장소로 갔다).
        binaryPostings.forEach((f) => {
          if (f.id != null) void deletePendingAutoPrepFile(f.id).catch(() => undefined);
        });
      }
      onRun({
        query: query.trim() || undefined,
        applicationCaseId,
        attachmentFileIds: resumeIds.length ? resumeIds : undefined,
        jobPostingFileIds: postingTextIds.length ? postingTextIds : undefined,
      });
      setFiles([]);
      setQuery("");
    } catch (error) {
      setSubmitError(
        error instanceof ApiError && /[가-힣]/.test(error.message)
          ? error.message
          : "공고 파일 처리에 실패했어요. 같은 파일로 다시 시도해 주세요.",
      );
    } finally {
      setSubmitting(false);
    }
  };

  const anyUploading = files.some((f) => f.uploading);

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
        <Zap className="size-4 text-primary" aria-hidden /> AI 오케스트레이터
        {onHistory && (
          <button
            type="button"
            onClick={onHistory}
            className="ml-auto flex items-center gap-1 rounded-lg border border-border bg-card px-2 py-1 text-[11px] font-semibold text-muted-foreground transition hover:text-foreground"
            title="지난 준비 기록"
          >
            <History className="size-3.5" /> 기록
          </button>
        )}
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
          <Paperclip className="size-4" />
        </button>
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter") void submit();
          }}
          placeholder="네이버 백엔드 신입 통째로 준비해줘"
          className="min-w-0 flex-1 rounded-lg border border-border bg-background px-3 py-2.5 text-sm text-foreground outline-none placeholder:text-muted-foreground focus:border-primary"
        />
        <button
          type="button"
          onClick={() => void submit()}
          disabled={busy || submitting || anyUploading}
          className="flex-none rounded-lg bg-primary px-4 text-sm font-semibold text-primary-foreground transition hover:brightness-110 disabled:opacity-50"
        >
          {submitting ? <Loader2 className="mx-1 size-4 animate-spin" /> : "준비 시작"}
        </button>
      </div>

      {files.length > 0 && (
        <div className="mt-3 flex flex-wrap gap-2">
          {files.map((f) => (
            <div
              key={f.key}
              className={`flex items-center gap-2 rounded-lg border border-border bg-card px-2.5 py-1.5 text-xs ${
                f.error ? "text-destructive" : "text-foreground"
              }`}
            >
              <span className="flex min-w-0 items-center gap-1.5">
                {f.uploading ? (
                  <Loader2 className="size-3.5 shrink-0 animate-spin" />
                ) : f.error ? (
                  <AlertTriangle className="size-3.5 shrink-0" />
                ) : (
                  <FileText className="size-3.5 shrink-0" />
                )}
                <span className="max-w-[140px] truncate">{f.file.name}</span>
              </span>
              {/* 역할 토글 — 공고(지원 건 생성) / 자소서(첨삭). 사용자가 명시 선택. */}
              <span className="flex overflow-hidden rounded-md border border-border">
                <button
                  type="button"
                  onClick={() => setRole(f, "posting")}
                  className={`px-1.5 py-0.5 text-[10.5px] font-semibold transition ${
                    f.role === "posting"
                      ? "bg-primary text-primary-foreground"
                      : "text-muted-foreground hover:text-foreground"
                  }`}
                >
                  공고
                </button>
                <button
                  type="button"
                  onClick={() => setRole(f, "resume")}
                  className={`px-1.5 py-0.5 text-[10.5px] font-semibold transition ${
                    f.role === "resume"
                      ? "bg-primary text-primary-foreground"
                      : "text-muted-foreground hover:text-foreground"
                  }`}
                >
                  자소서
                </button>
              </span>
              <button
                type="button"
                onClick={() => void removeFile(f)}
                className="shrink-0 text-muted-foreground hover:text-foreground"
                aria-label="첨부 제거"
              >
                <X className="size-3.5" />
              </button>
            </div>
          ))}
        </div>
      )}

      {submitError && <p className="mt-2 text-[11px] text-destructive">{submitError}</p>}

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
        <Lock className="mr-1 inline size-3 align-[-1px]" /> 공고를 첨부하면 지원 건을 자동으로 만들어요. 무료는 첨부 1개까지 반영돼요.
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
