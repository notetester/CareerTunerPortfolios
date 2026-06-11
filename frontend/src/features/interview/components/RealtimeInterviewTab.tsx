import { useEffect, useRef, useState } from "react";
import { Loader2, Mic, PhoneOff, Radio } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { createRealtimeSession } from "../api/interviewApi";
import type { InterviewSession } from "../types/interview";

type Status = "idle" | "connecting" | "live" | "ended" | "error";
type Line = { role: "ai" | "user"; text: string };

/**
 * 실시간 음성 AI 면접관 (OpenAI Realtime + WebRTC).
 * 백엔드에서 단기 ephemeral key 를 받아 브라우저가 OpenAI 에 직접 연결한다.
 * 면접관 음성을 듣고 마이크로 답하면 실시간 대화가 이어지고, 자막(트랜스크립트)이 표시된다.
 */
export function RealtimeInterviewTab({ session }: { session: InterviewSession | null }) {
  const [status, setStatus] = useState<Status>("idle");
  const [error, setError] = useState<string | null>(null);
  const [lines, setLines] = useState<Line[]>([]);

  const pcRef = useRef<RTCPeerConnection | null>(null);
  const micRef = useRef<MediaStream | null>(null);
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const aiDraftRef = useRef<string>("");

  const supported =
    typeof window !== "undefined" &&
    "RTCPeerConnection" in window &&
    typeof navigator !== "undefined" &&
    !!navigator.mediaDevices;

  useEffect(() => {
    return () => cleanup();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const cleanup = () => {
    pcRef.current?.getSenders().forEach((s) => s.track?.stop());
    pcRef.current?.close();
    pcRef.current = null;
    micRef.current?.getTracks().forEach((t) => t.stop());
    micRef.current = null;
  };

  const pushUser = (text: string) => {
    if (text.trim()) setLines((prev) => [...prev, { role: "user", text: text.trim() }]);
  };

  const handleEvent = (raw: string) => {
    let ev: Record<string, unknown>;
    try {
      ev = JSON.parse(raw);
    } catch {
      return;
    }
    const type = ev.type as string;
    if (type === "response.audio_transcript.delta") {
      aiDraftRef.current += (ev.delta as string) ?? "";
    } else if (type === "response.audio_transcript.done") {
      const text = aiDraftRef.current.trim();
      aiDraftRef.current = "";
      if (text) setLines((prev) => [...prev, { role: "ai", text }]);
    } else if (type === "conversation.item.input_audio_transcription.completed") {
      pushUser((ev.transcript as string) ?? "");
    }
  };

  const start = async () => {
    if (!session) return;
    setStatus("connecting");
    setError(null);
    setLines([]);
    try {
      const rt = await createRealtimeSession(session.id);

      const pc = new RTCPeerConnection();
      pcRef.current = pc;

      // 면접관 음성 수신.
      pc.ontrack = (e) => {
        if (audioRef.current) audioRef.current.srcObject = e.streams[0];
      };

      // 마이크 송신.
      const mic = await navigator.mediaDevices.getUserMedia({ audio: true });
      micRef.current = mic;
      mic.getTracks().forEach((t) => pc.addTrack(t, mic));

      // 이벤트 채널(트랜스크립트/세션 제어).
      const dc = pc.createDataChannel("oai-events");
      dc.onmessage = (e) => handleEvent(e.data);
      dc.onopen = () => {
        // 사용자 음성도 텍스트로 받도록 활성화.
        dc.send(
          JSON.stringify({
            type: "session.update",
            session: { input_audio_transcription: { model: "whisper-1" } },
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

  const stop = () => {
    cleanup();
    setStatus("ended");
  };

  if (!session) {
    return (
      <div className="rounded-xl border border-dashed border-slate-200 bg-white p-10 text-center text-sm text-slate-400">
        "면접 모드 선택" 탭에서 지원 건과 모드를 고르고 면접을 시작하면 실시간 음성 면접관과 대화할 수 있습니다.
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <Card className="border border-slate-200 bg-white">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <Radio className="size-4 text-rose-600" />
            실시간 음성 면접관
            {status === "live" ? (
              <Badge className="gap-1 bg-rose-100 text-rose-700">
                <span className="size-2 animate-pulse rounded-full bg-rose-500" /> LIVE
              </Badge>
            ) : (
              <Badge className="bg-slate-100 text-slate-600">베타</Badge>
            )}
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <p className="text-sm text-slate-500">
            AI 면접관이 음성으로 질문합니다. 마이크로 자연스럽게 답하면 실시간으로 대화가 이어집니다.
            (회사·직무·면접 모드와 준비된 질문이 면접관에게 전달됩니다.)
          </p>

          {!supported && (
            <p className="rounded-lg bg-amber-50 p-3 text-sm text-amber-700">
              이 브라우저는 실시간 음성(WebRTC)을 지원하지 않습니다. 최신 Chrome/Edge 에서 이용해 주세요.
            </p>
          )}

          {supported && (
            <div className="flex flex-wrap items-center gap-2">
              {(status === "idle" || status === "ended" || status === "error") && (
                <Button onClick={start} className="gap-1.5 bg-rose-600 hover:bg-rose-700">
                  <Mic className="size-4" /> {status === "idle" ? "면접 시작" : "다시 시작"}
                </Button>
              )}
              {status === "connecting" && (
                <Button disabled className="gap-1.5">
                  <Loader2 className="size-4 animate-spin" /> 연결 중…
                </Button>
              )}
              {status === "live" && (
                <Button onClick={stop} variant="destructive" className="gap-1.5">
                  <PhoneOff className="size-4" /> 면접 종료
                </Button>
              )}
            </div>
          )}

          {error && <p className="text-sm text-red-500">{error}</p>}

          {/* 면접관 음성 출력 (자동 재생) */}
          <audio ref={audioRef} autoPlay className="hidden" />
        </CardContent>
      </Card>

      {lines.length > 0 && (
        <Card className="border border-slate-200 bg-white">
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
