import { useEffect, useRef, useState } from "react";
import { useSearchParams } from "react-router";
import { useAuth } from "@/app/auth/AuthContext";
import { Loader2, Mic, MicOff, PhoneOff, Smartphone, Video } from "lucide-react";
import { Button } from "@/app/components/ui/button";
import {
  closeMicHandoff,
  fetchIceServers,
  getMicHandoffState,
  postMicHandoffAnswer,
  waitIceGatheringComplete,
} from "../api/micHandoffApi";

type Phase = "idle" | "joining" | "connecting" | "connected" | "ended" | "error";

/**
 * 폰 마이크/카메라 송신 페이지 — 데스크탑 면접의 원격 마이크(또는 카메라)가 된다.
 * 데스크탑 화면에 뜬 6자리 코드를 입력하면 이 기기의 오디오(+ ?video=1 이면 카메라 영상)를 WebRTC 로 전송한다.
 * (오디오/영상은 P2P — TURN 릴레이 폴백이 있어 다른 망에서도 붙는다.)
 */
export function MicRemotePage() {
  const { isAuthenticated } = useAuth();
  const [searchParams] = useSearchParams();
  const videoParam = searchParams.get("video") === "1";
  // 실제 카메라 전송 여부 — URL 힌트로 시작하되, 연결 시 데스크탑 offer SDP(m=video)가 단일 진실 소스.
  // (로그인 리다이렉트·수동 코드 입력으로 URL 파라미터가 유실돼도 offer 를 보고 카메라를 켠다.)
  const [wantVideo, setWantVideo] = useState(videoParam);
  const [code, setCode] = useState(searchParams.get("code") ?? "");
  const [phase, setPhase] = useState<Phase>("idle");
  const [error, setError] = useState<string | null>(null);
  const [muted, setMuted] = useState(false);

  const pcRef = useRef<RTCPeerConnection | null>(null);
  const micRef = useRef<MediaStream | null>(null);
  const localVideoRef = useRef<HTMLVideoElement | null>(null);
  const codeRef = useRef<string>("");
  const wakeLockRef = useRef<{ release: () => Promise<void> } | null>(null);

  useEffect(() => {
    return () => {
      void wakeLockRef.current?.release().catch(() => undefined);
      pcRef.current?.close();
      micRef.current?.getTracks().forEach((t) => t.stop());
    };
  }, []);

  // 연결 후 셀프뷰 <video> 가 렌더되면 카메라 스트림을 바인딩한다(획득 시점엔 아직 없을 수 있음).
  useEffect(() => {
    if (wantVideo && phase === "connected" && localVideoRef.current && micRef.current) {
      localVideoRef.current.srcObject = micRef.current;
    }
  }, [phase, wantVideo]);

  const connect = async () => {
    const trimmed = code.trim();
    if (!/^\d{6}$/.test(trimmed)) {
      setError("데스크탑 화면에 표시된 6자리 코드를 입력해 주세요.");
      return;
    }
    setPhase("joining");
    setError(null);
    codeRef.current = trimmed;
    try {
      // ① 합류 + 데스크탑 offer 대기 (보통 즉시 있음).
      let offerSdp: string | null = null;
      for (let i = 0; i < 30; i++) {
        const state = await getMicHandoffState(trimmed, "phone");
        if (state.offerSdp) {
          offerSdp = state.offerSdp;
          break;
        }
        await new Promise((r) => setTimeout(r, 1000));
      }
      if (!offerSdp) throw new Error("데스크탑의 연결 준비를 기다리다 시간이 지났습니다. 다시 시도해 주세요.");

      // ② 마이크(+ 카메라) 획득 → answer 생성/게시.
      // 카메라 필요 여부는 데스크탑 offer 가 결정한다(m=video 포함 = 화상 면접).
      const useVideo = /\r?\nm=video\b/.test(offerSdp) || offerSdp.startsWith("m=video");
      setWantVideo(useVideo);
      setPhase("connecting");
      const [mic, iceServers] = await Promise.all([
        navigator.mediaDevices.getUserMedia({
          audio: { echoCancellation: true, noiseSuppression: true },
          video: useVideo ? { facingMode: "user", width: { ideal: 1280 }, height: { ideal: 720 } } : false,
        }),
        fetchIceServers(),
      ]);
      micRef.current = mic;
      if (useVideo && localVideoRef.current) localVideoRef.current.srcObject = mic;

      const pc = new RTCPeerConnection({ iceServers });
      pcRef.current = pc;
      pc.onconnectionstatechange = () => {
        if (pc.connectionState === "connected") setPhase("connected");
        if (pc.connectionState === "failed") {
          setError("연결에 실패했습니다. 네트워크 상태를 확인하고 다시 시도해 주세요.");
          setPhase("error");
        }
        if (pc.connectionState === "disconnected") setPhase("ended");
      };
      await pc.setRemoteDescription({ type: "offer", sdp: offerSdp });
      mic.getTracks().forEach((t) => pc.addTrack(t, mic));
      const answer = await pc.createAnswer();
      await pc.setLocalDescription(answer);
      await waitIceGatheringComplete(pc);
      await postMicHandoffAnswer(trimmed, pc.localDescription?.sdp ?? answer.sdp ?? "");

      // 전송 중 화면 꺼짐 방지 (지원 기기 한정, 실패 무시).
      try {
        const nav = navigator as Navigator & {
          wakeLock?: { request: (type: "screen") => Promise<{ release: () => Promise<void> }> };
        };
        wakeLockRef.current = (await nav.wakeLock?.request("screen")) ?? null;
      } catch {
        // wake lock 미지원/거부 — 치명적이지 않다.
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "연결에 실패했습니다.");
      setPhase("error");
      pcRef.current?.close();
      micRef.current?.getTracks().forEach((t) => t.stop());
    }
  };

  const toggleMute = () => {
    const next = !muted;
    setMuted(next);
    micRef.current?.getAudioTracks().forEach((t) => (t.enabled = !next));
  };

  const end = () => {
    void wakeLockRef.current?.release().catch(() => undefined);
    pcRef.current?.close();
    micRef.current?.getTracks().forEach((t) => t.stop());
    if (codeRef.current) void closeMicHandoff(codeRef.current).catch(() => undefined);
    setPhase("ended");
  };

  // QR로 열었는데 이 폰 브라우저에 로그인 세션이 없으면 API(같은계정 검증)가 막힌다 → 로그인 유도.
  if (!isAuthenticated) {
    const params = new URLSearchParams();
    if (code) params.set("code", code);
    if (videoParam) params.set("video", "1");
    const qs = params.toString();
    const returnTo = `/mic-remote${qs ? `?${qs}` : ""}`;
    return (
      <div className="mx-auto flex min-h-[70vh] max-w-md flex-col items-center justify-center px-4 py-10">
        <div className="w-full rounded-2xl border border-slate-200 bg-card p-6 text-center shadow-sm">
          <div className="mx-auto flex size-12 items-center justify-center rounded-xl bg-indigo-50 text-indigo-600">
            <Smartphone className="size-6" />
          </div>
          <h1 className="mt-3 text-lg font-black text-slate-900">로그인이 필요합니다</h1>
          <p className="mt-1 text-sm leading-6 text-slate-500">
            데스크탑과 <b>같은 계정</b>으로 로그인해야 마이크를 연결할 수 있어요.
          </p>
          {code && (
            <p className="mt-2 rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-500">
              연결 코드 <b className="font-mono tracking-widest text-slate-700">{code}</b> 는
              기억해 뒀습니다. 로그인 후 이 화면으로 돌아오면 자동으로 채워져요.
            </p>
          )}
          <a
            href={`/login?returnTo=${encodeURIComponent(returnTo)}`}
            className="mt-4 inline-flex w-full items-center justify-center rounded-xl bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-indigo-700"
          >
            로그인하러 가기
          </a>
        </div>
      </div>
    );
  }

  return (
    <div className="mx-auto flex min-h-[70vh] max-w-md flex-col items-center justify-center px-4 py-10">
      <div className="w-full rounded-2xl border border-slate-200 bg-card p-6 text-center shadow-sm">
        <div className="mx-auto flex size-12 items-center justify-center rounded-xl bg-indigo-50 text-indigo-600">
          {wantVideo ? <Video className="size-6" /> : <Smartphone className="size-6" />}
        </div>
        <h1 className="mt-3 text-lg font-black text-slate-900">
          {wantVideo ? "폰 카메라로 연결" : "폰 마이크로 연결"}
        </h1>
        <p className="mt-1 text-sm leading-6 text-slate-500">
          {wantVideo
            ? "데스크탑에서 진행 중인 화상 면접에 이 폰의 카메라와 마이크를 연결합니다. 다른 네트워크(LTE 등)여도 연결됩니다."
            : "데스크탑에서 진행 중인 음성 모의면접에 이 폰의 마이크를 연결합니다. 다른 네트워크(LTE 등)여도 연결됩니다."}
        </p>

        {(phase === "idle" || phase === "error") && (
          <div className="mt-5 space-y-3">
            <input
              value={code}
              onChange={(e) => setCode(e.target.value.replace(/\D/g, "").slice(0, 6))}
              inputMode="numeric"
              placeholder="6자리 코드"
              className="w-full rounded-xl border border-slate-300 bg-card px-4 py-3 text-center font-mono text-2xl font-black tracking-[0.3em] text-slate-900 outline-none focus:border-indigo-500"
            />
            {error && <p className="text-xs text-red-500">{error}</p>}
            <Button onClick={connect} className="w-full gap-1.5 bg-indigo-600 hover:bg-indigo-700">
              {wantVideo ? <Video className="size-4" /> : <Mic className="size-4" />}
              {wantVideo ? "카메라 연결" : "마이크 연결"}
            </Button>
          </div>
        )}

        {(phase === "joining" || phase === "connecting") && (
          <div className="mt-6 flex flex-col items-center gap-2 text-sm text-slate-500">
            <Loader2 className="size-6 animate-spin text-indigo-500" />
            {phase === "joining" ? "데스크탑 연결 준비를 기다리는 중…" : "P2P 연결 중…"}
          </div>
        )}

        {phase === "connected" && (
          <div className="mt-6 space-y-4">
            {wantVideo ? (
              <div className="mx-auto overflow-hidden rounded-xl bg-slate-900">
                <video
                  ref={localVideoRef}
                  autoPlay
                  muted
                  playsInline
                  className="mx-auto aspect-video w-full max-w-xs -scale-x-100 object-cover"
                />
              </div>
            ) : (
              <div className="relative mx-auto flex size-20 items-center justify-center rounded-full bg-rose-50">
                <span className="absolute size-20 animate-ping rounded-full bg-rose-200/60" />
                {muted ? (
                  <MicOff className="size-8 text-slate-400" />
                ) : (
                  <Mic className="size-8 text-rose-600" />
                )}
              </div>
            )}
            <p className="text-sm font-semibold text-slate-700">
              {muted
                ? "음소거됨"
                : wantVideo
                  ? "카메라 전송 중 — 데스크탑에서 면접을 진행하세요"
                  : "마이크 전송 중 — 데스크탑에서 면접을 진행하세요"}
            </p>
            <p className="text-xs text-slate-400">면접이 끝날 때까지 이 화면을 켜 두세요.</p>
            <div className="flex justify-center gap-2">
              <Button onClick={toggleMute} variant="outline" className="gap-1.5">
                {muted ? <Mic className="size-4" /> : <MicOff className="size-4" />}
                {muted ? "음소거 해제" : "음소거"}
              </Button>
              <Button onClick={end} variant="destructive" className="gap-1.5">
                <PhoneOff className="size-4" /> 연결 종료
              </Button>
            </div>
          </div>
        )}

        {phase === "ended" && (
          <div className="mt-6 space-y-3">
            <p className="text-sm font-semibold text-slate-600">연결이 종료됐습니다.</p>
            <Button
              onClick={() => {
                setPhase("idle");
                setMuted(false);
              }}
              variant="outline"
            >
              다시 연결
            </Button>
          </div>
        )}
      </div>
    </div>
  );
}
