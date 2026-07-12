import { useCallback, useEffect, useState } from "react";
import {
  AlertCircle,
  ArrowRight,
  CornerDownRight,
  Lightbulb,
  Loader2,
  RotateCcw,
  Sparkles,
} from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent } from "@/app/components/ui/card";
import { AiChargeCostBadge } from "@/features/billing/components/AiChargeCostBadge";
import { ModelPicker, type AiModelChoice } from "@/app/components/ai/ModelPicker";
import { InterviewProgressBar } from "./InterviewProgressBar";
import {
  generateExpectedQuestions,
  generateFollowUps,
  getModelAnswer,
  getSessionReview,
  listSessionQuestions,
  submitAnswer,
} from "../api/interviewApi";
import {
  getScoreColor,
  toSentenceLines,
  type InterviewAnswer,
  type InterviewQuestion,
  type InterviewSession,
} from "../types/interview";

/** 한 질문 + 답변 입력 + AI 평가 결과. 평가 후 꼬리 질문을 생성할 수 있다. */
function QuestionItem({
  question,
  index,
  mode,
  initialAnswer = "",
  initialScore = null,
  alreadyHasRebuttal = false,
  onFollowUpsGenerated,
}: {
  question: InterviewQuestion;
  index: number;
  mode: string;
  initialAnswer?: string;
  initialScore?: number | null;
  alreadyHasRebuttal?: boolean;
  onFollowUpsGenerated: (questions: InterviewQuestion[]) => void;
}) {
  const [answer, setAnswer] = useState(initialAnswer);
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<InterviewAnswer | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [followingUp, setFollowingUp] = useState(false);
  const [modelAnswer, setModelAnswer] = useState<string | null>(null);
  const [loadingModel, setLoadingModel] = useState(false);
  const [showModel, setShowModel] = useState(false);
  // 이미 반박(자식)이 있는 본질문은 재제출해도 자동 반박을 또 만들지 않는다(복원 답변 재제출 시 중복 방지).
  const [rebuttalRequested, setRebuttalRequested] = useState(alreadyHasRebuttal);

  const isFollowUp = question.questionType === "FOLLOW_UP";
  const isPressure = mode === "PRESSURE";

  const handleFollowUp = async (): Promise<boolean> => {
    setFollowingUp(true);
    setError(null);
    try {
      // 반환된 전체 질문 목록을 그대로 반영한다. loadExisting(로딩 스피너로 전체 교체)을 쓰면
      // 답변/평가 결과가 있는 카드까지 언마운트돼 "초기화"처럼 보이므로 목록만 갈아끼운다.
      const updated = await generateFollowUps(question.id, {}, question.interviewSessionId);
      onFollowUpsGenerated(updated);
      return true;
    } catch (err) {
      setError(err instanceof Error ? err.message : "반박 질문 생성에 실패했습니다.");
      return false;
    } finally {
      setFollowingUp(false);
    }
  };

  const handleSubmit = async () => {
    if (!answer.trim()) return;
    setSubmitting(true);
    setError(null);
    try {
      // 모범답안을 봤다면 그 답안을 만점 기준(답안지)으로 함께 보낸다.
      const evaluated = await submitAnswer(question.id, { answerText: answer, modelAnswer });
      setResult(evaluated);
    } catch (err) {
      setError(err instanceof Error ? err.message : "답변 평가에 실패했습니다.");
      setSubmitting(false);
      return;
    }
    setSubmitting(false);
    // 압박 면접: 본질문에 답하면 반박(꼬리질문) 1개를 자동 생성한다. 반박 질문 자체엔 다시 하지 않는다(1회).
    if (isPressure && !isFollowUp && !rebuttalRequested) {
      setRebuttalRequested(true);
      const ok = await handleFollowUp();
      if (!ok) setRebuttalRequested(false); // 실패 시 재제출로 재시도 가능
    }
  };

  const handleModelAnswer = async () => {
    // 이미 받아둔 모범답안이면 재호출 없이 펼침/접기만 토글한다.
    if (modelAnswer) {
      setShowModel((v) => !v);
      return;
    }
    setLoadingModel(true);
    setError(null);
    try {
      const res = await getModelAnswer(question.id);
      setModelAnswer(res.modelAnswer);
      setShowModel(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "모범답안 생성에 실패했습니다.");
    } finally {
      setLoadingModel(false);
    }
  };

  return (
    <Card className={isFollowUp ? "border border-indigo-200 bg-indigo-50/40" : "border border-slate-200 bg-card"}>
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
          {initialScore !== null && !result && (
            <Badge className="shrink-0 bg-slate-100 text-slate-500">이전 {initialScore}점</Badge>
          )}
        </div>
        <textarea
          value={answer}
          onChange={(e) => setAnswer(e.target.value)}
          placeholder="답변을 입력하세요"
          rows={3}
          className="w-full resize-y rounded-lg border border-slate-200 p-3 text-sm outline-none focus:border-blue-400"
        />
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="flex flex-wrap gap-1.5">
            <AiChargeCostBadge featureType="INTERVIEW_ANSWER_EVAL" />
            {isPressure && !isFollowUp && !rebuttalRequested && (
              <AiChargeCostBadge featureType="INTERVIEW_FOLLOWUP_GEN" prefix="압박 꼬리질문" />
            )}
          </div>
          <div className="flex flex-wrap gap-2">
            <Button
              size="sm"
              variant="outline"
              className="gap-1.5"
              disabled={loadingModel}
              onClick={handleModelAnswer}
            >
              {loadingModel ? <Loader2 className="size-3.5 animate-spin" /> : <Lightbulb className="size-3.5" />}
              {loadingModel ? "생성 중…" : modelAnswer && showModel ? "모범답안 접기" : "모범답안 보기"}
            </Button>
            <Button size="sm" disabled={!answer.trim() || submitting} onClick={handleSubmit}>
              {submitting ? "평가 중…" : "답변 평가"}
            </Button>
          </div>
        </div>

        {showModel && modelAnswer && (
          <div className="rounded-lg border border-amber-100 bg-amber-50 p-3">
            <div className="mb-1 flex items-center gap-1.5 text-xs font-bold text-amber-700">
              <Lightbulb className="size-3.5" /> 모범답안
            </div>
            <p className="whitespace-pre-line text-sm leading-relaxed text-slate-700">{toSentenceLines(modelAnswer)}</p>
          </div>
        )}

        {error && (
          <p className="flex items-center gap-1.5 text-xs text-red-500">
            <AlertCircle className="size-3.5" /> {error}
          </p>
        )}

        <InterviewProgressBar active={submitting} estimatedMs={8000} label="AI가 답변을 채점·검증하고 있어요" />

        {!submitting && result && (
          <div className="space-y-3 rounded-lg bg-slate-50 p-3">
            {result.score !== null && (
              <div className="text-sm font-bold">
                점수 <span className={getScoreColor(result.score)}>{result.score}점</span>
              </div>
            )}
            {result.feedback && (
              <p className="whitespace-pre-line text-xs leading-relaxed text-slate-600">{toSentenceLines(result.feedback)}</p>
            )}
            {result.score === 100 ? (
              <div className="rounded-lg border border-green-100 bg-green-50 p-3 text-sm font-semibold text-green-700">
                만점이에요. 이대로 말하면 됩니다.
              </div>
            ) : (
              <p className="flex items-center gap-1.5 text-xs text-slate-500">
                <Lightbulb className="size-3.5 text-amber-500" /> 위 "모범답안 보기"로 만점 기준 답안을 확인해 보세요.
              </p>
            )}
            {isPressure && followingUp && (
              <div className="flex items-center gap-1.5 text-xs text-slate-500">
                <Loader2 className="size-3.5 animate-spin" /> 반박 질문 생성 중…
              </div>
            )}
          </div>
        )}
      </CardContent>
    </Card>
  );
}

export function ExpectedQuestionsTab({
  session,
  onGoToPractice,
}: {
  session: InterviewSession | null;
  onGoToPractice: () => void;
}) {
  const [questions, setQuestions] = useState<InterviewQuestion[]>([]);
  const [loading, setLoading] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [genModel, setGenModel] = useState<AiModelChoice>("AUTO");
  const [error, setError] = useState<string | null>(null);
  const [resetVersion, setResetVersion] = useState(0);
  const [modelAnswersPreparing, setModelAnswersPreparing] = useState(false);
  // 복원/기존 세션: 질문별 과거 최신 답변·점수 (textarea prefill + "이전 N점" 배지용)
  const [answerMap, setAnswerMap] = useState<Record<number, { answer: string; score: number | null }>>({});

  const loadExisting = useCallback(async () => {
    if (!session) return;
    setLoading(true);
    setError(null);
    try {
      // 질문과 함께 과거 답변/점수를 불러와 textarea 를 채운다(복원 느낌). 답변 조회 실패는 무시하고 질문만 표시.
      const [qs, review] = await Promise.all([
        listSessionQuestions(session.id),
        getSessionReview(session.id).catch(() => null),
      ]);
      setQuestions(qs);
      const map: Record<number, { answer: string; score: number | null }> = {};
      review?.items.forEach((it) => {
        if (it.answerText) map[it.questionId] = { answer: it.answerText, score: it.score };
      });
      setAnswerMap(map);
    } catch (err) {
      setError(err instanceof Error ? err.message : "질문을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, [session]);

  useEffect(() => {
    void loadExisting();
  }, [loadExisting]);

  // 모범답안은 질문 생성 후 백그라운드에서 만들어진다. 생성 직후 잠깐 "준비 중" 힌트를 보였다가 자동으로 거둔다.
  useEffect(() => {
    if (!modelAnswersPreparing) return;
    const timer = setTimeout(() => setModelAnswersPreparing(false), 25000);
    return () => clearTimeout(timer);
  }, [modelAnswersPreparing]);

  const handleGenerate = async () => {
    if (!session) return;
    const hasSavedAnswers = Object.keys(answerMap).length > 0;
    if (questions.length > 0 && hasSavedAnswers) {
      setError("이미 답변한 세션의 질문은 기록 보호를 위해 교체할 수 없습니다. '면접 모드 선택'에서 새 세션을 시작해 주세요.");
      return;
    }
    if (questions.length > 0 && !window.confirm(
      "아직 답변하지 않은 기존 질문과 모범답안을 새 질문으로 교체할까요? 이 작업은 되돌릴 수 없습니다.",
    )) {
      return;
    }
    setGenerating(true);
    setError(null);
    try {
      const generated = await generateExpectedQuestions(session.id, { mode: session.mode }, genModel);
      setQuestions(generated);
      setAnswerMap({}); // 재생성하면 과거 답변은 무효
      setModelAnswersPreparing(true); // 모범답안 백그라운드 생성 동안 잠깐 준비 중 힌트 표시
    } catch (err) {
      setError(err instanceof Error ? err.message : "질문 생성에 실패했습니다.");
    } finally {
      setGenerating(false);
    }
  };

  if (!session) {
    return (
      <div className="rounded-xl border border-dashed border-slate-200 bg-card p-10 text-center text-sm text-slate-400">
        "면접 모드 선택" 탭에서 지원 건과 모드를 고르고 면접을 시작하면 예상 질문이 생성됩니다.
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="font-bold text-slate-800">예상 면접 질문</h2>
        <div className="flex flex-wrap items-center justify-end gap-2">
          <AiChargeCostBadge featureType="INTERVIEW_QUESTION_GEN" />
          <ModelPicker value={genModel} onChange={setGenModel} disabled={generating} />
          {questions.length > 0 && (
            <Button size="sm" variant="outline" className="gap-1.5" disabled={generating} onClick={handleGenerate}>
              {generating ? <Loader2 className="size-4 animate-spin" /> : <Sparkles className="size-4" />}
              {generating ? "생성 중…" : "질문 재생성"}
            </Button>
          )}
        </div>
      </div>

      {error && (
        <p className="flex items-center gap-1.5 text-sm text-red-500">
          <AlertCircle className="size-4" /> {error}
        </p>
      )}

      <InterviewProgressBar active={generating} estimatedMs={13000} label="AI가 질문을 만들고 있어요" />
      {generating ? (
        <div className="space-y-3">
          {[0, 1, 2, 3].map((i) => (
            <div key={i} className="space-y-3 rounded-xl border border-slate-200 bg-card p-4">
              <div className="flex items-center gap-2">
                <div className="size-6 shrink-0 animate-pulse rounded-full bg-slate-200" />
                <div className="h-3.5 w-3/4 animate-pulse rounded bg-slate-200" />
              </div>
              <div className="space-y-2">
                <div className="h-3 w-full animate-pulse rounded bg-slate-100" />
                <div className="h-3 w-5/6 animate-pulse rounded bg-slate-100" />
              </div>
              <div className="flex justify-end gap-2">
                <div className="h-8 w-28 animate-pulse rounded bg-slate-100" />
                <div className="h-8 w-20 animate-pulse rounded bg-slate-100" />
              </div>
            </div>
          ))}
        </div>
      ) : loading ? (
        <div className="flex items-center justify-center gap-2 rounded-xl border border-slate-200 bg-card p-10 text-sm text-slate-400">
          <Loader2 className="size-4 animate-spin" /> 불러오는 중…
        </div>
      ) : questions.length === 0 ? (
        <Card className="border border-slate-200 bg-card">
          <CardContent className="flex flex-col items-center gap-4 p-10 text-center">
            <p className="text-sm text-slate-500">
              이 지원 건과 모드에 맞춘 예상 면접 질문을 AI가 생성합니다.
            </p>
            <Button
              size="lg"
              className="gap-2 bg-primary text-primary-foreground"
              disabled={generating}
              onClick={handleGenerate}
            >
              {generating ? <Loader2 className="size-5 animate-spin" /> : <Sparkles className="size-5" />}
              {generating ? "질문 생성 중…" : "질문 생성하기"}
            </Button>
          </CardContent>
        </Card>
      ) : (
        <div className="space-y-3" data-tut="tut-q-list">
          <InterviewProgressBar active={modelAnswersPreparing} estimatedMs={20000} label="모범답안 준비 중" />
          {questions.map((q, i) => (
            <QuestionItem
              key={`${q.id}-${resetVersion}`}
              question={q}
              index={questions.slice(0, i + 1).filter((x) => x.questionType !== "FOLLOW_UP").length - 1}
              mode={session.mode}
              initialAnswer={answerMap[q.id]?.answer ?? ""}
              initialScore={answerMap[q.id]?.score ?? null}
              alreadyHasRebuttal={questions.some((x) => x.parentQuestionId === q.id)}
              onFollowUpsGenerated={(qs) => setQuestions(qs)}
            />
          ))}

          <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-slate-200 bg-card p-4">
            <p className="text-sm text-slate-500">
              질문을 다 풀어봤다면, 모범답안 없이 복습 테스트로 제대로 소화했는지 점검해 보세요.
            </p>
            <div className="flex flex-wrap gap-2">
              <Button variant="outline" className="gap-1.5" onClick={() => setResetVersion((v) => v + 1)}>
                <RotateCcw className="size-4" /> 답변 다시 작성
              </Button>
              <Button
                className="gap-1.5 bg-primary text-primary-foreground"
                onClick={onGoToPractice}
              >
                복습 테스트 풀러 가기 <ArrowRight className="size-4" />
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
