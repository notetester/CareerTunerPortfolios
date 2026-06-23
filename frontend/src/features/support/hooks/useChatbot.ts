import { useState, useCallback, useRef } from "react";
import { api } from "@/app/lib/api";
import type { ChatMessage, BotStatus, VoiceState, ChatSession, SiteLink } from "../types/chatbot";

let msgId = 0;
const nextId = () => `msg-${++msgId}`;

/* ── API 응답 타입 (커뮤니티 챗봇 에이전트) ── */
interface ChatbotApiResponse {
  conversationId: number;
  message: string;
  links: SiteLink[];
  quickReplies: string[];
}

const MOCK_SESSIONS: ChatSession[] = [
  { id: "s1", title: "새 대화", lastMessage: "방금", meta: "", updatedAt: Date.now() },
];

export function useChatbot() {
  const [isOpen, setIsOpen] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [botStatus, setBotStatus] = useState<BotStatus>("idle");
  const [voiceState, setVoiceState] = useState<VoiceState>("idle");
  const [interimTranscript, setInterimTranscript] = useState("");
  const [sessions] = useState<ChatSession[]>(MOCK_SESSIONS);
  const [activeSessionId, setActiveSessionId] = useState<string>("s1");
  const abortRef = useRef<AbortController>();
  // 서버 발급 대화 ID. 새 대화면 null → 첫 응답에서 받아 보관, 이후 턴마다 재사용.
  const conversationIdRef = useRef<number | null>(null);

  const open = useCallback(() => setIsOpen(true), []);
  const close = useCallback(() => setIsOpen(false), []);
  const minimize = useCallback(() => setIsOpen(false), []);

  const sendMessage = useCallback((text: string) => {
    const userMsg: ChatMessage = {
      id: nextId(), role: "user", text,
      evidence: [], links: [], quickReplies: [], ttsState: "idle", ttsProgress: 0,
      timestamp: Date.now(),
    };
    setMessages((prev) => [...prev, userMsg]);
    setBotStatus("thinking");

    // 이전 요청 취소
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;

    api<ChatbotApiResponse>("/chatbot/ask", {
      method: "POST",
      body: JSON.stringify({ question: text, conversationId: conversationIdRef.current }),
      signal: controller.signal,
    })
      .then((data) => {
        if (controller.signal.aborted) return;

        // 대화 ID 보관 (다음 턴 맥락 유지)
        conversationIdRef.current = data.conversationId;

        // 에이전트가 메시지를 못 만들면 not_found
        if (!data.message || !data.message.trim()) {
          setBotStatus("not_found");
          return;
        }

        const botMsg: ChatMessage = {
          id: nextId(), role: "bot", text: data.message,
          evidence: [], links: data.links ?? [], quickReplies: data.quickReplies ?? [],
          ttsState: "idle", ttsProgress: 0,
          timestamp: Date.now(),
        };
        setMessages((prev) => [...prev, botMsg]);
        setBotStatus("answered");
      })
      .catch((err) => {
        if (controller.signal.aborted) return;
        console.error("챗봇 API 오류:", err);
        setBotStatus("disconnected");
      });
  }, []);

  const startVoice = useCallback(() => {
    setVoiceState("requesting");
    setTimeout(() => {
      if (navigator.mediaDevices) {
        setVoiceState("listening");
        setInterimTranscript("");
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
    conversationIdRef.current = null; // 새 대화 → 서버가 새 ID 발급
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
