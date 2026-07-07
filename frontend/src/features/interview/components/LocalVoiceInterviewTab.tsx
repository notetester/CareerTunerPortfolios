import { useEffect, useRef, useState } from "react";
import { ClipboardList, Loader2, Mic, RotateCcw, Square, Volume2 } from "lucide-react";
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
import { blobToBase64, computeVoiceScore, countFillers, VoiceMetricsTracker } from "../hooks/voiceAnalysis";
import { BrowserSttTracker } from "../hooks/speechToText";
import { createNegotiatedRecorder, isTtsSupported, mediaUnsupportedReason } from "../hooks/mediaSupport";
import { useDeviceCapabilities } from "../hooks/deviceCapabilities";
import { DeviceHandoffCard, type HandoffReason } from "./DeviceHandoffCard";
import { RemoteMicConnectCard } from "./RemoteMicConnectCard";
import { MicLevelMeter } from "./MicLevelMeter";
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

interface Recording {
  questionId: number;
  question: string;
  blob: Blob;
  /** 녹음 시 협상된 업로드 포맷(webm|mp4) — blob.type 스니핑 대신 이 값을 쓴다. */
  format: string;
  /** 녹음 중 수집한 온디바이스 음향 지표 트래커 — serve 미기동 시 전달력 폴백 채점에 쓴다. */
  tracker: VoiceMetricsTracker | null;
  /** 녹음 중 브라우저 음성인식(Web Speech)으로 받아둔 전사 — serve STT 미기동 시 전사 폴백. */
  webSpeechText: string;
}

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

const ZERO_DETAIL: VoiceScoreDetail = {
  pace: 0, fluency: 0, stability: 0, confidence: 0, responsiveness: 0, overall: 0,
};

/**
 * B(베이직) 로컬 음성 면접 — OpenAI Realtime 없이 완전 자체 (외부 API 0).
 * 질문을 TTS 로 읽어주고(다 읽으면 자동 녹음), 답변을 모아 6문제 끝에 일괄 채점한다.
 * 채점: 자체 STT(faster-whisper) + 전달력(serve LightGBM) + 내용(haiku LLM).
 * 음성은 채점 후 폐기, 결과(점수·피드백)는 saveMediaResult 로 저장한다.
 */
export function LocalVoiceInterviewTab({ session }: { session: InterviewSession | null }) {
  const tutorialActive = useTutorialStore((s) => s.mode !== "off");
  const [status, setStatus] = useState<Status>("idle");
  const [error, setError] = useState<string | null>(null);
  const [note, setNote] = useState<string | null>(null);
  const [capabilities, setCapabilities] = useState<MediaCapabilities | null>(null);
  const [questions, setQuestions] = useState<InterviewQuestion[] | null>(null);
  const [questionIdx, setQuestionIdx] = useState(-1);
  const [results, setResults] = useState<AnswerResult[]>([]);
  const [scoreProgress, setScoreProgress] = useState(0);
  const [consent, setConsent] = useState(true);
  const [speaking, setSpeaking] = useState(false);
  // 녹음 중 마이크 레벨 시각화용 — micRef 와 같은 스트림 (ref 는 리렌더를 못 일으켜 state 로 별도 보관)
  const [micStream, setMicStream] = useState<MediaStream | null>(null);

  const recorderRef = useRef<MediaRecorder | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const micRef = useRef<MediaStream | null>(null);
  const recordingsRef = useRef<Recording[]>([]);
  const trackerRef = useRef<VoiceMetricsTracker | null>(null);
  const sttRef = useRef<BrowserSttTracker | null>(null);
  /** 현재 녹음의 협상된 업로드 포맷 — Recording 으로 이관해 채점 요청에 쓴다. */
  const formatRef = useRef<string>("webm");

  const supported =
    typeof navigator !== "undefined" &&
    !!navigator.mediaDevices &&
    typeof window !== "undefined" &&
    "MediaRecorder" in window;

  // TTS 미지원(Android WebView 등)이면 질문 읽기를 건너뛰고 텍스트 강조로 진행한다.
  const ttsAvailable = isTtsSupported();
  const deviceCaps = useDeviceCapabilities();
  // 폰 마이크 핸드오프로 받은 원격 오디오 스트림(마이크 없는 기기에서 폰을 마이크로 사용).
  const [remoteMic, setRemoteMic] = useState<MediaStream | null>(null);
  // 이 기기에서 진행 불가한 원인 — 있으면 "폰으로 이어하기" 안내 카드를 띄운다.
  const handoffReason: HandoffReason | null = !supported
    ? (mediaUnsupportedReason() ?? "unsupported")
    : deviceCaps.hasMicrophone === false
      ? "no-microphone"
      : null;
  // 폰 마이크가 연결됐으면 무마이크여도 진행 가능.
  const canProceed = !handoffReason || (handoffReason === "no-microphone" && !!remoteMic);

  useEffect(() => {
    if (!session) return;
    listSessionQuestions(session.id)
      .then((qs) => setQuestions(qs.filter((q) => q.parentQuestionId == null).slice(0, 6)))
      .catch(() => setQuestions([]));
    getMediaCapabilities().then(setCapabilities).catch(() => setCapabilities(null));
  }, [session]);

  useEffect(() => () => cleanup(), []);
  useEffect(() => () => window.speechSynthesis?.cancel(), []);

  const cleanup = () => {
    if (recorderRef.current?.state === "recording") recorderRef.current.stop();
    recorderRef.current = null;
    micRef.current?.getTracks().forEach((t) => t.stop());
    micRef.current = null;
    setMicStream(null);
    // finishAnswer 가 Recording 으로 이관하지 못한 트래커만 정리(중단 등). 이관된 것은 trackerRef=null 이라 무해.
    trackerRef.current?.dispose();
    trackerRef.current = null;
    sttRef.current?.dispose();
    sttRef.current = null;
  };

  // 녹음 시작 신호음 (짧은 비프, Web Audio — API 0). 화면 안 봐도 답변 타이밍 캐치.
  const beep = () => {
    try {
      const Ctx =
        window.AudioContext ??
        (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      const ctx = new Ctx();
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.frequency.value = 880;
      gain.gain.setValueAtTime(0.12, ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.18);
      osc.start();
      osc.stop(ctx.currentTime + 0.18);
      osc.onended = () => ctx.close();
    } catch {
      // 비프 실패는 무시
    }
  };

  const startRecording = async () => {
    if (recorderRef.current) return; // 이미 녹음 중 (자동/수동 중복 방지)
    window.speechSynthesis?.cancel();
    setSpeaking(false);
    try {
      // 폰 마이크 핸드오프가 연결돼 있으면 그 스트림을 clone 해 쓴다(원본은 카드가 소유·정리).
      const mic = remoteMic
        ? remoteMic.clone()
        : await navigator.mediaDevices.getUserMedia({ audio: true });
      micRef.current = mic;
      setMicStream(mic);
      chunksRef.current = [];
      // 온디바이스 음향 지표 수집 시작(serve 미기동 시 전달력 폴백용). 녹음 시작 = 질문 TTS 직후 → 반응 지연 측정.
      const tracker = new VoiceMetricsTracker();
      tracker.start(mic);
      tracker.markAiSpeechEnd();
      trackerRef.current = tracker;
      // serve STT 폴백용 브라우저 음성인식 병행 시작(미지원 브라우저면 조용히 no-op).
      const stt = new BrowserSttTracker();
      stt.start();
      sttRef.current = stt;
      // 기기별 지원 mimeType 협상(webm/opus → mp4/aac) — WebView 등 webm 미지원 기기 대응.
      const { recorder, format } = createNegotiatedRecorder(mic, "audio");
      formatRef.current = format;
      recorder.ondataavailable = (e) => {
        if (e.data.size > 0) chunksRef.current.push(e.data);
      };
      recorder.start();
      recorderRef.current = recorder;
      setError(null);
      setStatus("recording");
      beep(); // 녹음 시작 신호음
    } catch {
      setError("마이크 접근에 실패했습니다.");
    }
  };

  // 질문이 뜨면 면접관이 읽어주고(TTS), 다 읽으면 자동으로 녹음을 시작한다.
  useEffect(() => {
    if (questionIdx < 0 || status !== "idle" || !questions?.[questionIdx]) return;
    if (typeof window === "undefined" || !window.speechSynthesis) {
      void startRecording(); // TTS 미지원이면 바로 녹음
      return;
    }
    const utter = new SpeechSynthesisUtterance(
      `${questionIdx + 1}번 질문. ${questions[questionIdx].question}`,
    );
    utter.lang = "ko-KR";
    setSpeaking(true);
    utter.onend = () => {
      setSpeaking(false);
      void startRecording(); // 다 읽으면 자동 녹음
    };
    utter.onerror = () => {
      // TTS 실패(WebView 등)해도 탭을 막지 않는다 — 질문은 화면 텍스트로 대신하고 바로 녹음.
      setSpeaking(false);
      void startRecording();
    };
    window.speechSynthesis.cancel();
    window.speechSynthesis.speak(utter);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [questionIdx, status, questions]);

  const startInterview = () => {
    setError(null);
    setNote(null);
    setResults([]);
    setScoreProgress(0);
    recordingsRef.current = [];
    setQuestionIdx(0);
    setStatus("idle");
  };

  /** 답변 완료 → 녹음 blob 모아두고 다음 질문 (마지막이면 일괄 채점). */
  const finishAnswer = async () => {
    if (!session || !questions) return;
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
    // 트래커는 샘플링만 멈추고(지표 확정은 채점 때 전사 글자수로) Recording 으로 이관 — cleanup 의 dispose 대상에서 뺀다.
    const tracker = trackerRef.current;
    tracker?.pause();
    trackerRef.current = null;
    // 브라우저 음성인식 정지 → 누적 전사 확보(serve STT 실패 시 폴백으로 쓴다).
    const webSpeechText = sttRef.current?.stop() ?? "";
    sttRef.current = null;
    cleanup();

    const q = questions[questionIdx];
    if (blob && blob.size > 0 && q) {
      recordingsRef.current.push({
        questionId: q.id,
        question: q.question,
        blob,
        format: formatRef.current,
        tracker,
        webSpeechText,
      });
    } else {
      tracker?.dispose();
    }

    const nextIdx = questionIdx + 1;
    if (nextIdx >= questions.length) {
      await scoreAll();
    } else {
      setQuestionIdx(nextIdx); // → TTS useEffect → 자동 녹음
      setStatus("idle");
    }
  };

  /** 모아둔 답변을 일괄 채점 (STT + 전달력 + 내용) 후 저장. */
  const scoreAll = async () => {
    if (!session) return;
    setStatus("scoring");
    setScoreProgress(0);
    const recs = recordingsRef.current;
    const out: AnswerResult[] = [];
    for (let i = 0; i < recs.length; i++) {
      const rec = recs[i];
      const audioBase64 = await blobToBase64(rec.blob).catch(() => "");
      const audioFormat = rec.format; // 녹음 시 협상한 포맷 (blob.type 스니핑 대체)

      // 1) 전사: serve STT(faster-whisper) 우선, 실패 시 브라우저 음성인식(Web Speech) 폴백.
      let transcript = "";
      try {
        const stt = await transcribeVoice(session.id, audioBase64, audioFormat);
        transcript = stt.text;
      } catch {
        transcript = rec.webSpeechText; // serve 미기동 → 브라우저 STT 폴백
      }
      const chars = transcript.replace(/\s/g, "").length;
      const fillers = transcript ? countFillers([transcript]) : 0;

      // 2) 내용 채점(백엔드 haiku, serve 무관) — 전사만 있으면 호출. serve 죽어도 전사가 있으면 채점된다.
      if (transcript) {
        await scoreVoiceTranscript(session.id, [
          { role: "ai", text: rec.question },
          { role: "user", text: transcript },
        ]).catch(() => undefined);
      }

      // 3) 전달력: serve LightGBM 우선, 실패 시 브라우저가 수집한 음향 지표로 온디바이스 폴백.
      try {
        const server = await scoreVoiceServer(session.id, {
          audioBase64,
          audioFormat,
          transcriptChars: chars,
          fillerCount: fillers,
        });
        out.push({
          questionId: rec.questionId,
          question: rec.question,
          transcript,
          score: server.score,
          detail: server.detail,
          source: server.source,
          contentScore: null,
          contentFeedback: null,
        });
      } catch {
        // serve 미기동 → 온디바이스 규칙점수(피치·성량·반응). 전사 없으면 말속도·군말은 중립 처리.
        const metrics = rec.tracker?.finish(chars, fillers) ?? null;
        const detail = metrics ? computeVoiceScore(metrics) : ZERO_DETAIL;
        out.push({
          questionId: rec.questionId,
          question: rec.question,
          transcript,
          score: detail.overall,
          detail,
          source: metrics ? "browser" : "error",
          contentScore: null,
          contentFeedback: null,
        });
      } finally {
        rec.tracker?.dispose();
      }
      setScoreProgress(i + 1);
    }

    // 내용 채점 점수/피드백 (1회 조회 + questionId 매칭)
    try {
      const review = await getSessionReview(session.id);
      out.forEach((r) => {
        const item = review.items.find((it) => it.questionId === r.questionId);
        if (item) {
          r.contentScore = item.score;
          r.contentFeedback = item.feedback;
        }
      });
    } catch {
      // 조회 실패 — 전달력만 표시
    }
    setResults(out);

    // 결과 저장 (음성은 폐기, 점수/트랜스크립트만)
    const overall = out.length ? Math.round(out.reduce((s, a) => s + a.score, 0) / out.length) : 0;
    try {
      await saveMediaResult(session.id, {
        kind: "VOICE",
        transcript: out.flatMap<TranscriptLine>((a) => [
          { role: "ai", text: a.question },
          { role: "user", text: a.transcript },
        ]),
        metrics: { mode: "local-basic", answers: out.length },
        score: overall,
        scoreDetail: { overall },
      });
      setNote("면접 완료 — 자체 STT·전달력 모델로 채점하고 결과를 저장했습니다 (외부 API 0, 음성은 폐기).");
    } catch (e) {
      setNote(e instanceof Error ? `결과 저장 실패: ${e.message}` : "결과 저장에 실패했습니다.");
    }
    setStatus("done");
  };

  if (tutorialActive) {
    return <TutorialMediaPreview kind="voice" />;
  }

  if (!session) {
    return (
      <div className="rounded-xl border border-dashed border-slate-200 bg-card p-10 text-center text-sm text-slate-400">
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
  const total = questions?.length ?? 0;
  const overall = results.length
    ? Math.round(results.reduce((s, a) => s + a.score, 0) / results.length)
    : 0;

  return (
    <div className="space-y-4">
      <Card className="border border-slate-200 bg-card">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <Mic className="size-4 text-emerald-600" />
            자체 AI 음성 면접
            <Badge className="bg-emerald-100 text-emerald-700">베이직 · 무료</Badge>
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <p className="text-sm text-slate-500">
            면접관이 질문을 읽어주면 답변을 녹음하세요(다 읽으면 자동으로 녹음 시작). 6문제를 마치면 자체 AI가
            한 번에 채점합니다(말 속도·톤·내용). 외부 API 없이 동작합니다.
          </p>

          {serveOff && (
            <p className="rounded-lg bg-amber-50 p-3 text-sm text-amber-700">
              자체 추론 서버(serve)가 꺼져 있어 <b>전달력만 브라우저에서 채점</b>합니다(전사·내용 채점은 없음).
              전체 기능은 ml/interview-nonverbal/run-serve.bat 기동 후 이용하세요.
            </p>
          )}

          {handoffReason === "no-microphone" && (
            <RemoteMicConnectCard sessionId={session.id} onStream={setRemoteMic} />
          )}
          {handoffReason && !remoteMic && (
            <DeviceHandoffCard sessionId={session.id} reason={handoffReason} />
          )}

          {!started && canProceed && (
            <label className="flex items-start gap-2 rounded-lg bg-slate-50 p-3 text-xs text-slate-600">
              <input
                type="checkbox"
                checked={consent}
                onChange={(e) => setConsent(e.target.checked)}
                className="mt-0.5"
              />
              <span>
                녹음된 음성이 분석 서버로 전송되어 자체 모델로 채점됩니다. 분석 후 음성은 즉시 폐기되며 채점 결과만 저장합니다.
              </span>
            </label>
          )}

          {started && status !== "done" && status !== "scoring" && questions && (
            <div className="space-y-1">
              <div className="flex items-center justify-between text-xs text-slate-500">
                <span>질문 {questionIdx + 1} / {total}</span>
                {!ttsAvailable && (
                  <span className="text-emerald-600">음성 안내 미지원 — 질문을 읽고 답변하세요</span>
                )}
                {speaking && (
                  <span className="flex items-center gap-1 text-emerald-600">
                    <Volume2 className="size-3 animate-pulse" /> 질문 읽는 중…
                  </span>
                )}
                {status === "recording" && (
                  <span className="flex items-center gap-1 text-rose-600">
                    <span className="size-2 animate-pulse rounded-full bg-rose-500" /> 녹음 중
                  </span>
                )}
              </div>
              <Progress value={(questionIdx / total) * 100} className="h-1.5" />
              {/* TTS 미지원 기기에서는 질문을 강조 표시해 텍스트로 대신한다. */}
              <p
                className={
                  ttsAvailable
                    ? "rounded-lg bg-slate-50 p-3 text-sm font-medium text-slate-700"
                    : "rounded-lg border-l-4 border-emerald-400 bg-emerald-50 p-3 text-base font-semibold text-slate-800"
                }
              >
                Q{questionIdx + 1}. {questions[questionIdx]?.question}
              </p>
            </div>
          )}

          {status === "recording" && (
            <div className="flex flex-wrap items-center justify-center gap-x-4 gap-y-2 rounded-lg bg-rose-50 py-3">
              <span className="flex items-center gap-2 text-base font-bold text-rose-700">
                <Mic className="size-5" /> 지금 답변하세요
              </span>
              {/* 실제 마이크 입력 레벨 — 내 목소리가 들어가고 있는지 즉시 확인 */}
              <MicLevelMeter stream={micStream} bars={14} className="h-6 gap-[3px] text-rose-500" />
            </div>
          )}

          {status === "scoring" && (
            <div className="space-y-1">
              <p className="flex items-center gap-2 text-sm text-slate-600">
                <Loader2 className="size-4 animate-spin" /> 채점 중… {scoreProgress}/{total}
              </p>
              <Progress value={total ? (scoreProgress / total) * 100 : 0} className="h-1.5" />
            </div>
          )}

          {supported && canProceed && (
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
                <Button onClick={() => void startRecording()} className="gap-1.5 bg-rose-600 hover:bg-rose-700">
                  <Mic className="size-4" /> {speaking ? "바로 답변하기" : "답변하기"}
                </Button>
              )}
              {status === "recording" && (
                <Button onClick={() => void finishAnswer()} variant="destructive" className="gap-1.5">
                  <Square className="size-4" /> 답변 완료
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

      {results.length > 0 && (
        <Card className="border border-slate-200 bg-card">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              답변별 채점 (내용 · 전달력)
              <span className={`ml-auto text-2xl font-black ${getScoreColor(overall)}`}>
                {overall}
                <span className="text-sm font-bold text-slate-400">/100</span>
              </span>
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            {results.map((a, i) => (
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
