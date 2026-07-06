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

/** 1차 스코프: 공용 STUN만 사용(같은 와이파이 전제). TURN 은 2차에서 추가. */
export const MIC_HANDOFF_ICE: RTCConfiguration = {
  iceServers: [{ urls: "stun:stun.l.google.com:19302" }],
};
