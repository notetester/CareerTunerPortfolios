import { useEffect, useMemo, useState } from "react";
import { AlertTriangle, FilePenLine, Lightbulb, ListChecks, ShieldAlert } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/app/components/ui/dialog";
import { Textarea } from "@/app/components/ui/textarea";
import type { AdminCorrectionDetail, ParsedCorrectionResult } from "../types";

interface CorrectionDetailDialogProps {
  open: boolean;
  detail: AdminCorrectionDetail | null;
  loading: boolean;
  error: string | null;
  onOpenChange: (open: boolean) => void;
  onRetry: () => void;
  onSaveMemo: (memo: string | null) => Promise<void>;
}

export function CorrectionDetailDialog({
  open,
  detail,
  loading,
  error,
  onOpenChange,
  onRetry,
  onSaveMemo,
}: CorrectionDetailDialogProps) {
  const [memo, setMemo] = useState("");
  const [saving, setSaving] = useState(false);
  const [memoError, setMemoError] = useState<string | null>(null);
  const result = useMemo(() => parseCorrectionResult(detail?.resultJson ?? null), [detail?.resultJson]);

  useEffect(() => {
    setMemo(detail?.adminMemo ?? "");
    setMemoError(null);
  }, [detail?.id, detail?.adminMemo]);

  const close = (nextOpen: boolean) => {
    if (!nextOpen && saving) return;
    onOpenChange(nextOpen);
  };

  const saveMemo = async () => {
    if (!detail || saving) return;
    setSaving(true);
    setMemoError(null);
    try {
      await onSaveMemo(memo.trim() || null);
    } catch (reason) {
      setMemoError(errorMessage(reason, "운영 메모를 저장하지 못했습니다."));
    } finally {
      setSaving(false);
    }
  };

  const originalMemo = detail?.adminMemo?.trim() ?? "";
  const memoChanged = memo.trim() !== originalMemo;

  return (
    <Dialog open={open} onOpenChange={close}>
      <DialogContent className="max-h-[92vh] overflow-y-auto border-border bg-card text-card-foreground sm:max-w-5xl">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <FilePenLine className="size-5 text-blue-600" />
            첨삭 상세
          </DialogTitle>
          <DialogDescription>
            {detail ? `${detail.userName} · ${typeLabel(detail.correctionType)} · ${formatDate(detail.createdAt)}` : "첨삭 결과를 불러오는 중입니다."}
          </DialogDescription>
        </DialogHeader>

        {loading && <DialogState message="첨삭 상세를 불러오는 중입니다." />}
        {!loading && error && (
          <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
            <div className="flex items-center gap-2 font-semibold"><AlertTriangle className="size-4" />{error}</div>
            <Button className="mt-3" size="sm" variant="outline" onClick={onRetry}>다시 시도</Button>
          </div>
        )}

        {!loading && !error && detail && (
          <div className="space-y-5">
            <div className="grid gap-3 rounded-lg border border-border bg-surface-2 p-4 text-sm sm:grid-cols-2 lg:grid-cols-4">
              <Meta label="회원" value={`${detail.userName} (${detail.userEmail})`} />
              <Meta label="지원 건" value={caseLabel(detail)} />
              <Meta label="모델" value={detail.model ?? "기록 없음"} />
              <Meta label="사용량" value={`${detail.totalTokens?.toLocaleString("ko-KR") ?? 0} tokens · ${detail.creditUsed ?? 0} credit`} />
            </div>

            <div className="grid gap-4 xl:grid-cols-2">
              <TextPanel title="원문" text={detail.originalText} className="bg-slate-50 dark:bg-slate-900/40" />
              <TextPanel title="개선문" text={detail.improvedText || "개선문이 기록되지 않았습니다."} className="bg-emerald-50/60 dark:bg-emerald-950/20" />
            </div>

            {result.summary && (
              <section className="rounded-lg border border-blue-100 bg-blue-50/60 p-4 dark:border-blue-900 dark:bg-blue-950/20">
                <h3 className="flex items-center gap-2 text-sm font-bold"><ListChecks className="size-4 text-blue-600" />첨삭 요약</h3>
                <p className="mt-2 whitespace-pre-wrap break-words text-sm leading-6 text-muted-foreground">{result.summary}</p>
              </section>
            )}

            <div className="grid gap-4 lg:grid-cols-3">
              <ResultList title="확인할 점" items={result.issues} icon={<ShieldAlert className="size-4 text-rose-600" />} />
              <ResultList title="변경 이유" items={result.changeReasons} icon={<ListChecks className="size-4 text-indigo-600" />} />
              <ResultList title="추천 표현" items={result.suggestions} icon={<Lightbulb className="size-4 text-amber-600" />} />
            </div>

            {result.raw && (
              <section className="rounded-lg border border-amber-200 bg-amber-50 p-4 dark:border-amber-900 dark:bg-amber-950/20">
                <h3 className="text-sm font-bold text-amber-800 dark:text-amber-300">구조화 결과를 해석하지 못했습니다.</h3>
                <pre className="mt-2 max-h-48 overflow-auto whitespace-pre-wrap break-words text-xs leading-5 text-amber-900 dark:text-amber-200">{result.raw}</pre>
              </section>
            )}

            <section>
              <div className="mb-2 flex items-center justify-between gap-3">
                <label htmlFor="correction-admin-memo" className="text-sm font-bold">운영 메모</label>
                <span className="text-xs text-muted-foreground">{memo.length}/2000</span>
              </div>
              <Textarea
                id="correction-admin-memo"
                value={memo}
                onChange={(event) => setMemo(event.target.value)}
                maxLength={2000}
                disabled={saving}
                placeholder="검수 결과나 후속 확인 사항을 기록하세요. 비우고 저장하면 메모가 삭제됩니다."
                className="min-h-28 resize-y"
              />
              {memoError && <p className="mt-2 text-sm text-red-600">{memoError}</p>}
            </section>
          </div>
        )}

        <DialogFooter>
          <Button variant="outline" onClick={() => close(false)} disabled={saving}>닫기</Button>
          <Button onClick={() => void saveMemo()} disabled={!detail || loading || !!error || saving || !memoChanged}>
            {saving ? "저장 중..." : "메모 저장"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function DialogState({ message }: { message: string }) {
  return <div className="rounded-lg border border-border bg-surface-2 p-8 text-center text-sm text-muted-foreground">{message}</div>;
}

function Meta({ label, value }: { label: string; value: string }) {
  return <div className="min-w-0"><div className="text-xs font-semibold text-muted-foreground">{label}</div><div className="mt-1 break-words font-semibold">{value}</div></div>;
}

function TextPanel({ title, text, className }: { title: string; text: string; className: string }) {
  return <section className={`min-w-0 rounded-lg border border-border p-4 ${className}`}><h3 className="text-sm font-bold">{title}</h3><p className="mt-3 max-h-96 overflow-auto whitespace-pre-wrap break-words text-sm leading-7 text-foreground">{text}</p></section>;
}

function ResultList({ title, items, icon }: { title: string; items: string[]; icon: React.ReactNode }) {
  return (
    <section className="rounded-lg border border-border p-4">
      <h3 className="flex items-center gap-2 text-sm font-bold">{icon}{title}</h3>
      {items.length > 0 ? <ul className="mt-3 space-y-2 text-sm text-muted-foreground">{items.map((item, index) => <li key={`${title}-${index}`} className="break-words leading-6">{index + 1}. {item}</li>)}</ul> : <p className="mt-3 text-sm text-muted-foreground">기록된 항목이 없습니다.</p>}
    </section>
  );
}

function parseCorrectionResult(raw: string | null): ParsedCorrectionResult {
  if (!raw) return { summary: "", issues: [], changeReasons: [], suggestions: [], raw: null };
  try {
    const value = JSON.parse(raw) as unknown;
    if (!isRecord(value)) throw new Error("not an object");
    return {
      summary: typeof value.summary === "string" ? value.summary : "",
      issues: stringArray(value.issues),
      changeReasons: stringArray(value.changeReasons ?? value.change_reasons),
      suggestions: stringArray(value.suggestions),
      raw: null,
    };
  } catch {
    return { summary: "", issues: [], changeReasons: [], suggestions: [], raw };
  }
}

function stringArray(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === "string") : [];
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}

function caseLabel(detail: AdminCorrectionDetail) {
  if (!detail.applicationCaseId) return "직접 입력";
  return [detail.companyName, detail.jobTitle].filter(Boolean).join(" · ") || `#${detail.applicationCaseId}`;
}

function typeLabel(type: string) {
  return ({ SELF_INTRO: "자기소개서", INTERVIEW_ANSWER: "면접 답변", RESUME: "이력서", PORTFOLIO: "포트폴리오" } as Record<string, string>)[type] ?? type;
}

function formatDate(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "날짜 정보 없음" : new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(date);
}

function errorMessage(reason: unknown, fallback: string) {
  return reason instanceof Error && reason.message ? reason.message : fallback;
}
