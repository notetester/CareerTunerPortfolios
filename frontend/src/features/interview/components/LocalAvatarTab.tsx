import { useEffect, useRef, useState } from "react";
import { ClipboardList, Download, Loader2, Lock, Maximize2, PhoneOff, Play, SkipForward, Video } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Progress } from "@/app/components/ui/progress";
import {
  getMediaCapabilities,
  getSessionReview,
  listSessionQuestions,
  saveMediaResult,
  scoreAvatarServer,
  scoreVoiceTranscript,
  transcribeVoice,
} from "../api/interviewApi";
import {
  blobToBase64,
  computeVoiceScore,
  countFillers,
  VoiceMetricsTracker,
} from "../hooks/voiceAnalysis";
import { computeVisualScore, VisualMetricsTracker, type VisualScoreDetail } from "../hooks/visualAnalysis";
import type {
  InterviewQuestion,
  InterviewSession,
  MediaCapabilities,
  SessionReviewItem,
  TranscriptLine,
  VoiceMetrics,
  VoiceScoreDetail,
} from "../types/interview";
import { getScoreColor } from "../types/interview";
import { VoiceScorePanel } from "./VoiceScorePanel";
import { useTutorialStore } from "../tutorial/tutorialStore";
import { TutorialMediaPreview } from "../tutorial/TutorialMediaPreview";
import interviewerPlaceholder from "../assets/interviewer-placeholder.png";

type Status = "idle" | "connecting" | "live" | "analyzing" | "scored" | "error";

/**
 * 베이직(무료) 자체 AI 화상 면접 (브라우저 TTS + MediaPipe).
 * 외부 아바타 API(HeyGen) 없이, 면접관 placeholder 위에서 브라우저 음성합성(speechSynthesis)이
 * 준비된 질문을 읽어준다. 유저 웹캠 영상은 동의 시 자체 추론 서버(serve)로 전송해 표정·자세·음성을
 * 채점하고(late fusion, ADR-006/007), 미동의/서버 미기동 시 온디바이스(MediaPipe)로 폴백한다.
 * 어느 경로든 원본 영상은 점수 산출 후 폐기되고 저장은 점수(JSON)만, 원하면 로컬 다운로드.
 */
export function LocalAvatarTab({ session }: { session: InterviewSession | null }) {
  const tutorialActive = useTutorialStore((s) => s.mode !== "off");
  const [status, setStatus] = useState<Status>("idle");
  const [error, setError] = useState<string | null>(null);
  const [note, setNote] = useState<string | null>(null);
  const [capabilities, setCapabilities] = useState<MediaCapabilities | null>(null);
  const [preparedQuestions, setPreparedQuestions] = useState<InterviewQuestion[] | null>(null);

  const [questions, setQuestions] = useState<string[]>([]);
  const [questionIdx, setQuestionIdx] = useState(-1); // -1 = 아직 첫 질문 전
  const [avatarTalking, setAvatarTalking] = useState(false);

  const [voiceDetail, setVoiceDetail] = useState<VoiceScoreDetail | null>(null);
  const [voiceMetrics, setVoiceMetrics] = useState<VoiceMetrics | null>(null);
  const [visualDetail, setVisualDetail] = useState<VisualScoreDetail | null>(null);
  const [overall, setOverall] = useState<number | null>(null);
  const [contentScore, setContentScore] = useState<number | null>(null);
  const [contentItems, setContentItems] = useState<SessionReviewItem[]>([]);
  const [downloadUrl, setDownloadUrl] = useState<string | null>(null);
  const [consent, setConsent] = useState(true); // 정밀 분석(원본 영상 서버 전송) 동의

  const videoBoxRef = useRef<HTMLDivElement | null>(null);
  const selfVideoRef = useRef<HTMLVideoElement | null>(null);
  const webcamRef = useRef<MediaStream | null>(null);
  const voiceTrackerRef = useRef<VoiceMetricsTracker | null>(null);
  const visualTrackerRef = useRef<VisualMetricsTracker | null>(null);
  const recorderRef = useRef<MediaRecorder | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const questionsRef = useRef<string[]>([]);
  const finishingRef = useRef(false);

  const supported =
    typeof navigator !== "undefined" &&
    !!navigator.mediaDevices &&
    typeof window !== "undefined" &&
    "MediaRecorder" in window &&
    "speechSynthesis" in window;

  // 준비된 질문(게이트) + capabilities 로드.
  useEffect(() => {
    if (!session) return;
    listSessionQuestions(session.id)
      .then((qs) => setPreparedQuestions(qs.filter((q) => q.parentQuestionId == null)))
      .catch(() => setPreparedQuestions([]));
    getMediaCapabilities()
      .then(setCapabilities)
      .catch(() => setCapabilities(null));
  }, [session]);

  useEffect(() => {
    return () => {
      cleanup();
      if (downloadUrl) URL.revokeObjectURL(downloadUrl);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const cleanup = () => {
    recorderRef.current?.state === "recording" && recorderRef.current.stop();
    recorderRef.current = null;
    voiceTrackerRef.current?.dispose();
    voiceTrackerRef.current = null;
    visualTrackerRef.current?.dispose();
    visualTrackerRef.current = null;
    if (typeof window !== "undefined" && "speechSynthesis" in window) {
      window.speechSynthesis.cancel();
    }
    webcamRef.current?.getTracks().forEach((t) => t.stop());
    webcamRef.current = null;
  };

  const start = async () => {
    if (!session) return;
    setStatus("connecting");
    setError(null);
    setNote(null);
    setVoiceDetail(null);
    setVoiceMetrics(null);
    setVisualDetail(null);
    setOverall(null);
    setContentScore(null);
    setContentItems([]);
    setQuestionIdx(-1);
    finishingRef.current = false;
    if (downloadUrl) {
      URL.revokeObjectURL(downloadUrl);
      setDownloadUrl(null);
    }
    try {
      // 1) 준비된 본질문 목록을 텍스트 배열로 로드 (베이직: 전체 질문, 최대 6).
      const qs = await listSessionQuestions(session.id);
      const mainQuestions = qs
        .filter((q) => q.parentQuestionId == null)
        .slice(0, 6)
        .map((q) => q.question);
      questionsRef.current = mainQuestions;
      setQuestions(mainQuestions);

      // 2) 웹캠 + 온디바이스 분석 준비.
      const webcam = await navigator.mediaDevices.getUserMedia({ video: true, audio: true });
      webcamRef.current = webcam;
      if (selfVideoRef.current) selfVideoRef.current.srcObject = webcam;

      const voiceTracker = new VoiceMetricsTracker();
      voiceTracker.start(webcam);
      voiceTrackerRef.current = voiceTracker;

      chunksRef.current = [];
      const recorder = new MediaRecorder(webcam);
      recorder.ondataavailable = (e) => {
        if (e.data.size > 0) chunksRef.current.push(e.data);
      };
      recorder.start();
      recorderRef.current = recorder;

      // 표정/자세 분석은 모델 로드 실패해도 면접 자체는 계속한다.
      const visualTracker = new VisualMetricsTracker();
      visualTrackerRef.current = visualTracker;
      if (selfVideoRef.current) {
        visualTracker.start(selfVideoRef.current).catch(() => {
          visualTrackerRef.current = null;
          setNote((prev) => prev ?? "표정/자세 분석 모델을 불러오지 못해 음성 지표만으로 채점합니다.");
        });
      }

      setStatus("live");
    } catch (err) {
      cleanup();
      setError(err instanceof Error ? err.message : "화상 면접 시작에 실패했습니다.");
      setStatus("error");
    }
  };

  /** 브라우저 TTS로 다음 질문을 읽어준다. 첫 호출이면 인사 + 1번 질문. */
  const askNext = () => {
    if (typeof window === "undefined" || !("speechSynthesis" in window)) return;
    const nextIdx = questionIdx + 1;
    if (nextIdx >= questionsRef.current.length) {
      void finishInterview();
      return;
    }
    const text =
      nextIdx === 0
        ? `안녕하세요, 면접을 시작하겠습니다. 첫 번째 질문입니다. ${questionsRef.current[0]}`
        : questionsRef.current[nextIdx];
    try {
      window.speechSynthesis.cancel();
      const u = new SpeechSynthesisUtterance(text);
      u.lang = "ko-KR";
      u.onstart = () => setAvatarTalking(true);
      u.onend = () => {
        setAvatarTalking(false);
        // 질문이 끝났다 → 답변 반응 지연 측정 시작.
        voiceTrackerRef.current?.markAiSpeechEnd();
      };
      window.speechSynthesis.speak(u);
      setQuestionIdx(nextIdx);
    } catch {
      setNote("질문 음성 합성에 실패했습니다. 다시 시도해 주세요.");
    }
  };

  /** 종료 → 온디바이스 분석 확정 → 종합 점수 저장. */
  const finishInterview = async () => {
    if (!session || finishingRef.current) return;
    finishingRef.current = true;
    setStatus("analyzing");

    // 측정 먼저 멈춰서 분석 대기 시간이 지표에 섞이지 않게 한다.
    voiceTrackerRef.current?.pause();
    const visualMetrics = visualTrackerRef.current?.finish() ?? null;

    const recordedBlob = await new Promise<Blob | null>((resolve) => {
      const recorder = recorderRef.current;
      if (!recorder || recorder.state !== "recording") {
        resolve(chunksRef.current.length > 0 ? new Blob(chunksRef.current) : null);
        return;
      }
      recorder.onstop = () =>
        resolve(chunksRef.current.length > 0 ? new Blob(chunksRef.current, { type: recorder.mimeType }) : null);
      recorder.stop();
    });
    recorderRef.current = null;

    if (typeof window !== "undefined" && "speechSynthesis" in window) {
      window.speechSynthesis.cancel();
    }

    // 원본 영상은 업로드하지 않고, 원하면 로컬 다운로드만 제공.
    if (recordedBlob && recordedBlob.size > 0) {
      setDownloadUrl(URL.createObjectURL(recordedBlob));
    }

    // 음성 답변 전사 (자체 STT, faster-whisper). 실패해도 전달력 지표로 진행.
    let userTranscript = "";
    if (recordedBlob && recordedBlob.size > 0) {
      try {
        const audioBase64 = await blobToBase64(recordedBlob);
        const audioFormat = (recordedBlob.type || "audio/webm").includes("webm") ? "webm" : "wav";
        const stt = await transcribeVoice(session.id, audioBase64, audioFormat);
        userTranscript = stt.text ?? "";
      } catch {
        setNote((prev) => prev ?? "음성 전사(STT)에 실패해 전달력 지표로만 채점했습니다.");
      }
    }

    const userChars = userTranscript.replace(/\s/g, "").length;
    const fillers = userTranscript ? countFillers([userTranscript]) : 0;
    const vMetrics = voiceTrackerRef.current?.finish(userChars, fillers) ?? null;

    const askedQuestions = questionsRef.current.slice(0, Math.max(questionIdx + 1, 0));
    const transcript: TranscriptLine[] = [
      ...askedQuestions.map<TranscriptLine>((q) => ({ role: "ai", text: q })),
      ...(userTranscript ? [{ role: "user" as const, text: userTranscript }] : []),
    ];

    // 온디바이스 점수는 항상 계산(폴백 + 지표 표시용). 동의 + serve 가용 시 자체 추론 점수로 교체.
    const vLocal = vMetrics ? computeVoiceScore(vMetrics) : null;
    const visLocal = visualMetrics ? computeVisualScore(visualMetrics) : null;
    let vDetail = vLocal;
    let visDetail = visLocal;
    let source = "browser";

    if (consent && capabilities?.nonverbal && recordedBlob && recordedBlob.size > 0) {
      try {
        const videoBase64 = await blobToBase64(recordedBlob);
        const videoFormat = (recordedBlob.type || "video/webm").includes("webm") ? "webm" : "mp4";
        const server = await scoreAvatarServer(session.id, {
          videoBase64,
          videoFormat,
          transcriptChars: userChars,
          fillerCount: fillers,
          latencySec: vMetrics?.avgResponseLatencySec ?? undefined,
        });
        // 음성/영상 각각 별 모델 점수(late fusion). 한쪽 실패면 그쪽만 온디바이스 폴백 유지.
        vDetail = server.voice?.detail ?? vLocal;
        visDetail = server.visual?.detail ?? visLocal;
        source = server.visual?.source ?? server.voice?.source ?? "rule";
        setNote((prev) => prev ?? `자체 추론 서버로 채점했습니다 (${source}).`);
      } catch {
        setNote((prev) => prev ?? "자체 추론 서버 호출 실패 — 온디바이스 지표로 채점했습니다.");
      }
    }

    // 답변 "내용" 채점: 트랜스크립트 → 질문별 haiku 채점 → 저장된 점수 조회해 표시.
    let contentAvg: number | null = null;
    if (transcript.some((l) => l.role === "user")) {
      try {
        const scored = await scoreVoiceTranscript(session.id, transcript);
        if (scored > 0) {
          const review = await getSessionReview(session.id);
          const answered = review.items.filter((it) => it.score != null && !!it.answerText?.trim());
          if (answered.length > 0) {
            contentAvg = Math.round(
              answered.reduce((s, it) => s + (it.score ?? 0), 0) / answered.length,
            );
            setContentItems(answered);
            setContentScore(contentAvg);
          }
        }
      } catch {
        // 내용 채점 실패는 전달력·영상 점수에 영향 없음.
      }
    }

    // 종합 = 답변 내용 + 음성 전달력 + 영상(표정·자세) 가중 평균(가용 항목만 정규화).
    const weighted: Array<[number, number]> = [];
    if (contentAvg != null) weighted.push([contentAvg, 0.5]);
    if (vDetail) weighted.push([vDetail.overall, 0.25]);
    if (visDetail) weighted.push([visDetail.overall, 0.25]);
    const totalW = weighted.reduce((s, [, w]) => s + w, 0);
    const combined =
      totalW > 0 ? Math.round(weighted.reduce((s, [sc, w]) => s + sc * w, 0) / totalW) : 0;

    setVoiceMetrics(vMetrics);
    setVoiceDetail(vDetail);
    setVisualDetail(visDetail);
    setOverall(combined);

    cleanup();

    try {
      await saveMediaResult(session.id, {
        kind: "AVATAR",
        transcript: transcript.length > 0 ? transcript : null,
        metrics: { voice: vMetrics, visual: visualMetrics, source },
        score: combined,
        scoreDetail: {
          ...(vDetail ? { ...vDetail, voiceOverall: vDetail.overall } : {}),
          ...(visDetail ? { ...visDetail, visualOverall: visDetail.overall } : {}),
          ...(contentAvg != null ? { contentScore: contentAvg } : {}),
          overall: combined,
        },
      });
      setNote((prev) => prev ?? "분석 결과가 저장되었습니다. (원본 영상은 서버에 저장하지 않습니다)");
    } catch (err) {
      setNote(err instanceof Error ? `결과 저장 실패: ${err.message}` : "결과 저장에 실패했습니다.");
    }

    setStatus("scored");
  };

  // 튜토리얼: 실제 웹캠 연결 대신 예시 결과 화면만 보여준다.
  if (tutorialActive) {
    return <TutorialMediaPreview kind="avatar" />;
  }

  if (!session) {
    return (
      <div className="rounded-xl border border-dashed border-slate-200 bg-card p-10 text-center text-sm text-slate-400">
        "면접 모드 선택" 탭에서 지원 건과 모드를 고르고 면접을 시작하면 자체 AI 화상 면접을 진행할 수 있습니다.
      </div>
    );
  }

  if (preparedQuestions != null && preparedQuestions.length === 0) {
    return (
      <div className="rounded-xl border border-dashed border-amber-200 bg-amber-50 p-10 text-center">
        <ClipboardList className="mx-auto size-8 text-amber-500" />
        <p className="mt-3 text-sm font-semibold text-amber-800">준비된 면접 질문이 없습니다</p>
        <p className="mt-1 text-sm text-amber-700">
          "예상 면접 질문" 탭에서 질문을 먼저 생성하면, 자체 AI 면접관이 그 질문으로 화상 면접을 진행합니다.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <Card className="border border-slate-200 bg-card">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <Video className="size-4 text-purple-600" />
            자체 AI 화상 면접
            {status === "live" ? (
              <Badge className="gap-1 bg-purple-100 text-purple-700">
                <span className="size-2 animate-pulse rounded-full bg-purple-500" /> LIVE · 브라우저 TTS · 자체 채점
              </Badge>
            ) : (
              <Badge className="bg-slate-100 text-slate-600">베이직 · 브라우저 TTS · 자체 채점</Badge>
            )}
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <p className="text-sm text-slate-500">
            면접관이 준비된 질문 {preparedQuestions ? Math.min(preparedQuestions.length, 6) : 6}개를 읽어주면
            웹캠으로 녹화·분석합니다. 외부 API 없이 무료.
          </p>

          {!supported && (
            <p className="rounded-lg bg-amber-50 p-3 text-sm text-amber-700">
              이 브라우저는 웹캠 녹화 또는 음성 합성을 지원하지 않습니다. 최신 Chrome/Edge 에서 이용해 주세요.
            </p>
          )}

          {/* 화면: 면접관 placeholder(메인) + 내 웹캠(서브) */}
          {(status === "connecting" || status === "live" || status === "analyzing") && (
            <div
              ref={videoBoxRef}
              className="relative overflow-hidden rounded-xl border border-slate-200 bg-white"
            >
              <img
                src={interviewerPlaceholder}
                alt="면접관"
                className="aspect-video w-full object-contain"
              />
              <div className="pointer-events-none absolute inset-x-0 top-0 flex justify-center px-3 pt-3">
                <span className="flex items-center gap-1.5 rounded-full bg-black/55 px-4 py-1.5 text-sm font-bold text-white shadow-lg backdrop-blur">
                  <Lock className="size-4 text-amber-300" /> 실제 면접관 아바타는 프리미엄 전용
                </span>
              </div>
              <video
                ref={selfVideoRef}
                autoPlay
                muted
                playsInline
                className="absolute bottom-3 right-3 w-1/4 rounded-lg border-2 border-white/40 object-cover"
              />
              <Button
                type="button"
                size="sm"
                variant="secondary"
                onClick={() => {
                  if (document.fullscreenElement) void document.exitFullscreen();
                  else void videoBoxRef.current?.requestFullscreen();
                }}
                className="absolute right-3 top-3 gap-1 opacity-80 hover:opacity-100"
              >
                <Maximize2 className="size-4" /> 전체화면
              </Button>
              {status === "connecting" && (
                <div className="absolute inset-0 flex items-center justify-center text-sm text-slate-300">
                  <Loader2 className="mr-2 size-4 animate-spin" /> 면접 준비 중…
                </div>
              )}
            </div>
          )}

          {/* 진행 표시 */}
          {status === "live" && questions.length > 0 && (
            <div className="space-y-1">
              <div className="flex items-center justify-between text-xs text-slate-500">
                <span>
                  질문 {Math.max(questionIdx + 1, 0)} / {questions.length}
                  {avatarTalking && " · 면접관이 말하는 중…"}
                </span>
              </div>
              <Progress value={((questionIdx + 1) / questions.length) * 100} className="h-1.5" />
              {questionIdx >= 0 && (
                <p className="rounded-lg bg-slate-50 p-3 text-sm font-medium text-slate-700">
                  Q{questionIdx + 1}. {questions[questionIdx]}
                </p>
              )}
            </div>
          )}

          {supported && capabilities?.nonverbal && (status === "idle" || status === "scored") && (
            <label className="flex items-start gap-2 rounded-lg border border-slate-200 bg-slate-50 p-3 text-xs text-slate-600">
              <input
                type="checkbox"
                checked={consent}
                onChange={(e) => setConsent(e.target.checked)}
                className="mt-0.5 size-3.5"
              />
              <span>
                <span className="font-semibold text-slate-700">정밀 분석(자체 AI)</span> — 원본 영상을 분석
                서버로 전송해 자체 모델(표정·자세·음성)로 채점합니다. 분석 후 영상은 즉시 폐기되며 서버에
                저장하지 않습니다. 끄면 브라우저 온디바이스 지표로만 채점합니다.
              </span>
            </label>
          )}

          {supported && (
            <div className="flex flex-wrap items-center gap-2">
              {(status === "idle" || status === "scored" || status === "error") && (
                <Button
                  onClick={start}
                  disabled={preparedQuestions == null}
                  className="gap-1.5 bg-purple-600 hover:bg-purple-700"
                >
                  <Video className="size-4" /> {status === "idle" ? "면접 시작" : "다시 시작"}
                </Button>
              )}
              {status === "connecting" && (
                <Button disabled className="gap-1.5">
                  <Loader2 className="size-4 animate-spin" /> 준비 중…
                </Button>
              )}
              {status === "analyzing" && (
                <Button disabled className="gap-1.5">
                  <Loader2 className="size-4 animate-spin" /> 영상·음성 분석 중…
                </Button>
              )}
              {status === "live" && (
                <>
                  <Button onClick={askNext} disabled={avatarTalking} className="gap-1.5">
                    {questionIdx < 0 ? <Play className="size-4" /> : <SkipForward className="size-4" />}
                    {questionIdx < 0
                      ? "첫 질문 시작"
                      : questionIdx + 1 >= questions.length
                        ? "마지막 답변 완료"
                        : "답변 완료 · 다음 질문"}
                  </Button>
                  <Button onClick={() => void finishInterview()} variant="destructive" className="gap-1.5">
                    <PhoneOff className="size-4" /> 면접 종료
                  </Button>
                </>
              )}
              {status === "scored" && downloadUrl && (
                <Button asChild variant="outline" className="gap-1.5">
                  <a href={downloadUrl} download="local-avatar-interview.webm">
                    <Download className="size-4" /> 내 답변 영상 다운로드
                  </a>
                </Button>
              )}
            </div>
          )}

          {error && <p className="text-sm text-red-500">{error}</p>}
          {note && <p className="text-xs font-semibold text-slate-500">{note}</p>}
        </CardContent>
      </Card>

      {/* 종합 점수 (답변 내용 + 음성 + 영상) */}
      {status === "scored" && overall != null && (
        <Card className="border border-slate-200 bg-card">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              종합 점수
              <span className={`ml-auto text-2xl font-black ${getScoreColor(overall)}`}>
                {overall}
                <span className="text-sm font-bold text-slate-400">/100</span>
              </span>
            </CardTitle>
          </CardHeader>
          <CardContent className="flex flex-wrap gap-x-3 gap-y-1 text-xs text-slate-500">
            {contentScore != null && <span>답변 내용 {contentScore}</span>}
            {voiceDetail && <span>· 음성 전달력 {voiceDetail.overall}</span>}
            {visualDetail && <span>· 영상(표정·자세) {visualDetail.overall}</span>}
          </CardContent>
        </Card>
      )}

      {/* 답변 내용 점수 (전사 → haiku 채점) */}
      {status === "scored" && contentScore != null && (
        <Card className="border border-slate-200 bg-card">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              답변 내용 점수
              <span className={`ml-auto text-2xl font-black ${getScoreColor(contentScore)}`}>
                {contentScore}
                <span className="text-sm font-bold text-slate-400">/100</span>
              </span>
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            {contentItems.map((it) => (
              <div key={it.questionId} className="rounded-lg border border-slate-100 bg-slate-50 p-3">
                <div className="mb-1 flex items-baseline justify-between gap-2 text-sm">
                  <span className="font-semibold text-slate-700">{it.question}</span>
                  {it.score != null && (
                    <span className={`shrink-0 font-bold ${getScoreColor(it.score)}`}>{it.score}</span>
                  )}
                </div>
                {it.answerText && (
                  <p className="mb-1 whitespace-pre-line text-xs text-slate-500">{it.answerText}</p>
                )}
                {it.feedback && <p className="text-[11px] text-slate-400">{it.feedback}</p>}
              </div>
            ))}
          </CardContent>
        </Card>
      )}

      {/* 영상 점수 (표정·시선·자세·화면 유지) */}
      {status === "scored" && visualDetail && (
        <Card className="border border-slate-200 bg-card">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              영상 점수
              <span className={`ml-auto text-2xl font-black ${getScoreColor(visualDetail.overall)}`}>
                {visualDetail.overall}
                <span className="text-sm font-bold text-slate-400">/100</span>
              </span>
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            {(
              [
                ["expression", "표정", "자연스러운 미소, 긴장(미간) 최소화"],
                ["gaze", "시선 처리", "카메라(면접관) 응시 유지"],
                ["posture", "자세", "수평 어깨, 차분한 상체"],
                ["presence", "화면 유지", "얼굴이 화면에 안정적으로 잡힘"],
              ] as const
            ).map(([key, label, desc]) => (
              <div key={key}>
                <div className="mb-1 flex items-baseline justify-between text-sm">
                  <span className="font-semibold text-slate-700">{label}</span>
                  <span className={`font-bold ${getScoreColor(visualDetail[key])}`}>{visualDetail[key]}</span>
                </div>
                <Progress value={visualDetail[key]} className="h-2" />
                <p className="mt-0.5 text-[11px] text-slate-400">{desc}</p>
              </div>
            ))}
          </CardContent>
        </Card>
      )}

      {status === "scored" && voiceDetail && voiceMetrics && (
        <VoiceScorePanel detail={voiceDetail} metrics={voiceMetrics} title="음성 점수" />
      )}
    </div>
  );
}
