import { useCallback, useEffect, useRef, useState } from "react";
import { ClipboardList, Loader2, Mic, PhoneOff, Radio } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import {
  createRealtimeSession,
  getMediaCapabilities,
  listSessionQuestions,
  saveMediaResult,
  scoreVoiceServer,
  scoreVoiceTranscript,
} from "../api/interviewApi";
import {
  blobToBase64,
  computeVoiceScore,
  countFillers,
  VoiceMetricsTracker,
} from "../hooks/voiceAnalysis";
import { createNegotiatedRecorder, mediaUnsupportedReason } from "../hooks/mediaSupport";
import { useDeviceCapabilities } from "../hooks/deviceCapabilities";
import { DeviceHandoffCard, type HandoffReason } from "./DeviceHandoffCard";
import { RemoteMicConnectCard } from "./RemoteMicConnectCard";
import { MicLevelMeter } from "./MicLevelMeter";
import type {
  InterviewQuestion,
  InterviewSession,
  MediaCapabilities,
  TranscriptLine,
  VoiceMetrics,
  VoiceScoreDetail,
} from "../types/interview";
import { VoiceScorePanel } from "./VoiceScorePanel";
import { useTutorialStore } from "../tutorial/tutorialStore";
import { TutorialMediaPreview } from "../tutorial/TutorialMediaPreview";

type Status = "idle" | "connecting" | "live" | "analyzing" | "scored" | "error";

/**
 * 음성 모의면접 (OpenAI Realtime + WebRTC).
 * 백엔드에서 단기 ephemeral key 를 받아 브라우저가 OpenAI 에 직접 연결한다.
 * 준비된 질문(미생성 시 게이트)으로 면접관이 진행하고, 종료 시
 * 트랜스크립트 + 온디바이스 음성 지표 → 점수를 저장한다.
 * 원본 음성은 서버에 올리지 않는다 (ADR-002).
 */
export function RealtimeInterviewTab({ session }: { session: InterviewSession | null }) {
  const tutorialActive = useTutorialStore((s) => s.mode !== "off");
  const [status, setStatus] = useState<Status>("idle");
  const [error, setError] = useState<string | null>(null);
  const [lines, setLines] = useState<TranscriptLine[]>([]);
  const [questions, setQuestions] = useState<InterviewQuestion[] | null>(null);
  const [capabilities, setCapabilities] = useState<MediaCapabilities | null>(null);
  const [scoreDetail, setScoreDetail] = useState<VoiceScoreDetail | null>(null);
  const [metrics, setMetrics] = useState<VoiceMetrics | null>(null);
  const [saveNote, setSaveNote] = useState<string | null>(null);
  // 원본 음성을 자체 추론 서버로 보내 정밀 분석할지 동의 (ADR-006). 해제 시 브라우저 지표만 사용.
  const [consent, setConsent] = useState(true);
  // 폰 마이크 핸드오프(WebRTC) — 마이크 없는 기기에서 폰의 마이크를 원격 입력으로 쓴다.
  const [remoteMic, setRemoteMic] = useState<MediaStream | null>(null);
  // 면접 중 마이크 레벨 시각화용 — micRef 와 같은 스트림 (ref 는 리렌더를 못 일으켜 state 로 별도 보관)
  const [micStream, setMicStream] = useState<MediaStream | null>(null);

  const pcRef = useRef<RTCPeerConnection | null>(null);
  const micRef = useRef<MediaStream | null>(null);
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const aiDraftRef = useRef<string>("");
  const linesRef = useRef<TranscriptLine[]>([]);
  const trackerRef = useRef<VoiceMetricsTracker | null>(null);
  const recorderRef = useRef<MediaRecorder | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  /** 녹음 시 협상된 업로드 포맷(webm|mp4) — blob.type 스니핑 대신 이 값을 쓴다. */
  const recordFormatRef = useRef<string>("webm");

  const supported =
    typeof window !== "undefined" &&
    "RTCPeerConnection" in window &&
    typeof navigator !== "undefined" &&
    !!navigator.mediaDevices;

  const deviceCaps = useDeviceCapabilities();
  // 이 기기에서 진행 불가한 원인 — 있으면 "폰으로 이어하기" 안내 카드를 띄운다.
  const handoffReason: HandoffReason | null = !supported
    ? (mediaUnsupportedReason() ?? "unsupported")
    : deviceCaps.hasMicrophone === false
      ? "no-microphone"
      : null;

  // 준비된 질문(게이트) + 키 보유 여부 로드.
  useEffect(() => {
    if (!session) return;
    listSessionQuestions(session.id)
      .then((qs) => setQuestions(qs.filter((q) => q.parentQuestionId == null)))
      .catch(() => setQuestions([]));
    getMediaCapabilities()
      .then(setCapabilities)
      .catch(() => setCapabilities(null));
  }, [session]);

  useEffect(() => {
    return () => cleanup();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const cleanup = () => {
    recorderRef.current?.state === "recording" && recorderRef.current.stop();
    recorderRef.current = null;
    trackerRef.current?.dispose();
    trackerRef.current = null;
    pcRef.current?.getSenders().forEach((s) => s.track?.stop());
    pcRef.current?.close();
    pcRef.current = null;
    micRef.current?.getTracks().forEach((t) => t.stop());
    micRef.current = null;
    setMicStream(null);
  };

  const pushLine = useCallback((line: TranscriptLine) => {
    if (!line.text.trim()) return;
    const next = [...linesRef.current, { ...line, text: line.text.trim() }];
    linesRef.current = next;
    setLines(next);
  }, []);

  const handleEvent = (raw: string) => {
    let ev: Record<string, unknown>;
    try {
      ev = JSON.parse(raw);
    } catch {
      return;
    }
    const type = ev.type as string;
    // GA Realtime API는 response.output_audio_transcript.*, 구 beta는 response.audio_transcript.* — 둘 다 처리.
    if (type === "response.output_audio_transcript.delta" || type === "response.audio_transcript.delta") {
      aiDraftRef.current += (ev.delta as string) ?? "";
    } else if (type === "response.output_audio_transcript.done" || type === "response.audio_transcript.done") {
      const text = aiDraftRef.current.trim();
      aiDraftRef.current = "";
      if (text) pushLine({ role: "ai", text });
      // 질문이 끝났다 → 다음 사용자 발화까지의 반응 지연 측정 시작.
      trackerRef.current?.markAiSpeechEnd();
    } else if (type === "conversation.item.input_audio_transcription.completed") {
      pushLine({ role: "user", text: (ev.transcript as string) ?? "" });
    }
  };

  const start = async () => {
    if (!session) return;
    setStatus("connecting");
    setError(null);
    setSaveNote(null);
    setScoreDetail(null);
    setMetrics(null);
    linesRef.current = [];
    setLines([]);
    try {
      const rt = await createRealtimeSession(session.id);

      const pc = new RTCPeerConnection();
      pcRef.current = pc;

      // 면접관 음성 수신.
      pc.ontrack = (e) => {
        if (audioRef.current) audioRef.current.srcObject = e.streams[0];
      };

      // 마이크 송신 + 온디바이스 분석(지표 샘플링 · 감정 분석용 녹음).
      // 폰 마이크가 연결돼 있으면 그 원격 스트림을 쓴다 — clone 이라 종료(cleanup)해도 원본 연결은 유지된다.
      const mic = remoteMic ? remoteMic.clone() : await navigator.mediaDevices.getUserMedia({ audio: true });
      micRef.current = mic;
      setMicStream(mic);
      mic.getTracks().forEach((t) => pc.addTrack(t, mic));

      const tracker = new VoiceMetricsTracker();
      tracker.start(mic);
      trackerRef.current = tracker;

      // 녹음은 정밀 분석(전송 동의)용 — MediaRecorder 미지원 기기에서는 건너뛰고 지표 채점만 한다.
      chunksRef.current = [];
      if (typeof MediaRecorder !== "undefined") {
        // 기기별 지원 mimeType 협상(webm/opus → mp4/aac) — WebView 등 webm 미지원 기기 대응.
        const { recorder, format } = createNegotiatedRecorder(mic, "audio");
        recordFormatRef.current = format;
        recorder.ondataavailable = (e) => {
          if (e.data.size > 0) chunksRef.current.push(e.data);
        };
        recorder.start();
        recorderRef.current = recorder;
      }

      // 이벤트 채널(트랜스크립트/세션 제어).
      const dc = pc.createDataChannel("oai-events");
      dc.onmessage = (e) => handleEvent(e.data);
      dc.onopen = () => {
        // 사용자 음성도 텍스트로 받도록 활성화 (GA 스키마 — 구 input_audio_transcription 형식은 reject됨).
        dc.send(
          JSON.stringify({
            type: "session.update",
            session: {
              type: "realtime",
              audio: { input: { transcription: { model: "whisper-1" } } },
            },
          }),
        );
        // 면접관이 먼저 인사하며 면접을 시작하도록 유도.
        dc.send(
          JSON.stringify({
            type: "response.create",
            response: { instructions: "지원자에게 한국어로 짧게 인사하고 자기소개를 요청하며 면접을 시작하라." },
          }),
        );
      };

      const offer = await pc.createOffer();
      await pc.setLocalDescription(offer);

      const sdpRes = await fetch(`${rt.realtimeUrl}?model=${encodeURIComponent(rt.model)}`, {
        method: "POST",
        body: offer.sdp ?? "",
        headers: {
          Authorization: `Bearer ${rt.clientSecret}`,
          "Content-Type": "application/sdp",
        },
      });
      if (!sdpRes.ok) {
        throw new Error(`Realtime 연결 실패 (${sdpRes.status})`);
      }
      const answerSdp = await sdpRes.text();
      await pc.setRemoteDescription({ type: "answer", sdp: answerSdp });

      setStatus("live");
    } catch (err) {
      cleanup();
      setError(err instanceof Error ? err.message : "실시간 면접 연결에 실패했습니다.");
      setStatus("error");
    }
  };

  /** 종료 → 지표 산출 → 점수 저장. */
  const stop = async () => {
    if (!session) return;
    setStatus("analyzing");

    // 녹음 blob 을 안전하게 회수한 뒤 연결을 정리한다.
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

    const transcript = linesRef.current;
    const userLines = transcript.filter((l) => l.role === "user").map((l) => l.text);
    const userChars = userLines.join("").replace(/\s/g, "").length;
    const fillers = countFillers(userLines);
    const finalMetrics =
      trackerRef.current?.finish(userChars, fillers) ?? null;

    cleanup();

    if (!finalMetrics) {
      setStatus("error");
      setError("음성 지표를 수집하지 못했습니다.");
      return;
    }

    // 점수 산출: 동의 + 자체 추론 서버(nonverbal) 가능하면 서버 정밀 채점, 아니면 브라우저 지표 폴백.
    let detail: VoiceScoreDetail;
    const hasAudio = !!recordedBlob && recordedBlob.size > 0;

    if (consent && capabilities?.nonverbal && hasAudio) {
      try {
        const audioBase64 = await blobToBase64(recordedBlob);
        const audioFormat = recordFormatRef.current; // 녹음 시 협상한 포맷 (blob.type 스니핑 대체)
        const server = await scoreVoiceServer(session.id, {
          audioBase64,
          audioFormat,
          transcriptChars: userChars,
          fillerCount: fillers,
          latencySec: finalMetrics.avgResponseLatencySec ?? undefined,
        });
        detail = server.detail;
        setSaveNote(`자체 추론 서버로 채점했습니다 (${server.source}).`);
      } catch {
        detail = computeVoiceScore(finalMetrics);
        setSaveNote("자체 추론 서버 호출 실패 — 브라우저 지표만으로 채점했습니다.");
      }
    } else {
      detail = computeVoiceScore(finalMetrics);
    }

    setMetrics(finalMetrics);
    setScoreDetail(detail);

    try {
      await saveMediaResult(session.id, {
        kind: "VOICE",
        transcript,
        metrics: { ...finalMetrics },
        score: detail.overall,
        scoreDetail: { ...detail },
      });
      setSaveNote((prev) => prev ?? "분석 결과가 저장되었습니다. (원본 음성은 저장하지 않습니다)");
    } catch (err) {
      setSaveNote(
        err instanceof Error ? `결과 저장 실패: ${err.message}` : "결과 저장에 실패했습니다.",
      );
    }
    // 답변 "내용" 채점: 트랜스크립트를 질문별로 채점해 저장한다(전달력 점수와 별개, interview_answer 경로 공유).
    if (transcript.some((l) => l.role === "user")) {
      try {
        const scored = await scoreVoiceTranscript(session.id, transcript);
        if (scored > 0) {
          setSaveNote((prev) =>
            `${prev ?? ""} 답변 ${scored}문항의 내용도 채점했습니다(리포트·복기에서 확인).`.trim(),
          );
        }
      } catch {
        // 내용 채점 실패는 음성(전달력) 점수 저장을 막지 않는다.
      }
    }
    setStatus("scored");
  };

  // 튜토리얼: 실제 OpenAI Realtime·마이크 연결 대신 예시 결과 화면만 보여준다.
  if (tutorialActive) {
    return <TutorialMediaPreview kind="voice" />;
  }

  if (!session) {
    return (
      <div className="rounded-xl border border-dashed border-slate-200 bg-card p-10 text-center text-sm text-slate-400">
        "면접 모드 선택" 탭에서 지원 건과 모드를 고르고 면접을 시작하면 음성 모의면접을 진행할 수 있습니다.
      </div>
    );
  }

  // 질문 미생성 게이트 — 준비된 질문으로만 진행한다 (ADR-002).
  if (questions != null && questions.length === 0) {
    return (
      <div className="rounded-xl border border-dashed border-amber-200 bg-amber-50 p-10 text-center">
        <ClipboardList className="mx-auto size-8 text-amber-500" />
        <p className="mt-3 text-sm font-semibold text-amber-800">준비된 면접 질문이 없습니다</p>
        <p className="mt-1 text-sm text-amber-700">
          "예상 면접 질문" 탭에서 질문을 먼저 생성하면, 면접관이 그 질문으로 음성 모의면접을 진행합니다.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <Card className="border border-slate-200 bg-card">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <Radio className="size-4 text-rose-600" />
            실시간 AI 음성 면접
            {status === "live" ? (
              <Badge className="gap-1 bg-rose-100 text-rose-700">
                <span className="size-2 animate-pulse rounded-full bg-rose-500" /> LIVE
              </Badge>
            ) : (
              <Badge className="bg-slate-100 text-slate-600">실시간 음성</Badge>
            )}
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <p className="text-sm text-slate-500">
            AI 면접관이 준비된 질문{questions ? ` ${Math.min(questions.length, 6)}개` : ""}로 음성 면접을
            진행합니다. 종료하면 답변 트랜스크립트와 음성 분석 점수(말 속도·군말·톤·자신감)가 저장됩니다.
          </p>

          {capabilities?.nonverbal ? (
            <label className="flex items-start gap-2 rounded-lg bg-slate-50 p-3 text-xs text-slate-600">
              <input
                type="checkbox"
                checked={consent}
                onChange={(e) => setConsent(e.target.checked)}
                className="mt-0.5"
              />
              <span>
                <b className="text-slate-700">정밀 음성 분석(자체 AI)</b> — 원본 음성을 분석 서버로 전송해
                자체 모델로 채점합니다. 분석 후 음성은 즉시 폐기되며 저장하지 않습니다. 해제하면 브라우저 내
                지표만으로 채점합니다.
              </span>
            </label>
          ) : (
            capabilities && (
              <p className="rounded-lg bg-slate-50 p-3 text-xs text-slate-500">
                브라우저 측정 지표만으로 채점합니다.
              </p>
            )
          )}

          {/* 마이크 없음: ① 폰을 원격 마이크로 연결(면접은 이 화면에서), ② 세션 자체를 폰으로 이어하기 */}
          {handoffReason === "no-microphone" && (
            <RemoteMicConnectCard sessionId={session.id} onStream={setRemoteMic} />
          )}
          {handoffReason && !remoteMic && <DeviceHandoffCard sessionId={session.id} reason={handoffReason} />}

          {supported && (!handoffReason || (handoffReason === "no-microphone" && remoteMic)) && (
            <div className="flex flex-wrap items-center gap-2">
              {(status === "idle" || status === "scored" || status === "error") && (
                <Button
                  onClick={start}
                  disabled={questions == null}
                  className="gap-1.5 bg-rose-600 hover:bg-rose-700"
                >
                  <Mic className="size-4" /> {status === "idle" ? "면접 시작" : "다시 시작"}
                </Button>
              )}
              {status === "connecting" && (
                <Button disabled className="gap-1.5">
                  <Loader2 className="size-4 animate-spin" /> 연결 중…
                </Button>
              )}
              {status === "analyzing" && (
                <Button disabled className="gap-1.5">
                  <Loader2 className="size-4 animate-spin" /> 음성 분석 중…
                </Button>
              )}
              {status === "live" && (
                <Button onClick={stop} variant="destructive" className="gap-1.5">
                  <PhoneOff className="size-4" /> 면접 종료
                </Button>
              )}
            </div>
          )}

          {/* 실제 마이크 입력 레벨 — 내 목소리가 들어가고 있는지 즉시 확인 */}
          {status === "live" && (
            <div className="flex items-center gap-3 rounded-lg bg-rose-50 px-3 py-2.5">
              <Mic className="size-4 shrink-0 text-rose-600" />
              <MicLevelMeter
                stream={micStream}
                bars={24}
                className="h-5 flex-1 justify-center gap-[3px] text-rose-500"
              />
              <span className="shrink-0 text-xs font-semibold text-rose-700">내 목소리 인식 중</span>
            </div>
          )}

          {error && <p className="text-sm text-red-500">{error}</p>}
          {saveNote && <p className="text-xs font-semibold text-slate-500">{saveNote}</p>}

          {/* 면접관 음성 출력 (자동 재생) */}
          <audio ref={audioRef} autoPlay className="hidden" />
        </CardContent>
      </Card>

      {scoreDetail && metrics && (
        <VoiceScorePanel detail={scoreDetail} metrics={metrics} title="음성 모의면접 점수" />
      )}

      {lines.length > 0 && (
        <Card className="border border-slate-200 bg-card">
          <CardHeader>
            <CardTitle className="text-sm text-slate-600">대화 기록</CardTitle>
          </CardHeader>
          <CardContent className="space-y-2">
            {lines.map((line, i) => (
              <div key={i} className={line.role === "ai" ? "flex" : "flex justify-end"}>
                <div
                  className={
                    line.role === "ai"
                      ? "max-w-[80%] rounded-2xl rounded-tl-sm bg-slate-100 px-3 py-2 text-sm text-slate-800"
                      : "max-w-[80%] rounded-2xl rounded-tr-sm bg-blue-600 px-3 py-2 text-sm text-white"
                  }
                >
                  <div className="mb-0.5 text-[10px] font-bold opacity-60">
                    {line.role === "ai" ? "면접관" : "나"}
                  </div>
                  {line.text}
                </div>
              </div>
            ))}
          </CardContent>
        </Card>
      )}
    </div>
  );
}
