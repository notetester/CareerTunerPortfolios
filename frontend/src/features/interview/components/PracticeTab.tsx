import { useCallback, useEffect, useState } from "react";
import {
  AlertCircle,
  ArrowRight,
  CheckCircle2,
  FileText,
  Loader2,
  Play,
  RotateCcw,
  Shuffle,
  Sparkles,
} from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent } from "@/app/components/ui/card";
import { Progress } from "@/app/components/ui/progress";
import {
  generateExpectedQuestions,
  getAgentSteps,
  listSessionQuestions,
  submitAnswer,
} from "../api/interviewApi";
import {
  getScoreColor,
  type InterviewAgentStep,
  type InterviewAnswer,
  type InterviewQuestion,
  type InterviewSession,
} from "../types/interview";
import { AgentTimeline } from "./AgentTimeline";
import { InterviewProgressBar } from "./InterviewProgressBar";

type Phase = "loading" | "empty" | "intro" | "answering" | "scoring" | "results";

interface QuestionResult {
  question: InterviewQuestion;
  answerText: string;
  evaluation: InterviewAnswer;
  steps: InterviewAgentStep[];
}

/** Fisher-Yates 셔플. 출제 순서를 무작위화한다. */
function shuffle<T>(arr: T[]): T[] {
  const a = [...arr];
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

/**
 * 복습 테스트. 공부한 예상 면접 질문을 랜덤 순서로 출제하고,
 * 모범답안·피드백 없이(블라인드) 전부 답한 뒤 한 번에 채점한다.
 * 채점이 끝나면 문제별 점수·피드백·멀티에이전트 트레이스를 공개한다.
 */
export function PracticeTab({
  session,
  onGoToReport,
}: {
  session: InterviewSession | null;
  onGoToReport: () => void;
}) {
  const [phase, setPhase] = useState<Phase>("loading");
  const [error, setError] = useState<string | null>(null);
  const [generating, setGenerating] = useState(false);

  const [questions, setQuestions] = useState<InterviewQuestion[]>([]);
  const [current, setCurrent] = useState(0);
  const [answers, setAnswers] = useState<Record<number, string>>({});
  const [draft, setDraft] = useState("");
  const [results, setResults] = useState<QuestionResult[]>([]);

  const load = useCallback(async () => {
    if (!session) return;
    setPhase("loading");
    setError(null);
    try {
      const all = await listSessionQuestions(session.id);
      // 꼬리질문은 제외하고, 공부한 예상 질문만 테스트 대상으로 한다.
      const core = all.filter((q) => q.questionType !== "FOLLOW_UP");
      setQuestions(core);
      setPhase(core.length === 0 ? "empty" : "intro");
    } catch (err) {
      setError(err instanceof Error ? err.message : "질문을 불러오지 못했습니다.");
      setPhase("empty");
    }
  }, [session]);

  useEffect(() => {
    void load();
  }, [load]);

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
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "질문 생성에 실패했습니다.");
    } finally {
      setGenerating(false);
    }
  };

  const start = () => {
    setQuestions((prev) => shuffle(prev));
    setCurrent(0);
    setAnswers({});
    setDraft("");
    setResults([]);
    setError(null);
    setPhase("answering");
  };

  const total = questions.length;
  const isLast = current >= total - 1;

  const goNext = () => {
    const q = questions[current];
    setAnswers((prev) => ({ ...prev, [q.id]: draft.trim() }));
    setDraft("");
    setCurrent((c) => c + 1);
  };

  const handleSubmitAll = async () => {
    const q = questions[current];
    const finalAnswers = { ...answers, [q.id]: draft.trim() };
    setAnswers(finalAnswers);
    setPhase("scoring");
    setError(null);
    try {
      const evals: Record<number, InterviewAnswer> = {};
      for (const question of questions) {
        evals[question.id] = await submitAnswer(question.id, {
          answerText: finalAnswers[question.id] ?? "",
        });
      }
      let steps: InterviewAgentStep[] = [];
      try {
        steps = await getAgentSteps(session.id);
      } catch {
        steps = [];
      }
      setResults(
        questions.map((question) => ({
          question,
          answerText: finalAnswers[question.id] ?? "",
          evaluation: evals[question.id],
          steps: steps.filter((s) => s.questionId === question.id),
        })),
      );
      setPhase("results");
    } catch (err) {
      setError(err instanceof Error ? err.message : "채점에 실패했습니다.");
      setPhase("answering");
    }
  };

  // ───── 렌더 ─────

  if (phase === "loading") {
    return (
      <div className="flex items-center justify-center gap-2 rounded-xl border border-slate-200 bg-white p-10 text-sm text-slate-400">
        <Loader2 className="size-4 animate-spin" /> 불러오는 중…
      </div>
    );
  }

  if (phase === "empty") {
    return (
      <div className="space-y-4">
        {error && <ErrorLine message={error} />}
        <Card className="border border-slate-200 bg-white">
          <CardContent className="space-y-4 p-10 text-center">
            <p className="text-sm text-slate-500">
              복습 테스트를 보려면 예상 면접 질문이 필요합니다. "예상 면접 질문" 탭에서 먼저 질문을 만들어 학습하세요.
            </p>
            <Button
              onClick={handleGenerate}
              disabled={generating}
              className="gap-2 bg-primary text-primary-foreground"
            >
              {generating ? <Loader2 className="size-4 animate-spin" /> : <Sparkles className="size-4" />}
              {generating ? "질문 생성 중…" : "질문 생성하기"}
            </Button>
          </CardContent>
        </Card>
      </div>
    );
  }

  if (phase === "intro") {
    return (
      <Card className="border border-slate-200 bg-white">
        <CardContent className="space-y-4 p-8 text-center">
          <Shuffle className="mx-auto size-10 text-blue-600" />
          <div>
            <h3 className="text-lg font-black text-slate-900">복습 테스트</h3>
            <p className="mt-1 text-sm leading-relaxed text-slate-600">
              공부한 예상 면접 질문 <b>{total}개</b>가 <b>랜덤 순서</b>로 출제됩니다.
              <br />
              모범답안·피드백 없이 전부 답한 뒤 <b>한 번에 채점</b>됩니다.
            </p>
          </div>
          <Button
            onClick={start}
            className="gap-2 bg-primary text-primary-foreground"
          >
            <Play className="size-4" /> 시작하기
          </Button>
        </CardContent>
      </Card>
    );
  }

  if (phase === "scoring") {
    return (
      <div className="rounded-xl border border-slate-200 bg-white p-10">
        <InterviewProgressBar active estimatedMs={Math.max(8000, total * 7000)} label={`${total}개 답변을 채점하는 중`} />
      </div>
    );
  }

  if (phase === "results") {
    const avg = results.length
      ? Math.round(results.reduce((sum, r) => sum + (r.evaluation.score ?? 0), 0) / results.length)
      : 0;
    return (
      <div className="space-y-4">
        <Card className="border border-green-200 bg-green-50">
          <CardContent className="flex flex-wrap items-center justify-between gap-3 p-6">
            <div className="flex items-center gap-3">
              <CheckCircle2 className="size-8 text-green-600" />
              <div>
                <h3 className="font-black text-slate-900">복습 테스트 완료</h3>
                <p className="text-sm text-slate-600">
                  평균 점수 <span className={getScoreColor(avg)}>{avg}점</span>
                </p>
              </div>
            </div>
            <div className="flex flex-wrap gap-2">
              <Button variant="outline" className="gap-1.5" onClick={start}>
                <RotateCcw className="size-4" /> 다시 풀기
              </Button>
              <Button
                className="gap-1.5 bg-primary text-primary-foreground"
                onClick={onGoToReport}
              >
                <FileText className="size-4" /> 면접 리포트 보기
              </Button>
            </div>
          </CardContent>
        </Card>

        {results.map((r, i) => (
          <Card key={r.question.id} className="border border-slate-200 bg-white">
            <CardContent className="space-y-3 p-5">
              <div className="flex items-start gap-2">
                <Badge className="bg-blue-100 text-blue-700">Q{i + 1}</Badge>
                <p className="flex-1 text-sm font-semibold text-slate-900">{r.question.question}</p>
                {r.evaluation.score !== null && (
                  <span className={`shrink-0 text-lg font-black ${getScoreColor(r.evaluation.score)}`}>
                    {r.evaluation.score}점
                  </span>
                )}
              </div>
              <div className="rounded-lg bg-slate-50 p-3 text-sm text-slate-700">
                <div className="mb-1 text-xs font-bold text-slate-500">내 답변</div>
                {r.answerText || <span className="text-slate-400">(무응답)</span>}
              </div>
              {r.evaluation.feedback && <p className="text-xs text-slate-600">{r.evaluation.feedback}</p>}
              {r.evaluation.improvedAnswer && (
                <div className="rounded-lg border border-green-100 bg-green-50 p-3">
                  <div className="mb-1 text-xs font-bold text-green-700">AI 개선 답변</div>
                  <p className="whitespace-pre-line text-sm leading-relaxed text-slate-700">
                    {r.evaluation.improvedAnswer}
                  </p>
                </div>
              )}
              <AgentTimeline steps={r.steps} />
            </CardContent>
          </Card>
        ))}
      </div>
    );
  }

  // phase === "answering"
  const q = questions[current];
  const percent = total > 0 ? Math.round((current / total) * 100) : 0;
  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2 text-sm text-slate-500">
        <Badge className="bg-slate-100 text-slate-600">복습 테스트</Badge>
        <span>블라인드 · 랜덤 출제 (채점은 마지막에 한 번에)</span>
      </div>

      <div className="space-y-1.5">
        <div className="flex items-center justify-between text-xs text-slate-500">
          <span>
            문제 {current + 1}/{total}
          </span>
          <span>{percent}%</span>
        </div>
        <Progress value={percent} />
      </div>

      {error && <ErrorLine message={error} />}

      <Card className="border border-slate-200 bg-white">
        <CardContent className="space-y-3 p-5">
          <div className="flex items-start gap-2">
            <Badge className="bg-blue-100 text-blue-700">Q{current + 1}</Badge>
            <p className="flex-1 text-base font-semibold text-slate-900">{q.question}</p>
          </div>
          <textarea
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            placeholder="답변을 입력하세요 (채점은 마지막에 한 번에 진행됩니다)"
            rows={5}
            className="w-full resize-y rounded-lg border border-slate-200 p-3 text-sm outline-none focus:border-blue-400"
          />
          <div className="flex justify-end">
            {isLast ? (
              <Button
                disabled={!draft.trim()}
                onClick={handleSubmitAll}
                className="gap-1.5 bg-primary text-primary-foreground"
              >
                <Play className="size-4" /> 제출하고 채점
              </Button>
            ) : (
              <Button disabled={!draft.trim()} onClick={goNext} className="gap-1.5">
                다음 질문 <ArrowRight className="size-4" />
              </Button>
            )}
          </div>
        </CardContent>
      </Card>
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
