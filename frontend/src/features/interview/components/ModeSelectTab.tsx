import { useState } from "react";
import { AlertCircle, BarChart3, ChevronDown, FileText, Loader2, Play, X } from "lucide-react";
import { Bar, BarChart, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import type { ApplicationCase } from "@/features/applications/types/applicationCase";
import { createInterviewSession, deleteInterviewSession, getSessionReview } from "../api/interviewApi";
import { useInterviewSessions } from "../hooks/useInterviewSessions";
import {
  INTERVIEW_MODES,
  getInterviewModeLabel,
  getScoreColor,
  toSentenceLines,
  type InterviewMode,
  type InterviewSession,
  type SessionReview,
} from "../types/interview";

interface ModeSelectTabProps {
  cases: ApplicationCase[];
  casesLoading: boolean;
  casesError: string | null;
  selectedCaseId: number | null;
  selectedMode: InterviewMode | null;
  onSelectCase(id: number): void;
  onSelectMode(mode: InterviewMode): void;
  onSessionStarted(session: InterviewSession): void;
  /** 최근 기록에서 "이어서 복원하기"를 누르면 그 세션으로 면접 흐름을 복원한다. */
  onResume(session: InterviewSession): void;
  /** 현재 진행 중(복원/새) 세션 라벨. 있으면 새 면접 시작 시 전환 경고를 띄운다. */
  activeSessionLabel?: string | null;
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

export function ModeSelectTab({
  cases,
  casesLoading,
  casesError,
  selectedCaseId,
  selectedMode,
  onSelectCase,
  onSelectMode,
  onSessionStarted,
  onResume,
  activeSessionLabel,
}: ModeSelectTabProps) {
  const sessions = useInterviewSessions();
  const [starting, setStarting] = useState(false);
  const [startError, setStartError] = useState<string | null>(null);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [reviewSession, setReviewSession] = useState<InterviewSession | null>(null);
  const [review, setReview] = useState<SessionReview | null>(null);
  const [reviewOpen, setReviewOpen] = useState(false);
  const [reviewLoading, setReviewLoading] = useState(false);
  const [reviewError, setReviewError] = useState<string | null>(null);

  const canStart = selectedCaseId !== null && selectedMode !== null && !starting;

  const handleStart = async () => {
    if (selectedCaseId === null || selectedMode === null) return;
    if (
      activeSessionLabel &&
      !window.confirm(
        `지금 '${activeSessionLabel}' 세션을 보고 있어요.\n새 면접을 시작하면 이 세션에서 나가 새로 시작합니다. 계속할까요?`,
      )
    ) {
      return;
    }
    setStarting(true);
    setStartError(null);
    try {
      const session = await createInterviewSession({
        applicationCaseId: selectedCaseId,
        mode: selectedMode,
      });
      onSessionStarted(session);
    } catch (err) {
      setStartError(err instanceof Error ? err.message : "면접을 시작하지 못했습니다.");
    } finally {
      setStarting(false);
    }
  };

  const handleDelete = async (id: number) => {
    if (!window.confirm("이 면접 기록을 삭제할까요? (복습 기록도 사라집니다)")) return;
    setDeletingId(id);
    try {
      await deleteInterviewSession(id);
      sessions.removeSession(id);
    } catch (err) {
      window.alert(err instanceof Error ? err.message : "삭제하지 못했습니다.");
    } finally {
      setDeletingId(null);
    }
  };

  const openReview = async (session: InterviewSession) => {
    setReviewSession(session);
    setReviewOpen(true);
    setReview(null);
    setReviewError(null);
    setReviewLoading(true);
    try {
      setReview(await getSessionReview(session.id));
    } catch (err) {
      setReviewError(err instanceof Error ? err.message : "면접 기록을 불러오지 못했습니다.");
    } finally {
      setReviewLoading(false);
    }
  };

  const handleResume = () => {
    if (!reviewSession) return;
    setReviewOpen(false);
    onResume(reviewSession);
  };

  const caseLabel = (caseId: number) => {
    const c = cases.find((item) => item.id === caseId);
    return c ? `${c.companyName} · ${c.jobTitle}` : `지원 건 #${caseId}`;
  };

  // 복기 모달 상단 요약 지표 (review 로드 후 계산)
  const answeredCount = review ? review.items.filter((it) => it.answerText).length : 0;
  const scored = review ? review.items.filter((it) => it.score !== null) : [];
  const avgScore = scored.length
    ? Math.round(scored.reduce((sum, it) => sum + (it.score ?? 0), 0) / scored.length)
    : null;
  const scoreValues = scored.map((it) => it.score as number);
  const maxScore = scoreValues.length ? Math.max(...scoreValues) : null;
  const minScore = scoreValues.length ? Math.min(...scoreValues) : null;
  // 질문별 점수 막대그래프 데이터 (미답변은 0점으로 표시)
  const chartData = review ? review.items.map((it, i) => ({ name: `Q${i + 1}`, score: it.score ?? 0 })) : [];

  return (
    <div className="space-y-6">
      {/* 지원 건 선택 */}
      <div className="rounded-2xl border border-slate-200 bg-card p-5">
        <div className="mb-3 text-sm font-bold text-slate-700">지원 건 선택</div>
        {casesLoading ? (
          <p className="text-sm text-slate-400">지원 건을 불러오는 중…</p>
        ) : casesError ? (
          <p className="text-sm text-red-500">{casesError}</p>
        ) : cases.length === 0 ? (
          <p className="text-sm text-slate-400">먼저 지원 건을 등록하면 해당 공고 기반으로 면접을 볼 수 있습니다.</p>
        ) : (
          <div className="flex flex-wrap gap-2">
            {cases.map((c) => (
              <button
                key={c.id}
                onClick={() => onSelectCase(c.id)}
                className={`rounded-full border px-3 py-1.5 text-xs font-semibold transition-colors ${
                  selectedCaseId === c.id
                    ? "border-blue-600 bg-blue-600 text-white"
                    : "border-slate-300 text-slate-600 hover:border-blue-400"
                }`}
              >
                {c.companyName} · {c.jobTitle}
              </button>
            ))}
          </div>
        )}
      </div>

      {/* 모드 그리드 */}
      <div data-tut="tut-modes-grid" className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {INTERVIEW_MODES.map((mode) => (
          <button
            key={mode.id}
            onClick={() => onSelectMode(mode.id)}
            className={`group relative rounded-2xl border-2 p-5 text-left transition-all hover:shadow-lg ${
              selectedMode === mode.id
                ? "border-blue-500 bg-blue-50 shadow-lg"
                : "border-slate-200 bg-card hover:border-blue-300"
            }`}
          >
            {mode.recommended && (
              <div className="absolute -right-2 -top-2">
                <Badge className="bg-primary px-2 py-0.5 text-[9px] text-primary-foreground">
                  추천
                </Badge>
              </div>
            )}
            <div className="mb-3 text-muted-foreground transition-transform group-hover:scale-110"><mode.icon className="size-7" /></div>
            <div className="mb-1 text-sm font-bold text-slate-800">{mode.title}</div>
            <div className="mb-2 text-xs leading-relaxed text-slate-500">{mode.desc}</div>
            <Badge
              className={`px-2 py-0.5 text-[10px] ${
                mode.difficulty === "상"
                  ? "bg-red-100 text-red-700"
                  : mode.difficulty === "중"
                    ? "bg-amber-100 text-amber-700"
                    : "bg-green-100 text-green-700"
              }`}
            >
              난이도 {mode.difficulty}
            </Badge>
          </button>
        ))}
      </div>

      {/* 시작 버튼 */}
      <div className="space-y-2">
        {activeSessionLabel && (
          <p className="flex items-center gap-1.5 rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-700">
            <AlertCircle className="size-3.5 shrink-0" />
            지금 '{activeSessionLabel}' 진행 중이에요. 새로 시작하면 이 세션에서 나가 새 면접이 시작됩니다.
          </p>
        )}
        <Button
          size="lg"
          className="h-14 w-full gap-2 bg-primary text-primary-foreground"
          disabled={!canStart}
          onClick={handleStart}
        >
          <FileText className="size-5" />
          {starting ? "시작하는 중…" : "면접 시작하기"}
        </Button>
        {(selectedCaseId === null || selectedMode === null) && !starting && (
          <p className="flex items-center gap-1.5 text-sm text-amber-600">
            <AlertCircle className="size-4" />
            {selectedCaseId === null && selectedMode === null
              ? "지원 건과 면접 모드를 선택하면 시작할 수 있습니다."
              : selectedCaseId === null
                ? "지원 건을 선택하세요."
                : "면접 모드를 선택하세요."}
          </p>
        )}
      </div>
      {startError && (
        <p className="flex items-center gap-1.5 text-sm text-red-500">
          <AlertCircle className="size-4" /> {startError}
        </p>
      )}

      {/* 최근 면접 기록 */}
      <div>
        <h3 className="mb-3 font-bold text-slate-800">최근 면접 기록</h3>
        {sessions.loading ? (
          <p className="text-sm text-slate-400">불러오는 중…</p>
        ) : sessions.error ? (
          <p className="text-sm text-red-500">{sessions.error}</p>
        ) : sessions.sessions.length === 0 ? (
          <p className="rounded-xl border border-dashed border-slate-200 bg-card p-6 text-center text-sm text-slate-400">
            아직 면접 기록이 없습니다. 위에서 모드를 골라 첫 면접을 시작해 보세요.
          </p>
        ) : (
          <>
            <div className="space-y-2">
              {sessions.sessions.map((s) => (
                <div key={s.id} className="group relative">
                  <button
                    type="button"
                    onClick={() => openReview(s)}
                    className="flex w-full items-center gap-4 rounded-xl border border-slate-200 bg-card p-4 pr-12 text-left transition-colors hover:border-blue-300"
                  >
                    <div className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-accent-soft text-xs font-bold text-primary">
                      {caseLabel(s.applicationCaseId).slice(0, 1)}
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="text-sm font-semibold text-slate-800">
                        {caseLabel(s.applicationCaseId)} · {getInterviewModeLabel(s.mode)}
                      </div>
                      <div className="mt-0.5 flex flex-wrap items-center gap-1 text-xs text-slate-500">
                        <span>{formatDate(s.createdAt)}</span>
                        {s.lastResumedAt && (
                          <span className="text-indigo-500">· 복습 {formatDate(s.lastResumedAt)}</span>
                        )}
                        {s.avgVoiceScore != null && (
                          <span className="rounded bg-emerald-50 px-1.5 py-0.5 font-semibold text-emerald-600">
                            음성 {s.avgVoiceScore}
                          </span>
                        )}
                      </div>
                    </div>
                    <div className="text-center">
                      {/* 리포트 총점 우선, 없으면 답변 평균 점수로 폴백 (복습만 해도 점수가 뜨도록) */}
                      <div className={`text-lg font-black ${(s.totalScore ?? s.avgScore) != null ? getScoreColor((s.totalScore ?? s.avgScore) as number) : "text-slate-300"}`}>
                        {(s.totalScore ?? s.avgScore) ?? "-"}
                      </div>
                      <div className="text-[10px] text-slate-400">점수</div>
                    </div>
                    <BarChart3 className="size-4 text-slate-400" />
                  </button>
                  <button
                    type="button"
                    onClick={() => handleDelete(s.id)}
                    disabled={deletingId === s.id}
                    title="기록 삭제"
                    className="absolute right-2 top-1/2 -translate-y-1/2 rounded-lg p-1.5 text-slate-300 opacity-0 transition hover:bg-red-50 hover:text-red-500 group-hover:opacity-100"
                  >
                    {deletingId === s.id ? <Loader2 className="size-4 animate-spin" /> : <X className="size-4" />}
                  </button>
                </div>
              ))}
            </div>
            {sessions.hasNext && (
              <div className="mt-3 flex justify-center">
                <Button
                  variant="outline"
                  className="gap-1.5"
                  disabled={sessions.loadingMore}
                  onClick={sessions.loadMore}
                >
                  {sessions.loadingMore ? (
                    <Loader2 className="size-4 animate-spin" />
                  ) : (
                    <ChevronDown className="size-4" />
                  )}
                  더보기 ({sessions.remaining})
                </Button>
              </div>
            )}
          </>
        )}
      </div>

      {reviewOpen && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
          onClick={() => setReviewOpen(false)}
        >
          <div
            className="max-h-[85vh] w-full max-w-2xl overflow-y-auto rounded-2xl bg-card p-6 shadow-xl"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="mb-4 flex items-center justify-between">
              <h3 className="text-lg font-bold text-slate-900">면접 기록 복기</h3>
              <button
                type="button"
                onClick={() => setReviewOpen(false)}
                className="rounded-lg px-2 py-1 text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-600"
              >
                ✕
              </button>
            </div>

            {/* 요약 + 복원 */}
            {reviewSession && (
              <div className="mb-4 rounded-xl border border-slate-200 bg-slate-50 p-4">
                <div className="text-sm font-bold text-slate-800">{caseLabel(reviewSession.applicationCaseId)}</div>
                <div className="mt-0.5 text-xs text-slate-500">
                  {getInterviewModeLabel(reviewSession.mode)} · {formatDate(reviewSession.createdAt)}
                </div>
                <div className="mt-3 grid grid-cols-3 gap-2 text-center">
                  <div className="rounded-lg bg-card p-2">
                    <div className="text-base font-black text-slate-800">{review ? review.items.length : "—"}</div>
                    <div className="text-[10px] text-slate-400">질문</div>
                  </div>
                  <div className="rounded-lg bg-card p-2">
                    <div className="text-base font-black text-slate-800">{review ? answeredCount : "—"}</div>
                    <div className="text-[10px] text-slate-400">답변</div>
                  </div>
                  <div className="rounded-lg bg-card p-2">
                    <div className={`text-base font-black ${avgScore !== null ? getScoreColor(avgScore) : "text-slate-300"}`}>
                      {avgScore ?? "—"}
                    </div>
                    <div className="text-[10px] text-slate-400">평균점수</div>
                  </div>
                </div>
                <Button
                  className="mt-3 w-full gap-1.5 bg-primary text-primary-foreground"
                  onClick={handleResume}
                >
                  <Play className="size-4" /> 이 면접 이어서 복원하기
                </Button>
                <p className="mt-1.5 text-center text-[11px] text-slate-400">
                  지원 건·모드와 생성된 질문이 그대로 복원돼 예상 질문부터 이어집니다.
                </p>
              </div>
            )}

            {reviewLoading ? (
              <p className="py-10 text-center text-sm text-slate-400">불러오는 중…</p>
            ) : reviewError ? (
              <p className="py-10 text-center text-sm text-red-500">{reviewError}</p>
            ) : review && review.items.length > 0 ? (
              <div className="space-y-4">
                {scored.length > 0 && (
                  <div className="rounded-xl border border-slate-200 bg-card p-4">
                    <div className="mb-3 flex flex-wrap items-center justify-between gap-1">
                      <span className="text-sm font-bold text-slate-700">질문별 점수</span>
                      <span className="text-xs text-slate-500">
                        평균 <b className={getScoreColor(avgScore as number)}>{avgScore}</b> · 최고 {maxScore} · 최저 {minScore}
                      </span>
                    </div>
                    <ResponsiveContainer width="100%" height={180}>
                      <BarChart data={chartData} margin={{ top: 8, right: 8, bottom: 0, left: -18 }}>
                        <XAxis dataKey="name" tick={{ fontSize: 11 }} axisLine={false} tickLine={false} />
                        <YAxis domain={[0, 100]} tick={{ fontSize: 11 }} axisLine={false} tickLine={false} width={32} />
                        <Tooltip cursor={{ fill: "rgba(0,0,0,0.04)" }} formatter={(v) => [`${v}점`, "점수"]} />
                        <Bar dataKey="score" radius={[4, 4, 0, 0]}>
                          {chartData.map((d, i) => (
                            <Cell
                              key={i}
                              fill={d.score >= 75 ? "#16a34a" : d.score >= 60 ? "#d97706" : "#ef4444"}
                            />
                          ))}
                        </Bar>
                      </BarChart>
                    </ResponsiveContainer>
                  </div>
                )}
                {review.items.map((it, i) => (
                  <div key={it.questionId} className="rounded-xl border border-slate-200 p-4">
                    <div className="mb-2 text-sm font-bold text-slate-800">
                      Q{i + 1}. {it.question}
                    </div>
                    {it.answerText && (
                      <div className="mb-2 rounded-lg bg-slate-50 p-3">
                        <div className="mb-1 flex items-center gap-1.5 text-xs font-bold text-slate-500">
                          내 답변
                          {it.score !== null && (
                            <span className={getScoreColor(it.score)}>· {it.score}점</span>
                          )}
                        </div>
                        <p className="whitespace-pre-line text-sm text-slate-700">{it.answerText}</p>
                      </div>
                    )}
                    {it.modelAnswer ? (
                      <div className="rounded-lg border border-amber-100 bg-amber-50 p-3">
                        <div className="mb-1 text-xs font-bold text-amber-700">모범답안</div>
                        <p className="whitespace-pre-line text-sm leading-relaxed text-slate-700">
                          {toSentenceLines(it.modelAnswer)}
                        </p>
                      </div>
                    ) : (
                      <p className="text-xs text-slate-400">모범답안 준비 중…</p>
                    )}
                  </div>
                ))}
              </div>
            ) : (
              <p className="py-10 text-center text-sm text-slate-400">아직 생성된 질문이 없습니다.</p>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
