import { useCallback, useEffect, useRef, useState } from "react";
import { Loader2, Smartphone, Unplug, Video, Wifi } from "lucide-react";
import { QRCodeSVG } from "qrcode.react";
import { Button } from "@/app/components/ui/button";
import {
  closeMicHandoff,
  createMicHandoff,
  fetchIceServers,
  getMicHandoffState,
  postMicHandoffOffer,
  waitIceGatheringComplete,
} from "../api/micHandoffApi";

type Phase = "idle" | "pairing" | "waiting" | "connecting" | "connected" | "error";

/**
 * 폰 마이크/카메라 연결 카드(데스크탑 수신측) — 마이크나 카메라가 없는 기기에서 면접을 진행할 때,
 * 폰의 마이크(오디오)나 카메라(오디오+영상)를 WebRTC 로 받아 이 기기의 입력으로 쓴다.
 *
 * withVideo=true 면 폰 카메라 영상까지 받는다(아바타·화상 면접). false 면 오디오만(음성 면접).
 * 연결되면 onStream 으로 원격 MediaStream 을 올려보낸다. 소비측은 stream.clone() 으로 사용해
 * 면접 종료(cleanup)가 원본 연결을 끊지 않게 한다.
 */
export function RemoteMicConnectCard({
  sessionId,
  onStream,
  withVideo = false,
}: {
  sessionId: number;
  onStream: (stream: MediaStream | null) => void;
  withVideo?: boolean;
}) {
  const [phase, setPhase] = useState<Phase>("idle");
  const [code, setCode] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const pcRef = useRef<RTCPeerConnection | null>(null);
  const codeRef = useRef<string | null>(null);
  const stopPollRef = useRef(false);

  const teardown = useCallback(
    (notify: boolean) => {
      stopPollRef.current = true;
      pcRef.current?.close();
      pcRef.current = null;
      if (codeRef.current) {
        void closeMicHandoff(codeRef.current).catch(() => undefined);
        codeRef.current = null;
      }
      setCode(null);
      if (notify) onStream(null);
    },
    [onStream],
  );

  useEffect(() => {
    return () => teardown(false);
  }, [teardown]);

  const begin = async () => {
    setPhase("pairing");
    setError(null);
    stopPollRef.current = false;
    try {
      const [{ code: newCode }, iceServers] = await Promise.all([
        createMicHandoff(sessionId),
        fetchIceServers(),
      ]);
      codeRef.current = newCode;
      setCode(newCode);

      const pc = new RTCPeerConnection({ iceServers });
      pcRef.current = pc;
      pc.addTransceiver("audio", { direction: "recvonly" });
      if (withVideo) pc.addTransceiver("video", { direction: "recvonly" });
      pc.ontrack = (e) => {
        const stream = e.streams[0] ?? new MediaStream([e.track]);
        onStream(stream);
      };
      pc.onconnectionstatechange = () => {
        if (pc.connectionState === "connected") setPhase("connected");
        if (pc.connectionState === "failed") {
          setError("연결에 실패했습니다. 네트워크 상태를 확인하고 다시 시도해 주세요.");
          setPhase("error");
          teardown(true);
        }
        if (pc.connectionState === "disconnected" || pc.connectionState === "closed") {
          onStream(null);
        }
      };

      const offer = await pc.createOffer();
      await pc.setLocalDescription(offer);
      await waitIceGatheringComplete(pc);
      await postMicHandoffOffer(newCode, pc.localDescription?.sdp ?? offer.sdp ?? "");
      setPhase("waiting");

      // 폰의 answer 를 폴링으로 기다린다 (TTL 10분, 1초 간격).
      for (let i = 0; i < 600 && !stopPollRef.current; i++) {
        const state = await getMicHandoffState(newCode, "desktop");
        if (state.answerSdp) {
          setPhase("connecting");
          await pc.setRemoteDescription({ type: "answer", sdp: state.answerSdp });
          return; // 이후 onconnectionstatechange 가 connected 로 마무리
        }
        await new Promise((r) => setTimeout(r, 1000));
      }
      if (!stopPollRef.current) {
        setError("폰 연결 대기가 만료되었습니다. 다시 시도해 주세요.");
        setPhase("error");
        teardown(true);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "폰 마이크 연결에 실패했습니다.");
      setPhase("error");
      teardown(true);
    }
  };

  const disconnect = () => {
    teardown(true);
    setPhase("idle");
  };

  const remoteUrl = `${window.location.origin}${import.meta.env.BASE_URL.replace(/\/$/, "")}/mic-remote${
    code ? `?code=${code}${withVideo ? "&video=1" : ""}` : ""
  }`;

  return (
    <div className="rounded-xl border border-indigo-200 bg-indigo-50/50 p-4">
      <div className="flex items-center gap-2 text-sm font-semibold text-indigo-800">
        {withVideo ? <Video className="size-4" /> : <Smartphone className="size-4" />}
        {withVideo ? "폰을 카메라로 사용" : "폰을 마이크로 사용"}
        {phase === "connected" && (
          <span className="ml-1 inline-flex items-center gap-1 rounded-full bg-emerald-100 px-2 py-0.5 text-[11px] font-bold text-emerald-700">
            <Wifi className="size-3" /> 연결됨
          </span>
        )}
      </div>
      <p className="mt-1 text-xs leading-5 text-indigo-700/80">
        {withVideo
          ? "면접은 이 화면에서 진행하고, 답변 음성과 얼굴 영상을 폰 카메라로 받습니다. 폰이 다른 네트워크(LTE 등)여도 연결됩니다."
          : "면접은 이 화면에서 진행하고, 답변 음성만 폰 마이크로 받습니다. 폰이 다른 네트워크(LTE 등)여도 연결됩니다."}
      </p>

      {phase === "idle" && (
        <Button onClick={begin} size="sm" className="mt-3 gap-1.5 bg-indigo-600 hover:bg-indigo-700">
          <Smartphone className="size-4" /> 연결 코드 만들기
        </Button>
      )}

      {(phase === "pairing" || phase === "waiting" || phase === "connecting") && (
        <div className="mt-3">
          {code ? (
            <div className="flex flex-col gap-4 sm:flex-row sm:items-center">
              {/* QR — 폰 카메라로 스캔하면 코드가 채워진 채 바로 열린다 */}
              <div className="flex shrink-0 flex-col items-center gap-2 rounded-xl bg-card p-3 shadow-sm">
                <QRCodeSVG value={remoteUrl} size={132} marginSize={1} />
                <span className="text-[11px] font-semibold text-slate-500">폰 카메라로 스캔</span>
              </div>
              <div className="min-w-0 space-y-2">
                <p className="text-sm font-semibold text-indigo-800">
                  ① 폰 카메라(또는 QR 앱)로 위 코드를 스캔하세요.
                </p>
                <p className="text-xs leading-5 text-indigo-700/80">
                  스캔이 안 되면, 폰 브라우저에서 <b>{remoteUrl.replace(/^https?:\/\//, "")}</b> 로
                  직접 접속해도 됩니다. (같은 계정 로그인 필요)
                </p>
                <div className="flex items-center gap-2">
                  <span className="rounded-lg bg-indigo-100 px-3 py-1 font-mono text-lg font-black tracking-[0.25em] text-indigo-700">
                    {code}
                  </span>
                  <span className="flex items-center gap-1.5 text-xs text-indigo-600">
                    <Loader2 className="size-3.5 animate-spin" />
                    {phase === "connecting" ? "폰과 연결 중…" : "폰 접속 대기 중…"}
                  </span>
                </div>
                <Button onClick={disconnect} size="sm" variant="outline" className="gap-1">
                  취소
                </Button>
              </div>
            </div>
          ) : (
            <span className="flex items-center gap-1.5 text-xs text-indigo-600">
              <Loader2 className="size-3.5 animate-spin" /> 연결 코드 발급 중…
            </span>
          )}
        </div>
      )}

      {phase === "connected" && (
        <div className="mt-3 flex items-center gap-2">
          <p className="text-xs font-semibold text-emerald-700">
            {withVideo ? "폰 카메라가 연결됐습니다" : "폰 마이크가 연결됐습니다"} — 아래에서 면접을 시작하세요.
          </p>
          <Button onClick={disconnect} size="sm" variant="outline" className="gap-1">
            <Unplug className="size-3.5" /> 연결 해제
          </Button>
        </div>
      )}

      {phase === "error" && (
        <div className="mt-3 space-y-2">
          <p className="text-xs text-red-600">{error}</p>
          <Button onClick={begin} size="sm" variant="outline">
            다시 시도
          </Button>
        </div>
      )}
    </div>
  );
}
