import { useEffect, useMemo, useState } from "react";
import { FileText, FileUp, Image, Link as LinkIcon, Loader2, PencilLine, Save, Upload } from "lucide-react";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Input } from "@/app/components/ui/input";
import { Textarea } from "@/app/components/ui/textarea";
import type { ApplicationSourceType } from "../types/applicationCase";
import type { JobPosting, JobPostingRequest } from "../types/jobPosting";

interface JobPostingPanelProps {
  jobPosting: JobPosting | null;
  revisions: JobPosting[];
  loading: boolean;
  saving: boolean;
  uploading: boolean;
  error: string | null;
  onSave(request: JobPostingRequest): Promise<JobPosting | null>;
  onUpload(sourceType: Extract<ApplicationSourceType, "PDF" | "IMAGE">, file: File): Promise<JobPosting | null>;
}

const sourceOptions: {
  value: ApplicationSourceType;
  label: string;
  description: string;
  icon: typeof FileText;
}[] = [
  { value: "TEXT", label: "텍스트", description: "공고문 원문 붙여넣기", icon: FileText },
  { value: "PDF", label: "PDF", description: "PDF 업로드 후 추출", icon: FileUp },
  { value: "IMAGE", label: "이미지", description: "이미지 OCR 추출", icon: Image },
  { value: "URL", label: "URL", description: "공고 페이지 본문 추출", icon: LinkIcon },
  { value: "MANUAL", label: "수동", description: "메모 기반 직접 입력", icon: PencilLine },
];

function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

function isTextSource(sourceType: ApplicationSourceType): boolean {
  return sourceType === "TEXT" || sourceType === "MANUAL";
}

function isFileSource(sourceType: ApplicationSourceType): sourceType is Extract<ApplicationSourceType, "PDF" | "IMAGE"> {
  return sourceType === "PDF" || sourceType === "IMAGE";
}

export function JobPostingPanel({
  jobPosting,
  revisions,
  loading,
  saving,
  uploading,
  error,
  onSave,
  onUpload,
}: JobPostingPanelProps) {
  const initialText = useMemo(
    () => jobPosting?.extractedText ?? jobPosting?.originalText ?? "",
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
  const [file, setFile] = useState<File | null>(null);
  const [localError, setLocalError] = useState<string | null>(null);

  useEffect(() => setText(initialText), [initialText]);
  useEffect(() => setSourceUrl(initialUrl), [initialUrl]);
  useEffect(() => setSourceType(initialSourceType), [initialSourceType]);

  const handleSave = async () => {
    const value = text.trim();
    const url = sourceUrl.trim();

    if (isTextSource(sourceType) && !value) {
      setLocalError("공고문 텍스트를 입력해 주세요.");
      return;
    }
    if (sourceType === "URL" && !url) {
      setLocalError("공고 URL을 입력해 주세요.");
      return;
    }
    if (isFileSource(sourceType) && !value && !url) {
      setLocalError("파일을 업로드하거나 추출 텍스트를 입력해 주세요.");
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

  const handleUpload = async () => {
    if (!isFileSource(sourceType)) return;
    if (!file) {
      setLocalError("업로드할 파일을 선택해 주세요.");
      return;
    }
    setLocalError(null);
    await onUpload(sourceType, file);
    setFile(null);
  };

  return (
    <Card className="border-slate-200 bg-white">
      <CardHeader className="gap-2">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <CardTitle className="flex items-center gap-2 text-lg font-bold text-slate-900">
              <FileText className="size-5 text-blue-600" />
              공고문
            </CardTitle>
            {jobPosting ? (
              <p className="mt-1 text-xs text-slate-500">
                최신 revision {jobPosting.revision} · 저장됨: {formatDateTime(jobPosting.createdAt)}
              </p>
            ) : (
              <p className="mt-1 text-xs text-slate-500">공고문 미등록</p>
            )}
          </div>
          <div className="flex flex-wrap gap-2">
            {isFileSource(sourceType) && (
              <Button
                type="button"
                size="sm"
                variant="outline"
                disabled={loading || uploading || saving}
                onClick={() => void handleUpload()}
              >
                {uploading ? <Loader2 className="size-4 animate-spin" /> : <Upload className="size-4" />}
                업로드 및 추출
              </Button>
            )}
            <Button
              type="button"
              size="sm"
              className="bg-blue-600 text-white hover:bg-blue-700"
              disabled={loading || saving || uploading}
              onClick={() => void handleSave()}
            >
              {saving ? <Loader2 className="size-4 animate-spin" /> : <Save className="size-4" />}
              {sourceType === "URL" ? "URL 저장" : "저장"}
            </Button>
          </div>
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        {loading ? (
          <div className="h-52 animate-pulse rounded-lg bg-slate-100" />
        ) : (
          <>
            <div className="grid grid-cols-2 gap-2 sm:grid-cols-5">
              {sourceOptions.map((option) => (
                <button
                  key={option.value}
                  type="button"
                  className={`flex min-h-16 flex-col items-center justify-center gap-1 rounded-md border px-2 text-center text-sm font-semibold transition-colors ${
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
                  <span>{option.label}</span>
                  <span className="text-[11px] font-normal text-slate-400">{option.description}</span>
                </button>
              ))}
            </div>

            {sourceType === "URL" && (
              <div className="space-y-2">
                <label className="text-xs font-semibold text-slate-600" htmlFor="job-posting-source-url">
                  공고 URL
                </label>
                <Input
                  id="job-posting-source-url"
                  value={sourceUrl}
                  onChange={(event) => setSourceUrl(event.target.value)}
                  placeholder="https://example.com/jobs/123"
                  className="bg-white"
                />
                <div className="rounded-lg border border-blue-100 bg-blue-50 px-3 py-2 text-xs leading-5 text-blue-800">
                  <p>URL만 입력하면 공개 페이지 본문을 추출합니다.</p>
                  <p>로그인 페이지, 동적 렌더링 페이지, 접근 차단 페이지는 추출되지 않을 수 있습니다.</p>
                  <p>추출 결과가 부족하면 아래 텍스트를 직접 보정해 저장하세요.</p>
                </div>
              </div>
            )}

            {isFileSource(sourceType) && (
              <div className="grid gap-3 rounded-lg border border-slate-200 bg-slate-50 p-3 md:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
                <div className="space-y-2">
                  <label className="text-xs font-semibold text-slate-600" htmlFor="job-posting-file">
                    {sourceType === "PDF" ? "PDF 파일" : "이미지 파일"}
                  </label>
                  <Input
                    id="job-posting-file"
                    type="file"
                    accept={sourceType === "PDF" ? "application/pdf" : "image/png,image/jpeg,image/webp,image/gif"}
                    onChange={(event) => setFile(event.target.files?.[0] ?? null)}
                    className="bg-white"
                  />
                </div>
                <div className="space-y-1 text-xs text-slate-500">
                  <div className="font-semibold text-slate-600">추출 방식</div>
                  <p>텍스트 PDF는 서버에서 바로 추출합니다.</p>
                  <p>이미지와 스캔 PDF는 OpenAI OCR을 사용합니다.</p>
                </div>
              </div>
            )}

            <Textarea
              value={text}
              onChange={(event) => setText(event.target.value)}
              placeholder={
                isTextSource(sourceType)
                  ? "공고문 원문을 붙여넣어 주세요."
                  : "업로드 또는 URL 추출 후 결과가 표시됩니다. 필요하면 직접 보정할 수 있습니다."
              }
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
          <span>파일 참조: {jobPosting?.uploadedFileUrl ?? "없음"}</span>
          <span>저장 필드: {isTextSource(sourceType) ? "originalText" : "extractedText"}</span>
        </div>

        {revisions.length > 0 && (
          <div className="rounded-lg border border-slate-200 bg-white">
            <div className="border-b border-slate-100 px-3 py-2 text-xs font-semibold text-slate-500">
              공고문 revision 이력
            </div>
            <div className="divide-y divide-slate-100">
              {revisions.map((revision) => (
                <div key={revision.id} className="flex flex-wrap items-center justify-between gap-2 px-3 py-2 text-xs text-slate-600">
                  <span className="font-semibold text-slate-900">rev {revision.revision}</span>
                  <span>{revision.sourceType}</span>
                  <span>{formatDateTime(revision.createdAt)}</span>
                  <span className="max-w-sm truncate text-slate-400">
                    {revision.extractedText ?? revision.originalText ?? revision.uploadedFileUrl ?? "내용 없음"}
                  </span>
                </div>
              ))}
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
