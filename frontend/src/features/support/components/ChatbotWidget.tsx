import { useState, useRef, useEffect, useCallback } from "react";
import { useNavigate } from "react-router";
import {
  Sparkles, MessageCircle, Mic, MicOff, ArrowUp, Minus, X,
  KeyRound, CreditCard, FileText, FileSearch, Pause, Volume2,
  ArrowUpRight, Shield, SearchX, Headset, PenLine, WifiOff,
  RotateCw, Check, Keyboard, ArrowRight, Play, LogOut,
} from "lucide-react";
import { useChatbot } from "../hooks/useChatbot";
import type {
  ChatMessage, ChatEvidence, SiteLink, IntakeCaseCandidate, IntakeModeOption,
} from "../types/chatbot";
import { SUGGESTED_QUESTIONS } from "../types/chatbot";
import { AutoPrepWorkView } from "@/features/autoprep/components/AutoPrepWorkView";

/** 오케스트레이터 정체성 글리프(U+2726). */
const ORCH_GLYPH = "✦";

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
        <div className="relative bg-card border border-black/10 rounded-[14px] rounded-br-[5px] shadow-lg p-3 pr-9 max-w-[236px]">
          <button
            onClick={() => setTooltipVisible(false)}
            className="absolute top-2.5 right-2.5 w-[18px] h-[18px] rounded-full bg-slate-100 flex items-center justify-center text-slate-400 hover:bg-slate-200 transition-colors"
          >
            <X size={11} />
          </button>
          <div className="text-[13px] font-bold mb-0.5">무엇이든 물어보세요</div>
          <div className="text-[12px] leading-[1.55] text-slate-500">
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

function ChatbotPanel({ chatbot }: ChatbotPanelProps) {
  const {
    close, minimize, messages, sendMessage, botStatus,
    voiceState, startVoice, cancelVoice, confirmVoice, setVoiceState,
    interimTranscript, retryConnection, toggleTts,
    orchestrator, runStarted, runParts, runRunning, runPlan, runCaseId,
    selectCase, selectMode,
    showExitSheet, openExitSheet, closeExitSheet, exitOrchestrator,
  } = chatbot;

  const navigate = useNavigate();
  const [input, setInput] = useState("");
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" });
  }, [messages, botStatus, runParts]);

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

  return (
    <div className="fixed right-5 bottom-5 z-50 w-[360px] h-[560px] flex flex-col bg-card border border-black/10 rounded-2xl overflow-hidden"
      style={{
        boxShadow: orchestrator
          ? "0 16px 40px rgba(109,40,217,0.22), 0 4px 12px rgba(15,23,42,0.06)"
          : "0 12px 28px rgba(15,23,42,0.12), 0 4px 10px rgba(15,23,42,0.06)",
      }}>

      {/* ── Header ── */}
      <WidgetHeader
        orchestrator={orchestrator}
        isDisconnected={isDisconnected}
        isVoiceListening={voiceState === "listening"}
        onMinimize={minimize}
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
          <div ref={scrollRef} className="flex-1 p-4 overflow-y-auto flex flex-col gap-3.5"
            style={{ background: orchestrator ? "var(--orch-chat-bg)" : "#f8fafc" }}>
            {messages.length === 0 && botStatus === "idle" ? (
              <EmptyState onSelect={sendMessage} />
            ) : (
              <>
                {messages.map((m) =>
                  m.role === "user" ? (
                    <UserBubble key={m.id} text={m.text} dimmed={isDisconnected} />
                  ) : (
                    <div key={m.id} className="flex flex-col gap-2.5">
                      <BotBubble message={m} onToggleTts={toggleTts} variant="widget"
                        onQuickReply={sendMessage} orchestrator={orchestrator} />
                      {m.id === lastBotId && m.intake && !m.intake.ready && !runStarted && (
                        <IntakeChips intake={m.intake} onSelectCase={selectCase} onSelectMode={selectMode} />
                      )}
                    </div>
                  )
                )}
                {runStarted && (
                  <div className="ml-[37px]">
                    <AutoPrepWorkView
                      running={runRunning}
                      parts={runParts}
                      caseId={runCaseId}
                      onNavigate={(p) => navigate(p)}
                    />
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
    </div>
  );
}

/* ════════════════ Orchestrator components ════════════════ */

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

function IntakeChips({ intake, onSelectCase, onSelectMode }: {
  intake: NonNullable<ChatMessage["intake"]>;
  onSelectCase: (c: IntakeCaseCandidate) => void;
  onSelectMode: (m: IntakeModeOption) => void;
}) {
  if (intake.nextAsk === "CASE" && intake.candidates.length > 0) {
    return (
      <div className="ml-[37px] flex flex-col gap-2">
        {intake.candidates.map((c) => (
          <ApplicationChip key={c.id} candidate={c} onSelect={() => onSelectCase(c)} />
        ))}
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
  const initial = candidate.companyName?.trim().charAt(0) || "?";
  return (
    <button onClick={onSelect} role="button"
      className="group flex items-center gap-2.5 w-full min-h-[56px] px-3 py-2.5 rounded-[13px] bg-card text-left transition-all hover:shadow-[0_4px_12px_rgba(109,40,217,0.10)]"
      style={{ border: "1px solid rgba(0,0,0,0.10)" }}>
      <span className="w-[34px] h-[34px] rounded-[9px] flex items-center justify-center text-white font-extrabold text-[15px] shrink-0"
        style={{ background: "var(--gradient-orchestrator)" }}>
        {initial}
      </span>
      <span className="flex-1 min-w-0">
        <span className="block text-[13.5px] font-bold text-foreground truncate">{candidate.companyName}</span>
        <span className="block text-[11.5px] font-semibold text-muted-foreground truncate">{candidate.jobTitle}</span>
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

/* ════════════════ Sub-components ════════════════ */

function WidgetHeader({ orchestrator, isDisconnected, isVoiceListening, onMinimize, onClose }: {
  orchestrator?: boolean; isDisconnected: boolean; isVoiceListening: boolean;
  onMinimize: () => void; onClose: () => void;
}) {
  return (
    <div className="flex items-center gap-2.5 px-4 py-3.5 border-b border-black/8 transition-colors"
      style={{ background: orchestrator ? "var(--orch-header-tint)" : undefined }}>
      {orchestrator && !isDisconnected ? (
        <div className="relative">
          <OrchestratorAvatar size={36} iconScale={0.5} />
          <span className="absolute -right-px -bottom-px w-[11px] h-[11px] rounded-full border-2 border-white"
            style={{ background: "#16a34a" }} />
        </div>
      ) : (
        <div className="relative w-9 h-9 rounded-full flex items-center justify-center text-white"
          style={{ background: isDisconnected ? "#e2e8f0" : "linear-gradient(135deg, #2563eb, #4f46e5)" }}>
          <Sparkles size={18} className={isDisconnected ? "text-slate-400" : ""} />
          <span className="absolute -right-px -bottom-px w-[11px] h-[11px] rounded-full border-2 border-white"
            style={{ background: isDisconnected ? "#94a3b8" : "#16a34a" }} />
        </div>
      )}
      <div className="leading-tight">
        <div className="text-sm font-bold">튜너봇</div>
        {isVoiceListening ? (
          <div className="text-[11.5px] font-semibold text-red-600">● 음성 인식 중</div>
        ) : isDisconnected ? (
          <div className="text-[11.5px] font-semibold text-slate-400">연결 대기 중</div>
        ) : orchestrator ? (
          <div className="text-[11.5px] font-bold" style={{ color: "var(--orch-point)" }}>오케스트레이터 모드</div>
        ) : (
          <div className="text-[11.5px] font-semibold" style={{ color: "#16a34a" }}>응답 가능</div>
        )}
      </div>
      <div className="ml-auto flex gap-1 text-slate-400">
        <button onClick={onMinimize} className="w-[30px] h-[30px] rounded-lg flex items-center justify-center hover:bg-slate-100 transition-colors">
          <Minus size={17} />
        </button>
        <button onClick={onClose} className="w-[30px] h-[30px] rounded-lg flex items-center justify-center hover:bg-slate-100 transition-colors">
          <X size={17} />
        </button>
      </div>
    </div>
  );
}

function EmptyState({ onSelect }: { onSelect: (text: string) => void }) {
  return (
    <div className="flex flex-col h-full">
      <div className="flex flex-col items-center text-center mt-[18px] mb-5">
        <div className="w-[52px] h-[52px] rounded-[15px] flex items-center justify-center text-white mb-3"
          style={{ background: "linear-gradient(135deg, #2563eb, #4f46e5)", boxShadow: "0 8px 18px rgba(37,99,235,0.32)" }}>
          <Sparkles size={25} />
        </div>
        <div className="text-base font-extrabold mb-1">안녕하세요, 튜너봇이에요</div>
        <div className="text-[13px] leading-relaxed text-slate-500 max-w-[250px]">
          CareerTuner 이용 중 궁금한 점을 물어보세요. FAQ와 공지를 찾아 바로 알려드릴게요.
        </div>
      </div>
      <div className="text-[11.5px] font-bold text-slate-400 mb-2 ml-0.5">자주 묻는 질문</div>
      <div className="flex flex-col gap-2">
        {SUGGESTED_QUESTIONS.map(({ icon, text }) => {
          const Icon = ICON_MAP[icon];
          return (
            <button key={text} onClick={() => onSelect(text)}
              className="flex items-center gap-2.5 px-3 py-2.5 bg-card border border-black/10 rounded-xl text-[13px] font-medium text-slate-700 shadow-[0_1px_2px_rgba(15,23,42,0.04)] hover:border-blue-300 hover:bg-blue-50/30 transition-colors text-left">
              <Icon size={15} className="text-blue-600 shrink-0" />
              <span className="flex-1">{text}</span>
              <ArrowRight size={15} className="text-slate-300 shrink-0" />
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

function BotBubble({ message, onToggleTts, variant = "widget", onQuickReply, orchestrator }: {
  message: ChatMessage; onToggleTts: (id: string) => void; variant?: "widget" | "full";
  onQuickReply?: (text: string) => void; orchestrator?: boolean;
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
        <div className="bg-card border border-black/8 rounded-[15px] rounded-tl-[5px] px-3.5 py-3 text-[13.5px] leading-[1.65] text-slate-700">
          <span dangerouslySetInnerHTML={{ __html: message.text.replace(/\*\*(.*?)\*\*/g, '<b class="text-[#030213]">$1</b>') }} />

          {/* TTS controls */}
          {message.ttsState !== "idle" || variant === "full" ? (
            <div className="flex items-center gap-2 mt-2.5 pt-2.5 border-t border-black/6">
              {variant === "full" ? (
                <>
                  <button onClick={() => onToggleTts(message.id)}
                    className="inline-flex items-center gap-1.5 h-[30px] px-3 rounded-full bg-blue-50 text-blue-600 text-xs font-semibold hover:bg-blue-100 transition-colors">
                    <Volume2 size={14} />
                    <span className="text-xs font-semibold">음성으로 듣기</span>
                  </button>
                </>
              ) : (
                <>
                  <button onClick={() => onToggleTts(message.id)}
                    className="w-[26px] h-[26px] rounded-full bg-blue-50 text-blue-600 flex items-center justify-center hover:bg-blue-100 transition-colors">
                    {message.ttsState === "playing" ? <Pause size={13} /> : <Play size={13} />}
                  </button>
                  <div className="flex-1 h-1 rounded-full bg-slate-200 relative overflow-hidden">
                    <div className="absolute left-0 top-0 bottom-0 bg-blue-600 rounded-full" style={{ width: `${message.ttsProgress || 46}%` }} />
                  </div>
                  <span className="text-[10.5px] text-slate-400 tabular-nums">0:06</span>
                </>
              )}
            </div>
          ) : null}
        </div>

        {/* SiteLink buttons */}
        {message.links && message.links.length > 0 && (
          <SiteLinkButtons links={message.links} />
        )}

        {/* Quick reply chips */}
        {onQuickReply && message.quickReplies && message.quickReplies.length > 0 && (
          <QuickReplyChips replies={message.quickReplies} onSelect={onQuickReply} />
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
          className="inline-flex items-center px-3 py-1.5 rounded-full border border-blue-200 bg-white text-blue-700 text-[12.5px] font-semibold hover:bg-blue-50 transition-colors"
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
          className="flex items-center justify-center gap-1.5 w-full h-9 rounded-lg border border-blue-200 bg-blue-50 text-blue-700 text-[13px] font-semibold hover:bg-blue-100 transition-colors"
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
          className="inline-flex items-center gap-1 px-2.5 py-1 bg-blue-50 border border-blue-600/20 rounded-full text-[11.5px] font-semibold text-blue-700 hover:bg-blue-100 transition-colors">
          <FileText size={12} />
          {e.title}
          <ArrowUpRight size={11} className="opacity-60" />
        </a>
      ))}
    </div>
  );
}

function EvidenceCards({ evidence }: { evidence: ChatEvidence[] }) {
  const iconBg: Record<string, string> = { "도움말": "bg-blue-50 text-blue-600", "가이드": "bg-green-50 text-green-600", "FAQ": "bg-blue-50 text-blue-600", "공지": "bg-amber-50 text-amber-600" };
  const badgeBg: Record<string, string> = { "도움말": "bg-blue-50 text-blue-700", "가이드": "bg-green-50 text-green-700", "FAQ": "bg-blue-50 text-blue-700", "공지": "bg-amber-50 text-amber-700" };

  return (
    <div className="flex flex-col gap-2.5">
      {evidence.map((e) => (
        <a key={e.id} href={e.url}
          className="flex gap-3 items-start bg-card border border-black/10 rounded-xl p-3.5 shadow-[0_1px_2px_rgba(15,23,42,0.04)] hover:border-blue-300 transition-colors">
          <span className={`shrink-0 w-9 h-9 rounded-[9px] flex items-center justify-center ${iconBg[e.type] || "bg-blue-50 text-blue-600"}`}>
            {e.type === "가이드" ? <Shield size={17} /> : <FileText size={17} />}
          </span>
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-1.5 mb-0.5">
              <span className={`text-[10px] font-bold rounded-full px-2 py-0.5 ${badgeBg[e.type] || "bg-blue-50 text-blue-700"}`}>{e.type}</span>
              <span className="text-[13.5px] font-bold">{e.title}</span>
            </div>
            <div className="text-xs leading-[1.55] text-slate-500">{e.snippet}</div>
          </div>
          <ArrowUpRight size={16} className="text-slate-300 shrink-0 mt-0.5" />
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
        <div className="bg-card border border-black/8 rounded-[15px] rounded-bl-[5px] px-4 py-3.5 flex gap-1.5 items-center">
          {[0, 0.18, 0.36].map((delay, i) => (
            <span key={i} className="ct-typing-dot w-[7px] h-[7px] rounded-full bg-slate-400"
              style={{ animation: "ctTyping 1.2s infinite ease-in-out", animationDelay: `${delay}s` }} />
          ))}
        </div>
      </div>
      <div className="flex items-center gap-1.5 ml-[37px] text-[11px] text-slate-400">
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
        <div className="bg-card border border-black/8 rounded-[15px] rounded-tl-[5px] px-3.5 py-3">
          <div className="flex items-center gap-1.5 text-[12.5px] font-bold text-amber-700 mb-1.5">
            <SearchX size={15} />
            정확한 답변을 찾지 못했어요
          </div>
          <div className="text-[13px] leading-[1.65] text-slate-600">
            관련 문서를 확인했지만 확실한 안내가 없어서, <b className="text-[#030213]">잘못된 정보를 드리지 않으려고</b> 답변을 멈췄어요. 상담사가 정확히 도와드릴게요.
          </div>
        </div>
        <button className="flex items-center justify-center gap-1.5 w-full h-[42px] rounded-lg bg-[#030213] text-white text-[13.5px] font-bold hover:bg-[#1a1a2e] transition-colors">
          <Headset size={16} />
          상담사 연결하기
        </button>
        <a href="/support/contact"
          className="flex items-center justify-center gap-1.5 w-full h-10 rounded-lg border border-black/12 bg-card text-slate-700 text-[13px] font-semibold hover:bg-slate-50 transition-colors">
          <PenLine size={15} />
          1:1 문의 남기기
        </a>
        <div className="text-[11px] text-slate-400 text-center">
          상담 가능 시간 평일 09:00–18:00 · 보통 5분 내 응답
        </div>
      </div>
    </div>
  );
}

function DisconnectedView({ onRetry }: { onRetry: () => void }) {
  return (
    <>
      <div className="flex-1 p-4 flex flex-col" style={{ background: "#f8fafc" }}>
        <div className="mt-auto flex flex-col items-center text-center px-1.5 pb-1">
          <div className="w-12 h-12 rounded-[14px] bg-amber-50 flex items-center justify-center text-amber-700 mb-3">
            <WifiOff size={23} />
          </div>
          <div className="text-sm font-extrabold mb-1">AI 상담이 일시적으로 어려워요</div>
          <div className="text-[12.5px] leading-relaxed text-slate-500 max-w-[262px]">
            잠시 후 다시 시도하거나, 지금 바로 1:1 문의를 남기면 상담사가 순서대로 답변드려요.
          </div>
        </div>
      </div>
      <div className="px-3 py-2.5 border-t border-black/8 flex flex-col gap-2.5">
        <a href="/support/contact"
          className="flex items-center justify-center gap-1.5 w-full h-[42px] rounded-lg bg-[#030213] text-white text-[13.5px] font-bold hover:bg-[#1a1a2e] transition-colors">
          <PenLine size={16} />
          1:1 문의 남기기
        </a>
        <button onClick={onRetry}
          className="flex items-center justify-center gap-1.5 w-full h-9 rounded-lg bg-transparent text-slate-500 text-[12.5px] font-semibold hover:bg-slate-50 transition-colors">
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
      <div className="flex-1 p-4 flex flex-col items-center justify-center" style={{ background: "#f8fafc" }}>
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
          <div className="bg-card border border-black/8 rounded-xl px-3.5 py-2.5 text-[13px] leading-relaxed text-slate-700 max-w-[280px] text-center">
            {interimTranscript}<span className="text-slate-400"> 확인…</span>
          </div>
        )}
      </div>

      <div className="px-3 py-3 border-t border-black/8 flex items-center justify-center gap-2.5">
        <button onClick={onCancel}
          className="flex items-center justify-center gap-1.5 h-[42px] px-5 border border-black/12 rounded-full bg-card text-slate-600 text-[13px] font-semibold hover:bg-slate-50 transition-colors">
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
      <div className="flex-1 p-4 flex flex-col items-center justify-center text-center" style={{ background: "#f8fafc" }}>
        <div className="w-[54px] h-[54px] rounded-[15px] bg-red-50 flex items-center justify-center text-[#d4183d] mb-3.5">
          <MicOff size={25} />
        </div>
        <div className="text-[15px] font-extrabold mb-1.5">마이크 권한이 필요해요</div>
        <div className="text-[12.5px] leading-relaxed text-slate-500 max-w-[266px] mb-4">
          음성으로 물어보려면 브라우저에서 마이크 사용을 허용해 주세요. 그동안엔 텍스트로 입력할 수 있어요.
        </div>
        <div className="w-full max-w-[288px] bg-card border border-black/8 rounded-xl p-3 text-left flex flex-col gap-2.5">
          <div className="flex gap-2.5 items-start text-[12.5px] leading-[1.5] text-slate-600">
            <span className="shrink-0 w-[18px] h-[18px] rounded-full bg-blue-50 text-blue-600 text-[11px] font-extrabold flex items-center justify-center">1</span>
            주소창 왼쪽 자물쇠 아이콘을 누르세요
          </div>
          <div className="flex gap-2.5 items-start text-[12.5px] leading-[1.5] text-slate-600">
            <span className="shrink-0 w-[18px] h-[18px] rounded-full bg-blue-50 text-blue-600 text-[11px] font-extrabold flex items-center justify-center">2</span>
            마이크 권한을 '허용'으로 바꿔주세요
          </div>
        </div>
      </div>
      <div className="px-3 py-2.5 border-t border-black/8 flex flex-col gap-2.5">
        <button onClick={onRetry}
          className="flex items-center justify-center gap-1.5 w-full h-[42px] rounded-lg bg-[#030213] text-white text-[13.5px] font-bold hover:bg-[#1a1a2e] transition-colors">
          <RotateCw size={16} />
          권한 다시 요청
        </button>
        <button onClick={onTextMode}
          className="flex items-center justify-center gap-1.5 w-full h-9 rounded-lg bg-transparent text-slate-500 text-[12.5px] font-semibold hover:bg-slate-50 transition-colors">
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
    <div className="px-3 py-2.5 border-t border-black/8 flex items-center gap-2">
      <div className="flex-1 flex items-center gap-2 rounded-full px-4 pr-2 py-2"
        style={{ background: orchestrator ? "var(--orch-input-bg)" : "#f3f3f5" }}>
        <input
          value={value}
          onChange={(e) => onChange(e.target.value)}
          onKeyDown={onKeyDown}
          placeholder={orchestrator ? "메시지를 입력하거나 위 선택지를 눌러보세요" : "메시지를 입력하세요"}
          className="flex-1 bg-transparent border-none outline-none text-[13px] text-[#030213] placeholder:text-slate-400"
          disabled={disabled}
        />
        {/* mic 은 일반 모드에서만(오케스트레이터 모드는 입력 집중). */}
        {!orchestrator && (
          <button onClick={onMic}
            className="w-[30px] h-[30px] rounded-full flex items-center justify-center text-slate-500 hover:bg-slate-200 transition-colors">
            <Mic size={16} />
          </button>
        )}
      </div>
      <button onClick={onSend} disabled={!hasText}
        className="w-10 h-10 rounded-full flex items-center justify-center text-white shrink-0 transition-all"
        style={{
          background: !hasText ? "#e2e8f0" : orchestrator ? "var(--gradient-orchestrator)" : "linear-gradient(135deg, #2563eb, #4f46e5)",
          color: hasText ? "#fff" : "#94a3b8",
        }}>
        <ArrowUp size={17} />
      </button>
    </div>
  );
}

export { BotBubble, UserBubble, EvidenceCards, EvidenceChips, SiteLinkButtons, InputBar };
