import { useState, useCallback, useEffect, useRef } from "react";
import { api } from "@/app/lib/api";
import { useAuth } from "@/app/auth/AuthContext";
import type { AiModelChoice } from "@/app/components/ai/ModelPicker";
import { getAccessToken } from "@/app/lib/tokenStore";
import {
  deletePendingAutoPrepFile,
  discardPendingAutoPrepFiles,
} from "@/app/lib/pendingAutoPrepFiles";
import { uploadAttachment } from "@/features/autoprep/api/autoPrepApi";
import { useAutoPrepRun } from "@/features/autoprep/hooks/useAutoPrepRun";
import { displayCompany, displayJobTitle } from "@/features/autoprep/lib/caseLabels";
import type { AutoPrepRequest } from "@/features/autoprep/types/autoPrep";
import { getInterviewReport, listInterviewSessions } from "@/features/interview/api/interviewApi";
import { BrowserSttTracker, isBrowserSttSupported } from "@/features/interview/hooks/speechToText";
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
import {
  ChatbotRequestScope,
  chatbotAccountKey,
  interviewHandoffStorageKey,
} from "./chatbotAccountScopeCore.mjs";

// v1의 계정 공용 키는 소유자를 판별할 수 없으므로 읽지 않고 제거만 한다.
const LEGACY_LS_AWAIT_INTERVIEW = "tunerbot:awaitInterview";

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
    /** 서버 확정 수집값(사용자 답 원문) — 가이드 재마운트 하이드레이션용(표시). 미수집 필드 null. */
    collected?: { job: string | null; skills: string | null } | null;
  } | null;
}

/** ④ 재진입 하이드레이션용 수집값(가이드 패널에 내려보냄). */
export interface OnbCollected {
  job: string | null;
  skills: string | null;
}

/* ── 세션 목록 API 응답 (GET /chatbot/conversations) ── */
interface SessionSummaryDto {
  conversationId: number;
  title: string | null;
  mode: string | null;
  updatedAt: number | null;
  kind: "INTAKE" | "GENERAL" | null;
}

export function useChatbot() {
  const { user } = useAuth();
  const accountId = user?.id ?? null;
  const accountKey = chatbotAccountKey(accountId);
  const [stateAccountKey, setStateAccountKey] = useState(accountKey);
  const [isOpen, setIsOpen] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [botStatus, setBotStatus] = useState<BotStatus>("idle");
  const [voiceState, setVoiceState] = useState<VoiceState>("idle");
  const [interimTranscript, setInterimTranscript] = useState("");
  // 브라우저 STT 미지원이면 위젯이 마이크 버튼을 숨긴다. (진행 세션 ref = voiceRecognizerRef)
  const voiceSupported = isBrowserSttSupported();
  const [sessions, setSessions] = useState<ChatSession[]>([]);
  // openSession 이 클릭 시점의 목록에서 kind 를 조회한다 — 상태를 deps 로 끌면 콜백이 목록 갱신마다 재생성돼 ref 로 고정.
  const sessionsRef = useRef<ChatSession[]>([]);
  useEffect(() => { sessionsRef.current = sessions; }, [sessions]);
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

  // 서버 발급 대화 ID. 새 대화면 null → 첫 응답에서 받아 보관, 이후 턴마다 재사용.
  const conversationIdRef = useRef<number | null>(null);
  // ③ 인테이크 가이드(스텝 UI)에서 올린 자소서 fileId — ready 시 run 요청에 프론트에서 병합한다.
  //   (백엔드 인테이크는 attachmentFileIds 를 아직 안 받음(2단계) — run 엔드포인트는 이미 받는 필드라 프로토콜 유지.)
  const pendingAttachmentIdsRef = useRef<number[]>([]);
  // 마지막 run 요청(첨부 병합 후 최종본) — 재시도 = 이 요청 전체 재실행. 세션 전환/이탈 시 비운다.
  const lastRunRequestRef = useRef<AutoPrepRequest | null>(null);
  // 연결 끊김(챗봇 API 실패) 직전 요청을 보관 → "다시 시도"가 UI 리셋이 아니라 실제 재전송을 하게 한다.
  // disconnected 로 가는 모든 경로가 이 ref 를 먼저 채우므로 retryConnection 시점엔 항상 최신 실패다.
  const lastFailedActionRef = useRef<(() => void) | null>(null);
  const setPendingAttachments = useCallback((ids: number[]) => {
    pendingAttachmentIdsRef.current = ids;
  }, []);
  // 복원은 세션당 1회만 시도 (열 때마다 재호출 방지).
  const restoredRef = useRef(false);
  const voiceRecognizerRef = useRef<BrowserSttTracker | null>(null);
  const accountSwitchCleanupIdsRef = useRef<number[]>([]);
  // ④ 재진입 하이드레이션 — 복원 resume 에 실려 온 확정 수집값(가이드 패널이 소비, 표시용).
  const [onbCollected, setOnbCollected] = useState<OnbCollected | null>(null);

  const requestScopeRef = useRef<ChatbotRequestScope | null>(null);
  if (requestScopeRef.current == null) {
    requestScopeRef.current = new ChatbotRequestScope(accountId);
  }
  const requestScope = requestScopeRef.current;

  function takePendingAutoPrepFileIds(): number[] {
    const ids = new Set<number>(pendingAttachmentIdsRef.current);
    for (const fileId of lastRunRequestRef.current?.attachmentFileIds ?? []) ids.add(fileId);
    for (const fileId of lastRunRequestRef.current?.jobPostingFileIds ?? []) ids.add(fileId);
    pendingAttachmentIdsRef.current = [];
    lastRunRequestRef.current = null;
    return [...ids];
  }

  function discardOwnedPendingAutoPrepFiles(options: { keepalive?: boolean } = {}): void {
    const fileIds = takePendingAutoPrepFileIds();
    if (fileIds.length > 0) void discardPendingAutoPrepFiles(fileIds, options);
  }

  // AuthContext의 계정 값은 render 중 이미 바뀐다. 이때 request generation과 민감 ref를
  // 동기 전환해 effect가 실행되기 전 들어오는 이전 계정 응답도 즉시 폐기한다.
  if (requestScope.switchAccount(accountId)) {
    accountSwitchCleanupIdsRef.current = [
      ...new Set([...accountSwitchCleanupIdsRef.current, ...takePendingAutoPrepFileIds()]),
    ];
    conversationIdRef.current = null;
    lastFailedActionRef.current = null;
    restoredRef.current = false;
    runStartedRef.current = false;
    orchRef.current = false;
    sessionsRef.current = [];
    voiceRecognizerRef.current?.dispose();
    voiceRecognizerRef.current = null;
  }

  const runReset = run.reset;
  const runCancel = run.cancel;
  useEffect(() => {
    // state는 계정별로 완전히 비운 뒤 마지막에 scope key를 맞춘다. 그 전 render에서는
    // return 값이 아래 accountStateCurrent guard로 마스킹돼 이전 계정 UI가 노출되지 않는다.
    setIsOpen(false);
    setMessages([]);
    setBotStatus("idle");
    setVoiceState("idle");
    setInterimTranscript("");
    setSessions([]);
    setActiveSessionId("");
    setOrchestrator(false);
    setRunStarted(false);
    setRunCaseId(null);
    setShowExitSheet(false);
    setSurface("corner");
    setOnbCollected(null);
    runReset();
    const cleanupFileIds = accountSwitchCleanupIdsRef.current;
    accountSwitchCleanupIdsRef.current = [];
    if (cleanupFileIds.length > 0) void discardPendingAutoPrepFiles(cleanupFileIds, { keepalive: true });
    try {
      localStorage.removeItem(LEGACY_LS_AWAIT_INTERVIEW);
    } catch {
      // 저장소 사용 불가 환경은 무시한다.
    }
    setStateAccountKey(accountKey);
  }, [accountKey, runReset]);

  useEffect(() => () => {
    requestScope.abortAll();
    runCancel();
    discardOwnedPendingAutoPrepFiles({ keepalive: true });
    voiceRecognizerRef.current?.dispose();
    voiceRecognizerRef.current = null;
  }, [requestScope, runCancel]);

  const restoreRecent = useCallback(() => {
    if (restoredRef.current) return;
    if (!getAccessToken()) return; // 비로그인은 복원 대상 아님
    restoredRef.current = true;
    const request = requestScope.begin("restore");
    api<ChatHistoryResponse | null>("/chatbot/conversations/recent", {
      signal: request.controller.signal,
    })
      .then((data) => {
        if (!requestScope.isCurrent(request)) return;
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
          if (r.collected && (r.collected.job || r.collected.skills)) {
            setOnbCollected({ job: r.collected.job, skills: r.collected.skills });
          }
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
        setMessages((prev) => {
          if (prev.length === 0) return restored;
          // open()은 최근 대화와 면접 복귀 결과를 병렬 조회한다. 결과 카드가 먼저 도착했을 때도
          // 복원 history를 잃지 않되, 그 사이 사용자가 실제 발화를 시작했다면 현재 스레드를 보존한다.
          return prev.every((message) => message.interviewReport != null)
            ? [...restored, ...prev]
            : prev;
        });
        // 넛지/칩 활성 조건(botStatus) 정합 — 진행 중 상태(thinking 등)는 덮지 않는다.
        setBotStatus((s) => (s === "idle" ? "answered" : s));
      })
      .catch((err) => {
        if (!requestScope.isCurrent(request)) return;
        console.error("이전 대화 복원 실패:", err);
      })
      .finally(() => requestScope.finish(request));
  }, [requestScope]);

  /* ── 대화 목록(사이드바) 로드 — 로그인 유저의 최근 대화 최대 20건(인테이크+일반 상담). ── */
  const loadSessions = useCallback(() => {
    if (!getAccessToken()) {
      requestScope.cancelLane("sessions");
      setSessions([]);
      return;
    }
    const request = requestScope.begin("sessions", { conversationBound: false });
    api<SessionSummaryDto[] | null>("/chatbot/conversations", {
      signal: request.controller.signal,
    })
      .then((data) => {
        if (!requestScope.isCurrent(request)) return;
        setSessions(
          (data ?? []).map((s) => {
            const kind = s.kind === "GENERAL" ? "GENERAL" as const : "INTAKE" as const;
            return {
              id: String(s.conversationId),
              title: s.title || (kind === "INTAKE" ? "면접 준비 세션" : "일반 상담"),
              lastMessage: kind === "INTAKE" ? "면접 준비" : "일반 상담",
              meta: "",
              updatedAt: s.updatedAt ?? 0,
              mode: s.mode,
              kind,
            };
          }),
        );
      })
      .catch((err) => {
        if (requestScope.isCurrent(request)) console.error("대화 목록 로드 실패:", err);
      })
      .finally(() => requestScope.finish(request));
  }, [requestScope]);

  /* ── 세션 클릭 → 그 conversationId 로 전환 + 메시지 로드. 다음 요청부터 백엔드가 슬롯 복원(Phase D). ── */
  const openSession = useCallback((id: string) => {
    const conversationId = Number(id);
    if (!Number.isFinite(conversationId)) return;
    requestScope.invalidateConversation();
    run.reset();
    discardOwnedPendingAutoPrepFiles();
    restoredRef.current = true;
    // 인테이크(지원건) 세션만 오케 모드+플로팅으로. 일반 상담 대화는 일반 챗 표면 그대로 이어본다.
    const isIntake = (sessionsRef.current.find((s) => s.id === id)?.kind ?? "INTAKE") === "INTAKE";
    conversationIdRef.current = conversationId;
    lastFailedActionRef.current = null;
    setActiveSessionId(id);
    setMessages([]);
    setBotStatus("thinking");
    setOnbCollected(null);
    lastRunRequestRef.current = null; // 다른 세션의 요청으로 재시도하지 않게
    runStartedRef.current = false;
    setRunStarted(false);
    setRunCaseId(null);
    setOrchestrator(isIntake); // 인테이크 세션 — 모드 배너 유지
    orchRef.current = isIntake;
    if (isIntake) {
      expandToFloating(); // 인테이크 세션 열기 = 오케 진입 → 플로팅
    } else {
      collapseToCorner(); // 일반 대화 = 일반 챗 표면(코너). 오케 무대(플로팅)에서 열어도 표면을 정리한다.
    }
    setShowExitSheet(false);
    const request = requestScope.begin("history");
    api<ChatHistoryResponse | null>(`/chatbot/conversations/${conversationId}/messages`, {
      signal: request.controller.signal,
    })
      .then((data) => {
        if (!requestScope.isCurrent(request) || conversationIdRef.current !== conversationId) return;
        const msgs: ChatMessage[] = (data?.messages ?? []).map((m) => ({
          id: nextId(), role: m.role, text: m.text,
          evidence: [], links: [], quickReplies: [],
          ttsState: "idle" as const, ttsProgress: 0, timestamp: Date.now(),
        }));
        setMessages(msgs);
        setBotStatus(msgs.length ? "answered" : "idle");
      })
      .catch((err) => {
        if (!requestScope.isCurrent(request)) return;
        console.error("세션 로드 실패:", err);
        setBotStatus("disconnected");
      })
      .finally(() => requestScope.finish(request));
  }, [requestScope, run]);

  // ── 면접 인계: caseId 를 표식으로 남긴다(면접 페이지로 navigate 는 ChatbotWidget 에서). ──
  const markInterviewHandoff = useCallback((caseId: number | null) => {
    if (caseId == null) return;
    const storageKey = interviewHandoffStorageKey(accountId);
    if (!storageKey) return;
    try {
      localStorage.setItem(storageKey, JSON.stringify({ caseId, ts: Date.now() }));
    } catch {
      /* localStorage 불가 환경 무시 */
    }
  }, [accountId]);

  // ── 복귀 결과: 면접 완료(리포트 생성)면 챗봇 말풍선에 결과 카드로 표시. ──
  // Push(브라우저 알림)와 별개 — 챗봇이 열릴 때 caseId 로 최근 완료 세션을 찾아 /report 를 재조회한다.
  const checkInterviewResult = useCallback(async () => {
    if (!getAccessToken()) return;
    const storageKey = interviewHandoffStorageKey(accountId);
    if (!storageKey) return;
    let pending: { caseId: number; ts: number } | null = null;
    try {
      const raw = localStorage.getItem(storageKey);
      if (raw) pending = JSON.parse(raw);
    } catch {
      pending = null;
    }
    if (!pending) return;
    // 24시간 지나면 대기 해제(무한 재조회 방지).
    if (Date.now() - pending.ts > 24 * 60 * 60 * 1000) {
      localStorage.removeItem(storageKey);
      return;
    }
    const request = requestScope.begin("interview-result");
    try {
      // caseId 로 결과 조회 API 가 없어(sessionId 로만) → 세션 목록에서 해당 케이스의 최근 "완료" 세션을 찾는다.
      const page = await listInterviewSessions(0, 20);
      if (!requestScope.isCurrent(request)) return;
      const done = page.sessions
        .filter((s) => s.applicationCaseId === pending!.caseId && s.totalScore != null)
        .sort((a, b) => b.id - a.id)[0];
      if (!done) return; // 아직 완료 전 → 다음 오픈에 재확인(표식 유지)
      const report = await getInterviewReport(done.id);
      if (!requestScope.isCurrent(request)) return;
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
      localStorage.removeItem(storageKey);
    } catch (e) {
      if (requestScope.isCurrent(request)) {
        console.error("면접 결과 재조회 실패:", e); // 조용히 — 다음 오픈에 재시도
      }
    } finally {
      requestScope.finish(request);
    }
  }, [accountId, requestScope]);

  const open = useCallback(() => {
    setIsOpen(true);
    restoreRecent();
    loadSessions();
    void checkInterviewResult();
  }, [restoreRecent, loadSessions, checkInterviewResult]);
  // 닫기/최소화(버블로) 시 다음 오픈은 코너부터.
  const close = useCallback(() => { setIsOpen(false); setSurface("corner"); }, []);
  const minimize = useCallback(() => { setIsOpen(false); setSurface("corner"); }, []);

  const sendMessage = useCallback((text: string, opts?: { selectedCaseId?: number; selectedModeCode?: string; silent?: boolean; faqChip?: boolean; model?: AiModelChoice }) => {
    // silent: 유저 말풍선을 남기지 않는다(가이드 X/여기서-물어보기의 서버 이탈 — UI 제스처지 사용자 발화가 아님).
    //   응답 처리(봇 말풍선·orchestrator·onbPhase 파생)는 그대로 재사용한다.
    if (!opts?.silent) {
      const userMsg: ChatMessage = {
        id: nextId(), role: "user", text,
        evidence: [], links: [], quickReplies: [], ttsState: "idle", ttsProgress: 0,
        timestamp: Date.now(),
      };
      setMessages((prev) => [...prev, userMsg]);
    }
    setBotStatus("thinking");

    // 사용자가 발화를 시작한 시점부터 복원/세션 history가 현재 스레드를 덮을 수 없다.
    requestScope.cancelLane("restore");
    requestScope.cancelLane("history");
    requestScope.cancelLane("interview-result");
    const request = requestScope.begin("reply");

    api<ChatbotApiResponse>(`/chatbot/ask${opts?.model && opts.model !== "AUTO" ? `?model=${opts.model}` : ""}`, {
      method: "POST",
      body: JSON.stringify({
        question: text,
        conversationId: conversationIdRef.current,
        // ③ 칩/버튼 직접 선택 시 caseId·modeCode 를 실어 보낸다 → 백엔드가 qwen3 거치지 않고 결정적 confirm.
        ...(opts?.selectedCaseId != null ? { selectedCaseId: opts.selectedCaseId } : {}),
        ...(opts?.selectedModeCode ? { selectedModeCode: opts.selectedModeCode } : {}),
        // 빈 화면 FAQ 추천 칩 클릭 — 깡통 온보딩 게이트를 그 턴만 우회(자유 입력은 플래그 없이 게이트 유지).
        ...(opts?.faqChip ? { faqChip: true } : {}),
      }),
      signal: request.controller.signal,
    })
      .then((data) => {
        if (!requestScope.isCurrent(request)) return;

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
        if (!requestScope.isCurrent(request)) return;
        console.error("챗봇 API 오류:", err);
        // 재시도용 보관 — 유저 말풍선은 이미 남아 있으므로 silent 로 조용히 재전송한다.
        lastFailedActionRef.current = () => sendMessage(text, { ...opts, silent: true });
        setBotStatus("disconnected");
      })
      .finally(() => requestScope.finish(request));
  }, [requestScope, run]);

  /* ── ④ 온보딩 완전 이탈(버그2): "그만"(silent)을 서버로 보내 markOnboardingDeclined+clearOnboarding 을 태운다.
     응답 route="온보딩이탈"→onbPhase=null 로 가이드가 닫힌다(서버-먼저·UI-나중 — 낙관적 close 없음이라
     서버 실패 시 가이드 유지 = 상태 불일치 차단). 유저 말풍선은 남기지 않고 봇 안내만 남긴다. ── */
  const leaveOnboarding = useCallback(() => {
    sendMessage("그만", { silent: true });
  }, [sendMessage]);

  /* ── 추천 후기 압축 요약 칩 → 묶음 요약 요청(POST /chatbot/summarize-posts). ── */
  const summarizePosts = useCallback((postIds: number[], opts?: { silent?: boolean }) => {
    if (!postIds || postIds.length === 0) return;

    if (!opts?.silent) {
      const userMsg: ChatMessage = {
        id: nextId(), role: "user", text: "추천 후기 요약해줘",
        evidence: [], links: [], quickReplies: [], ttsState: "idle", ttsProgress: 0,
        timestamp: Date.now(),
      };
      setMessages((prev) => [...prev, userMsg]);
    }
    setBotStatus("thinking");

    requestScope.cancelLane("restore");
    requestScope.cancelLane("history");
    requestScope.cancelLane("interview-result");
    const request = requestScope.begin("reply");
    api<ChatbotApiResponse>("/chatbot/summarize-posts", {
      method: "POST",
      body: JSON.stringify({ conversationId: conversationIdRef.current, postIds }),
      signal: request.controller.signal,
    })
      .then((data) => {
        if (!requestScope.isCurrent(request)) return;
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
        if (!requestScope.isCurrent(request)) return;
        console.error("추천 후기 요약 API 오류:", err);
        lastFailedActionRef.current = () => summarizePosts(postIds, { silent: true });
        setBotStatus("disconnected");
      })
      .finally(() => requestScope.finish(request));
  }, [requestScope]);

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

  /* ── 자소서 첨부: 읽지 못한 이전 pending을 최신 파일 하나로 교체해 재실행(WRITE 가 소비). ── */
  const attachCoverLetter = useCallback(async (file: File) => {
    const req = lastRunRequestRef.current;
    if (!req) {
      // 비정상 상태(실행 이력 없음) — 조용한 no-op 대신 throw 해서 첨부 버튼의 catch 가
      // 실패 문구를 렌더하게 한다(정상 흐름에선 SKIPPED 카드 = 실행 후라 도달 안 함).
      throw new Error("실행 이력이 없어 첨부할 수 없어요. 준비를 다시 시작해 주세요.");
    }
    const request = requestScope.begin("attachment");
    try {
      const uploaded = await uploadAttachment(file);
      if (!requestScope.isCurrent(request)) {
        await deletePendingAutoPrepFile(uploaded.id).catch(() => {});
        return;
      }
      const supersededIds = (req.attachmentFileIds ?? []).filter((fileId) => fileId !== uploaded.id);
      const next: AutoPrepRequest = {
        ...req,
        attachmentFileIds: [uploaded.id],
      };
      lastRunRequestRef.current = next;
      if (supersededIds.length > 0) void discardPendingAutoPrepFiles(supersededIds);
      void run.start(next);
    } finally {
      requestScope.finish(request);
    }
  }, [requestScope, run]);

  /* ── 모드 이탈: 실행 전이면 백엔드 모드 해제("그만"), 실행 중/후면 로컬 정리만. ── */
  const exitOrchestrator = useCallback(() => {
    setShowExitSheet(false);
    const wasRunning = runStartedRef.current;
    requestScope.cancelLane("attachment");
    run.reset();
    discardOwnedPendingAutoPrepFiles();
    runStartedRef.current = false;
    orchRef.current = false;
    setRunStarted(false);
    setRunCaseId(null);
    setOrchestrator(false);
    collapseToCorner(); // 오케 이탈 → 코너로
    if (!wasRunning) {
      sendMessage("그만"); // 인테이크 단계 → 서버 sticky 해제
    }
  }, [requestScope, run, sendMessage, collapseToCorner]);

  /* ── 음성 입력(STT): Web Speech 세션을 열고 실시간 전사를 interimTranscript 로 흘린다.
        전송은 confirmVoice → 기존 sendMessage 경로 그대로 — 타이핑 메시지와 동일하게 취급된다. ── */
  const startVoice = useCallback(() => {
    voiceRecognizerRef.current?.dispose();
    voiceRecognizerRef.current = null;
    if (!isBrowserSttSupported()) {
      setVoiceState("denied");
      setInterimTranscript("");
      return;
    }
    const request = requestScope.begin("voice");
    setVoiceState("requesting");
    const tracker = new BrowserSttTracker(
      (text) => {
        if (requestScope.isCurrent(request)) setInterimTranscript(text);
      },
      () => {
        if (!requestScope.isCurrent(request)) return;
        requestScope.cancelLane("voice");
        voiceRecognizerRef.current = null;
        setVoiceState("denied");
        setInterimTranscript("");
      },
    );
    voiceRecognizerRef.current = tracker;
    if (tracker.start()) {
      setVoiceState("listening");
      setInterimTranscript("");
    } else {
      voiceRecognizerRef.current = null;
      requestScope.cancelLane("voice");
      setVoiceState("denied");
    }
  }, [requestScope]);

  const cancelVoice = useCallback(() => {
    voiceRecognizerRef.current?.dispose();
    voiceRecognizerRef.current = null;
    requestScope.cancelLane("voice");
    setVoiceState("idle");
    setInterimTranscript("");
  }, [requestScope]);

  const confirmVoice = useCallback(() => {
    const transcript = voiceRecognizerRef.current?.stop() || interimTranscript;
    voiceRecognizerRef.current = null;
    requestScope.cancelLane("voice");
    if (transcript) {
      sendMessage(transcript);
    }
    setVoiceState("idle");
    setInterimTranscript("");
  }, [interimTranscript, requestScope, sendMessage]);

  // 위젯 닫힘(닫기/최소화 공통) 시 마이크 정리 — 닫힌 화면 뒤에서 계속 듣지 않게.
  useEffect(() => {
    if (!isOpen) {
      voiceRecognizerRef.current?.dispose();
      voiceRecognizerRef.current = null;
      requestScope.cancelLane("voice");
      setVoiceState("idle");
      setInterimTranscript("");
    }
  }, [isOpen, requestScope]);
  // 언마운트 시 인식 세션 해제.
  useEffect(() => () => {
    voiceRecognizerRef.current?.dispose();
    voiceRecognizerRef.current = null;
  }, []);

  const retryConnection = useCallback(() => {
    const retry = lastFailedActionRef.current;
    lastFailedActionRef.current = null;
    if (retry) {
      retry(); // 마지막 실패 요청 실제 재전송(또 실패하면 catch 가 disconnected + 재시도 액션을 다시 보관)
    } else {
      setBotStatus("idle"); // 재전송할 이력이 없으면 기존 동작 — 입력만 다시 연다
    }
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
    requestScope.invalidateConversation();
    requestScope.cancelLane("attachment");
    run.reset();
    discardOwnedPendingAutoPrepFiles();
    setMessages([]);
    setBotStatus("idle");
    conversationIdRef.current = null; // 새 대화 → 서버가 새 ID 발급
    lastFailedActionRef.current = null;
    runStartedRef.current = false;
    orchRef.current = false;
    setRunStarted(false);
    setRunCaseId(null);
    setOrchestrator(false);
    setActiveSessionId("");
    setOnbCollected(null);
    voiceRecognizerRef.current?.dispose();
    voiceRecognizerRef.current = null;
    requestScope.cancelLane("voice");
    setVoiceState("idle");
    setInterimTranscript("");
    collapseToCorner();
    setShowExitSheet(false);
  }, [requestScope, run, collapseToCorner]);

  const accountStateCurrent = stateAccountKey === accountKey;

  /* ── 대화 삭제(본인 소유만, 서버가 소유 검증) — 목록에서 제거하고, 지금 보던 대화면 새 대화로 리셋.
        반환값 = 열려 있던 대화를 지웠는지(위젯이 가이드 오버레이 등 로컬 상태를 함께 정리할 신호).
        실패는 throw 로 전파 — 확인 UI(SessionPanel)가 에러 안내를 맡는다. ── */
  const deleteSession = useCallback(async (id: string): Promise<boolean> => {
    const conversationId = Number(id);
    if (!Number.isFinite(conversationId)) return false;
    await api<void>(`/chatbot/conversations/${conversationId}`, { method: "DELETE" });
    // 활성 판정은 activeSessionId 가 아니라 conversationIdRef 로 — 목록을 거치지 않고 새로 시작한
    // 대화(activeSessionId="")도 지금 열려 있으면 리셋해야 다음 전송이 죽은 id 로 나가지 않는다.
    const wasCurrent = conversationIdRef.current === conversationId;
    setSessions((prev) => prev.filter((s) => s.id !== id));
    setActiveSessionId((cur) => (cur === id ? "" : cur));
    if (wasCurrent) newSession();
    return wasCurrent;
  }, [newSession]);

  return {
    isOpen: accountStateCurrent ? isOpen : false,
    open, close, minimize, restoreRecent,
    messages: accountStateCurrent ? messages : [],
    sendMessage, leaveOnboarding,
    botStatus: accountStateCurrent ? botStatus : "idle" as BotStatus,
    setBotStatus,
    voiceState: accountStateCurrent ? voiceState : "idle" as VoiceState,
    startVoice, cancelVoice, confirmVoice, setVoiceState,
    interimTranscript: accountStateCurrent ? interimTranscript : "",
    voiceSupported,
    retryConnection,
    toggleTts,
    sessions: accountStateCurrent ? sessions : [],
    activeSessionId: accountStateCurrent ? activeSessionId : "",
    setActiveSessionId, newSession,
    loadSessions, openSession, deleteSession,
    // 오케스트레이터
    orchestrator: accountStateCurrent ? orchestrator : false,
    runStarted: accountStateCurrent ? runStarted : false,
    runParts: accountStateCurrent ? run.parts : [],
    runRunning: accountStateCurrent ? run.running : false,
    runPlan: accountStateCurrent ? run.plan : null,
    runError: accountStateCurrent ? run.error : null,
    runCaseId: accountStateCurrent ? runCaseId : null,
    retryRun,
    attachCoverLetter,
    selectCase, selectMode,
    setPendingAttachments,
    summarizePosts,
    onbCollected: accountStateCurrent ? onbCollected : null,
    showExitSheet: accountStateCurrent ? showExitSheet : false,
    openExitSheet: () => setShowExitSheet(true),
    closeExitSheet: () => setShowExitSheet(false),
    exitOrchestrator,
    // 표면 크기 전환(morph)
    surface: accountStateCurrent ? surface : "corner" as const,
    expandToFloating,
    collapseToCorner,
    // 면접 인계/복귀
    markInterviewHandoff,
  };
}
