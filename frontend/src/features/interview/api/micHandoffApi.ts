import { api } from "@/app/lib/api";

// 폰 마이크 핸드오프 시그널링 (데스크탑 화면 + 폰 마이크, 1차: 같은 망 STUN only).
// 백엔드 계약: /api/interview/mic-handoff — non-trickle SDP를 짧은 폴링으로 교환한다.
// 오디오 자체는 P2P(WebRTC)로 흘러 서버를 거치지 않는다.

export interface MicHandoffState {
  sessionId: number;
  phoneJoined: boolean;
  offerSdp: string | null;
  answerSdp: string | null;
}

/** 페어링 생성(데스크탑) → 6자리 연결 코드. */
export function createMicHandoff(sessionId: number): Promise<{ code: string }> {
  return api<{ code: string }>("/interview/mic-handoff", {
    method: "POST",
    body: JSON.stringify({ sessionId }),
  });
}

/** 교환 상태 조회 — role=phone 은 합류로 표시된다. */
export function getMicHandoffState(code: string, role: "desktop" | "phone"): Promise<MicHandoffState> {
  return api<MicHandoffState>(`/interview/mic-handoff/${code}?role=${role}`);
}

/** 데스크탑 offer SDP 게시. */
export function postMicHandoffOffer(code: string, sdp: string): Promise<void> {
  return api<void>(`/interview/mic-handoff/${code}/offer`, {
    method: "POST",
    body: JSON.stringify({ sdp }),
  });
}

/** 폰 answer SDP 게시. */
export function postMicHandoffAnswer(code: string, sdp: string): Promise<void> {
  return api<void>(`/interview/mic-handoff/${code}/answer`, {
    method: "POST",
    body: JSON.stringify({ sdp }),
  });
}

/** 페어링 종료. */
export function closeMicHandoff(code: string): Promise<void> {
  return api<void>(`/interview/mic-handoff/${code}`, { method: "DELETE" });
}

/** ICE 후보 수집 완료까지 대기 (non-trickle). 느린 망 대비 타임아웃 후 현재 SDP로 진행. */
export function waitIceGatheringComplete(pc: RTCPeerConnection, timeoutMs = 3000): Promise<void> {
  if (pc.iceGatheringState === "complete") return Promise.resolve();
  return new Promise((resolve) => {
    const timer = setTimeout(finish, timeoutMs);
    function finish() {
      clearTimeout(timer);
      pc.removeEventListener("icegatheringstatechange", onChange);
      resolve();
    }
    function onChange() {
      if (pc.iceGatheringState === "complete") finish();
    }
    pc.addEventListener("icegatheringstatechange", onChange);
  });
}

/** 공용 STUN 폴백 (백엔드 ICE 조회 실패 시). */
const STUN_FALLBACK: RTCIceServer[] = [{ urls: "stun:stun.l.google.com:19302" }];

interface IceServerDto {
  urls: string[];
  username: string | null;
  credential: string | null;
}

/**
 * 백엔드에서 ICE 서버(STUN + 단기자격 TURN)를 받아온다.
 * TURN 이 포함되면 P2P 실패 시 서버 릴레이로 붙어 다른 망(LTE 등)에서도 연결된다.
 * 실패하면 STUN 만으로 폴백(같은 망에서는 여전히 동작).
 */
export async function fetchIceServers(): Promise<RTCIceServer[]> {
  try {
    const dto = await api<IceServerDto[]>("/interview/mic-handoff/ice");
    const servers = dto.map((s) =>
      s.username && s.credential
        ? { urls: s.urls, username: s.username, credential: s.credential }
        : { urls: s.urls },
    );
    return servers.length > 0 ? servers : STUN_FALLBACK;
  } catch {
    return STUN_FALLBACK;
  }
}
