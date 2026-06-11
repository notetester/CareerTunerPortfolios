import { useCallback, useEffect, useState } from "react";
import { AlertCircle, CornerDownRight, Loader2, Sparkles, ThumbsUp } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent } from "@/app/components/ui/card";
import {
  generateExpectedQuestions,
  generateFollowUps,
  listSessionQuestions,
  submitAnswer,
} from "../api/interviewApi";
import {
  getScoreColor,
  type InterviewAnswer,
  type InterviewQuestion,
  type InterviewSession,
} from "../types/interview";

/** 한 질문 + 답변 입력 + AI 평가 결과. 평가 후 꼬리 질문을 생성할 수 있다. */
function QuestionItem({
  question,
  index,
  onFollowUpsGenerated,
}: {
  question: InterviewQuestion;
  index: number;
  onFollowUpsGenerated: () => void;
}) {
  const [answer, setAnswer] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<InterviewAnswer | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [followingUp, setFollowingUp] = useState(false);

  const isFollowUp = question.questionType === "FOLLOW_UP";

  const handleSubmit = async () => {
    if (!answer.trim()) return;
    setSubmitting(true);
    setError(null);
    try {
      const evaluated = await submitAnswer(question.id, { answerText: answer });
      setResult(evaluated);
    } catch (err) {
      setError(err instanceof Error ? err.message : "답변 평가에 실패했습니다.");
    } finally {
      setSubmitting(false);
    }
  };

  const handleFollowUp = async () => {
    setFollowingUp(true);
    setError(null);
    try {
      await generateFollowUps(question.id);
      onFollowUpsGenerated();
    } catch (err) {
      setError(err instanceof Error ? err.message : "꼬리 질문 생성에 실패했습니다.");
    } finally {
      setFollowingUp(false);
    }
  };

  return (
    <Card className={isFollowUp ? "border border-indigo-200 bg-indigo-50/40" : "border border-slate-200 bg-white"}>
      <CardContent className="space-y-3 p-4">
        <div className="flex items-start gap-2">
          {isFollowUp ? (
            <Badge className="gap-1 bg-indigo-100 text-indigo-700">
              <CornerDownRight className="size-3" /> 꼬리질문
            </Badge>
          ) : (
            <Badge className="bg-blue-100 text-blue-700">Q{index + 1}</Badge>
          )}
          <p className="flex-1 text-sm font-medium text-slate-800">{question.question}</p>
        </div>
        <textarea
          value={answer}
          onChange={(e) => setAnswer(e.target.value)}
          placeholder="답변을 입력하세요"
          rows={3}
          className="w-full resize-y rounded-lg border border-slate-200 p-3 text-sm outline-none focus:border-blue-400"
        />
        <div className="flex justify-end">
          <Button size="sm" disabled={!answer.trim() || submitting} onClick={handleSubmit}>
            {submitting ? "평가 중…" : "답변 평가"}
          </Button>
        </div>

        {error && (
          <p className="flex items-center gap-1.5 text-xs text-red-500">
            <AlertCircle className="size-3.5" /> {error}
          </p>
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
            <div className="flex justify-end">
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
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

export function ExpectedQuestionsTab({ session }: { session: InterviewSession | null }) {
  const [questions, setQuestions] = useState<InterviewQuestion[]>([]);
  const [loading, setLoading] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadExisting = useCallback(async () => {
    if (!session) return;
    setLoading(true);
    setError(null);
    try {
      setQuestions(await listSessionQuestions(session.id));
    } catch (err) {
      setError(err instanceof Error ? err.message : "질문을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, [session]);

  useEffect(() => {
    void loadExisting();
  }, [loadExisting]);

  const handleGenerate = async () => {
    if (!session) return;
    setGenerating(true);
    setError(null);
    try {
      const generated = await generateExpectedQuestions(session.id, { mode: session.mode });
      setQuestions(generated);
    } catch (err) {
      setError(err instanceof Error ? err.message : "질문 생성에 실패했습니다.");
    } finally {
      setGenerating(false);
    }
  };

  if (!session) {
    return (
      <div className="rounded-xl border border-dashed border-slate-200 bg-white p-10 text-center text-sm text-slate-400">
        "면접 모드 선택" 탭에서 지원 건과 모드를 고르고 면접을 시작하면 예상 질문이 생성됩니다.
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="font-bold text-slate-800">예상 면접 질문</h2>
        {questions.length > 0 && (
          <Button size="sm" variant="outline" className="gap-1.5" disabled={generating} onClick={handleGenerate}>
            {generating ? <Loader2 className="size-4 animate-spin" /> : <Sparkles className="size-4" />}
            {generating ? "생성 중…" : "질문 재생성"}
          </Button>
        )}
      </div>

      {error && (
        <p className="flex items-center gap-1.5 text-sm text-red-500">
          <AlertCircle className="size-4" /> {error}
        </p>
      )}

      {loading ? (
        <div className="flex items-center justify-center gap-2 rounded-xl border border-slate-200 bg-white p-10 text-sm text-slate-400">
          <Loader2 className="size-4 animate-spin" /> 불러오는 중…
        </div>
      ) : questions.length === 0 ? (
        <Card className="border border-slate-200 bg-white">
          <CardContent className="flex flex-col items-center gap-4 p-10 text-center">
            <p className="text-sm text-slate-500">
              이 지원 건과 모드에 맞춘 예상 면접 질문을 AI가 생성합니다.
            </p>
            <Button size="lg" className="gap-2" disabled={generating} onClick={handleGenerate}>
              {generating ? <Loader2 className="size-5 animate-spin" /> : <Sparkles className="size-5" />}
              {generating ? "질문 생성 중…" : "질문 생성하기"}
            </Button>
          </CardContent>
        </Card>
      ) : (
        <div className="space-y-3">
          {questions.map((q, i) => (
            <QuestionItem key={q.id} question={q} index={i} onFollowUpsGenerated={loadExisting} />
          ))}
        </div>
      )}
    </div>
  );
}
