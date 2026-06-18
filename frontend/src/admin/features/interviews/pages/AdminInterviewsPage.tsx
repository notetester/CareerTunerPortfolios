import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router";
import { BookMarked, FileText, MessageSquare, Mic, RefreshCw, Search, Video } from "lucide-react";
import AdminShell from "../../../components/AdminShell";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Input } from "@/app/components/ui/input";
import { Progress } from "@/app/components/ui/progress";
import {
  INTERVIEW_MODES,
  getInterviewModeLabel,
  getScoreColor,
  type InterviewReport,
} from "@/features/interview/types/interview";
import {
  getAdminInterviewAiFailures,
  getAdminInterviewSessionDetail,
  getAdminInterviewSessions,
  updateAdminMemo,
} from "../api";
import { TrainingPipelineCard } from "../components/TrainingPipelineCard";
import type { AdminInterviewAiFailureRow, AdminInterviewSessionDetail, AdminInterviewSessionRow } from "../types";

function formatDateTime(value: string | null): string {
  if (!value) return "-";
  return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

function parseReport(raw: string | null): InterviewReport | null {
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as Partial<InterviewReport>;
    // 이 화면이 기대하는 구조(categories/summaryFeedback 배열)가 아니면 무시한다.
    // (구버전/시드 리포트는 {summary, strengths, weaknesses} 등 다른 형식일 수 있음)
    if (!Array.isArray(parsed?.categories) || !Array.isArray(parsed?.summaryFeedback)) {
      return null;
    }
    return parsed as InterviewReport;
  } catch {
    return null;
  }
}

export function AdminInterviewsPage() {
  const [rows, setRows] = useState<AdminInterviewSessionRow[]>([]);
  const [detail, setDetail] = useState<AdminInterviewSessionDetail | null>(null);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [keyword, setKeyword] = useState("");
  const [mode, setMode] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const selected = useMemo(() => rows.find((r) => r.id === selectedId) ?? rows[0] ?? null, [rows, selectedId]);
  const report = useMemo(() => parseReport(detail?.report ?? null), [detail]);
  const answerByQuestion = useMemo(() => {
    const map = new Map<number, AdminInterviewSessionDetail["answers"][number]>();
    detail?.answers.forEach((a) => map.set(a.questionId, a));
    return map;
  }, [detail]);

  const loadRows = async () => {
    setLoading(true);
    setError(null);
    try {
      const next = await getAdminInterviewSessions({ keyword, mode });
      setRows(next);
      if (!selectedId && next[0]) setSelectedId(next[0].id);
    } catch (err) {
      setError(err instanceof Error ? err.message : "면접 세션 목록을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  };

  const loadDetail = async (id: number) => {
    setError(null);
    try {
      setDetail(await getAdminInterviewSessionDetail(id));
    } catch (err) {
      setError(err instanceof Error ? err.message : "면접 세션 상세를 불러오지 못했습니다.");
    }
  };

  useEffect(() => {
    void loadRows();
  }, []);

  useEffect(() => {
    if (selected?.id) void loadDetail(selected.id);
  }, [selected?.id]);

  return (
    <AdminShell
      active="interviews"
      breadcrumb="면접 모니터링"
      title="면접 세션 관리"
      icon={MessageSquare}
      desc="사용자 면접 세션과 답변·리포트, 학습 파이프라인, AI 실패를 모니터링합니다."
      actions={
        <div className="flex gap-2">
          <Button asChild variant="outline" size="sm">
            <Link to="/admin/prompts/interview"><FileText className="size-4" /> 프롬프트</Link>
          </Button>
          <Button asChild variant="outline" size="sm">
            <Link to="/admin/interview/knowledge"><BookMarked className="size-4" /> RAG 지식</Link>
          </Button>
          <Button variant="outline" onClick={() => void loadRows()} disabled={loading}>
            <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
          </Button>
        </div>
      }
    >
      <div className="grid gap-5 lg:grid-cols-[360px_minmax(0,1fr)]">
        {/* 좌: 목록 */}
        <section className="space-y-4">

          <Card className="border-slate-200 bg-card">
            <CardContent className="space-y-3 p-4">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
                <Input
                  value={keyword}
                  onChange={(e) => setKeyword(e.target.value)}
                  placeholder="기업, 직무, 이메일 검색"
                  className="pl-9"
                />
              </div>
              <div className="flex flex-wrap gap-2">
                <Button size="sm" variant={mode === "" ? "default" : "outline"} onClick={() => setMode("")}>
                  전체
                </Button>
                {INTERVIEW_MODES.map((m) => (
                  <Button
                    key={m.id}
                    size="sm"
                    variant={mode === m.id ? "default" : "outline"}
                    onClick={() => setMode(m.id)}
                  >
                    {m.title}
                  </Button>
                ))}
              </div>
              <Button className="w-full bg-blue-600 text-white hover:bg-blue-700" onClick={() => void loadRows()}>
                필터 적용
              </Button>
            </CardContent>
          </Card>

          {error && (
            <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>
          )}

          <div className="space-y-2">
            {rows.length === 0 && !loading ? (
              <div className="rounded-lg border border-dashed border-slate-200 bg-card p-6 text-center text-sm text-slate-400">
                면접 세션이 없습니다.
              </div>
            ) : (
              rows.map((row) => (
                <button
                  key={row.id}
                  type="button"
                  className={`w-full rounded-lg border bg-card p-3 text-left transition-colors ${
                    selected?.id === row.id ? "border-blue-300 ring-2 ring-blue-100" : "border-slate-200 hover:border-blue-200"
                  }`}
                  onClick={() => setSelectedId(row.id)}
                >
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      <div className="truncate text-sm font-bold text-slate-950">
                        {row.companyName} · {row.jobTitle}
                      </div>
                      <div className="truncate text-xs text-slate-500">{row.userEmail}</div>
                    </div>
                    <Badge variant="outline">{getInterviewModeLabel(row.mode)}</Badge>
                  </div>
                  <div className="mt-2 flex flex-wrap items-center gap-2 text-[11px] text-slate-500">
                    <span className={row.totalScore !== null ? getScoreColor(row.totalScore) : ""}>
                      총점 {row.totalScore ?? "-"}
                    </span>
                    <span>· 답변 {row.answeredCount}/{row.questionCount}</span>
                    <span>· {formatDateTime(row.createdAt)}</span>
                  </div>
                </button>
              ))
            )}
          </div>
        </section>

        {/* 우: 상세 */}
        <section className="min-w-0 space-y-4">
          <TrainingPipelineCard />
          <AiFailuresCard />
          {!detail ? (
            <Card className="border-slate-200 bg-card">
              <CardContent className="p-8 text-center text-sm text-slate-500">면접 세션을 선택하세요.</CardContent>
            </Card>
          ) : (
            <>
              <Card className="border-slate-200 bg-card">
                <CardHeader>
                  <CardTitle className="text-lg font-bold text-slate-950">
                    {detail.session.companyName} · {detail.session.jobTitle}
                  </CardTitle>
                </CardHeader>
                <CardContent className="grid gap-3 md:grid-cols-4">
                  <Info label="사용자" value={detail.session.userEmail} />
                  <Info label="모드" value={getInterviewModeLabel(detail.session.mode)} />
                  <Info label="총점" value={detail.session.totalScore !== null ? `${detail.session.totalScore}점` : "-"} />
                  <Info label="시작" value={formatDateTime(detail.session.startedAt)} />
                </CardContent>
              </Card>

              <MemoCard
                sessionId={detail.session.id}
                initial={detail.session.adminMemo}
                onSaved={() => void loadDetail(detail.session.id)}
              />

              {report && (
                <Card className="border-slate-200 bg-card">
                  <CardHeader>
                    <CardTitle className="text-base font-bold text-slate-900">면접 리포트</CardTitle>
                  </CardHeader>
                  <CardContent className="space-y-3">
                    <div className="text-sm">
                      총점 <span className={getScoreColor(report.totalScore)}>{report.totalScore}점</span>
                      {report.previousScore != null && (
                        <span className="ml-1 text-xs text-slate-400">(이전 {report.previousScore}점)</span>
                      )}
                    </div>
                    {report.categories.map((c) => (
                      <div key={c.label} className="space-y-1">
                        <div className="flex items-center justify-between text-xs">
                          <span className="font-semibold text-slate-700">{c.label}</span>
                          <span className={`font-black ${getScoreColor(c.score)}`}>{c.score}점</span>
                        </div>
                        <Progress value={c.score} className="h-2" />
                      </div>
                    ))}
                    {report.summaryFeedback.length > 0 && (
                      <ul className="list-disc space-y-1 pl-5 text-sm text-slate-600">
                        {report.summaryFeedback.map((line, i) => (
                          <li key={i}>{line}</li>
                        ))}
                      </ul>
                    )}
                  </CardContent>
                </Card>
              )}

              {detail.mediaResults.length > 0 && (
                <Card className="border-slate-200 bg-card">
                  <CardHeader>
                    <CardTitle className="text-base font-bold text-slate-900">음성/영상 면접 분석</CardTitle>
                  </CardHeader>
                  <CardContent className="space-y-3">
                    {detail.mediaResults.map((m) => (
                      <div key={m.id} className="rounded-lg border border-slate-200 p-3">
                        <div className="mb-2 flex items-center justify-between">
                          <Badge variant="outline">
                            {m.kind === "AVATAR" ? "아바타 화상 면접" : "음성 모의면접"}
                          </Badge>
                          <span className={`text-sm font-black ${getScoreColor(m.score)}`}>{m.score}점</span>
                        </div>
                        {m.scoreDetail && (
                          <div className="flex flex-wrap gap-x-3 gap-y-1 text-xs text-slate-500">
                            {Object.entries(m.scoreDetail).map(([k, v]) => (
                              <span key={k}>
                                {k} <span className="font-semibold text-slate-700">{v}</span>
                              </span>
                            ))}
                          </div>
                        )}
                        {Array.isArray(m.transcript) && m.transcript.length > 0 && (
                          <p className="mt-2 line-clamp-2 text-xs text-slate-400">
                            {m.transcript.map((t) => t.text).join(" ")}
                          </p>
                        )}
                      </div>
                    ))}
                  </CardContent>
                </Card>
              )}

              <section className="space-y-2">
                <h2 className="text-sm font-bold text-slate-900">질문 / 답변</h2>
                {detail.questions.length === 0 ? (
                  <Card className="border-slate-200 bg-card">
                    <CardContent className="p-6 text-center text-sm text-slate-400">생성된 질문이 없습니다.</CardContent>
                  </Card>
                ) : (
                  detail.questions.map((q, i) => {
                    const answer = answerByQuestion.get(q.id);
                    return (
                      <Card key={q.id} className="border-slate-200 bg-card">
                        <CardContent className="space-y-2 p-4 text-sm">
                          <div className="flex items-start gap-2">
                            <Badge className="bg-blue-100 text-blue-700">Q{i + 1}</Badge>
                            <span className="font-medium text-slate-900">{q.question}</span>
                          </div>
                          {answer ? (
                            <div className="space-y-2 rounded-lg bg-slate-50 p-3">
                              <p className="whitespace-pre-line text-slate-700">{answer.answerText ?? "답변 없음"}</p>
                              {answer.score !== null && (
                                <div className="text-xs font-bold">
                                  점수 <span className={getScoreColor(answer.score)}>{answer.score}점</span>
                                </div>
                              )}
                              {answer.feedback && <p className="text-xs text-slate-500">{answer.feedback}</p>}
                              {(answer.audioUrl || answer.videoUrl) && (
                                <div className="flex flex-wrap gap-2 pt-1">
                                  {answer.audioUrl && (
                                    <a
                                      href={answer.audioUrl}
                                      target="_blank"
                                      rel="noreferrer"
                                      className="inline-flex items-center gap-1 rounded border border-slate-200 bg-white px-2 py-1 text-xs text-slate-600 hover:bg-slate-100"
                                    >
                                      <Mic className="size-3" /> 음성 답변
                                    </a>
                                  )}
                                  {answer.videoUrl && (
                                    <a
                                      href={answer.videoUrl}
                                      target="_blank"
                                      rel="noreferrer"
                                      className="inline-flex items-center gap-1 rounded border border-slate-200 bg-white px-2 py-1 text-xs text-slate-600 hover:bg-slate-100"
                                    >
                                      <Video className="size-3" /> 영상 답변
                                    </a>
                                  )}
                                </div>
                              )}
                            </div>
                          ) : (
                            <p className="text-xs text-slate-400">미답변</p>
                          )}
                        </CardContent>
                      </Card>
                    );
                  })
                )}
              </section>
            </>
          )}
        </section>
      </div>
    </AdminShell>
  );
}

function Info({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
      <div className="text-xs font-semibold text-slate-500">{label}</div>
      <div className="mt-1 truncate text-sm font-bold text-slate-900">{value}</div>
    </div>
  );
}

/** 면접 AI 기능 실패 모니터링 — GET /api/admin/interview/ai-failures. */
function AiFailuresCard() {
  const [rows, setRows] = useState<AdminInterviewAiFailureRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      setRows(await getAdminInterviewAiFailures(50));
    } catch (e) {
      setError(e instanceof Error ? e.message : "AI 실패 이력을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  return (
    <Card className="border-slate-200 bg-card">
      <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
        <CardTitle className="text-base">면접 AI 실패 이력</CardTitle>
        <Button variant="outline" size="sm" onClick={() => void load()} disabled={loading}>
          <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
        </Button>
      </CardHeader>
      <CardContent className="space-y-2">
        {error && <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div>}
        {!error && rows.length === 0 && !loading && (
          <div className="rounded-lg bg-slate-50 p-4 text-center text-sm text-slate-400">최근 AI 실패가 없습니다.</div>
        )}
        {rows.map((r) => (
          <div key={r.id} className="rounded-lg border border-red-100 bg-red-50/40 p-3 text-sm">
            <div className="flex items-center justify-between gap-2">
              <span className="font-semibold text-red-800">{r.featureType}</span>
              <span className="shrink-0 text-xs text-slate-400">{formatDateTime(r.createdAt)}</span>
            </div>
            <div className="mt-0.5 truncate text-xs text-slate-500">
              {r.userEmail}{r.companyName ? ` · ${r.companyName}` : ""}{r.jobTitle ? ` · ${r.jobTitle}` : ""}
            </div>
            {r.errorMessage && <div className="mt-1 line-clamp-2 text-xs text-red-600">{r.errorMessage}</div>}
          </div>
        ))}
      </CardContent>
    </Card>
  );
}

/** 관리자 운영 메모 — PUT /api/admin/interview/sessions/{id}/memo. 사용자에게 노출되지 않는다. */
function MemoCard({
  sessionId,
  initial,
  onSaved,
}: {
  sessionId: number;
  initial: string | null;
  onSaved: () => void;
}) {
  const [memo, setMemo] = useState(initial ?? "");
  const [saving, setSaving] = useState(false);
  const [note, setNote] = useState<string | null>(null);

  useEffect(() => {
    setMemo(initial ?? "");
    setNote(null);
  }, [initial, sessionId]);

  const save = async () => {
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
    <Card className="border-slate-200 bg-card">
      <CardHeader className="pb-2">
        <CardTitle className="text-base">운영 메모</CardTitle>
      </CardHeader>
      <CardContent className="space-y-2">
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
      </CardContent>
    </Card>
  );
}
