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
import { scoreAvatarServer, transcribeVoice } from "../../api/interviewApi";

/**
 * 몰입형 화상 답변 (모바일 풀스크린) — 카메라 프리뷰 전체화면 + 질문 오버레이.
 * 진입 즉시 녹화 시작 → 정지 → 전사(serve, 폴백 브라우저 STT)
 * → 비언어 채점(serve late-fusion, 폴백 온디바이스 MediaPipe/음향 지표)
 * → onResult 로 스레드에 반환. 원본 영상은 폐기.
 */
export interface AvatarResult {
  transcript: string;
  voiceScore: number | null;
  visualScore: number | null;
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

  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const recorderRef = useRef<MediaRecorder | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const voiceTrackerRef = useRef<VoiceMetricsTracker | null>(null);
  const visualTrackerRef = useRef<VisualMetricsTracker | null>(null);
  const sttRef = useRef<BrowserSttTracker | null>(null);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const closedRef = useRef(false);

  const stopAll = () => {
    if (timerRef.current) clearInterval(timerRef.current);
    timerRef.current = null;
    recorderRef.current = null;
    streamRef.current?.getTracks().forEach((t) => t.stop());
    streamRef.current = null;
  };

  /** 스트림 시작(+플립 시 재시작 — 녹화도 새로 시작한다). */
  const startCapture = async (face: "user" | "environment") => {
    stopAll();
    setSeconds(0);
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: face },
        audio: true,
      });
      if (closedRef.current) {
        stream.getTracks().forEach((t) => t.stop());
        return;
      }
      streamRef.current = stream;
      if (videoRef.current) videoRef.current.srcObject = stream;

      const voiceTracker = new VoiceMetricsTracker();
      voiceTracker.start(stream);
      voiceTracker.markAiSpeechEnd();
      voiceTrackerRef.current = voiceTracker;

      const stt = new BrowserSttTracker();
      stt.start();
      sttRef.current = stt;

      chunksRef.current = [];
      const recorder = new MediaRecorder(stream);
      recorder.ondataavailable = (e) => {
        if (e.data.size > 0) chunksRef.current.push(e.data);
      };
      recorder.start();
      recorderRef.current = recorder;

      // 표정/자세(MediaPipe) — 모델 로드 실패해도 면접은 계속.
      const visualTracker = new VisualMetricsTracker();
      visualTrackerRef.current = visualTracker;
      if (videoRef.current) {
        visualTracker.start(videoRef.current).catch(() => {
          visualTrackerRef.current = null;
          setPoseNote("표정·자세 분석 모델 미로드 — 음성 지표만 채점");
        });
      }

      timerRef.current = setInterval(() => setSeconds((s) => s + 1), 1000);
      setError(null);
    } catch {
      setError("카메라 접근에 실패했습니다. 권한을 확인해 주세요.");
    }
  };

  useEffect(() => {
    void startCapture("user");
    return () => {
      closedRef.current = true;
      sttRef.current?.stop();
      voiceTrackerRef.current?.dispose();
      visualTrackerRef.current?.dispose();
      stopAll();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

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
    setMuted(next);
  };

  const finish = async () => {
    if (phase !== "recording") return;
    setPhase("processing");

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

    const voiceTracker = voiceTrackerRef.current;
    voiceTracker?.pause();
    voiceTrackerRef.current = null;
    const visualTracker = visualTrackerRef.current;
    const visualMetrics = visualTracker?.finish() ?? null;
    visualTrackerRef.current = null;
    const webSpeechText = sttRef.current?.stop() ?? "";
    sttRef.current = null;
    stopAll();

    // 1) 전사
    let transcript = webSpeechText;
    let videoBase64 = "";
    const videoFormat = (blob?.type || "video/webm").includes("webm") ? "webm" : "mp4";
    if (blob && blob.size > 0) {
      videoBase64 = await blobToBase64(blob).catch(() => "");
      if (videoBase64) {
        try {
          const stt = await transcribeVoice(sessionId, videoBase64, videoFormat);
          if (stt.text) transcript = stt.text;
        } catch {
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
      });
      voiceScore = server.voice?.score ?? null;
      visualScore = server.visual?.score ?? null;
    } catch {
      const vMetrics = voiceTracker?.finish(chars, fillers) ?? null;
      voiceScore = vMetrics ? computeVoiceScore(vMetrics).overall : null;
      visualScore = visualMetrics ? computeVisualScore(visualMetrics).overall : null;
    } finally {
      voiceTracker?.dispose();
    }

    if (!closedRef.current) onResult({ transcript, voiceScore, visualScore });
  };

  const mm = Math.floor(seconds / 60);
  const ss = String(seconds % 60).padStart(2, "0");

  return (
    <div className="fixed inset-0 z-[60] flex flex-col bg-[#020203]">
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
              REC {mm}:{ss}
            </span>
          )}
          <button
            onClick={onClose}
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
            표정·자세·음성 분석 중 — 원본은 폐기됩니다
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
    </div>
  );
}
