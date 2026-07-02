// 브라우저 내장 음성인식(Web Speech API SpeechRecognition) STT 래퍼.
//
// serve(faster-whisper) 미기동 시 답변 전사 폴백용. 질문 읽기(TTS, SpeechSynthesis)와 같은
// Web Speech 계열이라 베이직(무료·자체) 정체성을 유지한다. serve 가 살아 있으면 faster-whisper 를
// 우선 쓰고(고품질), 죽었을 때만 이 폴백으로 전사를 확보해 내용 채점(haiku)까지 이어지게 한다.
//
// ⚠️ 품질은 faster-whisper 보다 낮고, Chrome/Edge(webkitSpeechRecognition) 에서만 동작하며
//    인식 시 브라우저가 구글 서버를 경유한다(완전 오프라인 아님). 폴백 용도라 이 한계를 감수한다.
//
// SpeechRecognition 은 아직 표준화 중이라 lib.dom 에 타입이 없어 최소 형태로 직접 다룬다.

type RecognitionAlternativeLike = { transcript: string };
type RecognitionResultLike = { isFinal: boolean; 0: RecognitionAlternativeLike };
type RecognitionEventLike = { resultIndex: number; results: ArrayLike<RecognitionResultLike> };
type RecognitionLike = {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  onresult: ((e: RecognitionEventLike) => void) | null;
  onerror: (() => void) | null;
  onend: (() => void) | null;
  start: () => void;
  stop: () => void;
  abort: () => void;
};

function getRecognitionCtor(): (new () => RecognitionLike) | null {
  if (typeof window === "undefined") return null;
  const w = window as unknown as {
    SpeechRecognition?: new () => RecognitionLike;
    webkitSpeechRecognition?: new () => RecognitionLike;
  };
  return w.SpeechRecognition ?? w.webkitSpeechRecognition ?? null;
}

/** 브라우저가 Web Speech STT 를 지원하는지(Chrome/Edge). 미지원이면 폴백 전사는 빈 문자열이 된다. */
export function isBrowserSttSupported(): boolean {
  return getRecognitionCtor() != null;
}

/**
 * 녹음과 병행해 브라우저 음성인식으로 답변 텍스트를 누적한다.
 * <p>{@code start()} 로 시작, {@code stop()} 으로 멈추며 누적 전사를 돌려준다.
 * continuous 인식이라도 침묵/타임아웃으로 세션이 끊길 수 있어, 명시적 stop 전까진 자동 재시작해
 * 답변 전체를 놓치지 않는다. 미지원 브라우저에서는 조용히 no-op(빈 전사) 이다.
 */
export class BrowserSttTracker {
  private rec: RecognitionLike | null = null;
  private finalText = "";
  private keepAlive = false;

  start() {
    const Ctor = getRecognitionCtor();
    if (!Ctor) return;
    this.finalText = "";
    this.keepAlive = true;
    this.spawn(Ctor);
  }

  private spawn(Ctor: new () => RecognitionLike) {
    const rec = new Ctor();
    rec.lang = "ko-KR";
    rec.continuous = true;
    rec.interimResults = false;
    rec.onresult = (e) => {
      for (let i = e.resultIndex; i < e.results.length; i++) {
        const r = e.results[i];
        if (r.isFinal) this.finalText += r[0].transcript + " ";
      }
    };
    rec.onerror = () => {
      // no-speech/aborted 등은 무시 — onend 의 자동 재시작이 이어받는다.
    };
    rec.onend = () => {
      // 명시적 stop 전에 끊기면(침묵 등) 자동 재시작해 답변 전체를 잡는다.
      if (this.keepAlive && this.rec === rec) {
        try {
          rec.start();
        } catch {
          /* 재시작 실패는 무시(폴백) */
        }
      }
    };
    try {
      rec.start();
    } catch {
      /* 시작 실패(권한/중복 등)는 폴백이라 무시 */
    }
    this.rec = rec;
  }

  /** 인식을 멈추고 누적 전사를 돌려준다(공백 정리). */
  stop(): string {
    this.keepAlive = false;
    try {
      this.rec?.stop();
    } catch {
      /* noop */
    }
    this.rec = null;
    return this.finalText.replace(/\s+/g, " ").trim();
  }

  dispose() {
    this.keepAlive = false;
    try {
      this.rec?.abort();
    } catch {
      /* noop */
    }
    this.rec = null;
  }
}
