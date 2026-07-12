// 크로스플랫폼 미디어 지원 헬퍼.
//
// 면접 탭들이 쓰는 MediaRecorder/Web Speech 는 플랫폼별 지원 편차가 크다:
//  - Android WebView(Capacitor)/iOS WKWebView 는 webm 대신 mp4 계열만 지원하기도 하고,
//    speechSynthesis/webkitSpeechRecognition 이 아예 없을 수 있다.
//  - LAN http(비보안) 오리진에서는 navigator.mediaDevices 자체가 undefined 다.
// 이 파일이 mimeType 협상·원인 판별을 한 곳에 모아, 각 탭은 결과만 쓰게 한다.

export type RecorderKind = "audio" | "video";

export interface RecorderMimeChoice {
  /** MediaRecorder 생성 옵션에 넣을 mimeType. undefined 면 브라우저 기본값에 맡긴다. */
  mimeType?: string;
  /** 업로드 payload 의 format 필드 값 — blob.type 문자열 스니핑 대신 이 값을 쓴다. */
  format: "webm" | "mp4";
}

// webm/opus(크롬 계열) → mp4/aac(사파리·WebView) 순으로 협상한다.
const AUDIO_CANDIDATES: ReadonlyArray<readonly [string, "webm" | "mp4"]> = [
  ["audio/webm;codecs=opus", "webm"],
  ["audio/webm", "webm"],
  ["audio/mp4;codecs=mp4a.40.2", "mp4"],
  ["audio/mp4", "mp4"],
];

const VIDEO_CANDIDATES: ReadonlyArray<readonly [string, "webm" | "mp4"]> = [
  ["video/webm;codecs=vp9,opus", "webm"],
  ["video/webm;codecs=vp8,opus", "webm"],
  ["video/webm", "webm"],
  ["video/mp4;codecs=avc1.42E01E,mp4a.40.2", "mp4"],
  ["video/mp4", "mp4"],
];

/**
 * MediaRecorder.isTypeSupported 로 이 기기가 실제 녹음/녹화할 수 있는 mimeType 을 고른다.
 * 아무 후보도 지원하지 않으면(또는 isTypeSupported 미구현) mimeType 없이 브라우저 기본값에
 * 맡기고 format 은 webm 으로 가정한다(크롬 계열 기본값).
 */
export function pickRecorderMime(kind: RecorderKind): RecorderMimeChoice {
  if (
    typeof MediaRecorder === "undefined" ||
    typeof MediaRecorder.isTypeSupported !== "function"
  ) {
    return { format: "webm" };
  }
  const candidates = kind === "audio" ? AUDIO_CANDIDATES : VIDEO_CANDIDATES;
  for (const [mimeType, format] of candidates) {
    try {
      if (MediaRecorder.isTypeSupported(mimeType)) return { mimeType, format };
    } catch {
      // 일부 WebView 는 isTypeSupported 가 던질 수 있다 — 다음 후보로.
    }
  }
  return { format: "webm" };
}

/** 협상 결과로 MediaRecorder 를 만든다. mimeType 지정 생성이 실패하면 기본 생성으로 폴백. */
export function createNegotiatedRecorder(
  stream: MediaStream,
  kind: RecorderKind,
  bitrate: Pick<MediaRecorderOptions, "audioBitsPerSecond" | "videoBitsPerSecond"> = {},
): { recorder: MediaRecorder; format: "webm" | "mp4" } {
  const choice = pickRecorderMime(kind);
  try {
    return {
      recorder: new MediaRecorder(stream, {
        ...bitrate,
        ...(choice.mimeType ? { mimeType: choice.mimeType } : {}),
      }),
      format: choice.format,
    };
  } catch {
    try {
      // 일부 WebView는 특정 mimeType만 거부한다. bitrate 제한은 유지한 채 기본 컨테이너로 재시도한다.
      return { recorder: new MediaRecorder(stream, bitrate), format: choice.format };
    } catch {
      // 매우 오래된 WebView의 options 미지원 폴백. 호출부의 길이·실제 Blob 크기 상한이 최종 방어다.
      return { recorder: new MediaRecorder(stream), format: choice.format };
    }
  }
}

export type MediaBlockReason = "insecure-context" | "unsupported";

/**
 * navigator.mediaDevices 가 없을 때 원인을 판별한다.
 *  - "insecure-context": http(비보안) 오리진이라 브라우저가 카메라/마이크를 차단
 *  - "unsupported": 정말 미지원 브라우저
 *  - null: mediaDevices 사용 가능
 */
export function mediaUnsupportedReason(): MediaBlockReason | null {
  if (typeof navigator === "undefined") return "unsupported";
  if (navigator.mediaDevices) return null;
  if (typeof window !== "undefined" && window.isSecureContext === false) {
    return "insecure-context";
  }
  return "unsupported";
}

/**
 * 질문 TTS(speechSynthesis) 지원 여부. Android WebView(Capacitor) 등에는 없다.
 * 미지원이면 질문 읽기를 건너뛰고 화면 텍스트 강조로 진행한다 — 탭 자체를 막지 않는다.
 */
export function isTtsSupported(): boolean {
  return typeof window !== "undefined" && "speechSynthesis" in window && !!window.speechSynthesis;
}
