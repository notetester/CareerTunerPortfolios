import { useState, useCallback, useRef } from "react";
import { api } from "@/app/lib/api";
import { getAccessToken } from "@/app/lib/tokenStore";
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

/* ── 이전 대화 복원 응답 (GET /chatbot/conversations/recent) ── */
interface ChatHistoryResponse {
  conversationId: number;
  messages: { role: "user" | "bot"; text: string }[];
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
  // 복원은 세션당 1회만 시도 (열 때마다 재호출 방지).
  const restoredRef = useRef(false);

  // 로그인 유저의 가장 최근 대화를 불러와 화면에 복원하고 conversationId 를 이어붙인다.
  // 비로그인/이전 대화 없음/이미 복원함 → 조용히 빈 채팅 유지.
  const restoreRecent = useCallback(() => {
    if (restoredRef.current) return;
    if (!getAccessToken()) return; // 비로그인은 복원 대상 아님
    restoredRef.current = true;
    api<ChatHistoryResponse | null>("/chatbot/conversations/recent")
      .then((data) => {
        if (!data || !data.messages?.length) return; // 이전 대화 없음
        conversationIdRef.current = data.conversationId;
        setMessages((prev) =>
          prev.length > 0
            ? prev // 사용자가 이미 대화 시작했으면 덮어쓰지 않음
            : data.messages.map((m) => ({
                id: nextId(),
                role: m.role,
                text: m.text,
                evidence: [],
                links: [], // 휘발성 → 복원 안 됨(텍스트만)
                quickReplies: [],
                ttsState: "idle" as const,
                ttsProgress: 0,
                timestamp: Date.now(),
              })),
        );
      })
      .catch((err) => {
        console.error("이전 대화 복원 실패:", err);
      });
  }, []);

  const open = useCallback(() => {
    setIsOpen(true);
    restoreRecent();
  }, [restoreRecent]);
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
    isOpen, open, close, minimize, restoreRecent,
    messages, sendMessage,
    botStatus, setBotStatus,
    voiceState, startVoice, cancelVoice, confirmVoice, setVoiceState,
    interimTranscript,
    retryConnection,
    toggleTts,
    sessions, activeSessionId, setActiveSessionId, newSession,
  };
}
