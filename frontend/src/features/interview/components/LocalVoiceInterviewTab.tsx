import { useEffect, useRef, useState } from "react";
import { ClipboardList, Loader2, Mic, RotateCcw, Square } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Progress } from "@/app/components/ui/progress";
import {
  getMediaCapabilities,
  getSessionReview,
  listSessionQuestions,
  saveMediaResult,
  scoreVoiceServer,
  scoreVoiceTranscript,
  transcribeVoice,
} from "../api/interviewApi";
import { blobToBase64, countFillers } from "../hooks/voiceAnalysis";
import type {
  InterviewQuestion,
  InterviewSession,
  MediaCapabilities,
  TranscriptLine,
  VoiceScoreDetail,
} from "../types/interview";
import { getScoreColor } from "../types/interview";
import { useTutorialStore } from "../tutorial/tutorialStore";
import { TutorialMediaPreview } from "../tutorial/TutorialMediaPreview";

type Status = "idle" | "recording" | "scoring" | "done";

interface AnswerResult {
  questionId: number;
  question: string;
  transcript: string;
  score: number; // 전달력 (serve LightGBM)
  detail: VoiceScoreDetail;
  source: string;
  contentScore: number | null; // 내용 채점 (haiku LLM)
  contentFeedback: string | null;
}

/**
 * B(베이직) 로컬 음성 면접 — OpenAI Realtime 없이 완전 자체 (외부 API 0).
 * 준비된 질문을 하나씩 띄우고 답변을 녹음 → 자체 STT(faster-whisper) + 자체 전달력 모델(LightGBM serve)
 * + 내용 채점(LLM)으로 채점한다. A(프리미엄)와 달리 실시간 음성 API를 쓰지 않는다 (serve 필수).
 */
export function LocalVoiceInterviewTab({ session }: { session: InterviewSession | null }) {
  const tutorialActive = useTutorialStore((s) => s.mode !== "off");
  const [status, setStatus] = useState<Status>("idle");
  const [error, setError] = useState<string | null>(null);
  const [note, setNote] = useState<string | null>(null);
  const [capabilities, setCapabilities] = useState<MediaCapabilities | null>(null);
  const [questions, setQuestions] = useState<InterviewQuestion[] | null>(null);
  const [questionIdx, setQuestionIdx] = useState(-1);
  const [answers, setAnswers] = useState<AnswerResult[]>([]);
  const [consent, setConsent] = useState(true);

  const recorderRef = useRef<MediaRecorder | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const micRef = useRef<MediaStream | null>(null);

  const supported =
    typeof navigator !== "undefined" &&
    !!navigator.mediaDevices &&
    typeof window !== "undefined" &&
    "MediaRecorder" in window;

  useEffect(() => {
    if (!session) return;
    listSessionQuestions(session.id)
      .then((qs) => setQuestions(qs.filter((q) => q.parentQuestionId == null).slice(0, 6)))
      .catch(() => setQuestions([]));
    getMediaCapabilities().then(setCapabilities).catch(() => setCapabilities(null));
  }, [session]);

  useEffect(() => () => cleanup(), []);

  // 질문이 뜨면 면접관이 음성으로 읽어준다 (브라우저 내장 TTS, API 0).
  useEffect(() => {
    if (questionIdx < 0 || status !== "idle" || !questions?.[questionIdx]) return;
    if (typeof window === "undefined" || !window.speechSynthesis) return;
    const utter = new SpeechSynthesisUtterance(
      `${questionIdx + 1}번 질문. ${questions[questionIdx].question}`,
    );
    utter.lang = "ko-KR";
    window.speechSynthesis.cancel();
    window.speechSynthesis.speak(utter);
  }, [questionIdx, status, questions]);

  useEffect(() => () => window.speechSynthesis?.cancel(), []);

  const cleanup = () => {
    if (recorderRef.current?.state === "recording") recorderRef.current.stop();
    recorderRef.current = null;
    micRef.current?.getTracks().forEach((t) => t.stop());
    micRef.current = null;
  };

  const startInterview = () => {
    setError(null);
    setNote(null);
    setAnswers([]);
    setQuestionIdx(0);
    setStatus("idle");
  };

  const startRecording = async () => {
    window.speechSynthesis?.cancel(); // 질문 읽는 중이면 멈추고 녹음 시작
    try {
      const mic = await navigator.mediaDevices.getUserMedia({ audio: true });
      micRef.current = mic;
      chunksRef.current = [];
      const recorder = new MediaRecorder(mic);
      recorder.ondataavailable = (e) => {
        if (e.data.size > 0) chunksRef.current.push(e.data);
      };
      recorder.start();
      recorderRef.current = recorder;
      setError(null);
      setStatus("recording");
    } catch {
      setError("마이크 접근에 실패했습니다.");
    }
  };

  /** 녹음 종료 → 자체 STT + 전달력(serve) + 내용 채점 → 다음 질문. */
  const stopAndScore = async () => {
    if (!session || !questions) return;
    setStatus("scoring");
    const blob = await new Promise<Blob | null>((resolve) => {
      const r = recorderRef.current;
      if (!r || r.state !== "recording") {
        resolve(chunksRef.current.length ? new Blob(chunksRef.current) : null);
        return;
      }
      r.onstop = () =>
        resolve(chunksRef.current.length ? new Blob(chunksRef.current, { type: r.mimeType }) : null);
      r.stop();
    });
    cleanup();

    const question = questions[questionIdx]?.question ?? `질문 ${questionIdx + 1}`;
    if (!blob || blob.size === 0) {
      setError("녹음이 비어 있습니다. 다시 답변해 주세요.");
      setStatus("idle");
      return;
    }
    try {
      const audioBase64 = await blobToBase64(blob);
      const audioFormat = (blob.type || "audio/webm").includes("webm") ? "webm" : "wav";
      // 1) 자체 STT (faster-whisper)
      const stt = await transcribeVoice(session.id, audioBase64, audioFormat);
      const chars = stt.text.replace(/\s/g, "").length;
      const fillers = countFillers([stt.text]);
      // 2) 자체 전달력 (serve LightGBM)
      const server = await scoreVoiceServer(session.id, {
        audioBase64,
        audioFormat,
        transcriptChars: chars,
        fillerCount: fillers,
      });
      // 3) 내용 채점 (haiku) — 질문-답변 저장 후 점수/피드백 조회 (실패해도 전달력은 표시)
      const transcript: TranscriptLine[] = [
        { role: "ai", text: question },
        { role: "user", text: stt.text },
      ];
      let contentScore: number | null = null;
      let contentFeedback: string | null = null;
      try {
        await scoreVoiceTranscript(session.id, transcript);
        const review = await getSessionReview(session.id);
        const item = review.items.find((it) => it.questionId === questions[questionIdx].id);
        contentScore = item?.score ?? null;
        contentFeedback = item?.feedback ?? null;
      } catch {
        // 내용 채점/조회 실패 — 전달력 점수만으로 진행
      }

      const result: AnswerResult = {
        questionId: questions[questionIdx].id,
        question,
        transcript: stt.text,
        score: server.score,
        detail: server.detail,
        source: server.source,
        contentScore,
        contentFeedback,
      };
      const next = [...answers, result];
      setAnswers(next);

      const nextIdx = questionIdx + 1;
      if (nextIdx >= questions.length) {
        const overall = Math.round(next.reduce((s, a) => s + a.score, 0) / next.length);
        await saveMediaResult(session.id, {
          kind: "VOICE",
          transcript: next.flatMap<TranscriptLine>((a) => [
            { role: "ai", text: a.question },
            { role: "user", text: a.transcript },
          ]),
          metrics: { mode: "local-basic", answers: next.length },
          score: overall,
          scoreDetail: { overall },
        });
        setQuestionIdx(nextIdx);
        setStatus("done");
        setNote("로컬 면접 완료 — 자체 STT·전달력 모델로 채점했습니다 (외부 API 0).");
      } else {
        setQuestionIdx(nextIdx);
        setStatus("idle");
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : "채점에 실패했습니다.");
      setStatus("idle");
    }
  };

  if (tutorialActive) {
    return <TutorialMediaPreview kind="voice" />;
  }

  if (!session) {
    return (
      <div className="rounded-xl border border-dashed border-slate-200 bg-white p-10 text-center text-sm text-slate-400">
        "면접 모드 선택" 탭에서 지원 건과 모드를 고르고 면접을 시작하면 로컬 음성 면접을 진행할 수 있습니다.
      </div>
    );
  }

  if (questions != null && questions.length === 0) {
    return (
      <div className="rounded-xl border border-dashed border-amber-200 bg-amber-50 p-10 text-center">
        <ClipboardList className="mx-auto size-8 text-amber-500" />
        <p className="mt-3 text-sm font-semibold text-amber-800">준비된 면접 질문이 없습니다</p>
        <p className="mt-1 text-sm text-amber-700">
          "예상 면접 질문" 탭에서 질문을 먼저 생성하면, 그 질문으로 로컬 음성 면접을 진행합니다.
        </p>
      </div>
    );
  }

  const serveOff = capabilities != null && !capabilities.nonverbal;
  const started = questionIdx >= 0;
  const overall = answers.length
    ? Math.round(answers.reduce((s, a) => s + a.score, 0) / answers.length)
    : 0;

  return (
    <div className="space-y-4">
      <Card className="border border-slate-200 bg-white">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <Mic className="size-4 text-emerald-600" />
            녹음형 음성 면접
            <Badge className="bg-emerald-100 text-emerald-700">베이직 · 무료</Badge>
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <p className="text-sm text-slate-500">
            준비된 질문을 하나씩 답변 녹음하면 자체 STT(faster-whisper)와 전달력 모델(LightGBM)로 채점합니다.
            실시간 음성 API를 쓰지 않는 베이직 모드입니다.
          </p>

          {serveOff && (
            <p className="rounded-lg bg-amber-50 p-3 text-sm text-amber-700">
              자체 추론 서버(serve)가 꺼져 있어 로컬 면접을 시작할 수 없습니다. (ml/interview-nonverbal/run-serve.bat 기동)
            </p>
          )}

          {!supported && (
            <p className="rounded-lg bg-amber-50 p-3 text-sm text-amber-700">
              이 브라우저는 녹음을 지원하지 않습니다. 최신 Chrome/Edge 에서 이용해 주세요.
            </p>
          )}

          {!started && !serveOff && (
            <label className="flex items-start gap-2 rounded-lg bg-slate-50 p-3 text-xs text-slate-600">
              <input
                type="checkbox"
                checked={consent}
                onChange={(e) => setConsent(e.target.checked)}
                className="mt-0.5"
              />
              <span>
                녹음된 음성이 분석 서버로 전송되어 자체 모델로 채점됩니다. 분석 후 음성은 즉시 폐기되며 저장하지 않습니다.
              </span>
            </label>
          )}

          {started && status !== "done" && questions && (
            <div className="space-y-1">
              <div className="flex items-center justify-between text-xs text-slate-500">
                <span>질문 {questionIdx + 1} / {questions.length}</span>
              </div>
              <Progress value={(questionIdx / questions.length) * 100} className="h-1.5" />
              <p className="rounded-lg bg-slate-50 p-3 text-sm font-medium text-slate-700">
                Q{questionIdx + 1}. {questions[questionIdx]?.question}
              </p>
            </div>
          )}

          {supported && !serveOff && (
            <div className="flex flex-wrap items-center gap-2">
              {!started && (
                <Button
                  onClick={startInterview}
                  disabled={questions == null || !consent}
                  className="gap-1.5 bg-emerald-600 hover:bg-emerald-700"
                >
                  <Mic className="size-4" /> 면접 시작
                </Button>
              )}
              {started && status === "idle" && (
                <Button onClick={startRecording} className="gap-1.5 bg-rose-600 hover:bg-rose-700">
                  <Mic className="size-4" /> 답변 녹음 시작
                </Button>
              )}
              {status === "recording" && (
                <Button onClick={stopAndScore} variant="destructive" className="gap-1.5">
                  <Square className="size-4" /> 답변 완료
                </Button>
              )}
              {status === "scoring" && (
                <Button disabled className="gap-1.5">
                  <Loader2 className="size-4 animate-spin" /> 채점 중…
                </Button>
              )}
              {status === "done" && (
                <Button onClick={startInterview} variant="outline" className="gap-1.5">
                  <RotateCcw className="size-4" /> 다시 면접
                </Button>
              )}
            </div>
          )}

          {error && <p className="text-sm text-red-500">{error}</p>}
          {note && <p className="text-xs font-semibold text-slate-500">{note}</p>}
        </CardContent>
      </Card>

      {answers.length > 0 && (
        <Card className="border border-slate-200 bg-white">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              답변별 채점 (내용 · 전달력)
              {status === "done" && (
                <span className={`ml-auto text-2xl font-black ${getScoreColor(overall)}`}>
                  {overall}
                  <span className="text-sm font-bold text-slate-400">/100</span>
                </span>
              )}
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            {answers.map((a, i) => (
              <div key={i} className="space-y-2 rounded-lg border border-slate-100 p-3">
                <p className="text-sm font-semibold text-slate-700">
                  Q{i + 1}. {a.question}
                </p>
                <p className="text-xs text-slate-500">{a.transcript || "(전사 없음)"}</p>
                <div className="flex flex-wrap gap-4 text-sm">
                  <span className="text-slate-600">
                    내용 <b className={getScoreColor(a.contentScore ?? 0)}>{a.contentScore ?? "—"}</b>
                  </span>
                  <span className="text-slate-600">
                    전달력 <b className={getScoreColor(a.score)}>{a.score}</b>
                  </span>
                </div>
                <div className="flex flex-wrap gap-x-3 gap-y-0.5 text-[11px] text-slate-400">
                  <span>말속도 {a.detail.pace}</span>
                  <span>유창성 {a.detail.fluency}</span>
                  <span>안정감 {a.detail.stability}</span>
                  <span>자신감 {a.detail.confidence}</span>
                  <span>반응성 {a.detail.responsiveness}</span>
                </div>
                {a.contentFeedback && (
                  <p className="rounded bg-slate-50 p-2 text-[11px] leading-relaxed text-slate-500">
                    {a.contentFeedback}
                  </p>
                )}
              </div>
            ))}
          </CardContent>
        </Card>
      )}
    </div>
  );
}
