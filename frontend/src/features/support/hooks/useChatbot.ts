import { useState, useCallback, useRef } from "react";
import { api } from "@/app/lib/api";
import { getAccessToken } from "@/app/lib/tokenStore";
import { useAutoPrepRun } from "@/features/autoprep/hooks/useAutoPrepRun";
import type { AutoPrepRequest } from "@/features/autoprep/types/autoPrep";
import type {
  ChatMessage,
  BotStatus,
  VoiceState,
  ChatSession,
  SiteLink,
  IntakeCaseCandidate,
  IntakeModeOption,
} from "../types/chatbot";

let msgId = 0;
const nextId = () => `msg-${++msgId}`;

/* ── ③ 인테이크 한 턴 메타(백엔드 ChatAskResponse.IntakeStep) ── */
interface IntakeStepResp {
  ready: boolean;
  nextAsk: "CASE" | "MODE" | null;
  autoPrepRequest: AutoPrepRequest | null;
  candidates: IntakeCaseCandidate[];
  modes: IntakeModeOption[];
}

/* ── API 응답 타입 (통합 챗봇: ①FAQ/에이전트 + ③오케스트레이터 인테이크) ── */
interface ChatbotApiResponse {
  conversationId: number;
  message: string;
  links: SiteLink[];
  quickReplies: string[];
  route?: string;
  intake?: IntakeStepResp | null;
  /** 이 턴 이후 위젯이 오케스트레이터 모드를 유지해야 하는지의 단일 신호. */
  inOrchestration?: boolean;
}

/* ── 이전 대화 복원 응답 (GET /chatbot/conversations/recent) ── */
interface ChatHistoryResponse {
  conversationId: number;
  messages: { role: "user" | "bot"; text: string }[];
}

/* ── 세션 목록 API 응답 (GET /chatbot/conversations) ── */
interface SessionSummaryDto {
  conversationId: number;
  title: string | null;
}

export function useChatbot() {
  const [isOpen, setIsOpen] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [botStatus, setBotStatus] = useState<BotStatus>("idle");
  const [voiceState, setVoiceState] = useState<VoiceState>("idle");
  const [interimTranscript, setInterimTranscript] = useState("");
  const [sessions, setSessions] = useState<ChatSession[]>([]);
  const [activeSessionId, setActiveSessionId] = useState<string>("");

  // ── 오케스트레이터 모드 상태 ──
  const [orchestrator, setOrchestrator] = useState(false);   // 모드 배너/색 분기의 단일 소스
  const [runStarted, setRunStarted] = useState(false);       // ready 후 6단계 실행 진입 여부
  const [runCaseId, setRunCaseId] = useState<number | null>(null);
  const [showExitSheet, setShowExitSheet] = useState(false);
  const runStartedRef = useRef(false);                       // 클로저 안전용(exit 판정)
  const run = useAutoPrepRun();

  const abortRef = useRef<AbortController>();
  // 서버 발급 대화 ID. 새 대화면 null → 첫 응답에서 받아 보관, 이후 턴마다 재사용.
  const conversationIdRef = useRef<number | null>(null);
  // 복원은 세션당 1회만 시도 (열 때마다 재호출 방지).
  const restoredRef = useRef(false);

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
            ? prev
            : data.messages.map((m) => ({
                id: nextId(),
                role: m.role,
                text: m.text,
                evidence: [],
                links: [],
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

  /* ── 세션 목록(사이드바) 로드 — 로그인 유저의 인테이크(지원건) 세션 최대 5건. ── */
  const loadSessions = useCallback(() => {
    if (!getAccessToken()) { setSessions([]); return; }
    api<SessionSummaryDto[] | null>("/chatbot/conversations")
      .then((data) => {
        setSessions(
          (data ?? []).map((s) => ({
            id: String(s.conversationId),
            title: s.title || "면접 준비 세션",
            lastMessage: "면접 준비",
            meta: "",
            updatedAt: 0,
          })),
        );
      })
      .catch((err) => console.error("세션 목록 로드 실패:", err));
  }, []);

  /* ── 세션 클릭 → 그 conversationId 로 전환 + 메시지 로드. 다음 요청부터 백엔드가 슬롯 복원(Phase D). ── */
  const openSession = useCallback((id: string) => {
    const conversationId = Number(id);
    if (!Number.isFinite(conversationId)) return;
    conversationIdRef.current = conversationId;
    setActiveSessionId(id);
    run.reset();
    runStartedRef.current = false;
    setRunStarted(false);
    setRunCaseId(null);
    setOrchestrator(true); // 인테이크(지원건) 세션 — 모드 배너 유지
    setShowExitSheet(false);
    api<ChatHistoryResponse | null>(`/chatbot/conversations/${conversationId}/messages`)
      .then((data) => {
        const msgs: ChatMessage[] = (data?.messages ?? []).map((m) => ({
          id: nextId(), role: m.role, text: m.text,
          evidence: [], links: [], quickReplies: [],
          ttsState: "idle" as const, ttsProgress: 0, timestamp: Date.now(),
        }));
        setMessages(msgs);
        setBotStatus(msgs.length ? "answered" : "idle");
      })
      .catch((err) => console.error("세션 로드 실패:", err));
  }, [run]);

  const open = useCallback(() => {
    setIsOpen(true);
    restoreRecent();
    loadSessions();
  }, [restoreRecent, loadSessions]);
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

        conversationIdRef.current = data.conversationId;

        // 모드 신호: ON 이면 유지, OFF 면 (실행 중이 아닐 때만) 일반 모드로 복귀.
        if (data.inOrchestration) {
          setOrchestrator(true);
        } else if (!runStartedRef.current) {
          setOrchestrator(false);
        }

        if (!data.message || !data.message.trim()) {
          setBotStatus("not_found");
          return;
        }

        const intake = data.intake ?? null;
        const botMsg: ChatMessage = {
          id: nextId(), role: "bot", text: data.message,
          evidence: [], links: data.links ?? [], quickReplies: data.quickReplies ?? [],
          ttsState: "idle", ttsProgress: 0,
          timestamp: Date.now(),
          intake: intake
            ? {
                ready: intake.ready,
                nextAsk: intake.nextAsk,
                candidates: intake.candidates ?? [],
                modes: intake.modes ?? [],
              }
            : undefined,
        };
        setMessages((prev) => [...prev, botMsg]);
        setBotStatus("answered");

        // 슬롯이 다 차면(ready) D 의 SSE 실행으로 이어간다(배너는 유지). 비로그인은 실행 불가 → 안내만.
        if (intake?.ready && intake.autoPrepRequest) {
          if (!getAccessToken()) {
            setMessages((prev) => [
              ...prev,
              {
                id: nextId(), role: "bot",
                text: "준비를 시작하려면 먼저 로그인이 필요해요. 로그인 후 다시 요청해 주세요.",
                evidence: [], links: [{ label: "로그인", url: "/login" }],
                quickReplies: [], ttsState: "idle", ttsProgress: 0, timestamp: Date.now(),
              },
            ]);
            return;
          }
          runStartedRef.current = true;
          setRunStarted(true);
          setRunCaseId(intake.autoPrepRequest.applicationCaseId ?? null);
          run.start(intake.autoPrepRequest);
        }
      })
      .catch((err) => {
        if (controller.signal.aborted) return;
        console.error("챗봇 API 오류:", err);
        setBotStatus("disconnected");
      });
  }, [run]);

  /* ── 칩 선택 → 자연어 메시지로 변환해 전송(③ 슬롯 접지: chooseCase/chooseMode). ── */
  const selectCase = useCallback((c: IntakeCaseCandidate) => {
    sendMessage(`${c.companyName} ${c.jobTitle} 지원 건으로 진행할게요`);
  }, [sendMessage]);

  const selectMode = useCallback((m: IntakeModeOption) => {
    sendMessage(`${m.label}으로 할게요`);
  }, [sendMessage]);

  /* ── 모드 이탈: 실행 전이면 백엔드 모드 해제("그만"), 실행 중/후면 로컬 정리만. ── */
  const exitOrchestrator = useCallback(() => {
    setShowExitSheet(false);
    const wasRunning = runStartedRef.current;
    run.reset();
    runStartedRef.current = false;
    setRunStarted(false);
    setRunCaseId(null);
    setOrchestrator(false);
    if (!wasRunning) {
      sendMessage("그만"); // 인테이크 단계 → 서버 sticky 해제
    }
  }, [run, sendMessage]);

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
    run.reset();
    runStartedRef.current = false;
    setRunStarted(false);
    setRunCaseId(null);
    setOrchestrator(false);
    setShowExitSheet(false);
  }, [run]);

  return {
    isOpen, open, close, minimize, restoreRecent,
    messages, sendMessage,
    botStatus, setBotStatus,
    voiceState, startVoice, cancelVoice, confirmVoice, setVoiceState,
    interimTranscript,
    retryConnection,
    toggleTts,
    sessions, activeSessionId, setActiveSessionId, newSession,
    loadSessions, openSession,
    // 오케스트레이터
    orchestrator,
    runStarted,
    runParts: run.parts,
    runRunning: run.running,
    runPlan: run.plan,
    runError: run.error,
    runCaseId,
    selectCase, selectMode,
    showExitSheet,
    openExitSheet: () => setShowExitSheet(true),
    closeExitSheet: () => setShowExitSheet(false),
    exitOrchestrator,
  };
}
