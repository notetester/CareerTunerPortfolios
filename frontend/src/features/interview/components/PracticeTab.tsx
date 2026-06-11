import { useCallback, useEffect, useState } from "react";
import {
  AlertCircle,
  ArrowRight,
  CheckCircle2,
  CornerDownRight,
  FileText,
  Loader2,
  Play,
  Sparkles,
  ThumbsUp,
} from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Progress } from "@/app/components/ui/progress";
import {
  generateExpectedQuestions,
  generateFollowUps,
  getAgentSteps,
  getInterviewProgress,
  submitAnswer,
} from "../api/interviewApi";
import {
  getScoreColor,
  type InterviewAgentStep,
  type InterviewAnswer,
  type InterviewProgress,
  type InterviewSession,
} from "../types/interview";
import { AgentTimeline } from "./AgentTimeline";

/**
 * 복습 테스트 진행. 세션의 질문을 순차 출제하고, 답변→AI 평가→다음 질문으로 이어간다.
 * 진행 판단은 백엔드 progress(답변 유무 기반)를 기준으로 한다. (AI 면접관 대화 진행)
 */
export function PracticeTab({
  session,
  onGoToReport,
}: {
  session: InterviewSession | null;
  onGoToReport: () => void;
}) {
  const [progress, setProgress] = useState<InterviewProgress | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [answer, setAnswer] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<InterviewAnswer | null>(null);
  const [generating, setGenerating] = useState(false);
  const [followingUp, setFollowingUp] = useState(false);
  const [steps, setSteps] = useState<InterviewAgentStep[]>([]);

  const loadProgress = useCallback(async () => {
    if (!session) return;
    setLoading(true);
    setError(null);
    try {
      setProgress(await getInterviewProgress(session.id));
    } catch (err) {
      setError(err instanceof Error ? err.message : "진행 상태를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, [session]);

  useEffect(() => {
    setResult(null);
    setAnswer("");
    void loadProgress();
  }, [loadProgress]);

  if (!session) {
    return (
      <div className="rounded-xl border border-dashed border-slate-200 bg-white p-10 text-center text-sm text-slate-400">
        "면접 모드 선택" 탭에서 지원 건과 모드를 고르고 면접을 시작하면 복습 테스트를 진행할 수 있습니다.
      </div>
    );
  }

  const handleGenerate = async () => {
    setGenerating(true);
    setError(null);
    try {
      await generateExpectedQuestions(session.id, { mode: session.mode });
      await loadProgress();
    } catch (err) {
      setError(err instanceof Error ? err.message : "질문 생성에 실패했습니다.");
    } finally {
      setGenerating(false);
    }
  };

  const handleSubmit = async () => {
    if (!answer.trim() || !progress?.currentQuestion) return;
    setSubmitting(true);
    setError(null);
    try {
      const evaluated = await submitAnswer(progress.currentQuestion.id, { answerText: answer });
      setResult(evaluated);
      // 멀티에이전트 진행(Evaluator→Critic) 트레이스 표시.
      try {
        const all = await getAgentSteps(session.id);
        setSteps(all.filter((s) => s.questionId === progress.currentQuestion?.id));
      } catch {
        setSteps([]);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "답변 평가에 실패했습니다.");
    } finally {
      setSubmitting(false);
    }
  };

  const handleFollowUp = async () => {
    if (!progress?.currentQuestion) return;
    setFollowingUp(true);
    setError(null);
    try {
      await generateFollowUps(progress.currentQuestion.id);
      // 꼬리 질문은 아직 답변 전이므로, 다음 질문으로 진행 시 자연스럽게 출제된다.
      await loadProgress();
      setResult(null);
      setAnswer("");
      setSteps([]);
    } catch (err) {
      setError(err instanceof Error ? err.message : "꼬리 질문 생성에 실패했습니다.");
    } finally {
      setFollowingUp(false);
    }
  };

  const handleNext = async () => {
    setResult(null);
    setAnswer("");
    setSteps([]);
    await loadProgress();
  };

  const total = progress?.totalQuestions ?? 0;
  const answered = progress?.answeredQuestions ?? 0;
  const percent = total > 0 ? Math.round((answered / total) * 100) : 0;

  // 질문이 아직 없음 → 생성 안내.
  if (!loading && total === 0) {
    return (
      <div className="space-y-4">
        <SessionHeader session={session} />
        <Card className="border border-slate-200 bg-white">
          <CardContent className="space-y-4 p-6 text-center">
            <p className="text-sm text-slate-500">
              아직 질문이 없습니다. AI 예상 질문을 생성하면 복습 테스트를 시작할 수 있습니다.
            </p>
            <Button onClick={handleGenerate} disabled={generating} className="gap-1.5">
              {generating ? <Loader2 className="size-4 animate-spin" /> : <Sparkles className="size-4" />}
              {generating ? "질문 생성 중…" : "질문 생성하고 시작"}
            </Button>
          </CardContent>
        </Card>
        {error && <ErrorLine message={error} />}
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <SessionHeader session={session} />

      <div className="space-y-1.5">
        <div className="flex items-center justify-between text-xs text-slate-500">
          <span>진행률 {answered}/{total}</span>
          <span>{percent}%</span>
        </div>
        <Progress value={percent} />
      </div>

      {error && <ErrorLine message={error} />}

      {loading && !progress ? (
        <div className="flex items-center justify-center gap-2 rounded-xl border border-slate-200 bg-white p-10 text-sm text-slate-400">
          <Loader2 className="size-4 animate-spin" /> 불러오는 중…
        </div>
      ) : progress?.finished ? (
        <Card className="border border-green-200 bg-green-50">
          <CardContent className="space-y-4 p-8 text-center">
            <CheckCircle2 className="mx-auto size-10 text-green-600" />
            <div>
              <h3 className="text-lg font-black text-slate-900">면접 완료</h3>
              <p className="mt-1 text-sm text-slate-600">
                모든 질문에 답했습니다. AI 종합 리포트로 결과를 확인하세요.
              </p>
            </div>
            <Button onClick={onGoToReport} className="gap-1.5 bg-gradient-to-r from-blue-600 to-indigo-600">
              <FileText className="size-4" /> 면접 리포트 보기
            </Button>
          </CardContent>
        </Card>
      ) : progress?.currentQuestion ? (
        <Card className="border border-slate-200 bg-white">
          <CardContent className="space-y-3 p-5">
            <div className="flex items-start gap-2">
              {progress.currentQuestion.questionType === "FOLLOW_UP" ? (
                <Badge className="gap-1 bg-indigo-100 text-indigo-700">
                  <CornerDownRight className="size-3" /> 꼬리질문
                </Badge>
              ) : (
                <Badge className="bg-blue-100 text-blue-700">Q{answered + 1}</Badge>
              )}
              <p className="flex-1 text-base font-semibold text-slate-900">
                {progress.currentQuestion.question}
              </p>
            </div>

            <textarea
              value={answer}
              onChange={(e) => setAnswer(e.target.value)}
              placeholder="답변을 입력하세요"
              rows={4}
              disabled={!!result}
              className="w-full resize-y rounded-lg border border-slate-200 p-3 text-sm outline-none focus:border-blue-400 disabled:bg-slate-50"
            />

            {!result && (
              <div className="flex justify-end">
                <Button disabled={!answer.trim() || submitting} onClick={handleSubmit} className="gap-1.5">
                  {submitting ? <Loader2 className="size-4 animate-spin" /> : <Play className="size-4" />}
                  {submitting ? "평가 중…" : "답변 제출"}
                </Button>
              </div>
            )}

            {result && (
              <div className="space-y-3 rounded-lg bg-slate-50 p-3">
                {result.score !== null && (
                  <div className="text-sm font-bold">
                    점수 <span className={getScoreColor(result.score)}>{result.score}점</span>
                  </div>
                )}
                {result.feedback && <p className="text-xs text-slate-600">{result.feedback}</p>}
                {result.improvedAnswer && (
                  <div className="rounded-lg border border-green-100 bg-green-50 p-3">
                    <div className="mb-1 flex items-center gap-1.5 text-xs font-bold text-green-700">
                      <ThumbsUp className="size-3.5" /> AI 개선 답변
                    </div>
                    <p className="text-sm leading-relaxed text-slate-700">{result.improvedAnswer}</p>
                  </div>
                )}

                <AgentTimeline steps={steps} />

                <div className="flex flex-wrap justify-end gap-2">
                  <Button
                    size="sm"
                    variant="outline"
                    className="gap-1.5"
                    disabled={followingUp}
                    onClick={handleFollowUp}
                  >
                    <CornerDownRight className="size-3.5" />
                    {followingUp ? "꼬리 질문 생성 중…" : "꼬리 질문 받기"}
                  </Button>
                  <Button size="sm" className="gap-1.5" onClick={handleNext}>
                    다음 질문 <ArrowRight className="size-3.5" />
                  </Button>
                </div>
              </div>
            )}
          </CardContent>
        </Card>
      ) : null}
    </div>
  );
}

function SessionHeader({ session }: { session: InterviewSession }) {
  return (
    <div className="flex items-center gap-2 text-sm text-slate-500">
      <Badge className="bg-slate-100 text-slate-600">세션 #{session.id}</Badge>
      <span>복습 테스트 진행 중</span>
    </div>
  );
}

function ErrorLine({ message }: { message: string }) {
  return (
    <p className="flex items-center gap-1.5 text-sm text-red-500">
      <AlertCircle className="size-4" /> {message}
    </p>
  );
}
