import { useEffect, useMemo, useState } from "react";
import { MessageSquare, RefreshCw, Search } from "lucide-react";
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
import { getAdminInterviewSessionDetail, getAdminInterviewSessions } from "../api";
import type { AdminInterviewSessionDetail, AdminInterviewSessionRow } from "../types";

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
    <div className="min-h-screen bg-slate-50">
      <div className="mx-auto grid max-w-7xl gap-5 px-4 py-8 sm:px-6 lg:grid-cols-[360px_minmax(0,1fr)]">
        {/* 좌: 목록 */}
        <section className="space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <Badge className="mb-2 bg-slate-900 text-white">D 관리자</Badge>
              <h1 className="flex items-center gap-2 text-2xl font-bold text-slate-950">
                <MessageSquare className="size-6 text-blue-600" />
                면접 세션 관리
              </h1>
            </div>
            <Button variant="outline" onClick={() => void loadRows()} disabled={loading}>
              <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
            </Button>
          </div>

          <Card className="border-slate-200 bg-white">
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
              <div className="rounded-lg border border-dashed border-slate-200 bg-white p-6 text-center text-sm text-slate-400">
                면접 세션이 없습니다.
              </div>
            ) : (
              rows.map((row) => (
                <button
                  key={row.id}
                  type="button"
                  className={`w-full rounded-lg border bg-white p-3 text-left transition-colors ${
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
          {!detail ? (
            <Card className="border-slate-200 bg-white">
              <CardContent className="p-8 text-center text-sm text-slate-500">면접 세션을 선택하세요.</CardContent>
            </Card>
          ) : (
            <>
              <Card className="border-slate-200 bg-white">
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

              {report && (
                <Card className="border-slate-200 bg-white">
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

              <section className="space-y-2">
                <h2 className="text-sm font-bold text-slate-900">질문 / 답변</h2>
                {detail.questions.length === 0 ? (
                  <Card className="border-slate-200 bg-white">
                    <CardContent className="p-6 text-center text-sm text-slate-400">생성된 질문이 없습니다.</CardContent>
                  </Card>
                ) : (
                  detail.questions.map((q, i) => {
                    const answer = answerByQuestion.get(q.id);
                    return (
                      <Card key={q.id} className="border-slate-200 bg-white">
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
    </div>
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
