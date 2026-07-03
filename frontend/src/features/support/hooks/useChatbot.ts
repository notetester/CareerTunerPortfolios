import { useState, useCallback, useRef } from "react";
import { api } from "@/app/lib/api";
import { getAccessToken } from "@/app/lib/tokenStore";
import { useAutoPrepRun } from "@/features/autoprep/hooks/useAutoPrepRun";
import { displayCompany, displayJobTitle } from "@/features/autoprep/lib/caseLabels";
import type { AutoPrepRequest } from "@/features/autoprep/types/autoPrep";
import { getInterviewReport, listInterviewSessions } from "@/features/interview/api/interviewApi";
import type {
  ChatMessage,
  BotStatus,
  VoiceState,
  ChatSession,
  SiteLink,
  IntakeCaseCandidate,
  IntakeModeOption,
  InterviewReportCard,
} from "../types/chatbot";

// 면접 인계 대기 표식(브라우저 저장) — 챗봇에서 면접 페이지로 caseId 를 넘기고, 복귀 시 결과를 재조회한다.
const LS_AWAIT_INTERVIEW = "tunerbot:awaitInterview";

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
  /** 추천 후기 압축 요약 칩 — 검색된 글이 2개 이상일 때만 주입(아니면 null). */
  summaryChip?: { label: string; postIds: number[] } | null;
}

/* ── 이전 대화 복원 응답 (GET /chatbot/conversations/recent) ── */
interface ChatHistoryResponse {
  conversationId: number;
  messages: { role: "user" | "bot"; text: string }[];
  /** ④ 온보딩 진행 중 복원 시 현재 스텝 재표시(route 로 가이드 자동 매핑) — 아니면 null/undefined. */
  resume?: {
    route: string;
    message: string;
    quickReplies: string[];
    intake: IntakeStepResp | null;
  } | null;
}

/* ── 세션 목록 API 응답 (GET /chatbot/conversations) ── */
interface SessionSummaryDto {
  conversationId: number;
  title: string | null;
  mode: string | null;
  updatedAt: number | null;
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

  // ── 표면 크기(morph 크기 전환): corner(360×560) ↔ floating(970×606, 중앙+스크림) ──
  // 코너 모드(FAQ/커뮤니티)는 기본값 corner 유지. 온보딩/오케 진입 때만 floating.
  const [surface, setSurface] = useState<"corner" | "floating">("corner");
  const orchRef = useRef(false); // 오케 진입 "전이"에서만 자동 확장(매 턴 강제 확장 방지)
  const expandToFloating = useCallback(() => setSurface("floating"), []);
  const collapseToCorner = useCallback(() => setSurface("corner"), []);

  const abortRef = useRef<AbortController>();
  // 서버 발급 대화 ID. 새 대화면 null → 첫 응답에서 받아 보관, 이후 턴마다 재사용.
  const conversationIdRef = useRef<number | null>(null);
  // ③ 인테이크 가이드(스텝 UI)에서 올린 자소서 fileId — ready 시 run 요청에 프론트에서 병합한다.
  //   (백엔드 인테이크는 attachmentFileIds 를 아직 안 받음(2단계) — run 엔드포인트는 이미 받는 필드라 프로토콜 유지.)
  const pendingAttachmentIdsRef = useRef<number[]>([]);
  // 마지막 run 요청(첨부 병합 후 최종본) — 재시도 = 이 요청 전체 재실행. 세션 전환/이탈 시 비운다.
  const lastRunRequestRef = useRef<AutoPrepRequest | null>(null);
  const setPendingAttachments = useCallback((ids: number[]) => {
    pendingAttachmentIdsRef.current = ids;
  }, []);
  // 복원은 세션당 1회만 시도 (열 때마다 재호출 방지).
  const restoredRef = useRef(false);

  const restoreRecent = useCallback(() => {
    if (restoredRef.current) return;
    if (!getAccessToken()) return; // 비로그인은 복원 대상 아님
    restoredRef.current = true;
    api<ChatHistoryResponse | null>("/chatbot/conversations/recent")
      .then((data) => {
        if (!data) return; // 이전 대화 없음
        if (conversationIdRef.current != null) return; // 복원 응답 전에 유저가 대화를 시작함 — 덮지 않음
        // ★F-06: 대화 id 는 messages 유무와 무관하게 입양한다 — ④ 온보딩 턴은 설계상 메모리 미기록이라
        //   진행 중 새로고침이 messages=[] 로 오는데, 여기서 버리면 다음 전송이 새 대화를 발급해
        //   서버 인메모리 step 이 고아가 된다(입양하면 다음 턴이 ④진행중 게이트로 그대로 이어진다).
        conversationIdRef.current = data.conversationId;
        const restored: ChatMessage[] = (data.messages ?? []).map((m) => ({
          id: nextId(),
          role: m.role,
          text: m.text,
          evidence: [],
          links: [],
          quickReplies: [],
          ttsState: "idle" as const,
          ttsProgress: 0,
          timestamp: Date.now(),
        }));
        // ④ 재개 프롬프트 — 현재 스텝 재표시를 봇 메시지로 이어붙인다. route 가 실리므로 위젯의
        //   가이드 자동 오픈(ONB_ROUTE_PHASE)이 그대로 반응하고, 사용자는 "보이지 않는 질문"에
        //   답하는 대신 무엇을 이어가면 되는지 본다. ready 실행(run) 경로는 복원에서 절대 안 탄다.
        if (data.resume) {
          const r = data.resume;
          restored.push({
            id: nextId(), role: "bot", text: r.message,
            evidence: [], links: [], quickReplies: r.quickReplies ?? [],
            ttsState: "idle", ttsProgress: 0, timestamp: Date.now(),
            route: r.route,
            intake: r.intake
              ? {
                  ready: r.intake.ready,
                  nextAsk: r.intake.nextAsk,
                  candidates: r.intake.candidates ?? [],
                  modes: r.intake.modes ?? [],
                }
              : undefined,
          });
        }
        if (restored.length === 0) return; // 보여줄 것도 이어갈 스텝도 없음(id 입양만 하고 끝)
        setMessages((prev) => (prev.length > 0 ? prev : restored));
        // 넛지/칩 활성 조건(botStatus) 정합 — 진행 중 상태(thinking 등)는 덮지 않는다.
        setBotStatus((s) => (s === "idle" ? "answered" : s));
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
            updatedAt: s.updatedAt ?? 0,
            mode: s.mode,
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
    lastRunRequestRef.current = null; // 다른 세션의 요청으로 재시도하지 않게
    runStartedRef.current = false;
    setRunStarted(false);
    setRunCaseId(null);
    setOrchestrator(true); // 인테이크(지원건) 세션 — 모드 배너 유지
    orchRef.current = true;
    expandToFloating(); // 세션 열기 = 오케 진입 → 플로팅
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

  // ── 면접 인계: caseId 를 표식으로 남긴다(면접 페이지로 navigate 는 ChatbotWidget 에서). ──
  const markInterviewHandoff = useCallback((caseId: number | null) => {
    if (caseId == null) return;
    try {
      localStorage.setItem(LS_AWAIT_INTERVIEW, JSON.stringify({ caseId, ts: Date.now() }));
    } catch {
      /* localStorage 불가 환경 무시 */
    }
  }, []);

  // ── 복귀 결과: 면접 완료(리포트 생성)면 챗봇 말풍선에 결과 카드로 표시. ──
  // Push(브라우저 알림)와 별개 — 챗봇이 열릴 때 caseId 로 최근 완료 세션을 찾아 /report 를 재조회한다.
  const checkInterviewResult = useCallback(async () => {
    if (!getAccessToken()) return;
    let pending: { caseId: number; ts: number } | null = null;
    try {
      const raw = localStorage.getItem(LS_AWAIT_INTERVIEW);
      if (raw) pending = JSON.parse(raw);
    } catch {
      pending = null;
    }
    if (!pending) return;
    // 24시간 지나면 대기 해제(무한 재조회 방지).
    if (Date.now() - pending.ts > 24 * 60 * 60 * 1000) {
      localStorage.removeItem(LS_AWAIT_INTERVIEW);
      return;
    }
    try {
      // caseId 로 결과 조회 API 가 없어(sessionId 로만) → 세션 목록에서 해당 케이스의 최근 "완료" 세션을 찾는다.
      const page = await listInterviewSessions(0, 20);
      const done = page.sessions
        .filter((s) => s.applicationCaseId === pending!.caseId && s.totalScore != null)
        .sort((a, b) => b.id - a.id)[0];
      if (!done) return; // 아직 완료 전 → 다음 오픈에 재확인(표식 유지)
      const report = await getInterviewReport(done.id);
      const card: InterviewReportCard = {
        sessionId: done.id,
        caseId: pending.caseId,
        totalScore: report.totalScore,
        questionCount: report.questionCount,
        durationLabel: report.durationLabel,
        categories: report.categories,
        summaryFeedback: report.summaryFeedback,
      };
      setMessages((prev) => [
        ...prev,
        {
          id: nextId(), role: "bot",
          text: `면접 잘 마치셨어요. **총점 ${report.totalScore}점**이에요. 영역별 결과를 아래에서 확인하고, 보완점은 자소서 첨삭으로 이어서 다듬어 보세요.`,
          evidence: [], links: [], quickReplies: [], ttsState: "idle", ttsProgress: 0,
          timestamp: Date.now(), interviewReport: card,
        },
      ]);
      setBotStatus("answered");
      localStorage.removeItem(LS_AWAIT_INTERVIEW);
    } catch (e) {
      console.error("면접 결과 재조회 실패:", e); // 조용히 — 다음 오픈에 재시도
    }
  }, []);

  const open = useCallback(() => {
    setIsOpen(true);
    restoreRecent();
    loadSessions();
    void checkInterviewResult();
  }, [restoreRecent, loadSessions, checkInterviewResult]);
  // 닫기/최소화(버블로) 시 다음 오픈은 코너부터.
  const close = useCallback(() => { setIsOpen(false); setSurface("corner"); }, []);
  const minimize = useCallback(() => { setIsOpen(false); setSurface("corner"); }, []);

  const sendMessage = useCallback((text: string, opts?: { selectedCaseId?: number; selectedModeCode?: string }) => {
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
      body: JSON.stringify({
        question: text,
        conversationId: conversationIdRef.current,
        // ③ 칩/버튼 직접 선택 시 caseId·modeCode 를 실어 보낸다 → 백엔드가 qwen3 거치지 않고 결정적 confirm.
        ...(opts?.selectedCaseId != null ? { selectedCaseId: opts.selectedCaseId } : {}),
        ...(opts?.selectedModeCode ? { selectedModeCode: opts.selectedModeCode } : {}),
      }),
      signal: controller.signal,
    })
      .then((data) => {
        if (controller.signal.aborted) return;

        conversationIdRef.current = data.conversationId;

        // 모드 신호: ON 이면 유지, OFF 면 (실행 중이 아닐 때만) 일반 모드로 복귀.
        if (data.inOrchestration) {
          if (!orchRef.current) expandToFloating(); // 진입 전이에서만 확장(사용자가 최소화했으면 유지)
          orchRef.current = true;
          setOrchestrator(true);
        } else if (!runStartedRef.current) {
          orchRef.current = false;
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
          route: data.route,
          intake: intake
            ? {
                ready: intake.ready,
                nextAsk: intake.nextAsk,
                candidates: intake.candidates ?? [],
                modes: intake.modes ?? [],
                // ready 턴이면 "면접 보러 가기" 칩 딥링크용 caseId 를 메시지에 함께 보관.
                caseId: intake.autoPrepRequest?.applicationCaseId ?? null,
              }
            : undefined,
          summaryChip: data.summaryChip ?? undefined,
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
          // 인테이크 가이드에서 받아 둔 자소서 fileId 를 실행 요청에 병합(WRITE 가 소비). 1회성 — 쓰고 비운다.
          const extraAttachments = pendingAttachmentIdsRef.current;
          pendingAttachmentIdsRef.current = [];
          const runRequest = extraAttachments.length
            ? {
                ...intake.autoPrepRequest,
                attachmentFileIds: [
                  ...(intake.autoPrepRequest.attachmentFileIds ?? []),
                  ...extraAttachments,
                ],
              }
            : intake.autoPrepRequest;
          lastRunRequestRef.current = runRequest; // 재시도용 보관(첨부 병합 최종본)
          run.start(runRequest);
        }
      })
      .catch((err) => {
        if (controller.signal.aborted) return;
        console.error("챗봇 API 오류:", err);
        setBotStatus("disconnected");
      });
  }, [run]);

  /* ── 추천 후기 압축 요약 칩 → 묶음 요약 요청(POST /chatbot/summarize-posts). ── */
  const summarizePosts = useCallback((postIds: number[]) => {
    if (!postIds || postIds.length === 0) return;

    const userMsg: ChatMessage = {
      id: nextId(), role: "user", text: "추천 후기 요약해줘",
      evidence: [], links: [], quickReplies: [], ttsState: "idle", ttsProgress: 0,
      timestamp: Date.now(),
    };
    setMessages((prev) => [...prev, userMsg]);
    setBotStatus("thinking");

    api<ChatbotApiResponse>("/chatbot/summarize-posts", {
      method: "POST",
      body: JSON.stringify({ conversationId: conversationIdRef.current, postIds }),
    })
      .then((data) => {
        conversationIdRef.current = data.conversationId;
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
        console.error("추천 후기 요약 API 오류:", err);
        setBotStatus("disconnected");
      });
  }, []);

  /* ── 칩 선택 → 자연어 메시지로 변환해 전송(③ 슬롯 접지: chooseCase/chooseMode). ── */
  const selectCase = useCallback((c: IntakeCaseCandidate) => {
    // caseId 를 함께 실어 결정적 바인딩(qwen3 미경유). 텍스트는 대화 맥락·히스토리 자연스러움용으로 유지.
    // placeholder 원문은 발화 라벨로도 안 내보낸다(F-02·F-17 재유입 표면) — 바인딩은 caseId 가 권위라 안전.
    sendMessage(`${displayCompany(c.companyName)} ${displayJobTitle(c.jobTitle)} 지원 건으로 진행할게요`,
      { selectedCaseId: c.id });
  }, [sendMessage]);

  const selectMode = useCallback((m: IntakeModeOption) => {
    // modeCode 를 함께 실어 결정적 확정(case 확정된 상태에서만 백엔드가 반영).
    sendMessage(`${m.label}으로 할게요`, { selectedModeCode: m.code });
  }, [sendMessage]);

  /* ── run 재시도: 마지막 실행 요청 전체 재실행(부분 재실행 API 없음 — 인테이크 슬롯 재질문 없이 즉시). ── */
  const retryRun = useCallback(() => {
    const req = lastRunRequestRef.current;
    if (!req) return; // 실행 이력이 없으면 no-op(재시도 어포던스는 runStarted 이후에만 노출됨)
    void run.start(req);
  }, [run]);

  /* ── 모드 이탈: 실행 전이면 백엔드 모드 해제("그만"), 실행 중/후면 로컬 정리만. ── */
  const exitOrchestrator = useCallback(() => {
    setShowExitSheet(false);
    const wasRunning = runStartedRef.current;
    pendingAttachmentIdsRef.current = []; // 이탈 시 가이드 첨부 병합 예약 해제(다른 세션 오염 방지)
    lastRunRequestRef.current = null; // 이탈 후 재시도는 무의미(세션 문맥 이탈) — 오발사 방지
    run.reset();
    runStartedRef.current = false;
    orchRef.current = false;
    setRunStarted(false);
    setRunCaseId(null);
    setOrchestrator(false);
    collapseToCorner(); // 오케 이탈 → 코너로
    if (!wasRunning) {
      sendMessage("그만"); // 인테이크 단계 → 서버 sticky 해제
    }
  }, [run, sendMessage, collapseToCorner]);

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
    pendingAttachmentIdsRef.current = [];
    lastRunRequestRef.current = null;
    run.reset();
    runStartedRef.current = false;
    orchRef.current = false;
    setRunStarted(false);
    setRunCaseId(null);
    setOrchestrator(false);
    collapseToCorner();
    setShowExitSheet(false);
  }, [run, collapseToCorner]);

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
    retryRun,
    selectCase, selectMode,
    setPendingAttachments,
    summarizePosts,
    showExitSheet,
    openExitSheet: () => setShowExitSheet(true),
    closeExitSheet: () => setShowExitSheet(false),
    exitOrchestrator,
    // 표면 크기 전환(morph)
    surface,
    expandToFloating,
    collapseToCorner,
    // 면접 인계/복귀
    markInterviewHandoff,
  };
}
