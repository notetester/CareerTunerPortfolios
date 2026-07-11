import { useEffect, useRef, useState } from "react";
import { X, Volume2, Square } from "lucide-react";
import {
  blobToBase64,
  computeVoiceScore,
  countFillers,
  VoiceMetricsTracker,
} from "../../hooks/voiceAnalysis";
import { BrowserSttTracker } from "../../hooks/speechToText";
import { createNegotiatedRecorder } from "../../hooks/mediaSupport";
import { scoreVoiceServer, transcribeVoice } from "../../api/interviewApi";
import {
  MOBILE_INTERVIEW_AUDIO_BITS_PER_SECOND,
  MOBILE_INTERVIEW_AUDIO_MAX_SECONDS,
  validateCapturedMediaSize,
} from "../../lib/mobileSubmission";
import { MicLevelMeter } from "../MicLevelMeter";
import {
  captureAppLockGeneration,
  isAppLockGenerationCurrent,
  keepStreamForAppLock,
  onAppLockState,
} from "@/platform/appLockEvents";

/**
 * 몰입형 음성 답변 (모바일 풀스크린) — Claude 앱식 최소 UI.
 * 진입 즉시 녹음 시작 → 정지 → 전사(serve STT, 실패 시 브라우저 STT 폴백)
 * → 전달력 채점(serve LightGBM, 실패 시 온디바이스 규칙점수)
 * → onResult 로 전사·점수·원본 Blob을 부모에 넘긴다. 부모가 INTERVIEW_ANSWER
 * pending 파일로 저장하고 표준 answers 요청 성공 시 원자 연결한다.
 */
export interface VoiceResult {
  transcript: string;
  voiceScore: number | null;
  audioBlob: Blob | null;
  audioFormat: string;
  captureError?: "AUDIO_TOO_LARGE";
}

type Phase = "recording" | "processing";

export function ImmersiveVoiceOverlay({
  sessionId,
  questionText,
  questionLabel,
  modeLabel,
  onResult,
  onClose,
}: {
  sessionId: number;
  questionText: string;
  questionLabel: string; // 예: "Q2 / 5"
  modeLabel: string;
  onResult: (r: VoiceResult) => void;
  onClose: () => void;
}) {
  const [phase, setPhase] = useState<Phase>("recording");
  const [seconds, setSeconds] = useState(0);
  const [error, setError] = useState<string | null>(null);
  // 웨이브폼 시각화용 마이크 스트림 — 실제 음량에 따라 움직인다 (ref 는 리렌더를 못 일으켜 state 로 별도 보관)
  const [micStream, setMicStream] = useState<MediaStream | null>(null);

  const micRef = useRef<MediaStream | null>(null);
  const recorderRef = useRef<MediaRecorder | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const trackerRef = useRef<VoiceMetricsTracker | null>(null);
  const sttRef = useRef<BrowserSttTracker | null>(null);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const closedRef = useRef(false);
  const captureAttemptRef = useRef(0);
  const captureGenerationRef = useRef<number | null>(null);
  const processingAbortRef = useRef<AbortController | null>(null);
  const finishRef = useRef<() => void>(() => undefined);
  /** 녹음 시 협상된 업로드 포맷(webm|mp4) — blob.type 스니핑 대신 이 값을 쓴다. */
  const formatRef = useRef<string>("webm");

  const cleanup = () => {
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
    micRef.current?.getTracks().forEach((t) => t.stop());
    micRef.current = null;
    chunksRef.current = [];
  };

  const close = () => {
    closedRef.current = true;
    captureAttemptRef.current += 1;
    processingAbortRef.current?.abort();
    processingAbortRef.current = null;
    sttRef.current?.stop();
    sttRef.current = null;
    trackerRef.current?.dispose();
    trackerRef.current = null;
    cleanup();
    onClose();
  };

  // 진입 즉시 녹음 시작 (권한 프리프롬프트는 진입 전에 이미 통과)
  useEffect(() => {
    let cancelled = false;
    const captureAttempt = ++captureAttemptRef.current;
    const lockGeneration = captureAppLockGeneration();
    if (lockGeneration === null) return undefined;
    captureGenerationRef.current = lockGeneration;
    void (async () => {
      try {
        const mic = await navigator.mediaDevices.getUserMedia({ audio: true });
        if (
          cancelled
          || captureAttempt !== captureAttemptRef.current
          || !keepStreamForAppLock(mic, lockGeneration)
        ) {
          mic.getTracks().forEach((t) => t.stop());
          return;
        }
        micRef.current = mic;
        setMicStream(mic);
        chunksRef.current = [];
        const tracker = new VoiceMetricsTracker();
        tracker.start(mic);
        tracker.markAiSpeechEnd();
        trackerRef.current = tracker;
        const stt = new BrowserSttTracker();
        stt.start();
        sttRef.current = stt;
        // 기기별 지원 mimeType 협상(webm/opus → mp4/aac) — WebView 등 webm 미지원 기기 대응.
        const { recorder, format } = createNegotiatedRecorder(mic, "audio", {
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
        timerRef.current = setInterval(() => setSeconds((s) => s + 1), 1000);
      } catch {
        if (!cancelled) setError("마이크 접근에 실패했습니다. 권한을 확인해 주세요.");
      }
    })();
    return () => {
      cancelled = true;
      closedRef.current = true;
      captureAttemptRef.current += 1;
      processingAbortRef.current?.abort();
      processingAbortRef.current = null;
      sttRef.current?.stop();
      sttRef.current = null;
      trackerRef.current?.dispose();
      trackerRef.current = null;
      cleanup();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => onAppLockState((locked) => {
    if (locked) close();
  }), [onClose]);

  const speakQuestion = () => {
    if (typeof window === "undefined" || !window.speechSynthesis) return;
    const utter = new SpeechSynthesisUtterance(questionText);
    utter.lang = "ko-KR";
    window.speechSynthesis.cancel();
    window.speechSynthesis.speak(utter);
  };

  /** 정지 → 전사·채점 → 결과 반환. */
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
    const tracker = trackerRef.current;
    tracker?.pause();
    trackerRef.current = null;
    const webSpeechText = sttRef.current?.stop() ?? "";
    sttRef.current = null;
    cleanup();

    try {
      if (stale()) return;
      const validation = blob ? validateCapturedMediaSize(blob.size) : null;
      if (validation && !validation.ok && validation.reason === "TOO_LARGE") {
        onResult({
          transcript: webSpeechText,
          voiceScore: null,
          audioBlob: null,
          audioFormat: formatRef.current,
          captureError: "AUDIO_TOO_LARGE",
        });
        return;
      }
      if (!blob || blob.size === 0) {
        onResult({
          transcript: webSpeechText,
          voiceScore: null,
          audioBlob: null,
          audioFormat: formatRef.current,
        });
        return;
      }

      const audioBase64 = await blobToBase64(blob).catch(() => "");
      if (stale()) return;
      const audioFormat = formatRef.current;

      // 1) 전사: serve STT 우선, 실패 시 브라우저 STT 폴백
      let transcript = "";
      try {
        const stt = await transcribeVoice(sessionId, audioBase64, audioFormat, "ko", controller.signal);
        if (stale()) return;
        transcript = stt.text;
      } catch {
        if (stale()) return;
        transcript = webSpeechText;
      }
      const chars = transcript.replace(/\s/g, "").length;
      const fillers = transcript ? countFillers([transcript]) : 0;

      // 2) 전달력: serve 우선, 실패 시 온디바이스 규칙점수
      let voiceScore: number | null = null;
      try {
        const server = await scoreVoiceServer(sessionId, {
          audioBase64,
          audioFormat,
          transcriptChars: chars,
          fillerCount: fillers,
        }, controller.signal);
        if (stale()) return;
        voiceScore = server.score;
      } catch {
        if (stale()) return;
        const metrics = tracker?.finish(chars, fillers) ?? null;
        voiceScore = metrics ? computeVoiceScore(metrics).overall : null;
      }

      if (!stale()) onResult({ transcript, voiceScore, audioBlob: blob, audioFormat });
    } finally {
      tracker?.dispose();
      if (processingAbortRef.current === controller) processingAbortRef.current = null;
    }
  };

  finishRef.current = () => {
    void finish();
  };

  useEffect(() => {
    if (phase === "recording" && seconds >= MOBILE_INTERVIEW_AUDIO_MAX_SECONDS) {
      finishRef.current();
    }
  }, [phase, seconds]);

  const mm = Math.floor(seconds / 60);
  const ss = String(seconds % 60).padStart(2, "0");

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="음성 면접 답변 녹음"
      className="fixed inset-0 z-[60] flex flex-col bg-[#020203]"
    >
      {/* 앰비언트 */}
      <div
        className="pointer-events-none absolute inset-0"
        style={{
          background:
            "radial-gradient(400px 300px at 50% 30%, rgba(94,106,210,0.08), transparent 70%)",
        }}
      />
      {/* 상단 */}
      <div
        className="relative flex items-center gap-2 px-4 pb-3"
        style={{ paddingTop: "calc(env(safe-area-inset-top) + 14px)" }}
      >
        <span className="rounded-full border border-[#5E6AD2]/30 bg-[#5E6AD2]/15 px-2.5 py-0.5 text-[11px] font-medium text-[#aab2ef]">
          {modeLabel}
        </span>
        <span className="font-mono text-[10px] font-semibold uppercase tracking-widest text-[#8A8F98]">
          {questionLabel}
        </span>
        <button
          autoFocus
          onClick={close}
          className="ml-auto flex size-9 items-center justify-center rounded-lg text-[#8A8F98] transition-colors hover:bg-white/[0.06]"
          aria-label="닫기"
        >
          <X className="size-5" />
        </button>
      </div>

      {/* 질문 */}
      <div className="relative px-6 py-2 text-[17px] font-semibold leading-relaxed tracking-tight">
        <span className="bg-gradient-to-b from-white to-white/60 bg-clip-text text-transparent">
          {questionText}
        </span>
      </div>

      {/* 중앙: 웨이브폼 + 타이머 */}
      <div className="relative flex flex-1 flex-col items-center justify-center gap-5">
        {error ? (
          <div className="px-8 text-center text-[13px] leading-relaxed text-[#e46962]">{error}</div>
        ) : phase === "processing" ? (
          <>
            <div className="size-9 animate-spin rounded-full border-2 border-white/10 border-t-[#5E6AD2]" />
            <div className="text-[13px] text-[#8A8F98]">전사·채점 중 — 제출 원본을 안전하게 준비합니다</div>
          </>
        ) : (
          <>
            {/* 실제 마이크 음량을 따라가는 웨이브폼 — 목소리가 들어가고 있음을 그대로 보여준다 */}
            <MicLevelMeter
              stream={micStream}
              bars={13}
              minRatio={0.13}
              className="h-[90px] gap-[5px]"
              barClassName="w-1 rounded-[3px] bg-gradient-to-b from-[#7d88de] to-[#5E6AD2] shadow-[0_0_12px_rgba(94,106,210,0.4)]"
            />
            <div className="font-mono text-[26px] font-bold tabular-nums text-[#EDEDEF]">
              {mm}:{ss} / 3:00
            </div>
            <div className="text-[12.5px] text-[#8A8F98]">듣고 있어요 — 답변이 끝나면 정지를 누르세요</div>
          </>
        )}
      </div>

      {/* 컨트롤 */}
      <div
        className="relative flex items-center justify-center gap-6 pt-4"
        style={{ paddingBottom: "calc(env(safe-area-inset-bottom) + 40px)" }}
      >
        <button
          onClick={speakQuestion}
          disabled={phase !== "recording"}
          className="flex size-14 items-center justify-center rounded-full border border-white/[0.06] bg-white/[0.05] text-[#EDEDEF] shadow-[inset_0_1px_0_rgba(255,255,255,0.06)] transition-transform hover:-translate-y-0.5 disabled:opacity-40"
          aria-label="질문 다시 듣기"
        >
          <Volume2 className="size-5" />
        </button>
        <button
          onClick={() => void finish()}
          disabled={phase !== "recording"}
          className="flex size-[72px] items-center justify-center rounded-full bg-[#e46962] text-white shadow-[0_0_0_1px_rgba(228,105,98,0.4),0_6px_24px_rgba(228,105,98,0.3),inset_0_1px_0_rgba(255,255,255,0.2)] disabled:opacity-40"
          aria-label="답변 완료"
        >
          <Square className="size-6 fill-current" />
        </button>
        <div className="size-14" aria-hidden />
      </div>
    </div>
  );
}
