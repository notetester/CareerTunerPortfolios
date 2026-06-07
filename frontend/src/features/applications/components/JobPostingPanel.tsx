import { useEffect, useMemo, useState } from "react";
import { FileText, FileUp, Image, Link as LinkIcon, PencilLine, Save } from "lucide-react";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Input } from "@/app/components/ui/input";
import { Textarea } from "@/app/components/ui/textarea";
import type { ApplicationSourceType } from "../types/applicationCase";
import type { JobPosting, JobPostingRequest } from "../types/jobPosting";

interface JobPostingPanelProps {
  jobPosting: JobPosting | null;
  loading: boolean;
  saving: boolean;
  error: string | null;
  onSave(request: JobPostingRequest): Promise<JobPosting | null>;
}

function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

const sourceOptions: {
  value: ApplicationSourceType;
  label: string;
  icon: typeof FileText;
}[] = [
  { value: "TEXT", label: "텍스트", icon: FileText },
  { value: "PDF", label: "PDF", icon: FileUp },
  { value: "IMAGE", label: "이미지", icon: Image },
  { value: "URL", label: "URL", icon: LinkIcon },
  { value: "MANUAL", label: "수동", icon: PencilLine },
];

function isTextSource(sourceType: ApplicationSourceType): boolean {
  return sourceType === "TEXT" || sourceType === "MANUAL";
}

export function JobPostingPanel({
  jobPosting,
  loading,
  saving,
  error,
  onSave,
}: JobPostingPanelProps) {
  const initialText = useMemo(
    () => jobPosting?.originalText ?? jobPosting?.extractedText ?? "",
    [jobPosting],
  );
  const initialUrl = useMemo(() => jobPosting?.uploadedFileUrl ?? "", [jobPosting]);
  const initialSourceType = useMemo<ApplicationSourceType>(
    () => jobPosting?.sourceType ?? "TEXT",
    [jobPosting],
  );
  const [sourceType, setSourceType] = useState<ApplicationSourceType>(initialSourceType);
  const [text, setText] = useState(initialText);
  const [sourceUrl, setSourceUrl] = useState(initialUrl);
  const [localError, setLocalError] = useState<string | null>(null);

  useEffect(() => {
    setText(initialText);
  }, [initialText]);

  useEffect(() => {
    setSourceUrl(initialUrl);
  }, [initialUrl]);

  useEffect(() => {
    setSourceType(initialSourceType);
  }, [initialSourceType]);

  const handleSave = async () => {
    const value = text.trim();
    const url = sourceUrl.trim();
    if (isTextSource(sourceType) && !value) {
      setLocalError("공고문 텍스트를 입력해주세요.");
      return;
    }
    if (!isTextSource(sourceType) && !url) {
      setLocalError("파일 URL 또는 공고 URL을 입력해주세요.");
      return;
    }

    setLocalError(null);
    const request: JobPostingRequest = {
      sourceType,
      uploadedFileUrl: url || null,
      originalText: isTextSource(sourceType) ? value : null,
      extractedText: value || null,
    };
    await onSave(request);
  };

  return (
    <Card className="border-slate-200 bg-white">
      <CardHeader className="gap-2">
        <div className="flex items-start justify-between gap-3">
          <div>
            <CardTitle className="flex items-center gap-2 text-lg font-bold text-slate-900">
              <FileText className="size-5 text-blue-600" />
              공고문
            </CardTitle>
            {jobPosting ? (
              <p className="mt-1 text-xs text-slate-500">저장됨: {formatDateTime(jobPosting.createdAt)}</p>
            ) : (
              <p className="mt-1 text-xs text-slate-500">공고문 미등록</p>
            )}
          </div>
          <Button
            type="button"
            size="sm"
            className="bg-blue-600 text-white hover:bg-blue-700"
            disabled={loading || saving}
            onClick={() => void handleSave()}
          >
            <Save className="size-4" />
            {saving ? "저장 중" : "저장"}
          </Button>
        </div>
      </CardHeader>
      <CardContent className="space-y-3">
        {loading ? (
          <div className="h-52 animate-pulse rounded-lg bg-slate-100" />
        ) : (
          <>
            <div className="grid grid-cols-2 gap-2 sm:grid-cols-5">
              {sourceOptions.map((option) => (
                <button
                  key={option.value}
                  type="button"
                  className={`flex h-10 items-center justify-center gap-1.5 rounded-md border px-2 text-sm font-semibold transition-colors ${
                    sourceType === option.value
                      ? "border-blue-600 bg-blue-50 text-blue-700"
                      : "border-slate-200 bg-white text-slate-600 hover:border-blue-200 hover:bg-slate-50"
                  }`}
                  onClick={() => {
                    setSourceType(option.value);
                    setLocalError(null);
                  }}
                >
                  <option.icon className="size-4" />
                  {option.label}
                </button>
              ))}
            </div>

            {!isTextSource(sourceType) && (
              <div className="space-y-2">
                <label className="text-xs font-semibold text-slate-600" htmlFor="job-posting-source-url">
                  {sourceType === "URL" ? "공고 URL" : "파일 URL"}
                </label>
                <Input
                  id="job-posting-source-url"
                  value={sourceUrl}
                  onChange={(event) => setSourceUrl(event.target.value)}
                  placeholder={sourceType === "URL" ? "https://example.com/jobs/123" : "https://example.com/posting-file"}
                  className="bg-white"
                />
              </div>
            )}

            <Textarea
              value={text}
              onChange={(event) => setText(event.target.value)}
              placeholder={isTextSource(sourceType) ? "공고문 원문을 붙여넣으세요." : "추출 텍스트 또는 메모를 입력하세요."}
              className="min-h-72 resize-y bg-white text-sm leading-6"
            />
          </>
        )}

        {(localError || error) && (
          <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
            {localError ?? error}
          </div>
        )}

        <div className="grid gap-2 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-xs leading-5 text-slate-500 sm:grid-cols-3">
          <span>저장 출처: {sourceOptions.find((option) => option.value === sourceType)?.label}</span>
          <span>파일 업로드/OCR: 준비 상태</span>
          <span>저장 필드: {isTextSource(sourceType) ? "originalText" : "uploadedFileUrl"}</span>
        </div>
      </CardContent>
    </Card>
  );
}
