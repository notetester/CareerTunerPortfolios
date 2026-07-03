import { useState, useRef, useEffect, useCallback } from "react";
import { useNavigate } from "react-router";
import {
  Sparkles, MessageCircle, Mic, MicOff, ArrowUp, X,
  KeyRound, CreditCard, FileText, FileSearch, Pause, Volume2,
  ArrowUpRight, Shield, SearchX, Headset, PenLine, WifiOff,
  RotateCw, Check, Keyboard, ArrowRight, Play, LogOut, History, Plus,
  Minimize2, Maximize2, Loader2,
} from "lucide-react";
import { getAccessToken } from "@/app/lib/tokenStore";
import { useChatbot } from "../hooks/useChatbot";
import type {
  ChatMessage, ChatEvidence, SiteLink, IntakeCaseCandidate, IntakeModeOption, ChatSession,
  InterviewReportCard,
} from "../types/chatbot";
import { SUGGESTED_QUESTIONS } from "../types/chatbot";
import type { GuideStep } from "../onboarding/guideData";
import { AutoPrepWorkView } from "@/features/autoprep/components/AutoPrepWorkView";
import { displayCompany, displayJobTitle, displayCaseText } from "@/features/autoprep/lib/caseLabels";
import { OnboardingGuide, type ServerGuidePhase } from "./OnboardingGuide";

/** 오케스트레이터 정체성 글리프(U+2726). */
const ORCH_GLYPH = "✦";

/**
 * ④ 깡통 온보딩(백엔드 텍스트 프로토콜) 라우트 → 가이드 국면 매핑.
 * 라우트 문자열은 ChatbotController.onboardingTurn 의 코드 리터럴(결정적 스텝 식별자).
 * 여기 없는 온보딩 라우트(확인-회사/확인-직무/모드선택/면접인계)는 가이드를 닫고 기존 챗 UI 폴백.
 */
const ONB_ROUTE_PHASE: Record<string, ServerGuidePhase> = {
  "④온보딩:직무": "role",
  "④온보딩:기술": "skills",
  "④온보딩:공고요청": "jd",
  "④온보딩:공고생성실패": "jd",
  "④온보딩:추출실패": "jd",
  "④온보딩:추출유실": "jd",
  "④온보딩:공고생성": "waiting",
  "④온보딩:추출대기": "waiting",
};

/** ④ 공고 추출 대기 자동 폴링 — 넛지 간격 백오프(총 ~2.7분: 백엔드 추출 커밋 실측 80~120초 커버).
 *  배열 길이 = 넛지 상한. 소진 시 안내 1회 + 가이드 닫기 + exhausted 래치(플래핑 방지). */
const ONB_NUDGE_DELAYS_MS = [3_500, 8_000, 15_000, 30_000, 45_000, 60_000];
/** 넛지 예산 sessionStorage 키 — 위젯 닫았다 열기(패널 리마운트)로 ref 가 리셋돼도 예산이 이어진다.
 *  conversationId 는 useChatbot 내부라 위젯에서 접근 불가(이번 수정 범위 밖) → 탭당 동시 온보딩
 *  대기는 1건뿐이므로 고정 키 + TTL/waiting 이탈 정리로 케이스 간 이월을 막는다. */
const ONB_NUDGE_STORE_KEY = "tunerbot:onbNudgeBudget:v1";
const ONB_NUDGE_STORE_TTL_MS = 15 * 60_000;

/** 넛지 예산(마운트 간 공유): count=보낸 수, exhausted=상한 래치, noticeSent=소진 안내 발송 여부. */
interface OnbNudgeBudget { count: number; exhausted: boolean; noticeSent: boolean; ts: number }

const freshOnbNudgeBudget = (): OnbNudgeBudget => ({ count: 0, exhausted: false, noticeSent: false, ts: Date.now() });

function readOnbNudgeBudget(): OnbNudgeBudget {
  try {
    const raw = sessionStorage.getItem(ONB_NUDGE_STORE_KEY);
    if (raw) {
      const b = JSON.parse(raw) as OnbNudgeBudget;
      // TTL 지난 예산은 이전 온보딩의 잔재로 보고 버린다(케이스 간 이월 방지).
      if (typeof b?.count === "number" && Date.now() - b.ts <= ONB_NUDGE_STORE_TTL_MS) return b;
    }
  } catch {
    /* storage 불가/파손 → 새 예산 */
  }
  return freshOnbNudgeBudget();
}

function writeOnbNudgeBudget(b: OnbNudgeBudget) {
  try {
    sessionStorage.setItem(ONB_NUDGE_STORE_KEY, JSON.stringify({ ...b, ts: Date.now() }));
  } catch {
    /* storage 불가 환경 — 마운트 내 ref 예산만으로 동작 */
  }
}

function clearOnbNudgeBudget() {
  try {
    sessionStorage.removeItem(ONB_NUDGE_STORE_KEY);
  } catch {
    /* 무시 */
  }
}

const ICON_MAP = { KeyRound, CreditCard, FileText } as const;

/* ════════════════ Floating Bubble ════════════════ */
export function ChatbotBubble() {
  const chatbot = useChatbot();
  const [tooltipVisible, setTooltipVisible] = useState(true);

  if (chatbot.isOpen) {
    return <ChatbotPanel chatbot={chatbot} />;
  }

  return (
    <div className="fixed right-5 bottom-5 z-50 flex flex-col items-end gap-2">
      {tooltipVisible && (
        <div className="relative bg-card border border-border rounded-[14px] rounded-br-[5px] shadow-lg p-3 pr-9 max-w-[236px]">
          <button
            onClick={() => setTooltipVisible(false)}
            className="absolute top-2.5 right-2.5 w-[18px] h-[18px] rounded-full bg-secondary flex items-center justify-center text-muted-foreground hover:bg-secondary transition-colors"
          >
            <X size={11} />
          </button>
          <div className="text-[13px] font-bold mb-0.5">무엇이든 물어보세요</div>
          <div className="text-[12px] leading-[1.55] text-muted-foreground">
            기능 사용법·요금·공지까지 AI가 바로 찾아드려요.
          </div>
        </div>
      )}
      <button
        onClick={chatbot.open}
        className="w-[60px] h-[60px] rounded-full flex items-center justify-center text-white shadow-[0_12px_24px_rgba(37,99,235,0.4)] hover:scale-105 transition-transform"
        style={{ background: "linear-gradient(135deg, #2563eb, #4f46e5)" }}
        aria-label="챗봇 열기"
      >
        <MessageCircle size={26} />
      </button>
    </div>
  );
}

/* ════════════════ Chat Panel (Widget) ════════════════ */
interface ChatbotPanelProps {
  chatbot: ReturnType<typeof useChatbot>;
}

/** 면접 모드 코드 → 라벨(배너 서브텍스트용). 백엔드 MODE_OPTIONS 와 동일. */
const MODE_LABELS: Record<string, string> = {
  BASIC: "기본 면접", JOB: "직무 면접", PERSONALITY: "인성 면접",
  PRESSURE: "압박 면접", RESUME: "자소서 기반", COMPANY: "기업 맞춤",
};

/** 면접 모드 코드 → 짧은 배지 라벨(SessionRow 모드 배지용 — MODE_LABELS 보다 압축). */
const MODE_BADGE: Record<string, string> = {
  BASIC: "기본", JOB: "직무", PERSONALITY: "인성",
  PRESSURE: "압박", RESUME: "자소서", COMPANY: "기업맞춤",
};

/** epoch millis → 상대시각("방금"/"3분 전"/"2시간 전"/"5일 전"). 0/미지정이면 빈 문자열. */
function relativeTime(ts: number): string {
  if (!ts) return "";
  const m = Math.floor((Date.now() - ts) / 60000);
  if (m < 1) return "방금";
  if (m < 60) return `${m}분 전`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h}시간 전`;
  return `${Math.floor(h / 24)}일 전`;
}

function ChatbotPanel({ chatbot }: ChatbotPanelProps) {
  const {
    close, messages, sendMessage, botStatus,
    voiceState, startVoice, cancelVoice, confirmVoice, setVoiceState,
    interimTranscript, retryConnection, toggleTts,
    orchestrator, runStarted, runParts, runRunning, runPlan, runCaseId, runError, retryRun,
    selectCase, selectMode, setPendingAttachments, summarizePosts,
    showExitSheet, openExitSheet, closeExitSheet, exitOrchestrator,
    sessions, activeSessionId, openSession, newSession, loadSessions,
    surface, expandToFloating, collapseToCorner, markInterviewHandoff,
  } = chatbot;
  const floating = surface === "floating";
  // 플로팅에서 오케 실행이 시작되면 WorkView를 채팅 항목이 아니라 "무대(stage)"로 —
  // 컨테이너 720 캡을 풀어 6파트 그리드가 콘텐츠 폭을 채우고, 말풍선은 좁은 컬럼으로 위에 남는다.
  const stage = floating && runStarted;

  // 면접 인계: caseId 를 표식으로 남기고 D 면접 페이지로 이동(모드 선택 탭). caseId 없으면 그냥 진입.
  const goInterview = (caseId: number | null) => {
    markInterviewHandoff(caseId);
    navigate(caseId != null ? `/interview?caseId=${caseId}&tab=modes` : "/interview");
  };
  // AutoPrepWorkView 등에서 온 경로가 면접이면 caseId 를 추출해 표식을 남긴 뒤 이동.
  const navigateFromWork = (path: string) => {
    if (path.startsWith("/interview")) {
      const q = path.split("?")[1] ?? "";
      const cid = new URLSearchParams(q).get("caseId");
      markInterviewHandoff(cid ? Number(cid) : null);
    }
    navigate(path);
  };

  const navigate = useNavigate();
  const [input, setInput] = useState("");
  const [showSessions, setShowSessions] = useState(false);
  const [showGuide, setShowGuide] = useState(false);
  // ③→가이드 매핑: 인테이크 CASE 되묻기를 텍스트 대신 가이드 스텝 UI 로. msgId = 어느 되묻기 턴에 붙은 가이드인지.
  const [intakeGuide, setIntakeGuide] = useState<{ msgId: string; steps: GuideStep[] } | null>(null);
  // 닫은(=텍스트 폴백 선택) 되묻기 턴은 다시 자동 오픈하지 않는다.
  const dismissedIntakeRef = useRef<Set<string>>(new Set());
  // "질문하기"로 잠시 비켜준 상태 — 같은 봇 메시지(msgId 불변)인 동안은 아래 자동 오픈 effect가
  // 즉시 재오픈하지 않게 억제한다(④ onbAskingRef 와 동일한 레이스 방지 패턴).
  const intakeAskingRef = useRef(false);
  const scrollRef = useRef<HTMLDivElement>(null);

  const handleOpenSessions = () => { loadSessions(); setShowSessions(true); };

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" });
  }, [messages, botStatus, runParts]);

  // ── ③→가이드 자동 매핑: CASE 되묻기인데 지원 건 후보가 0건(빈 계정 "면접 해줘")이면
  //    텍스트 되묻기 대신 가이드 전체 흐름(직군→역량→서류→공고)으로 슬롯을 채운다.
  //    후보가 있으면 기존 칩 유지(+"새 공고" 진입), 비로그인은 업로드/케이스 생성 불가 → 텍스트 폴백 유지.
  useEffect(() => {
    const last = [...messages].reverse().find((m) => m.role === "bot");
    if (!last?.intake || last.intake.ready || runStarted) return;
    if (last.intake.nextAsk !== "CASE" || last.intake.candidates.length > 0) return;
    if (!getAccessToken()) return;
    if (dismissedIntakeRef.current.has(last.id)) return;
    if (intakeAskingRef.current) return;
    setIntakeGuide((cur) =>
      cur?.msgId === last.id ? cur : { msgId: last.id, steps: ["role", "skills", "docs", "jd"] });
  }, [messages, runStarted]);

  // 가이드가 지원 건을 만들었으면 기존 인테이크 프로토콜 그대로 회신(selectedCaseId — 백엔드 무수정).
  // 가이드에서 받은 자소서 fileId 는 ready 시 run 요청에 병합되도록 예약.
  const handleSlotFilled = ({ caseId, coverLetterFileIds }: { caseId: number; coverLetterFileIds: number[] }) => {
    if (intakeGuide) dismissedIntakeRef.current.add(intakeGuide.msgId);
    setIntakeGuide(null);
    setPendingAttachments(coverLetterFileIds);
    sendMessage("방금 올린 공고 지원 건으로 진행할게요", { selectedCaseId: caseId });
  };

  const closeIntakeGuide = () => {
    if (intakeGuide) dismissedIntakeRef.current.add(intakeGuide.msgId);
    setIntakeGuide(null); // 닫으면 기존 텍스트/칩 되묻기로 폴백(챗은 그대로 남아 있음)
  };

  // ── ④→가이드 매핑: 깡통 계정의 텍스트 온보딩(직무→기술→공고)을 가이드 스텝 UI 로.
  //    국면은 마지막 봇 라우트에서 파생(서버 = 단일 소스). 닫으면 기존 텍스트 흐름 폴백.
  const lastBotMsg = [...messages].reverse().find((m) => m.role === "bot");
  const onbPhase: ServerGuidePhase | null =
    lastBotMsg?.route ? ONB_ROUTE_PHASE[lastBotMsg.route] ?? null : null;
  const [onbGuideOpen, setOnbGuideOpen] = useState(false);
  const onbDismissedRef = useRef(false);
  // 넛지 예산 — sessionStorage 에서 지연 복원(위젯 닫았다 열기 리마운트 간 공유 → 재발사 방지).
  const onbNudgeRef = useRef<OnbNudgeBudget | null>(null);
  if (onbNudgeRef.current == null) onbNudgeRef.current = readOnbNudgeBudget();
  // 넛지 소진 안내(로컬 1회 — 서버/LLM 무경유, 트랜스크립트 미기록). 다음 전송 시 걷힌다.
  const [onbNotice, setOnbNotice] = useState<ChatMessage | null>(null);
  // 소진 래치의 렌더용 미러 — 래치 자체는 ref(effect 간 동기 가시성)지만, 탈출 UI("지금 확인" 버튼)는
  // 렌더가 봐야 하므로 state 로 비춘다. 갱신 지점: 소진 시 true, A0 예산 정리 시 false.
  const [onbExhausted, setOnbExhausted] = useState(() => onbNudgeRef.current?.exhausted ?? false);
  // waiting 화면 정직화(F-22): 대기 시작 시각 + 다음 자동 확인 예정 시각 — 가이드 대기 화면에 내려보낸다.
  const [onbWaitingSince, setOnbWaitingSince] = useState<number | null>(null);
  const [onbNextPollAt, setOnbNextPollAt] = useState<number | null>(null);
  // 수동 "지금 확인": 즉시 폴 1회 — 넛지 예산 미소모, 소진 래치와 독립(소진 후에도 사용 가능).
  const onbManualCheck = () => sendMessage("진행 상황 알려줘");

  // waiting 진입/이탈 추적 — 진입 첫 턴에만 시각을 잡고, 국면을 벗어나면 초기화.
  useEffect(() => {
    if (onbPhase === "waiting") {
      setOnbWaitingSince((cur) => cur ?? Date.now());
    } else if (onbPhase !== null) {
      // null 은 새로고침 직후 "신호 없음"일 수 있어 유지(A0 와 같은 원칙) — 실제 다른 국면에서만 리셋.
      setOnbWaitingSince(null);
      setOnbNextPollAt(null);
    }
  }, [onbPhase]);
  // "질문하기" 링크로 잠시 비켜준 상태 — onbPhase 가 그대로인 채 닫히므로, 이 가드가 없으면
  // 같은 렌더 사이클에서 아래 자동 재오픈 effect가 즉시 다시 열어버린다(클릭이 안 먹히는 것처럼 보임).
  // 새 봇 응답이 도착(lastBotMsg 갱신)해야 해제 — 그 전까진 재오픈을 억제해 실제로 채팅이 열려 있게 한다.
  const onbAskingRef = useRef(false);

  // "질문하기"로 나간 상태 해제(③④ 공통) — 새 봇 메시지가 도착하면(질문에 답이 왔든, 실제 진행
  // 답변 응답이든) 다음부터 두 오버레이 모두 정상적으로 자동 오픈 판정을 받는다.
  useEffect(() => {
    intakeAskingRef.current = false;
    onbAskingRef.current = false;
  }, [lastBotMsg?.id]);

  // (A0) 넛지 예산·래치 정리: waiting "이탈" 신호(마지막 봇 route 가 실제로 있고 비-waiting)에서만.
  // 리마운트/새로고침 직후(메시지 없음·복원 메시지는 route 미보존)의 phase=null 은 "신호 없음"이지
  // 이탈이 아니다 — 여기서 지우면 storage 로 이어온 래치가 무효화돼 재발사가 샌다(잔재는 TTL 정리).
  // ★ 아래 재오픈 effect(A)보다 먼저 등록해야 한다: 같은 커밋에서 A0(래치 해제)→A(재오픈 판정)
  // 순서가 보장돼야 추출 FAILED→jd 복귀 때 가이드가 그 턴에 바로 다시 열린다.
  useEffect(() => {
    if (onbPhase === "waiting") return;
    const budget = onbNudgeRef.current!;
    if (lastBotMsg?.route != null && (budget.count > 0 || budget.exhausted || budget.noticeSent)) {
      onbNudgeRef.current = freshOnbNudgeBudget();
      clearOnbNudgeBudget();
      setOnbExhausted(false);
    }
  }, [onbPhase, lastBotMsg?.route]);

  // 자동 오픈/재오픈: 온보딩 국면이 살아 있으면 가이드를 연다 — 첫 질문(직무) 도착뿐 아니라
  // 질문 우회(④질문확인)·회사/직무 보정으로 잠시 닫혔다가 수집 단계로 복귀한 경우 포함.
  // X로 직접 닫은(폴백 선택) 사용자·"질문하기"로 잠시 나간 사용자·넛지 소진(exhausted)으로 닫힌
  // 상태에는 다시 안 연다 — exhausted 를 안 보면 아래 넛지 effect(상한 도달 시 close)와 서로
  // 뒤집는 무한 플래핑이 된다(고정점 부재). exhausted 는 waiting 이탈 시 자동 해제라 dismissed 와
  // 달리 추출 FAILED→jd 복귀 재오픈을 막지 않는다.
  // 이미 열려 있으면 no-op(최소화 상태 존중).
  useEffect(() => {
    if (onbPhase && !onbGuideOpen && !onbDismissedRef.current && !onbAskingRef.current
        && !onbNudgeRef.current?.exhausted && !runStarted) {
      setOnbGuideOpen(true);
      expandToFloating();
    }
  }, [onbPhase, onbGuideOpen, runStarted, expandToFloating]);

  // 자동 닫힘: 가이드 밖 온보딩 단계(회사/직무 보정·모드선택·면접인계)로 넘어가면 챗으로 복귀.
  //    (모드 칩·실행 UI 는 기존 챗 렌더가 담당 — 가이드는 빈 슬롯 수집까지만.)
  useEffect(() => {
    if (!onbGuideOpen) return;
    if (lastBotMsg?.route?.startsWith("④온보딩") && onbPhase === null) setOnbGuideOpen(false);
  }, [onbGuideOpen, lastBotMsg?.route, onbPhase]);

  // 공고 추출 대기(EXTRACTING) 자동 폴링: 백엔드가 "아무 메시지나 보내달라"는 프로토콜이라 넛지를 대신 보낸다.
  //    간격은 백오프(ONB_NUDGE_DELAYS_MS) — 추출 커밋까지 실측 80~120초를 예산 안에서 커버.
  //    소진 시 안내 1회 + 가이드 닫기 + exhausted 래치. 래치가 ref 인 이유: 위 재오픈 effect 와
  //    서로 다른 커밋 사이클에서 돌아 동기(즉시) 가시성이 필요 — state 면 한 사이클 늦게 보여
  //    그 사이 open/close 플랩 프레임이 화면에 샌다.
  useEffect(() => {
    const budget = onbNudgeRef.current!;
    if (onbPhase !== "waiting") return; // 이탈 시 예산 정리는 위 A0 effect 소관
    if (!onbGuideOpen || botStatus !== "answered") return;
    if (budget.count >= ONB_NUDGE_DELAYS_MS.length) {
      budget.exhausted = true;
      setOnbExhausted(true);
      setOnbNextPollAt(null);
      if (!budget.noticeSent) {
        // 소진 안내는 예산 생애 1회 — noticeSent 를 storage 에도 남겨 리마운트 후 중복 발송을 막는다.
        budget.noticeSent = true;
        setOnbNotice({
          id: "onb-nudge-notice", role: "bot",
          text: "분석이 평소보다 오래 걸리고 있어요. 가이드는 잠시 닫아둘게요 — 아래 \"지금 확인\" 버튼이나 아무 메시지로 이어서 진행할 수 있어요.",
          evidence: [], links: [], quickReplies: [], ttsState: "idle", ttsProgress: 0,
          timestamp: Date.now(),
        });
      }
      writeOnbNudgeBudget(budget);
      setOnbGuideOpen(false);
      return;
    }
    const delay = ONB_NUDGE_DELAYS_MS[budget.count];
    setOnbNextPollAt(Date.now() + delay); // waiting 화면의 "다음 자동 확인 N초 후" 표기용
    const t = setTimeout(() => {
      budget.count += 1;
      writeOnbNudgeBudget(budget);
      sendMessage("진행 상황 알려줘");
    }, delay);
    return () => clearTimeout(t);
  }, [onbGuideOpen, onbPhase, botStatus, sendMessage]);

  // 소진 안내 걷기: 안내 후 대화가 재개되면(새 전송 → thinking) 치운다 — 로컬 버블이라 항상
  // 마지막에 렌더되므로, 실제 메시지 뒤에 남으면 시간순이 왜곡된다.
  useEffect(() => {
    if (onbNotice && botStatus === "thinking") setOnbNotice(null);
  }, [botStatus, onbNotice]);

  const handleSend = () => {
    const text = input.trim();
    if (!text) return;
    setInput("");
    sendMessage(text);
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const isDisconnected = botStatus === "disconnected";

  // 활성 칩은 "마지막 봇 메시지"의 인테이크 턴에만 노출(이전 턴 칩은 비활성).
  const lastBotId = [...messages].reverse().find((m) => m.role === "bot")?.id;

  // 배너 서브텍스트: 실행 중이면 지원 건/모드, 인테이크면 진행 단계.
  let bannerSubtitle = "정보 확인 중";
  if (runStarted) {
    const s = runPlan?.slots;
    bannerSubtitle = [s?.company, s?.jobTitle, s?.mode ? MODE_LABELS[s.mode] ?? s.mode : null]
      .filter(Boolean).join(" · ") || "면접 준비 진행 중";
  } else {
    const lastIntake = [...messages].reverse().find((m) => m.role === "bot" && m.intake)?.intake;
    bannerSubtitle =
      lastIntake?.nextAsk === "CASE" ? "면접 준비 · 지원 건 확인 중 (1/2)"
      : lastIntake?.nextAsk === "MODE" ? "면접 준비 · 면접 모드 확인 중 (2/2)"
      : "면접 준비 · 정보 확인 중";
  }

  // 표면 크기 전환(morph): corner=우하단 340/360, floating=중앙 970×606 + 스크림.
  // 앵커(우하단↔중앙)가 달라 CSS 크기 보간은 깨지므로 즉시 전환(부드러운 shared-element 은 2차).
  const panelClass = floating
    ? "ct-float-in fixed z-50 flex flex-col bg-card overflow-hidden"
    : "fixed right-5 bottom-5 z-50 w-[360px] h-[560px] flex flex-col bg-card border border-border rounded-2xl overflow-hidden";
  const panelStyle: React.CSSProperties = floating
    ? {
        left: "50%", top: "50%", transform: "translate(-50%,-50%)",
        width: "min(970px, calc(100vw - 32px))",
        height: "min(606px, calc(100vh - 32px))",
        borderRadius: 20,
        border: "1px solid rgba(94,106,210,0.2)",
        boxShadow: "0 40px 90px rgba(22,23,26,0.4)",
      }
    : {
        boxShadow: orchestrator
          ? "0 16px 40px rgba(109,40,217,0.22), 0 4px 12px rgba(15,23,42,0.06)"
          : "0 12px 28px rgba(15,23,42,0.12), 0 4px 10px rgba(15,23,42,0.06)",
      };

  return (
    <>
      {/* 플로팅 스크림: 바깥 클릭 = 최소화(닫기 아님). 모달 아님(뒤 대시보드 보임). */}
      {floating && (
        <div className="ct-scrim-in fixed inset-0 z-40" style={{ background: "rgba(20,18,40,0.4)" }}
          onClick={collapseToCorner} aria-hidden />
      )}
    <div className={panelClass} style={panelStyle}>

      {/* ── Header ── */}
      <WidgetHeader
        orchestrator={orchestrator}
        isDisconnected={isDisconnected}
        isVoiceListening={voiceState === "listening"}
        floating={floating}
        canExpand={orchestrator}
        onSessions={handleOpenSessions}
        onCollapse={collapseToCorner}
        onExpand={expandToFloating}
        onClose={close}
      />

      {/* ── Mode Banner (인테이크·실행 내내 유지) ── */}
      {orchestrator && <ModeBanner subtitle={bannerSubtitle} onExit={openExitSheet} />}

      {/* ── Body ── */}
      {voiceState === "listening" ? (
        <VoiceListeningView
          interimTranscript={interimTranscript}
          onCancel={cancelVoice}
          onConfirm={confirmVoice}
        />
      ) : voiceState === "denied" ? (
        <MicDeniedView
          onRetry={startVoice}
          onTextMode={() => setVoiceState("idle")}
        />
      ) : isDisconnected ? (
        <DisconnectedView onRetry={retryConnection} />
      ) : (
        <>
          <div ref={scrollRef}
            className={`flex-1 p-4 overflow-y-auto flex flex-col gap-3.5 ${floating ? "items-stretch" : ""}`}
            style={{
              background: orchestrator ? "var(--orch-chat-bg)" : "var(--secondary)",
              // 플로팅은 무대(WorkView)와 동일 폭 기준 — 중앙 720 캡을 없애고 콘텐츠가 폭을 쓰게(좌우 24px).
              // 인테이크 말풍선/후보 카드·칩도 이 폭을 따른다. 실행 stage에선 말풍선만 아래 좁은 컬럼(720)으로 제한.
              ...(floating ? { width: "100%", paddingInline: 24 } : {}),
            }}>
            {messages.length === 0 && botStatus === "idle" ? (
              <EmptyState onSelect={sendMessage}
                onStartGuide={() => { setShowGuide(true); expandToFloating(); }} />
            ) : (
              <>
                {/* stage(오케 실행)에선 말풍선을 좁은 중앙 컬럼으로 — 넓은 무대에서 늘어지지 않게. */}
                <div className={stage ? "flex w-full max-w-[720px] mx-auto flex-col gap-3.5" : "contents"}>
                {messages.map((m) =>
                  m.role === "user" ? (
                    <UserBubble key={m.id} text={m.text} dimmed={isDisconnected} />
                  ) : (
                    <div key={m.id} className="flex flex-col gap-2.5">
                      <BotBubble message={m} onToggleTts={toggleTts} variant="widget"
                        onQuickReply={m.id === lastBotId ? sendMessage : undefined}
                        onSummarize={summarizePosts} orchestrator={orchestrator} />
                      {m.id === lastBotId && m.intake && !m.intake.ready && !runStarted && (
                        <IntakeChips intake={m.intake} onSelectCase={selectCase} onSelectMode={selectMode}
                          onNewCase={() => setIntakeGuide({ msgId: m.id, steps: ["jd"] })} />
                      )}
                      {/* 인테이크 완료(ready) 턴 — 면접 페이지 딥링크 칩(버그2 수술). 완료 직후는 sticky 가
                          풀려 "네" 같은 약신호가 FALLBACK 되묻기로 낙하하는 표면이었다 — 다음 행동을
                          칩으로 못박아 허공 타이핑 이유를 없앤다. run 실패(F-12)·WorkView 공백에도 유효. */}
                      {m.id === lastBotId && m.intake?.ready && (
                        <div className="ml-[37px]">
                          <InterviewGoChip onGo={() => goInterview(m.intake?.caseId ?? runCaseId)} />
                        </div>
                      )}
                      {m.interviewReport && (
                        <InterviewResultCard
                          data={m.interviewReport}
                          onContinueCorrection={() => sendMessage("이 면접 결과로 자소서 첨삭 이어서 해줘")}
                          onOpenCase={(cid) => navigateFromWork(`/applications/${cid}`)}
                        />
                      )}
                    </div>
                  )
                )}
                {/* ④ 넛지 소진 안내(로컬 1회) — 트랜스크립트 미기록, 다음 전송 시 걷힘. */}
                {onbNotice && (
                  <BotBubble message={onbNotice} onToggleTts={toggleTts} variant="widget"
                    orchestrator={orchestrator} />
                )}
                {/* ④ 넛지 소진 탈출 UI(F-22) — 안내 버블과 달리 waiting 인 동안 잔존한다. "지금 확인"은
                    즉시 폴 1회(예산 미소모·래치 독립) — 소진 뒤에도 사용자가 스스로 확인할 길을 남긴다. */}
                {onbPhase === "waiting" && onbExhausted && (
                  <OnbStuckNotice onCheck={onbManualCheck} disabled={botStatus === "thinking"} />
                )}
                </div>
                {runStarted && (
                  <div className={stage ? "w-full" : "ml-[37px]"}>
                    <AutoPrepWorkView
                      running={runRunning}
                      parts={runParts}
                      caseId={runCaseId}
                      company={runPlan?.slots.company ?? null}
                      onRetry={retryRun}
                      onNavigate={navigateFromWork}
                    />
                    {/* run 실패 가시화(F-09): parts 가 비면 WorkView 가 null 렌더라 이 배너가 유일한 표면 —
                        그때만 자체 재시도 버튼을 붙인다(파트가 있으면 WorkView 의 재시도 컨트롤이 담당). */}
                    {runError && !runRunning && (
                      <RunErrorNotice
                        message={runError}
                        showRetry={runParts.length === 0}
                        onRetry={retryRun}
                      />
                    )}
                  </div>
                )}
                {botStatus === "thinking" && <TypingIndicator orchestrator={orchestrator} />}
                {botStatus === "not_found" && <NotFoundView />}
              </>
            )}
          </div>
          <InputBar
            value={input}
            onChange={setInput}
            onSend={handleSend}
            onMic={startVoice}
            onKeyDown={handleKeyDown}
            disabled={botStatus === "thinking"}
            orchestrator={orchestrator}
          />
        </>
      )}

      {/* ── Exit confirm sheet ── */}
      {showExitSheet && <ExitSheet onConfirm={exitOrchestrator} onCancel={closeExitSheet} />}

      {/* ── Session panel (사이드바: 목록·전환·새 세션) ── */}
      {showSessions && (
        <SessionPanel
          sessions={sessions}
          activeSessionId={activeSessionId}
          onOpen={(id) => { openSession(id); setShowSessions(false); }}
          onNew={() => { newSession(); setShowSessions(false); }}
          onClose={() => setShowSessions(false)}
        />
      )}

      {/* ── ④→가이드 매핑 오버레이: 깡통 온보딩 텍스트 질문을 가이드 스텝으로. 회신은 기존
             텍스트 프로토콜 그대로(직무 텍스트→기술 CSV→공고 본문). 자소서 fileId 는 ready 병합 예약. ── */}
      {onbGuideOpen && onbPhase && (
        <OnboardingGuide
          wide={floating}
          server={{
            phase: onbPhase,
            bubbleText: lastBotMsg?.text,
            waitingSince: onbWaitingSince,
            nextPollAt: onbNextPollAt,
            submitting: botStatus === "thinking",
            onSubmit: (step, text, meta) => {
              if (step === "jd") setPendingAttachments(meta.coverLetterFileIds);
              // 공고 파일 경로: 가이드가 만든 지원 건 id 를 실어 보내면 ④가 입양(텍스트 생성 생략).
              sendMessage(text, meta.caseId != null ? { selectedCaseId: meta.caseId } : undefined);
            },
          }}
          onCollapse={collapseToCorner}
          onExpand={expandToFloating}
          onClose={() => { onbDismissedRef.current = true; setOnbGuideOpen(false); }}
          // 포기(X)와 달리 dismissedRef 를 안 건드린다 — 국면이 살아있는 채로 다음 봇 응답이
          // 다시 매핑되면 useEffect 가 새 인스턴스로 자동 재오픈한다(수집 내용은 서버 상태 기준).
          onAskQuestion={() => { onbAskingRef.current = true; setOnbGuideOpen(false); }}
          onGotoInterview={goInterview}
          onNavigate={navigateFromWork}
        />
      )}

      {/* ── ③→가이드 매핑 오버레이: 인테이크 CASE 슬롯을 가이드 스텝(빈 슬롯만큼)으로 채운다.
             제출 시 selectedCaseId 로 기존 프로토콜 회신, 닫으면 텍스트/칩 되묻기 폴백. ── */}
      {intakeGuide && (
        <OnboardingGuide
          wide={floating}
          intake={{ steps: intakeGuide.steps }}
          onCollapse={collapseToCorner}
          onExpand={expandToFloating}
          onClose={closeIntakeGuide}
          onAskQuestion={() => { intakeAskingRef.current = true; setIntakeGuide(null); }}
          onSlotFilled={handleSlotFilled}
          onGotoInterview={goInterview}
          onNavigate={navigateFromWork}
        />
      )}

      {/* ── 온보딩 가이드(대화로 준비 시작 → 서류·공고 첨부 → 실제 오케 SSE(WorkView) → 면접 권유) ── */}
      {showGuide && (
        <OnboardingGuide
          wide={floating}
          onCollapse={collapseToCorner}
          onExpand={expandToFloating}
          onClose={() => { setShowGuide(false); collapseToCorner(); }}
          onGotoInterview={(caseId) => {
            // 가이드에서 수집한 caseId 를 실어 D 면접 페이지로 인계(표식 남기고 복귀 시 결과 재조회).
            // ⚠️ D 확인 대상: InterviewPage 가 ?caseId 를 읽어 지원 건을 자동 선택해야 완결(현재 미소비).
            setShowGuide(false);
            collapseToCorner();
            goInterview(caseId);
          }}
          onNavigate={navigateFromWork}
        />
      )}
    </div>
    </>
  );
}

/* ════════════════ Orchestrator components ════════════════ */

/** 인테이크 완료 턴의 면접 페이지 인계 칩 — /interview?caseId=&tab=modes 는 기존 F 표면(goInterview·
 *  WorkView footer·actionFor)과 동일 계약. ?caseId 자동 선택은 D 페이지 소비 대기(감사 §6 D 인계). */
function InterviewGoChip({ onGo }: { onGo: () => void }) {
  return (
    <div className="flex flex-wrap gap-1.5">
      <button
        onClick={onGo}
        className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-[12.5px] font-bold transition-colors"
        style={{
          border: "1.5px solid var(--orch-violet)",
          background: "var(--orch-surface)",
          color: "var(--orch-violet)",
        }}>
        <ArrowUpRight size={13} />
        면접 보러 가기
      </button>
    </div>
  );
}

/** run 실패 안내(F-09) — WorkView 가 null 렌더(plan 전 사망)일 때는 이 카드가 유일한 실패 표면. */
/** ④ 공고 추출 대기 소진 탈출 배너(F-22) — RunErrorNotice 패턴. waiting 국면 + 소진 래치 동안 잔존. */
function OnbStuckNotice({ onCheck, disabled }: { onCheck: () => void; disabled: boolean }) {
  return (
    <div className="mt-2.5 rounded-[13px] px-3.5 py-3 flex flex-col gap-2 bg-card"
      style={{ border: "1px solid rgba(94,106,210,0.35)" }} role="status">
      <div className="flex items-center gap-1.5 text-[12.5px] font-bold text-foreground">
        <Loader2 size={14} className="shrink-0" style={{ color: "var(--orch-violet, #5e6ad2)" }} />
        공고 분석이 평소보다 오래 걸리고 있어요
      </div>
      <div className="text-[12px] leading-[1.55] text-muted-foreground">
        자동 확인은 멈췄지만 분석은 계속 진행 중일 수 있어요. 지금 바로 확인하거나, 아무 메시지를 보내도 이어져요.
      </div>
      <button type="button" onClick={onCheck} disabled={disabled}
        className="inline-flex h-8 items-center gap-1.5 self-start whitespace-nowrap rounded-full px-3 text-[11.5px] font-bold text-white transition-transform hover:brightness-110 disabled:opacity-50"
        style={{ background: "var(--gradient-orchestrator)" }}>
        <RotateCw size={13} />
        지금 확인
      </button>
    </div>
  );
}

function RunErrorNotice({ message, showRetry, onRetry }: {
  message: string; showRetry: boolean; onRetry: () => void;
}) {
  return (
    <div className="mt-2.5 rounded-[13px] px-3.5 py-3 flex flex-col gap-2 bg-card"
      style={{ border: "1px solid rgba(207,34,46,0.25)" }} role="alert">
      <div className="flex items-center gap-1.5 text-[12.5px] font-bold text-foreground">
        <WifiOff size={14} className="shrink-0" style={{ color: "var(--destructive)" }} />
        면접 준비 실행이 중단됐어요
      </div>
      <div className="text-[12px] leading-[1.55] text-muted-foreground">{message}</div>
      {showRetry && (
        <button type="button" onClick={onRetry}
          className="inline-flex h-8 items-center gap-1.5 self-start whitespace-nowrap rounded-full px-3 text-[11.5px] font-bold text-white transition-transform hover:brightness-110"
          style={{ background: "var(--gradient-orchestrator)" }}>
          <RotateCw size={13} />
          다시 시도
        </button>
      )}
    </div>
  );
}

function OrchestratorAvatar({ size = 28, iconScale = 0.52 }: { size?: number; iconScale?: number }) {
  return (
    <div className="rounded-full flex items-center justify-center text-white shrink-0 font-bold"
      style={{ width: size, height: size, background: "var(--gradient-orchestrator)", fontSize: size * iconScale }}>
      {ORCH_GLYPH}
    </div>
  );
}

function ModeBanner({ subtitle, onExit }: { subtitle: string; onExit: () => void }) {
  return (
    <div role="status" aria-live="polite"
      className="flex items-center gap-2.5 px-3.5 py-2.5 text-white"
      style={{ background: "var(--gradient-orchestrator)" }}>
      <div className="w-[26px] h-[26px] rounded-[8px] flex items-center justify-center text-white shrink-0 font-bold text-[14px]"
        style={{ background: "rgba(255,255,255,0.16)" }}>
        {ORCH_GLYPH}
      </div>
      <div className="leading-tight min-w-0 flex-1">
        <div className="text-[12.5px] font-extrabold">AI 오케스트레이터</div>
        <div className="text-[10.5px] truncate" style={{ color: "rgba(255,255,255,0.78)" }}>{subtitle}</div>
      </div>
      <button onClick={onExit} aria-label="일반 상담으로 돌아가기"
        className="inline-flex items-center gap-1 h-7 px-2.5 rounded-full text-[11px] font-bold text-white shrink-0 transition-colors hover:bg-white/15"
        style={{ border: "1px solid rgba(255,255,255,0.34)", background: "rgba(255,255,255,0.10)" }}>
        <LogOut size={13} />
        일반 상담으로
      </button>
    </div>
  );
}

function IntakeChips({ intake, onSelectCase, onSelectMode, onNewCase }: {
  intake: NonNullable<ChatMessage["intake"]>;
  onSelectCase: (c: IntakeCaseCandidate) => void;
  onSelectMode: (m: IntakeModeOption) => void;
  /** 기존 지원 건 말고 새 공고로 — 가이드 공고 스텝(빈 슬롯만큼)을 연다. */
  onNewCase?: () => void;
}) {
  if (intake.nextAsk === "CASE" && intake.candidates.length > 0) {
    return (
      <div className="ml-[37px] flex flex-col gap-2">
        {intake.candidates.map((c) => (
          <ApplicationChip key={c.id} candidate={c} onSelect={() => onSelectCase(c)} />
        ))}
        {onNewCase && (
          <button onClick={onNewCase}
            className="inline-flex items-center gap-1.5 self-start px-3 py-2 rounded-full text-[12.5px] font-semibold transition-colors"
            style={{ background: "var(--card)", border: "1px dashed var(--orch-point)", color: "var(--orch-violet)" }}>
            <Plus size={13} />
            새 공고로 시작
          </button>
        )}
      </div>
    );
  }
  if (intake.nextAsk === "MODE" && intake.modes.length > 0) {
    return (
      <div className="ml-[37px] flex flex-wrap gap-1.5">
        {intake.modes.map((m) => (
          <ModeChip key={m.code} mode={m} onSelect={() => onSelectMode(m)} />
        ))}
      </div>
    );
  }
  return null;
}

function ApplicationChip({ candidate, onSelect }: { candidate: IntakeCaseCandidate; onSelect: () => void }) {
  // placeholder 원문("기업명 확인 필요")은 표시 계층에서 "미확인"으로(F-02) — 진행 차단은 백엔드 게이트 소관.
  const company = displayCompany(candidate.companyName);
  const jobTitle = displayJobTitle(candidate.jobTitle);
  const initial = company.charAt(0) || "?";
  return (
    <button onClick={onSelect} role="button"
      className="group flex items-center gap-2.5 w-full min-h-[56px] px-3 py-2.5 rounded-[13px] bg-card text-left transition-all hover:shadow-[0_4px_12px_rgba(109,40,217,0.10)]"
      style={{ border: "1px solid rgba(0,0,0,0.10)" }}>
      <span className="w-[34px] h-[34px] rounded-[9px] flex items-center justify-center text-white font-extrabold text-[15px] shrink-0"
        style={{ background: "var(--gradient-orchestrator)" }}>
        {initial}
      </span>
      <span className="flex-1 min-w-0">
        <span className="block text-[13.5px] font-bold text-foreground truncate">{company}</span>
        <span className="block text-[11.5px] font-semibold text-muted-foreground truncate">{jobTitle}</span>
      </span>
      <ArrowRight size={15} className="shrink-0 text-muted-foreground transition-colors"
        style={{ color: "var(--orch-point)" }} />
    </button>
  );
}

function ModeChip({ mode, onSelect }: { mode: IntakeModeOption; onSelect: () => void }) {
  return (
    <button onClick={onSelect} role="button"
      className="inline-flex items-center px-3 py-2 rounded-full bg-card text-[12.5px] font-semibold text-muted-foreground transition-colors hover:text-foreground"
      style={{ border: "1px solid rgba(0,0,0,0.12)" }}>
      {mode.label}
    </button>
  );
}

function ExitSheet({ onConfirm, onCancel }: { onConfirm: () => void; onCancel: () => void }) {
  return (
    <div className="absolute inset-0 z-10 flex flex-col justify-end" style={{ background: "rgba(20,16,40,0.42)" }}
      onClick={onCancel}>
      <div className="m-3.5 rounded-[18px] bg-card px-[18px] pt-5 pb-4 text-center"
        style={{ boxShadow: "0 20px 50px rgba(20,16,40,0.4)" }}
        onClick={(e) => e.stopPropagation()}>
        <div className="mx-auto w-[46px] h-[46px] rounded-[13px] flex items-center justify-center mb-3"
          style={{ background: "var(--orch-surface)", color: "var(--orch-violet)" }}>
          <LogOut size={22} />
        </div>
        <div className="text-base font-extrabold mb-1.5">오케스트레이터를 종료할까요?</div>
        <div className="text-[13px] leading-[1.6] text-muted-foreground mb-4">
          지금 종료하면 일반 상담 모드로 돌아가요. 진행 중인 준비는 멈춰요.
        </div>
        <div className="flex flex-col gap-2">
          <button onClick={onConfirm}
            className="h-[46px] rounded-[11px] bg-foreground text-background text-sm font-bold hover:opacity-90 transition-opacity">
            종료하고 일반 상담으로
          </button>
          <button onClick={onCancel}
            className="h-[42px] rounded-[11px] bg-card text-[13.5px] font-semibold text-muted-foreground hover:bg-secondary transition-colors"
            style={{ border: "1px solid rgba(0,0,0,0.14)" }}>
            계속 준비하기
          </button>
        </div>
        <div className="mt-3 text-[11px] text-muted-foreground">
          입력창에 <b>“그만”</b> 이라고 보내도 빠져나올 수 있어요.
        </div>
      </div>
    </div>
  );
}

/**
 * 세션 행(카드형) — design_handoff README 의 SessionRow 스펙(ApplicationChip 확장).
 * [이니셜 아이콘] [ title(굵게) / (모드 배지 · 상대시각) ]. 진행률·안읽음은 범위 밖(제외).
 */
function SessionRow({ session, active, onClick }: {
  session: ChatSession; active: boolean; onClick: () => void;
}) {
  // 세션 제목은 백엔드가 "{회사} {직무}"로 조합 — placeholder 원문이 섞여 있으면 표시만 치환(F-02).
  const title = displayCaseText(session.title);
  const initial = title.trim().charAt(0) || "?";
  const badge = session.mode ? MODE_BADGE[session.mode] : null;
  const when = relativeTime(session.updatedAt);
  // 호버 배경/보더가 토큰·rgba라 Tailwind hover 유틸로 못 빼고, active 행은 호버를 막아야 해
  // JS 핸들러로 처리. 기본/호버 값을 한 곳에서 관리(테마 토큰 재사용).
  const REST = { background: "transparent", borderColor: "var(--border)" };
  const HOVER = { background: "var(--orch-header-tint)", borderColor: "rgba(124,58,237,0.32)" };
  return (
    <button onClick={onClick} role="button" aria-pressed={active}
      className="group flex items-center gap-2.5 w-full px-3 py-2.5 rounded-[13px] text-left transition-all"
      style={
        active
          ? { background: "var(--orch-surface)", border: "1.5px solid var(--orch-violet)" }
          : { background: REST.background, border: `1px solid ${REST.borderColor}` }
      }
      onMouseEnter={(e) => { if (!active) Object.assign(e.currentTarget.style, HOVER); }}
      onMouseLeave={(e) => { if (!active) Object.assign(e.currentTarget.style, REST); }}>
      <span className="w-[34px] h-[34px] rounded-[9px] flex items-center justify-center text-white font-extrabold text-[15px] shrink-0"
        style={{ background: "var(--gradient-orchestrator)" }}>
        {initial}
      </span>
      <span className="flex-1 min-w-0">
        <span className="block text-[13px] font-bold text-foreground truncate">{title}</span>
        <span className="flex items-center gap-1.5 mt-0.5">
          {badge && (
            <span className="inline-flex items-center px-1.5 py-0.5 rounded-full text-[10.5px] font-bold leading-none"
              style={{ background: "var(--orch-surface)", color: "var(--orch-violet)" }}>
              {badge}
            </span>
          )}
          {when && <span className="text-[11px] text-muted-foreground">{when}</span>}
        </span>
      </span>
    </button>
  );
}

/** 세션 사이드바(오버레이): 인테이크 세션 목록 + 전환 + 새 세션. */
function SessionPanel({ sessions, activeSessionId, onOpen, onNew, onClose }: {
  sessions: ChatSession[];
  activeSessionId: string;
  onOpen: (id: string) => void;
  onNew: () => void;
  onClose: () => void;
}) {
  return (
    <div className="absolute inset-0 z-10 flex flex-col" style={{ background: "rgba(20,16,40,0.42)" }}
      onClick={onClose}>
      <div className="m-3.5 rounded-[18px] bg-card overflow-hidden flex flex-col max-h-[460px]"
        style={{ boxShadow: "0 20px 50px rgba(20,16,40,0.4)" }}
        onClick={(e) => e.stopPropagation()}>
        <div className="flex items-center justify-between px-4 py-3 border-b border-border">
          <div className="text-[13.5px] font-extrabold">면접 준비 세션</div>
          <button onClick={onClose} aria-label="닫기"
            className="w-7 h-7 rounded-lg flex items-center justify-center text-muted-foreground hover:bg-accent transition-colors">
            <X size={15} />
          </button>
        </div>
        <div className="p-3">
          <button onClick={onNew}
            className="flex items-center justify-center gap-1.5 w-full h-[40px] rounded-lg text-white text-[13px] font-bold hover:opacity-90 transition-opacity"
            style={{ background: "var(--gradient-orchestrator)" }}>
            <Plus size={15} />
            새 면접 준비
          </button>
        </div>
        <div className="px-3 pb-3 overflow-y-auto flex flex-col gap-1.5">
          {sessions.length === 0 ? (
            <div className="text-[12px] leading-[1.6] text-muted-foreground text-center py-6">
              {/* 이 목록은 지난 "대화(세션)" 이력이다 — 지원 건 존재 여부와 다르므로 문구를 구분한다. */}
              아직 진행한 준비 세션이 없어요.<br />“카카오 백엔드 면접 준비해줘”처럼 시작하면 여기에 세션이 쌓여요.
            </div>
          ) : (
            sessions.map((s) => (
              <SessionRow key={s.id} session={s} active={s.id === activeSessionId}
                onClick={() => onOpen(s.id)} />
            ))
          )}
        </div>
      </div>
    </div>
  );
}

/* ════════════════ Sub-components ════════════════ */

function WidgetHeader({ orchestrator, isDisconnected, isVoiceListening, floating, canExpand, onSessions, onCollapse, onExpand, onClose }: {
  orchestrator?: boolean; isDisconnected: boolean; isVoiceListening: boolean;
  floating?: boolean; canExpand?: boolean;
  onSessions: () => void; onCollapse: () => void; onExpand: () => void; onClose: () => void;
}) {
  return (
    <div className="flex items-center gap-2.5 px-4 py-3.5 border-b border-border transition-colors"
      style={{ background: orchestrator ? "var(--orch-header-tint)" : undefined }}>
      {orchestrator && !isDisconnected ? (
        <div className="relative">
          <OrchestratorAvatar size={36} iconScale={0.5} />
          <span className="absolute -right-px -bottom-px w-[11px] h-[11px] rounded-full border-2 border-card"
            style={{ background: "#16a34a" }} />
        </div>
      ) : (
        <div className="relative w-9 h-9 rounded-full flex items-center justify-center text-white"
          style={{ background: isDisconnected ? "var(--muted)" : "linear-gradient(135deg, #2563eb, #4f46e5)" }}>
          <Sparkles size={18} className={isDisconnected ? "text-muted-foreground" : ""} />
          <span className="absolute -right-px -bottom-px w-[11px] h-[11px] rounded-full border-2 border-card"
            style={{ background: isDisconnected ? "var(--muted-foreground)" : "#16a34a" }} />
        </div>
      )}
      <div className="leading-tight">
        <div className="text-sm font-bold">튜너봇</div>
        {isVoiceListening ? (
          <div className="text-[11.5px] font-semibold text-red-600">● 음성 인식 중</div>
        ) : isDisconnected ? (
          <div className="text-[11.5px] font-semibold text-muted-foreground">연결 대기 중</div>
        ) : orchestrator ? (
          <div className="text-[11.5px] font-bold" style={{ color: "var(--orch-point)" }}>오케스트레이터 모드</div>
        ) : (
          <div className="text-[11.5px] font-semibold" style={{ color: "#16a34a" }}>응답 가능</div>
        )}
      </div>
      <div className="ml-auto flex gap-1 text-muted-foreground">
        <button onClick={onSessions} aria-label="면접 준비 세션"
          className="w-[30px] h-[30px] rounded-lg flex items-center justify-center hover:bg-secondary transition-colors">
          <History size={16} />
        </button>
        {floating ? (
          // 플로팅: ⤡ 코너로 최소화(세션 유지). 닫기는 X.
          <button onClick={onCollapse} aria-label="코너로 최소화"
            className="w-[30px] h-[30px] rounded-lg flex items-center justify-center hover:bg-secondary transition-colors">
            <Minimize2 size={16} />
          </button>
        ) : (
          // 코너에서 오케 진행 중 → ⤢ 다시 크게(플로팅). (최소화 버튼은 닫기와 기능이 같아 제거)
          canExpand && (
            <button onClick={onExpand} aria-label="크게 펼치기"
              className="w-[30px] h-[30px] rounded-lg flex items-center justify-center hover:bg-secondary transition-colors">
              <Maximize2 size={15} />
            </button>
          )
        )}
        <button onClick={onClose} aria-label="닫기"
          className="w-[30px] h-[30px] rounded-lg flex items-center justify-center hover:bg-secondary transition-colors">
          <X size={17} />
        </button>
      </div>
    </div>
  );
}

function EmptyState({ onSelect, onStartGuide }: { onSelect: (text: string) => void; onStartGuide: () => void }) {
  return (
    <div className="flex flex-col h-full">
      <div className="flex flex-col items-center text-center mt-[18px] mb-5">
        <div className="w-[52px] h-[52px] rounded-[15px] flex items-center justify-center text-white mb-3"
          style={{ background: "linear-gradient(135deg, #2563eb, #4f46e5)", boxShadow: "0 8px 18px rgba(37,99,235,0.32)" }}>
          <Sparkles size={25} />
        </div>
        <div className="text-base font-extrabold mb-1">안녕하세요, 튜너봇이에요</div>
        <div className="text-[13px] leading-relaxed text-muted-foreground max-w-[250px]">
          CareerTuner 이용 중 궁금한 점을 물어보세요. FAQ와 공지를 찾아 바로 알려드릴게요.
        </div>
      </div>

      {/* 온보딩 가이드 진입 — 처음 오거나 준비를 시작하고 싶은 사람용(대화 한 번으로 서류→공고→적합도→면접). */}
      <button onClick={onStartGuide}
        className="flex items-center gap-3 w-full px-3.5 py-3 rounded-xl mb-4 text-left transition-transform hover:brightness-[1.03]"
        style={{ background: "var(--orch-surface)", border: "1px solid var(--orch-point)" }}>
        <span className="w-9 h-9 rounded-[10px] flex items-center justify-center text-white shrink-0 font-bold"
          style={{ background: "var(--gradient-orchestrator)", fontSize: 17 }}>
          {ORCH_GLYPH}
        </span>
        <span className="flex-1 min-w-0">
          <span className="block text-[13px] font-extrabold" style={{ color: "var(--orch-violet)" }}>대화로 준비 시작하기</span>
          <span className="block text-[11.5px] leading-[1.5] text-muted-foreground">서류·공고만 올리면 적합도부터 면접까지 이끌어드려요</span>
        </span>
        <ArrowRight size={16} className="shrink-0" style={{ color: "var(--orch-violet)" }} />
      </button>

      <div className="text-[11.5px] font-bold text-muted-foreground mb-2 ml-0.5">자주 묻는 질문</div>
      <div className="flex flex-col gap-2">
        {SUGGESTED_QUESTIONS.map(({ icon, text }) => {
          const Icon = ICON_MAP[icon];
          return (
            <button key={text} onClick={() => onSelect(text)}
              className="flex items-center gap-2.5 px-3 py-2.5 bg-card border border-border rounded-xl text-[13px] font-medium text-foreground shadow-[0_1px_2px_rgba(15,23,42,0.04)] hover:border-primary/40 hover:bg-primary/10 transition-colors text-left">
              <Icon size={15} className="text-primary shrink-0" />
              <span className="flex-1">{text}</span>
              <ArrowRight size={15} className="text-muted-foreground shrink-0" />
            </button>
          );
        })}
      </div>
    </div>
  );
}

function UserBubble({ text, dimmed }: { text: string; dimmed?: boolean }) {
  return (
    <div className="flex justify-end" style={{ opacity: dimmed ? 0.55 : 1 }}>
      <div className="max-w-[78%] bg-blue-600 text-white rounded-[15px] rounded-tr-[5px] px-3 py-2.5 text-[13.5px] leading-relaxed">
        {text}
      </div>
    </div>
  );
}

function BotBubble({ message, onToggleTts, variant = "widget", onQuickReply, onSummarize, orchestrator }: {
  message: ChatMessage; onToggleTts: (id: string) => void; variant?: "widget" | "full";
  onQuickReply?: (text: string) => void; onSummarize?: (postIds: number[]) => void; orchestrator?: boolean;
}) {
  const avatarSize = variant === "full" ? 34 : 28;
  const iconSize = variant === "full" ? 16 : 14;
  const maxW = variant === "full" ? "max-w-[72%]" : "max-w-[84%]";

  return (
    <div className="flex gap-2.5 items-start">
      {orchestrator ? (
        <div className="mt-0.5">
          <OrchestratorAvatar size={avatarSize} iconScale={0.5} />
        </div>
      ) : (
        <div className="rounded-full flex items-center justify-center text-white shrink-0 mt-0.5"
          style={{ width: avatarSize, height: avatarSize, background: "linear-gradient(135deg, #2563eb, #4f46e5)" }}>
          <Sparkles size={iconSize} />
        </div>
      )}
      <div className={`${maxW} flex flex-col gap-2.5`}>
        <div className="bg-card border border-border rounded-[15px] rounded-tl-[5px] px-3.5 py-3 text-[13.5px] leading-[1.65] text-foreground">
          <span dangerouslySetInnerHTML={{ __html: message.text.replace(/\*\*(.*?)\*\*/g, '<b class="text-foreground">$1</b>') }} />

          {/* TTS controls */}
          {message.ttsState !== "idle" || variant === "full" ? (
            <div className="flex items-center gap-2 mt-2.5 pt-2.5 border-t border-border">
              {variant === "full" ? (
                <>
                  <button onClick={() => onToggleTts(message.id)}
                    className="inline-flex items-center gap-1.5 h-[30px] px-3 rounded-full bg-primary/10 text-primary text-xs font-semibold hover:bg-primary/20 transition-colors">
                    <Volume2 size={14} />
                    <span className="text-xs font-semibold">음성으로 듣기</span>
                  </button>
                </>
              ) : (
                <>
                  <button onClick={() => onToggleTts(message.id)}
                    className="w-[26px] h-[26px] rounded-full bg-primary/10 text-primary flex items-center justify-center hover:bg-primary/20 transition-colors">
                    {message.ttsState === "playing" ? <Pause size={13} /> : <Play size={13} />}
                  </button>
                  <div className="flex-1 h-1 rounded-full bg-secondary relative overflow-hidden">
                    <div className="absolute left-0 top-0 bottom-0 bg-blue-600 rounded-full" style={{ width: `${message.ttsProgress || 46}%` }} />
                  </div>
                  <span className="text-[10.5px] text-muted-foreground tabular-nums">0:06</span>
                </>
              )}
            </div>
          ) : null}
        </div>

        {/* SiteLink buttons */}
        {message.links && message.links.length > 0 && (
          <SiteLinkButtons links={message.links} />
        )}

        {/* 추천 후기 압축 요약 칩 (오케스트레이터 톤 — 일반 퀵리플라이와 시각적 구분) */}
        {onSummarize && message.summaryChip && message.summaryChip.postIds.length > 0 && (
          <SummaryChipButton chip={message.summaryChip} onSummarize={onSummarize} />
        )}

        {/* Quick reply chips */}
        {onQuickReply && message.quickReplies && message.quickReplies.length > 0 && (
          <QuickReplyChips replies={message.quickReplies} onSelect={onQuickReply} />
        )}

      </div>
    </div>
  );
}

/** 추천 후기 압축 요약 전용 버튼 — 보라(오케스트레이터) 톤·굵은 테두리로 일반 칩과 구분. */
function SummaryChipButton({ chip, onSummarize }: {
  chip: NonNullable<ChatMessage["summaryChip"]>; onSummarize: (postIds: number[]) => void;
}) {
  return (
    <div className="flex flex-wrap gap-1.5">
      <button
        onClick={() => onSummarize(chip.postIds)}
        className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-[12.5px] font-bold transition-colors"
        style={{
          border: "1.5px solid var(--orch-violet)",
          background: "var(--orch-surface)",
          color: "var(--orch-violet)",
        }}>
        <Sparkles size={13} />
        {chip.label}
      </button>
    </div>
  );
}

/** 면접 복귀 결과 카드 — 완료 후 챗봇에 재조회한 리포트(실값). 순위/상위% 없음. A톤. */
function InterviewResultCard({ data, onContinueCorrection, onOpenCase }: {
  data: InterviewReportCard;
  onContinueCorrection: () => void;
  onOpenCase: (caseId: number) => void;
}) {
  return (
    <div className="ml-[37px] rounded-2xl border overflow-hidden" style={{ borderColor: "var(--orch-point)" }}>
      <div className="flex items-center gap-3 px-4 py-3.5" style={{ background: "var(--orch-surface)" }}>
        <div className="w-11 h-11 rounded-full flex items-center justify-center text-white shrink-0 text-[15px] font-extrabold"
          style={{ background: "var(--gradient-orchestrator)" }}>
          {data.totalScore}
        </div>
        <div className="min-w-0">
          <div className="text-[13px] font-extrabold">면접 결과</div>
          <div className="text-[11px] text-muted-foreground">
            질문 {data.questionCount}개{data.durationLabel ? ` · ${data.durationLabel}` : ""}
          </div>
        </div>
      </div>

      {data.categories.length > 0 && (
        <div className="px-4 pt-3 flex flex-wrap gap-1.5">
          {data.categories.map((c) => (
            <span key={c.label} className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-[11px] font-bold"
              style={{ background: "var(--orch-surface)", color: "var(--orch-violet)" }}>
              {c.label} <b className="tabular-nums">{c.score}</b>
            </span>
          ))}
        </div>
      )}

      {data.summaryFeedback.length > 0 && (
        <div className="px-4 pt-3 flex flex-col gap-1.5">
          {data.summaryFeedback.slice(0, 3).map((f, i) => (
            <div key={i} className="flex gap-2 text-[12px] leading-[1.5] text-foreground">
              <Check size={14} className="mt-0.5 shrink-0" style={{ color: "var(--orch-violet)" }} />
              <span>{f}</span>
            </div>
          ))}
        </div>
      )}

      <div className="px-4 py-3.5 flex gap-2">
        <button onClick={onContinueCorrection}
          className="flex-1 h-10 rounded-lg text-white text-[12.5px] font-bold transition-transform hover:brightness-110"
          style={{ background: "var(--gradient-orchestrator)" }}>
          자소서 첨삭 이어가기
        </button>
        {data.caseId != null && (
          <button onClick={() => onOpenCase(data.caseId as number)}
            className="h-10 px-3 rounded-lg border border-border text-[12.5px] font-semibold text-foreground transition hover:bg-secondary">
            지원 건
          </button>
        )}
      </div>
    </div>
  );
}

function QuickReplyChips({ replies, onSelect }: { replies: string[]; onSelect: (text: string) => void }) {
  return (
    <div className="flex flex-wrap gap-1.5">
      {replies.map((r) => (
        <button
          key={r}
          onClick={() => onSelect(r)}
          className="inline-flex items-center px-3 py-1.5 rounded-full border border-primary/30 bg-card text-primary text-[12.5px] font-semibold hover:bg-primary/10 transition-colors"
        >
          {r}
        </button>
      ))}
    </div>
  );
}

function SiteLinkButtons({ links }: { links: SiteLink[] }) {
  const navigate = useNavigate();

  const handleClick = useCallback((url: string) => {
    if (url.startsWith("http")) {
      window.open(url, "_blank", "noopener,noreferrer");
    } else {
      navigate(url);
    }
  }, [navigate]);

  return (
    <div className="flex flex-col gap-1.5">
      {links.map((link) => (
        <button
          key={link.url}
          onClick={() => handleClick(link.url)}
          className="flex items-center justify-center gap-1.5 w-full h-9 rounded-lg border border-primary/30 bg-primary/10 text-primary text-[13px] font-semibold hover:bg-primary/20 transition-colors"
        >
          <ArrowRight size={14} />
          {link.label}
        </button>
      ))}
    </div>
  );
}

function EvidenceChips({ evidence }: { evidence: ChatEvidence[] }) {
  return (
    <div className="flex flex-wrap gap-1.5">
      {evidence.map((e) => (
        <a key={e.id} href={e.url}
          className="inline-flex items-center gap-1 px-2.5 py-1 bg-primary/10 border border-primary/20 rounded-full text-[11.5px] font-semibold text-primary hover:bg-primary/20 transition-colors">
          <FileText size={12} />
          {e.title}
          <ArrowUpRight size={11} className="opacity-60" />
        </a>
      ))}
    </div>
  );
}

function EvidenceCards({ evidence }: { evidence: ChatEvidence[] }) {
  const iconBg: Record<string, string> = { "도움말": "bg-primary/10 text-primary", "가이드": "bg-green-50 dark:bg-green-500/15 text-green-600", "FAQ": "bg-primary/10 text-primary", "공지": "bg-amber-50 dark:bg-amber-500/15 text-amber-600" };
  const badgeBg: Record<string, string> = { "도움말": "bg-primary/10 text-primary", "가이드": "bg-green-50 dark:bg-green-500/15 text-green-700", "FAQ": "bg-primary/10 text-primary", "공지": "bg-amber-50 dark:bg-amber-500/15 text-amber-700" };

  return (
    <div className="flex flex-col gap-2.5">
      {evidence.map((e) => (
        <a key={e.id} href={e.url}
          className="flex gap-3 items-start bg-card border border-border rounded-xl p-3.5 shadow-[0_1px_2px_rgba(15,23,42,0.04)] hover:border-primary/40 transition-colors">
          <span className={`shrink-0 w-9 h-9 rounded-[9px] flex items-center justify-center ${iconBg[e.type] || "bg-primary/10 text-primary"}`}>
            {e.type === "가이드" ? <Shield size={17} /> : <FileText size={17} />}
          </span>
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-1.5 mb-0.5">
              <span className={`text-[10px] font-bold rounded-full px-2 py-0.5 ${badgeBg[e.type] || "bg-primary/10 text-primary"}`}>{e.type}</span>
              <span className="text-[13.5px] font-bold">{e.title}</span>
            </div>
            <div className="text-xs leading-[1.55] text-muted-foreground">{e.snippet}</div>
          </div>
          <ArrowUpRight size={16} className="text-muted-foreground shrink-0 mt-0.5" />
        </a>
      ))}
    </div>
  );
}

function TypingIndicator({ orchestrator }: { orchestrator?: boolean }) {
  return (
    <>
      <div className="flex gap-2.5 items-end">
        {orchestrator ? (
          <OrchestratorAvatar size={28} iconScale={0.5} />
        ) : (
          <div className="w-7 h-7 rounded-full flex items-center justify-center text-white shrink-0"
            style={{ background: "linear-gradient(135deg, #2563eb, #4f46e5)" }}>
            <Sparkles size={14} />
          </div>
        )}
        <div className="bg-card border border-border rounded-[15px] rounded-bl-[5px] px-4 py-3.5 flex gap-1.5 items-center">
          {[0, 0.18, 0.36].map((delay, i) => (
            <span key={i} className="ct-typing-dot w-[7px] h-[7px] rounded-full bg-muted-foreground"
              style={{ animation: "ctTyping 1.2s infinite ease-in-out", animationDelay: `${delay}s` }} />
          ))}
        </div>
      </div>
      <div className="flex items-center gap-1.5 ml-[37px] text-[11px] text-muted-foreground">
        <FileSearch size={12} />
        도움말 문서를 찾는 중이에요…
      </div>
    </>
  );
}

function NotFoundView() {
  return (
    <div className="flex gap-2.5 items-start">
      <div className="w-7 h-7 rounded-full flex items-center justify-center text-white shrink-0 mt-0.5"
        style={{ background: "linear-gradient(135deg, #2563eb, #4f46e5)" }}>
        <Sparkles size={14} />
      </div>
      <div className="max-w-[86%] flex flex-col gap-2.5">
        <div className="bg-card border border-border rounded-[15px] rounded-tl-[5px] px-3.5 py-3">
          <div className="flex items-center gap-1.5 text-[12.5px] font-bold text-amber-700 mb-1.5">
            <SearchX size={15} />
            정확한 답변을 찾지 못했어요
          </div>
          <div className="text-[13px] leading-[1.65] text-foreground">
            관련 문서를 확인했지만 확실한 안내가 없어서, <b className="text-foreground">잘못된 정보를 드리지 않으려고</b> 답변을 멈췄어요. 상담사가 정확히 도와드릴게요.
          </div>
        </div>
        <button className="flex items-center justify-center gap-1.5 w-full h-[42px] rounded-lg bg-primary text-white text-[13.5px] font-bold hover:brightness-110 transition-colors">
          <Headset size={16} />
          상담사 연결하기
        </button>
        <a href="/support/contact"
          className="flex items-center justify-center gap-1.5 w-full h-10 rounded-lg border border-border bg-card text-foreground text-[13px] font-semibold hover:bg-secondary transition-colors">
          <PenLine size={15} />
          1:1 문의 남기기
        </a>
        <div className="text-[11px] text-muted-foreground text-center">
          상담 가능 시간 평일 09:00–18:00 · 보통 5분 내 응답
        </div>
      </div>
    </div>
  );
}

function DisconnectedView({ onRetry }: { onRetry: () => void }) {
  return (
    <>
      <div className="flex-1 p-4 flex flex-col" style={{ background: "var(--secondary)" }}>
        <div className="mt-auto flex flex-col items-center text-center px-1.5 pb-1">
          <div className="w-12 h-12 rounded-[14px] bg-amber-50 dark:bg-amber-500/15 flex items-center justify-center text-amber-700 mb-3">
            <WifiOff size={23} />
          </div>
          <div className="text-sm font-extrabold mb-1">AI 상담이 일시적으로 어려워요</div>
          <div className="text-[12.5px] leading-relaxed text-muted-foreground max-w-[262px]">
            잠시 후 다시 시도하거나, 지금 바로 1:1 문의를 남기면 상담사가 순서대로 답변드려요.
          </div>
        </div>
      </div>
      <div className="px-3 py-2.5 border-t border-border flex flex-col gap-2.5">
        <a href="/support/contact"
          className="flex items-center justify-center gap-1.5 w-full h-[42px] rounded-lg bg-primary text-white text-[13.5px] font-bold hover:brightness-110 transition-colors">
          <PenLine size={16} />
          1:1 문의 남기기
        </a>
        <button onClick={onRetry}
          className="flex items-center justify-center gap-1.5 w-full h-9 rounded-lg bg-transparent text-muted-foreground text-[12.5px] font-semibold hover:bg-secondary transition-colors">
          <RotateCw size={14} />
          다시 연결 시도
        </button>
      </div>
    </>
  );
}

function VoiceListeningView({ interimTranscript, onCancel, onConfirm }: {
  interimTranscript: string; onCancel: () => void; onConfirm: () => void;
}) {
  return (
    <>
      <div className="flex-1 p-4 flex flex-col items-center justify-center" style={{ background: "var(--secondary)" }}>
        {/* Pulse rings + mic button */}
        <div className="relative w-[104px] h-[104px] flex items-center justify-center mb-2">
          {[0, 0.6, 1.2].map((delay, i) => (
            <span key={i} className="ct-pulse-ring absolute w-[104px] h-[104px] rounded-full"
              style={{ background: "rgba(37,99,235,0.18)", animation: "ctPulse 1.8s ease-out infinite", animationDelay: `${delay}s` }} />
          ))}
          <span className="relative w-[62px] h-[62px] rounded-full flex items-center justify-center text-white"
            style={{ background: "linear-gradient(135deg, #2563eb, #4f46e5)", boxShadow: "0 8px 20px rgba(37,99,235,0.4)" }}>
            <Mic size={26} />
          </span>
        </div>

        {/* Waveform bars */}
        <div className="flex items-end gap-1 h-[30px] my-1.5 mb-3.5">
          {[0, 0.1, 0.2, 0.3, 0.15, 0.25, 0.05].map((delay, i) => (
            <span key={i} className="ct-wave-bar w-1 rounded-full"
              style={{
                background: i % 2 === 0 ? "#2563eb" : "#4f46e5",
                animation: "ctWave .9s ease-in-out infinite",
                animationDelay: `${delay}s`,
              }} />
          ))}
        </div>

        <div className="text-sm font-bold mb-2.5">듣고 있어요…</div>
        {interimTranscript && (
          <div className="bg-card border border-border rounded-xl px-3.5 py-2.5 text-[13px] leading-relaxed text-foreground max-w-[280px] text-center">
            {interimTranscript}<span className="text-muted-foreground"> 확인…</span>
          </div>
        )}
      </div>

      <div className="px-3 py-3 border-t border-border flex items-center justify-center gap-2.5">
        <button onClick={onCancel}
          className="flex items-center justify-center gap-1.5 h-[42px] px-5 border border-border rounded-full bg-card text-foreground text-[13px] font-semibold hover:bg-secondary transition-colors">
          <X size={15} />
          취소
        </button>
        <button onClick={onConfirm}
          className="flex items-center justify-center w-12 h-12 rounded-full bg-red-600 text-white hover:bg-red-700 transition-colors"
          style={{ boxShadow: "0 6px 16px rgba(220,38,38,0.32)" }}>
          <Check size={19} />
        </button>
      </div>
    </>
  );
}

function MicDeniedView({ onRetry, onTextMode }: { onRetry: () => void; onTextMode: () => void }) {
  return (
    <>
      <div className="flex-1 p-4 flex flex-col items-center justify-center text-center" style={{ background: "var(--secondary)" }}>
        <div className="w-[54px] h-[54px] rounded-[15px] bg-red-50 dark:bg-red-500/15 flex items-center justify-center text-destructive mb-3.5">
          <MicOff size={25} />
        </div>
        <div className="text-[15px] font-extrabold mb-1.5">마이크 권한이 필요해요</div>
        <div className="text-[12.5px] leading-relaxed text-muted-foreground max-w-[266px] mb-4">
          음성으로 물어보려면 브라우저에서 마이크 사용을 허용해 주세요. 그동안엔 텍스트로 입력할 수 있어요.
        </div>
        <div className="w-full max-w-[288px] bg-card border border-border rounded-xl p-3 text-left flex flex-col gap-2.5">
          <div className="flex gap-2.5 items-start text-[12.5px] leading-[1.5] text-foreground">
            <span className="shrink-0 w-[18px] h-[18px] rounded-full bg-primary/10 text-primary text-[11px] font-extrabold flex items-center justify-center">1</span>
            주소창 왼쪽 자물쇠 아이콘을 누르세요
          </div>
          <div className="flex gap-2.5 items-start text-[12.5px] leading-[1.5] text-foreground">
            <span className="shrink-0 w-[18px] h-[18px] rounded-full bg-primary/10 text-primary text-[11px] font-extrabold flex items-center justify-center">2</span>
            마이크 권한을 '허용'으로 바꿔주세요
          </div>
        </div>
      </div>
      <div className="px-3 py-2.5 border-t border-border flex flex-col gap-2.5">
        <button onClick={onRetry}
          className="flex items-center justify-center gap-1.5 w-full h-[42px] rounded-lg bg-primary text-white text-[13.5px] font-bold hover:brightness-110 transition-colors">
          <RotateCw size={16} />
          권한 다시 요청
        </button>
        <button onClick={onTextMode}
          className="flex items-center justify-center gap-1.5 w-full h-9 rounded-lg bg-transparent text-muted-foreground text-[12.5px] font-semibold hover:bg-secondary transition-colors">
          <Keyboard size={14} />
          텍스트로 입력하기
        </button>
      </div>
    </>
  );
}

function InputBar({ value, onChange, onSend, onMic, onKeyDown, disabled, orchestrator }: {
  value: string; onChange: (v: string) => void;
  onSend: () => void; onMic: () => void;
  onKeyDown: (e: React.KeyboardEvent) => void;
  disabled: boolean; orchestrator?: boolean;
}) {
  const hasText = value.trim().length > 0;

  return (
    <div className="px-3 py-2.5 border-t border-border flex items-center gap-2">
      <div className="flex-1 flex items-center gap-2 rounded-full px-4 pr-2 py-2"
        style={{ background: orchestrator ? "var(--orch-input-bg)" : "var(--secondary)" }}>
        <input
          value={value}
          onChange={(e) => onChange(e.target.value)}
          onKeyDown={onKeyDown}
          placeholder={orchestrator ? "메시지를 입력하거나 위 선택지를 눌러보세요" : "메시지를 입력하세요"}
          className="flex-1 bg-transparent border-none outline-none text-[13px] text-foreground placeholder:text-muted-foreground"
          disabled={disabled}
        />
        {/* mic 은 일반 모드에서만(오케스트레이터 모드는 입력 집중). */}
        {!orchestrator && (
          <button onClick={onMic}
            className="w-[30px] h-[30px] rounded-full flex items-center justify-center text-muted-foreground hover:bg-secondary transition-colors">
            <Mic size={16} />
          </button>
        )}
      </div>
      <button onClick={onSend} disabled={!hasText}
        className="w-10 h-10 rounded-full flex items-center justify-center text-white shrink-0 transition-all"
        style={{
          background: !hasText ? "var(--muted)" : orchestrator ? "var(--gradient-orchestrator)" : "linear-gradient(135deg, #2563eb, #4f46e5)",
          color: hasText ? "#fff" : "var(--muted-foreground)",
        }}>
        <ArrowUp size={17} />
      </button>
    </div>
  );
}

export { BotBubble, UserBubble, EvidenceCards, EvidenceChips, SiteLinkButtons, InputBar };
