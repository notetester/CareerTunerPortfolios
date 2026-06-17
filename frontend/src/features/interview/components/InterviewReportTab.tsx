import { useCallback, useEffect, useState } from "react";
import { AlertCircle, Brain, ChevronRight, Mic, Video } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Progress } from "@/app/components/ui/progress";
import { getInterviewReport, listMediaResults } from "../api/interviewApi";
import {
  getInterviewModeLabel,
  getScoreColor,
  type InterviewReport,
  type InterviewSession,
  type MediaAnalysis,
} from "../types/interview";

export function InterviewReportTab({ session }: { session: InterviewSession | null }) {
  const [report, setReport] = useState<InterviewReport | null>(null);
  const [media, setMedia] = useState<MediaAnalysis[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!session) return;
    setLoading(true);
    setError(null);
    try {
      // 텍스트 리포트와 음성/영상 분석은 독립 — 한쪽이 없어도 다른 쪽은 보여준다.
      const [rep, med] = await Promise.all([
        getInterviewReport(session.id).catch(() => null),
        listMediaResults(session.id).catch(() => [] as MediaAnalysis[]),
      ]);
      setReport(rep);
      setMedia(med);
    } catch (err) {
      setError(err instanceof Error ? err.message : "리포트를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, [session]);

  useEffect(() => {
    void load();
  }, [load]);

  if (!session) {
    return (
      <div className="rounded-xl border border-dashed border-slate-200 bg-white p-10 text-center text-sm text-slate-400">
        면접을 진행하고 종료하면 종합 리포트가 여기에 표시됩니다.
      </div>
    );
  }

  if (loading) return <p className="text-sm text-slate-400">리포트를 불러오는 중…</p>;
  if (error)
    return (
      <p className="flex items-center gap-1.5 text-sm text-red-500">
        <AlertCircle className="size-4" /> {error}
      </p>
    );
  if (!report && media.length === 0)
    return (
      <div className="rounded-xl border border-dashed border-slate-200 bg-white p-10 text-center text-sm text-slate-400">
        아직 생성된 리포트가 없습니다. 면접을 끝까지 진행해 주세요.
      </div>
    );

  return (
    <div className="max-w-3xl space-y-6">
      <div className="flex items-center justify-between">
        <h2 className="font-bold text-slate-800">면접 리포트</h2>
        <Badge className="bg-purple-100 text-purple-700">{getInterviewModeLabel(session.mode)}</Badge>
      </div>

      {report && (
        <>
          <div className="grid gap-4 md:grid-cols-3" data-tut="tut-report-score">
            <div className="rounded-2xl border-2 border-blue-200 bg-white p-6 text-center">
              <div className="text-5xl font-black text-blue-600">{report.totalScore}</div>
              <div className="mt-1 text-sm text-slate-500">
                총점
                {report.previousScore !== null && (
                  <span className="ml-1 text-xs">
                    (이전 {report.totalScore - report.previousScore >= 0 ? "+" : ""}
                    {report.totalScore - report.previousScore}점)
                  </span>
                )}
              </div>
            </div>
            <div className="rounded-2xl border border-slate-200 bg-white p-6 text-center">
              <div className="text-3xl font-black text-slate-700">{report.questionCount}</div>
              <div className="mt-1 text-sm text-slate-500">진행 질문 수</div>
            </div>
            <div className="rounded-2xl border border-slate-200 bg-white p-6 text-center">
              <div className="text-3xl font-black text-green-600">{report.durationLabel ?? "-"}</div>
              <div className="mt-1 text-sm text-slate-500">면접 진행 시간</div>
            </div>
          </div>

          <div className="space-y-3" data-tut="tut-report-categories">
            {report.categories.map((e) => (
              <div key={e.label} className="space-y-2 rounded-xl border border-slate-200 bg-white p-4">
                <div className="flex items-center justify-between">
                  <span className="text-sm font-semibold text-slate-700">{e.label}</span>
                  <span className={`text-sm font-black ${getScoreColor(e.score)}`}>{e.score}점</span>
                </div>
                <Progress value={e.score} className="h-2" />
              </div>
            ))}
          </div>

          {report.summaryFeedback.length > 0 && (
            <div className="rounded-xl border border-blue-200 bg-blue-50 p-5">
              <div className="mb-3 flex items-center gap-2 text-sm font-bold text-blue-800">
                <Brain className="size-4" /> AI 종합 피드백
              </div>
              <ul className="space-y-2 text-sm text-blue-700">
                {report.summaryFeedback.map((line, i) => (
                  <li key={i} className="flex items-start gap-2">
                    <ChevronRight className="mt-0.5 size-4 shrink-0" /> {line}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </>
      )}

      {media.length > 0 && (
        <div className="space-y-3">
          <h3 className="font-bold text-slate-800">음성·영상 면접 분석</h3>
          {media.map((m) => (
            <div key={m.id} className="space-y-2 rounded-xl border border-slate-200 bg-white p-4">
              <div className="flex items-center justify-between">
                <span className="flex items-center gap-1.5 text-sm font-semibold text-slate-700">
                  {m.kind === "VOICE" ? (
                    <Mic className="size-4 text-emerald-600" />
                  ) : (
                    <Video className="size-4 text-purple-600" />
                  )}
                  {m.kind === "VOICE" ? "음성 면접" : "아바타 화상 면접"}
                </span>
                <span className={`text-sm font-black ${getScoreColor(m.score)}`}>{m.score}점</span>
              </div>
              {m.transcript && m.transcript.length > 0 && (
                <details className="text-xs">
                  <summary className="cursor-pointer text-slate-500">답변 트랜스크립트 보기</summary>
                  <div className="mt-2 space-y-1">
                    {m.transcript.map((line, i) => (
                      <p key={i} className="text-slate-500">
                        <b className="text-slate-600">{line.role === "ai" ? "Q" : "A"}.</b> {line.text}
                      </p>
                    ))}
                  </div>
                </details>
              )}
              <p className="text-[11px] text-slate-400">{new Date(m.createdAt).toLocaleString("ko-KR")}</p>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
