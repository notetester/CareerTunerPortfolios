import { useEffect, useMemo, useState } from "react";
import { ScrollText } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/app/components/ui/dialog";
import { Progress } from "@/app/components/ui/progress";
import { getInterviewModeLabel, getScoreColor } from "@/features/interview/types/interview";
import { getAdminInterviewSessionDetail, updateAdminMemo } from "../../interviews/api";
import { parseReport } from "../../interviews/report";
import type { AdminInterviewSessionDetail, AdminInterviewSessionRow } from "../../interviews/types";
import { useAdminDomainAuthorization } from "../../../auth/useAdminAuthorization";

function formatDateTime(value: string | null): string {
  if (!value) return "-";
  return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

/** 리포트 상세 다이얼로그 — 세션 리포트(JSON) 렌더 + 운영 메모.
 *  세션 요약은 목록 행(row)으로 즉시 그리고, 리포트/메모는 상세 API 로딩 후 채운다. */
export function ReportDetailDialog({
  row,
  open,
  onOpenChange,
}: {
  row: AdminInterviewSessionRow | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { canUpdate } = useAdminDomainAuthorization("AI");
  const [detail, setDetail] = useState<AdminInterviewSessionDetail | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const report = useMemo(() => parseReport(detail?.report ?? null), [detail]);

  useEffect(() => {
    if (!open || !row) {
      setDetail(null);
      return;
    }
    let cancelled = false;
    setLoading(true);
    setError(null);
    getAdminInterviewSessionDetail(row.id)
      .then((d) => { if (!cancelled) setDetail(d); })
      .catch((e) => { if (!cancelled) setError(e instanceof Error ? e.message : "리포트를 불러오지 못했습니다."); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [open, row?.id]);

  if (!row) return null;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[85vh] overflow-y-auto border-border bg-card text-card-foreground sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2 text-foreground">
            <ScrollText className="size-5 text-blue-600" />
            {row.companyName} · {row.jobTitle}
          </DialogTitle>
          <DialogDescription>
            {row.userEmail} · {getInterviewModeLabel(row.mode)} · {formatDateTime(row.createdAt)}
          </DialogDescription>
        </DialogHeader>

        {error && (
          <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div>
        )}

        {loading && !detail && (
          <div className="rounded-lg bg-slate-50 p-6 text-center text-sm text-slate-400">리포트 불러오는 중...</div>
        )}

        {detail && !report && !loading && (
          <div className="rounded-lg border border-dashed border-slate-200 p-6 text-center text-sm text-slate-400">
            표준 형식 리포트가 아닙니다. (구버전 리포트는 세션 모니터링 화면에서 원문을 확인하세요)
          </div>
        )}

        {report && (
          <div className="space-y-4">
            {/* 총평 헤더 */}
            <div className="flex flex-wrap items-center gap-3 rounded-lg border border-slate-200 bg-slate-50 p-4">
              <div>
                <div className="text-xs font-semibold text-slate-500">총점</div>
                <div className={`text-3xl font-black ${getScoreColor(report.totalScore)}`}>{report.totalScore}점</div>
              </div>
              <div className="ml-auto flex flex-wrap items-center gap-2 text-xs text-slate-500">
                {report.previousScore != null && (
                  <Badge variant="outline">
                    이전 {report.previousScore}점 → {report.totalScore >= report.previousScore ? "+" : ""}
                    {report.totalScore - report.previousScore}
                  </Badge>
                )}
                <Badge variant="outline">질문 {report.questionCount}개</Badge>
                {report.durationLabel && <Badge variant="outline">{report.durationLabel}</Badge>}
              </div>
            </div>

            {/* 카테고리 점수 */}
            <section className="space-y-2">
              <h3 className="text-sm font-bold text-slate-900">항목별 점수</h3>
              {report.categories.map((c) => (
                <div key={c.label} className="space-y-1">
                  <div className="flex items-center justify-between text-xs">
                    <span className="font-semibold text-slate-700">{c.label}</span>
                    <span className={`font-black ${getScoreColor(c.score)}`}>{c.score}점</span>
                  </div>
                  <Progress value={c.score} className="h-2" />
                </div>
              ))}
            </section>

            {/* 총평 피드백 */}
            {report.summaryFeedback.length > 0 && (
              <section className="space-y-1">
                <h3 className="text-sm font-bold text-slate-900">총평</h3>
                <ul className="list-disc space-y-1 pl-5 text-sm text-slate-600">
                  {report.summaryFeedback.map((line, i) => (
                    <li key={i}>{line}</li>
                  ))}
                </ul>
              </section>
            )}

            {/* 질문별 채점 — 리포트 JSON 에 questionScores 가 있을 때만 (구버전 리포트는 없을 수 있음) */}
            {Array.isArray(report.questionScores) && report.questionScores.length > 0 && (
              <section className="space-y-2">
                <h3 className="text-sm font-bold text-slate-900">질문별 채점</h3>
                {report.questionScores.map((q) => (
                  <div key={q.questionId} className="rounded-lg border border-slate-200 p-3 text-sm">
                    <div className="flex items-start justify-between gap-2">
                      <div className="flex min-w-0 items-start gap-2">
                        <Badge className="shrink-0 bg-blue-100 text-blue-700">Q{q.order}</Badge>
                        <span className="font-medium text-slate-900">{q.question}</span>
                      </div>
                      <span className={`shrink-0 text-sm font-black ${q.score !== null ? getScoreColor(q.score) : "text-slate-400"}`}>
                        {q.score !== null ? `${q.score}점` : "-"}
                      </span>
                    </div>
                    {q.feedback && <p className="mt-1.5 text-xs text-slate-500">{q.feedback}</p>}
                  </div>
                ))}
              </section>
            )}
          </div>
        )}

        {detail && (
          <MemoSection
            sessionId={row.id}
            initial={detail.session.adminMemo}
            onSaved={() => void getAdminInterviewSessionDetail(row.id).then(setDetail).catch(() => undefined)}
            canUpdate={canUpdate}
          />
        )}
      </DialogContent>
    </Dialog>
  );
}

/** 운영 메모 — PUT /api/admin/interview/sessions/{id}/memo 재사용. 사용자 미노출. */
function MemoSection({
  sessionId,
  initial,
  onSaved,
  canUpdate,
}: {
  sessionId: number;
  initial: string | null;
  onSaved: () => void;
  canUpdate: boolean;
}) {
  const [memo, setMemo] = useState(initial ?? "");
  const [saving, setSaving] = useState(false);
  const [note, setNote] = useState<string | null>(null);

  useEffect(() => {
    setMemo(initial ?? "");
    setNote(null);
  }, [initial, sessionId]);

  const save = async () => {
    if (!canUpdate) return;
    setSaving(true);
    setNote(null);
    try {
      await updateAdminMemo(sessionId, memo);
      setNote("저장되었습니다.");
      onSaved();
    } catch (e) {
      setNote(e instanceof Error ? e.message : "메모 저장에 실패했습니다.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <section className="space-y-2 border-t border-slate-200 pt-3">
      <h3 className="text-sm font-bold text-slate-900">운영 메모</h3>
      {canUpdate ? (
        <>
          <textarea
            value={memo}
            onChange={(e) => setMemo(e.target.value)}
            placeholder="이 세션에 대한 운영 메모 (사용자에게 노출되지 않습니다)"
            rows={3}
            className="w-full resize-y rounded-lg border border-slate-200 bg-card p-2 text-sm focus:border-blue-300 focus:outline-none"
          />
          <div className="flex items-center gap-2">
            <Button size="sm" onClick={() => void save()} disabled={saving}>
              {saving ? "저장 중..." : "메모 저장"}
            </Button>
            {note && <span className="text-xs text-slate-500">{note}</span>}
          </div>
        </>
      ) : (
        <p className="whitespace-pre-wrap text-sm text-slate-600">{initial || "등록된 운영 메모가 없습니다."}</p>
      )}
    </section>
  );
}
