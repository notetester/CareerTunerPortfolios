import { useState, useCallback, useRef } from "react";
import type { ChatMessage, ChatEvidence, BotStatus, VoiceState, ChatSession } from "../types/chatbot";

let msgId = 0;
const nextId = () => `msg-${++msgId}`;

/* ── Mock RAG responses ── */
const MOCK_ANSWERS: Record<string, { text: string; evidence: ChatEvidence[] }> = {
  "비밀번호": {
    text: '마이페이지 **설정 > 보안**에서 비밀번호를 바꿀 수 있어요. 로그인이 안 되면 로그인 화면의 **\'비밀번호 찾기\'**로 이메일 인증 후 재설정하면 됩니다.',
    evidence: [
      { id: "e1", type: "도움말", title: "비밀번호 변경 방법", snippet: "마이페이지 설정 > 보안 탭에서 현재 비밀번호 확인 후 변경. 8자 이상 권장…", url: "/support/faq" },
      { id: "e2", type: "가이드", title: "계정 보안 가이드", snippet: "2단계 인증 설정과 안전한 비밀번호를 만드는 방법, 로그인 기기 관리…", url: "/support/guide" },
    ],
  },
  "환불": {
    text: "환불은 **결제일로부터 7일 이내**에 가능해요. 마이페이지 > 결제 내역에서 '환불 요청'을 누르시면 됩니다. 처리는 영업일 기준 3~5일 소요됩니다.",
    evidence: [
      { id: "e3", type: "FAQ", title: "환불 및 취소 안내", snippet: "결제 후 7일 이내 환불 가능. 크레딧 사용 이력이 있으면 잔여분만 환불…", url: "/support/faq" },
    ],
  },
  "이력서": {
    text: '**마이페이지 > 프로필**에서 이력서를 등록하면, 지원 건에서 **분석 시작** 버튼을 눌러 AI 분석을 받을 수 있어요.',
    evidence: [
      { id: "e4", type: "가이드", title: "이력서 분석 시작 가이드", snippet: "프로필에 이력서 등록 후 지원 건 상세에서 분석 시작 클릭…", url: "/support/guide" },
    ],
  },
};

function findAnswer(text: string): { text: string; evidence: ChatEvidence[] } | null {
  const lower = text.toLowerCase();
  for (const [keyword, answer] of Object.entries(MOCK_ANSWERS)) {
    if (lower.includes(keyword)) return answer;
  }
  return null;
}

const MOCK_SESSIONS: ChatSession[] = [
  { id: "s1", title: "비밀번호 변경 방법", lastMessage: "방금", meta: "근거 2건", updatedAt: Date.now() },
  { id: "s2", title: "프로 요금제 환불 규정", lastMessage: "어제", meta: "", updatedAt: Date.now() - 86400000 },
  { id: "s3", title: "이력서 분석 결과 위치", lastMessage: "06.10", meta: "", updatedAt: Date.now() - 432000000 },
  { id: "s4", title: "모의면접 마이크 설정", lastMessage: "06.08", meta: "상담사 연결됨", updatedAt: Date.now() - 604800000 },
];

export function useChatbot() {
  const [isOpen, setIsOpen] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [botStatus, setBotStatus] = useState<BotStatus>("idle");
  const [voiceState, setVoiceState] = useState<VoiceState>("idle");
  const [interimTranscript, setInterimTranscript] = useState("");
  const [sessions] = useState<ChatSession[]>(MOCK_SESSIONS);
  const [activeSessionId, setActiveSessionId] = useState<string>("s1");
  const thinkingTimer = useRef<ReturnType<typeof setTimeout>>();

  const open = useCallback(() => setIsOpen(true), []);
  const close = useCallback(() => setIsOpen(false), []);
  const minimize = useCallback(() => setIsOpen(false), []);

  const sendMessage = useCallback((text: string) => {
    const userMsg: ChatMessage = {
      id: nextId(), role: "user", text,
      evidence: [], ttsState: "idle", ttsProgress: 0,
      timestamp: Date.now(),
    };
    setMessages((prev) => [...prev, userMsg]);
    setBotStatus("thinking");

    thinkingTimer.current = setTimeout(() => {
      const answer = findAnswer(text);
      if (answer) {
        const botMsg: ChatMessage = {
          id: nextId(), role: "bot", text: answer.text,
          evidence: answer.evidence, ttsState: "idle", ttsProgress: 0,
          timestamp: Date.now(),
        };
        setMessages((prev) => [...prev, botMsg]);
        setBotStatus("answered");
      } else {
        setBotStatus("not_found");
      }
    }, 1500);
  }, []);

  const startVoice = useCallback(() => {
    setVoiceState("requesting");
    // Simulate permission check
    setTimeout(() => {
      if (navigator.mediaDevices) {
        setVoiceState("listening");
        setInterimTranscript("이력서 분석 결과를 어디서");
      } else {
        setVoiceState("denied");
      }
    }, 500);
  }, []);

  const cancelVoice = useCallback(() => {
    setVoiceState("idle");
    setInterimTranscript("");
  }, []);

  const confirmVoice = useCallback(() => {
    if (interimTranscript) {
      sendMessage(interimTranscript);
    }
    setVoiceState("idle");
    setInterimTranscript("");
  }, [interimTranscript, sendMessage]);

  const retryConnection = useCallback(() => {
    setBotStatus("idle");
  }, []);

  const toggleTts = useCallback((messageId: string) => {
    setMessages((prev) =>
      prev.map((m) => {
        if (m.id !== messageId) return m;
        const next = m.ttsState === "playing" ? "paused" : "playing";
        return { ...m, ttsState: next };
      })
    );
  }, []);

  const newSession = useCallback(() => {
    setMessages([]);
    setBotStatus("idle");
  }, []);

  return {
    isOpen, open, close, minimize,
    messages, sendMessage,
    botStatus, setBotStatus,
    voiceState, startVoice, cancelVoice, confirmVoice, setVoiceState,
    interimTranscript,
    retryConnection,
    toggleTts,
    sessions, activeSessionId, setActiveSessionId, newSession,
  };
}
