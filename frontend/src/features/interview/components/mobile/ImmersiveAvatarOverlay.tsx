import { useEffect, useRef, useState } from "react";
import { X, SwitchCamera, Square, Mic, MicOff } from "lucide-react";
import {
  blobToBase64,
  computeVoiceScore,
  countFillers,
  VoiceMetricsTracker,
} from "../../hooks/voiceAnalysis";
import { computeVisualScore, VisualMetricsTracker } from "../../hooks/visualAnalysis";
import { BrowserSttTracker } from "../../hooks/speechToText";
import { createNegotiatedRecorder } from "../../hooks/mediaSupport";
import { scoreAvatarServer, transcribeVoice } from "../../api/interviewApi";
import {
  MOBILE_INTERVIEW_AUDIO_BITS_PER_SECOND,
  MOBILE_INTERVIEW_VIDEO_BITS_PER_SECOND,
  MOBILE_INTERVIEW_VIDEO_MAX_SECONDS,
  validateCapturedMediaSize,
} from "../../lib/mobileSubmission";
import {
  captureAppLockGeneration,
  isAppLockGenerationCurrent,
  keepStreamForAppLock,
  onAppLockState,
} from "@/platform/appLockEvents";
import { registerNativeOverlayLifecycle } from "@/platform/nativeOverlayLifecycle";
import { MediaCaptureExitConfirm } from "./MediaCaptureExitConfirm";

/**
 * 몰입형 화상 답변 (모바일 풀스크린) — 카메라 프리뷰 전체화면 + 질문 오버레이.
 * 진입 즉시 녹화 시작 → 정지 → 전사(serve, 폴백 브라우저 STT)
 * → 비언어 채점(serve late-fusion, 폴백 온디바이스 MediaPipe/음향 지표)
 * → onResult 로 전사·점수·원본 Blob을 부모에 넘긴다. 부모가 INTERVIEW_ANSWER
 * pending 파일로 저장하고 표준 answers 요청 성공 시 원자 연결한다.
 */
export interface AvatarResult {
  transcript: string;
  voiceScore: number | null;
  visualScore: number | null;
  videoBlob: Blob | null;
  videoFormat: string;
  captureError?: "VIDEO_TOO_LARGE";
}

type Phase = "recording" | "processing";

export function ImmersiveAvatarOverlay({
  sessionId,
  questionText,
  questionLabel,
  onResult,
  onClose,
}: {
  sessionId: number;
  questionText: string;
  questionLabel: string;
  onResult: (r: AvatarResult) => void;
  onClose: () => void;
}) {
  const [phase, setPhase] = useState<Phase>("recording");
  const [seconds, setSeconds] = useState(0);
  const [muted, setMuted] = useState(false);
  const [facing, setFacing] = useState<"user" | "environment">("user");
  const [error, setError] = useState<string | null>(null);
  const [poseNote, setPoseNote] = useState<string | null>(null);
  const [confirmClose, setConfirmClose] = useState(false);

  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const recorderRef = useRef<MediaRecorder | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const voiceTrackerRef = useRef<VoiceMetricsTracker | null>(null);
  const visualTrackerRef = useRef<VisualMetricsTracker | null>(null);
  const sttRef = useRef<BrowserSttTracker | null>(null);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const closedRef = useRef(false);
  const mutedRef = useRef(false);
  const captureAttemptRef = useRef(0);
  const captureGenerationRef = useRef<number | null>(null);
  const processingAbortRef = useRef<AbortController | null>(null);
  const finishRef = useRef<() => void>(() => undefined);
  const closeRef = useRef<() => void>(() => undefined);
  const nativeBackRef = useRef<() => void>(() => undefined);
  const confirmCloseRef = useRef(false);
  /** 녹화 시 협상된 업로드 포맷(webm|mp4) — blob.type 스니핑 대신 이 값을 쓴다. */
  const formatRef = useRef<string>("webm");

  const stopAll = () => {
    if (timerRef.current) clearInterval(timerRef.current);
    timerRef.current = null;
    const recorder = recorderRef.current;
    recorderRef.current = null;
    if (recorder && recorder.state !== "inactive") {
      recorder.ondataavailable = null;
      recorder.onstop = null;
      try {
        recorder.stop();
      } catch {
        /* 이미 종료 중이면 무시 */
      }
    }
    streamRef.current?.getTracks().forEach((t) => t.stop());
    streamRef.current = null;
    chunksRef.current = [];
  };

  const disposeTrackers = () => {
    sttRef.current?.stop();
    sttRef.current = null;
    voiceTrackerRef.current?.dispose();
    voiceTrackerRef.current = null;
    visualTrackerRef.current?.dispose();
    visualTrackerRef.current = null;
  };

  const close = () => {
    if (closedRef.current) return;
    closedRef.current = true;
    captureAttemptRef.current += 1;
    processingAbortRef.current?.abort();
    processingAbortRef.current = null;
    disposeTrackers();
    stopAll();
    setConfirmClose(false);
    onClose();
  };

  closeRef.current = close;
  confirmCloseRef.current = confirmClose;
  nativeBackRef.current = () => {
    if (confirmCloseRef.current) setConfirmClose(false);
    else setConfirmClose(true);
  };

  /** 스트림 시작(+플립 시 재시작 — 녹화도 새로 시작한다). */
  const startCapture = async (face: "user" | "environment") => {
    const captureAttempt = ++captureAttemptRef.current;
    const lockGeneration = captureAppLockGeneration();
    if (lockGeneration === null) return;
    captureGenerationRef.current = lockGeneration;
    disposeTrackers();
    stopAll();
    setSeconds(0);
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: face },
        audio: true,
      });
      if (
        closedRef.current
        || captureAttempt !== captureAttemptRef.current
        || !keepStreamForAppLock(stream, lockGeneration)
      ) {
        stream.getTracks().forEach((t) => t.stop());
        return;
      }
      streamRef.current = stream;
      stream.getAudioTracks().forEach((track) => {
        track.enabled = !mutedRef.current;
      });
      if (videoRef.current) videoRef.current.srcObject = stream;

      const voiceTracker = new VoiceMetricsTracker();
      voiceTracker.start(stream);
      voiceTracker.markAiSpeechEnd();
      voiceTrackerRef.current = voiceTracker;

      const stt = new BrowserSttTracker();
      stt.start();
      sttRef.current = stt;

      chunksRef.current = [];
      // 기기별 지원 mimeType 협상(webm → mp4) — WebView 등 webm 미지원 기기 대응.
      const { recorder, format } = createNegotiatedRecorder(stream, "video", {
        videoBitsPerSecond: MOBILE_INTERVIEW_VIDEO_BITS_PER_SECOND,
        audioBitsPerSecond: MOBILE_INTERVIEW_AUDIO_BITS_PER_SECOND,
      });
      formatRef.current = format;
      recorder.ondataavailable = (e) => {
        if (
          captureAttempt === captureAttemptRef.current
          && isAppLockGenerationCurrent(lockGeneration)
          && e.data.size > 0
        ) chunksRef.current.push(e.data);
      };
      recorder.start();
      recorderRef.current = recorder;

      // 표정/자세(MediaPipe) — 모델 로드 실패해도 면접은 계속.
      const visualTracker = new VisualMetricsTracker();
      visualTrackerRef.current = visualTracker;
      if (videoRef.current) {
        void visualTracker.start(videoRef.current).then(() => {
          if (
            captureAttempt !== captureAttemptRef.current
            || !isAppLockGenerationCurrent(lockGeneration)
          ) visualTracker.dispose();
        }).catch(() => {
          if (visualTrackerRef.current === visualTracker) visualTrackerRef.current = null;
          if (captureAttempt === captureAttemptRef.current && !closedRef.current) {
            setPoseNote("표정·자세 분석 모델 미로드 — 음성 지표만 채점");
          }
        });
      }

      timerRef.current = setInterval(() => setSeconds((s) => s + 1), 1000);
      setError(null);
    } catch {
      if (
        captureAttempt === captureAttemptRef.current
        && !closedRef.current
        && isAppLockGenerationCurrent(lockGeneration)
      ) setError("카메라 접근에 실패했습니다. 권한을 확인해 주세요.");
    }
  };

  useEffect(() => {
    closedRef.current = false;
    void startCapture("user");
    return () => {
      closedRef.current = true;
      captureAttemptRef.current += 1;
      processingAbortRef.current?.abort();
      processingAbortRef.current = null;
      disposeTrackers();
      stopAll();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => onAppLockState((locked) => {
    if (locked) closeRef.current();
  }), []);

  useEffect(() => registerNativeOverlayLifecycle({
    onBack: () => nativeBackRef.current(),
    onSuspend: () => closeRef.current(),
  }), []);

  const flip = () => {
    if (phase !== "recording") return;
    const next = facing === "user" ? "environment" : "user";
    setFacing(next);
    setPoseNote("카메라 전환 — 답변 녹화를 새로 시작합니다");
    void startCapture(next);
  };

  const toggleMute = () => {
    const tracks = streamRef.current?.getAudioTracks() ?? [];
    const next = !muted;
    tracks.forEach((t) => (t.enabled = !next));
    mutedRef.current = next;
    setMuted(next);
  };

  const finish = async () => {
    if (phase !== "recording") return;
    setPhase("processing");

    const captureAttempt = captureAttemptRef.current;
    const lockGeneration = captureGenerationRef.current;
    const controller = new AbortController();
    processingAbortRef.current?.abort();
    processingAbortRef.current = controller;
    const stale = () =>
      controller.signal.aborted
      || closedRef.current
      || captureAttempt !== captureAttemptRef.current
      || !isAppLockGenerationCurrent(lockGeneration);

    const blob = await new Promise<Blob | null>((resolve) => {
      let settled = false;
      const settle = (value: Blob | null) => {
        if (settled) return;
        settled = true;
        controller.signal.removeEventListener("abort", onAbort);
        resolve(value);
      };
      const onAbort = () => settle(null);
      controller.signal.addEventListener("abort", onAbort, { once: true });
      const r = recorderRef.current;
      if (!r || r.state !== "recording") {
        settle(chunksRef.current.length ? new Blob(chunksRef.current) : null);
        return;
      }
      r.onstop = () =>
        settle(chunksRef.current.length ? new Blob(chunksRef.current, { type: r.mimeType }) : null);
      try {
        r.stop();
      } catch {
        settle(null);
      }
    });

    const voiceTracker = voiceTrackerRef.current;
    voiceTracker?.pause();
    voiceTrackerRef.current = null;
    const visualTracker = visualTrackerRef.current;
    const visualMetrics = visualTracker?.finish() ?? null;
    visualTrackerRef.current = null;
    const webSpeechText = sttRef.current?.stop() ?? "";
    sttRef.current = null;
    stopAll();

    try {
      if (stale()) return;
      const videoFormat = formatRef.current;
      const validation = blob ? validateCapturedMediaSize(blob.size) : { ok: false as const, reason: "EMPTY" as const };
      if (!validation.ok && validation.reason === "TOO_LARGE") {
        onResult({
          transcript: webSpeechText,
          voiceScore: null,
          visualScore: null,
          videoBlob: null,
          videoFormat,
          captureError: "VIDEO_TOO_LARGE",
        });
        return;
      }

      // 1) 전사
      let transcript = webSpeechText;
      let videoBase64 = "";
      if (blob && blob.size > 0) {
        videoBase64 = await blobToBase64(blob).catch(() => "");
        if (stale()) return;
        if (videoBase64) {
          try {
            const stt = await transcribeVoice(
              sessionId,
              videoBase64,
              videoFormat,
              "ko",
              controller.signal,
            );
            if (stale()) return;
            if (stt.text) transcript = stt.text;
          } catch {
            if (stale()) return;
            /* serve 미기동 → 브라우저 STT 폴백 유지 */
          }
        }
      }
      const chars = transcript.replace(/\s/g, "").length;
      const fillers = transcript ? countFillers([transcript]) : 0;

      // 2) 비언어 채점: serve late-fusion 우선, 실패 시 온디바이스
      let voiceScore: number | null = null;
      let visualScore: number | null = null;
      try {
        if (!videoBase64) throw new Error("no-video");
        const server = await scoreAvatarServer(sessionId, {
          videoBase64,
          videoFormat,
          transcriptChars: chars,
          fillerCount: fillers,
        }, controller.signal);
        if (stale()) return;
        voiceScore = server.voice?.score ?? null;
        visualScore = server.visual?.score ?? null;
      } catch {
        if (stale()) return;
        const vMetrics = voiceTracker?.finish(chars, fillers) ?? null;
        voiceScore = vMetrics ? computeVoiceScore(vMetrics).overall : null;
        visualScore = visualMetrics ? computeVisualScore(visualMetrics).overall : null;
      }

      if (!stale()) onResult({ transcript, voiceScore, visualScore, videoBlob: blob, videoFormat });
    } finally {
      voiceTracker?.dispose();
      visualTracker?.dispose();
      if (processingAbortRef.current === controller) processingAbortRef.current = null;
    }
  };

  finishRef.current = () => {
    void finish();
  };

  useEffect(() => {
    if (phase === "recording" && seconds >= MOBILE_INTERVIEW_VIDEO_MAX_SECONDS) {
      finishRef.current();
    }
  }, [phase, seconds]);

  const mm = Math.floor(seconds / 60);
  const ss = String(seconds % 60).padStart(2, "0");

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="화상 면접 답변 녹화"
      className="fixed inset-0 z-[60] flex flex-col bg-[#020203]"
    >
      {/* 카메라 프리뷰 (풀스크린) */}
      <video
        ref={videoRef}
        autoPlay
        playsInline
        muted
        className={`absolute inset-0 size-full object-cover ${facing === "user" ? "-scale-x-100" : ""}`}
      />
      <div className="pointer-events-none absolute inset-0 bg-gradient-to-b from-black/55 via-transparent to-black/65" />

      {/* 자세/상태 칩 */}
      {poseNote && (
        <div className="absolute right-4 top-28 z-10 rounded-full border border-white/10 bg-black/60 px-3 py-1 text-[11px] text-[#EDEDEF] backdrop-blur-md">
          {poseNote}
        </div>
      )}

      <div className="relative z-10 flex h-full flex-col">
        {/* 상단 */}
        <div
          className="flex items-center gap-2 px-4 pb-2"
          style={{ paddingTop: "calc(env(safe-area-inset-top) + 14px)" }}
        >
          <span className="rounded-full border border-[#5E6AD2]/30 bg-[#5E6AD2]/20 px-2.5 py-0.5 text-[11px] font-medium text-[#c3c9f4] backdrop-blur-md">
            화상 답변
          </span>
          <span className="font-mono text-[10px] font-semibold uppercase tracking-widest text-white/70">
            {questionLabel}
          </span>
          {phase === "recording" && !error && (
            <span className="flex items-center gap-1.5 rounded-full border border-white/10 bg-black/50 px-2.5 py-0.5 font-mono text-[10px] font-semibold text-white/80 backdrop-blur-md">
              <span className="size-1.5 animate-pulse rounded-full bg-[#e46962] shadow-[0_0_8px_rgba(228,105,98,0.6)]" />
              REC {mm}:{ss} / 0:{MOBILE_INTERVIEW_VIDEO_MAX_SECONDS}
            </span>
          )}
          <button
            autoFocus
            onClick={() => setConfirmClose(true)}
            className="ml-auto flex size-9 items-center justify-center rounded-lg bg-black/40 text-white/80 backdrop-blur-md"
            aria-label="닫기"
          >
            <X className="size-5" />
          </button>
        </div>

        <div className="flex-1" />

        {error && (
          <div className="mx-6 mb-4 rounded-xl border border-[#e46962]/30 bg-black/60 px-4 py-3 text-center text-[13px] text-[#e46962] backdrop-blur-md">
            {error}
          </div>
        )}
        {phase === "processing" && (
          <div className="mx-6 mb-4 flex items-center justify-center gap-3 rounded-xl border border-white/10 bg-black/60 px-4 py-3 text-[13px] text-[#EDEDEF] backdrop-blur-md">
            <span className="size-4 animate-spin rounded-full border-2 border-white/15 border-t-[#5E6AD2]" />
            표정·자세·음성 분석 중 — 제출 원본을 안전하게 준비합니다
          </div>
        )}

        {/* 질문 밴드 */}
        <div className="mx-4 rounded-[14px] border border-white/[0.08] bg-black/70 px-4 py-3.5 text-[14.5px] font-semibold leading-relaxed tracking-tight text-[#EDEDEF] shadow-[0_8px_32px_rgba(0,0,0,0.5),inset_0_1px_0_rgba(255,255,255,0.06)] backdrop-blur-xl">
          {questionText}
        </div>

        {/* 컨트롤 */}
        <div
          className="flex items-center justify-center gap-6 pt-5"
          style={{ paddingBottom: "calc(env(safe-area-inset-bottom) + 36px)" }}
        >
          <button
            onClick={flip}
            disabled={phase !== "recording"}
            className="flex size-14 items-center justify-center rounded-full border border-white/10 bg-black/50 text-white backdrop-blur-md transition-transform hover:-translate-y-0.5 disabled:opacity-40"
            aria-label="카메라 전환"
          >
            <SwitchCamera className="size-5" />
          </button>
          <button
            onClick={() => void finish()}
            disabled={phase !== "recording"}
            className="flex size-[72px] items-center justify-center rounded-full bg-[#e46962] text-white shadow-[0_0_0_1px_rgba(228,105,98,0.4),0_6px_24px_rgba(228,105,98,0.35),inset_0_1px_0_rgba(255,255,255,0.2)] disabled:opacity-40"
            aria-label="답변 완료"
          >
            <Square className="size-6 fill-current" />
          </button>
          <button
            onClick={toggleMute}
            disabled={phase !== "recording"}
            className="flex size-14 items-center justify-center rounded-full border border-white/10 bg-black/50 text-white backdrop-blur-md transition-transform hover:-translate-y-0.5 disabled:opacity-40"
            aria-label="마이크 음소거"
          >
            {muted ? <MicOff className="size-5" /> : <Mic className="size-5" />}
          </button>
        </div>
      </div>
      {confirmClose && (
        <MediaCaptureExitConfirm
          processing={phase === "processing"}
          onKeep={() => setConfirmClose(false)}
          onDiscard={() => closeRef.current()}
        />
      )}
    </div>
  );
}
