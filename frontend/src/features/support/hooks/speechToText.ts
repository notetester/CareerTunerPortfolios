// 챗봇 마이크 STT — Web Speech API(SpeechRecognition) 래퍼.
//
// interview/hooks/speechToText.ts(BrowserSttTracker)의 복사·개조본. 원본은 답변 녹음과 병행하는
// 최종 전사 누적 전용(interimResults=false)이라 "말하는 동안 실시간 표시"가 필요한 챗봇 입력과 맞지 않고,
// 타 도메인(interview) 파일이라 수정 대신 복사로 소유 경계를 지킨다 — cross-feature import 금지.
//
// ⚠️ Chrome/Edge(webkitSpeechRecognition) 전용이며 인식 시 브라우저가 벤더 서버를 경유한다(완전 오프라인 아님).
//    미지원 브라우저는 isSpeechInputSupported()=false — 위젯이 마이크 버튼 자체를 숨긴다.
//
// SpeechRecognition 은 아직 표준화 중이라 lib.dom 에 타입이 없어 최소 형태로 직접 다룬다.

type RecognitionAlternativeLike = { transcript: string };
type RecognitionResultLike = { isFinal: boolean; 0: RecognitionAlternativeLike };
type RecognitionEventLike = { resultIndex: number; results: ArrayLike<RecognitionResultLike> };
type RecognitionErrorLike = { error?: string };
type RecognitionLike = {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  onresult: ((e: RecognitionEventLike) => void) | null;
  onerror: ((e: RecognitionErrorLike) => void) | null;
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

/** 브라우저가 Web Speech STT 를 지원하는지(Chrome/Edge). false 면 마이크 입력을 노출하지 않는다. */
export function isSpeechInputSupported(): boolean {
  return getRecognitionCtor() != null;
}

export interface ChatSttCallbacks {
  /** 인식 진행마다 호출 — interim: 발화 중 미확정 조각, finals: 지금까지 확정된 누적 전사(둘 다 공백 정리됨). */
  onResult: (interim: string, finals: string) => void;
  /** 마이크 권한 거부·장치 없음 — 자동 재시작을 멈추므로 권한 안내 UI 로 전환할 신호. */
  onDenied: () => void;
}

/** 세션을 끝내는 오류들 — 이 외(no-speech/aborted 등)는 onend 자동 재시작이 이어받는다. */
const FATAL_ERRORS = new Set(["not-allowed", "service-not-allowed", "audio-capture"]);

/**
 * 챗봇 음성 입력 한 세션. start() 로 열고 onResult 로 실시간 전사를 흘리며,
 * stop() 은 인식을 끝내고 최종 전사(확정 누적 + 마지막 interim = 화면 표시분)를 돌려준다.
 * abort() 는 전사를 버리고 즉시 폐기한다(취소/위젯 닫힘/언마운트).
 * continuous 인식이라도 침묵/타임아웃으로 끊길 수 있어, 명시적 stop/abort 전까진 자동 재시작한다.
 */
export class ChatSpeechTracker {
  private rec: RecognitionLike | null = null;
  private finalText = "";
  private interimText = "";
  private keepAlive = false;

  constructor(private readonly cb: ChatSttCallbacks) {}

  start() {
    const Ctor = getRecognitionCtor();
    if (!Ctor) {
      // 위젯은 미지원이면 버튼을 숨기므로 보통 도달하지 않는다 — 잔여 호출측(FullScreen 등) 방어.
      this.cb.onDenied();
      return;
    }
    this.finalText = "";
    this.interimText = "";
    this.keepAlive = true;
    this.spawn(Ctor);
  }

  private spawn(Ctor: new () => RecognitionLike) {
    const rec = new Ctor();
    rec.lang = "ko-KR";
    rec.continuous = true;
    rec.interimResults = true;
    rec.onresult = (e) => {
      let interim = "";
      for (let i = e.resultIndex; i < e.results.length; i++) {
        const r = e.results[i];
        if (r.isFinal) this.finalText += r[0].transcript + " ";
        else interim += r[0].transcript;
      }
      this.interimText = collapse(interim);
      this.cb.onResult(this.interimText, collapse(this.finalText));
    };
    rec.onerror = (e) => {
      if (e.error && FATAL_ERRORS.has(e.error)) {
        this.keepAlive = false;
        this.rec = null;
        this.cb.onDenied();
      }
    };
    rec.onend = () => {
      if (this.keepAlive && this.rec === rec) {
        // 재시작 경계에서 미확정 interim 은 브라우저가 버린다 — 표시도 확정 누적으로 되돌려 유령 텍스트를 막는다.
        this.interimText = "";
        this.cb.onResult("", collapse(this.finalText));
        try {
          rec.start();
        } catch {
          /* 재시작 실패는 무시 — 사용자가 확인/취소로 세션을 마무리한다 */
        }
      }
    };
    try {
      rec.start();
    } catch {
      /* 시작 실패(중복 세션 등)는 무시 */
    }
    this.rec = rec;
  }

  /** 인식을 멈추고 최종 전사를 돌려준다 — 확정 누적 + 아직 확정 전인 마지막 interim(화면 표시와 동일). */
  stop(): string {
    this.keepAlive = false;
    try {
      this.rec?.stop();
    } catch {
      /* noop */
    }
    this.rec = null;
    return collapse(`${this.finalText} ${this.interimText}`);
  }

  /** 전사를 버리고 즉시 중단한다. */
  abort() {
    this.keepAlive = false;
    try {
      this.rec?.abort();
    } catch {
      /* noop */
    }
    this.rec = null;
  }
}

function collapse(s: string): string {
  return s.replace(/\s+/g, " ").trim();
}
