import { useState, useRef, useEffect } from "react";
import {
  Sparkles, Plus, Headset, Mic, ArrowUp, ThumbsUp, ThumbsDown,
} from "lucide-react";
import { useChatbot } from "../hooks/useChatbot";
import { SIDEBAR_SUGGESTIONS } from "../types/chatbot";
import { BotBubble, UserBubble, InputBar } from "./ChatbotWidget";

export function ChatbotFullScreen() {
  const chatbot = useChatbot();
  const {
    messages, sendMessage, botStatus,
    sessions, activeSessionId, setActiveSessionId, newSession,
    startVoice, toggleTts,
  } = chatbot;

  const [input, setInput] = useState("");
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" });
  }, [messages, botStatus]);

  const handleSend = () => {
    const text = input.trim();
    if (!text) return;
    setInput("");
    sendMessage(text);
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); handleSend(); }
  };

  return (
    <div className="flex w-full bg-card border border-black/10 rounded-2xl overflow-hidden"
      style={{ height: "calc(100vh - 180px)", minHeight: 560, maxHeight: 780, boxShadow: "0 12px 28px rgba(15,23,42,0.12), 0 4px 10px rgba(15,23,42,0.06)" }}>

      {/* ── Sidebar ── */}
      <div className="w-[248px] shrink-0 border-r border-black/8 flex flex-col" style={{ background: "#f8fafc" }}>
        <div className="p-4 pb-3">
          <button onClick={newSession}
            className="flex items-center justify-center gap-1.5 w-full h-[42px] rounded-lg text-white text-[13.5px] font-bold hover:opacity-90 transition-opacity"
            style={{ background: "linear-gradient(135deg, #2563eb, #4f46e5)", boxShadow: "0 4px 12px rgba(37,99,235,0.28)" }}>
            <Plus size={16} />
            새 문의 시작
          </button>
        </div>

        <div className="text-[11px] font-bold text-slate-400 px-[18px] py-1.5 pb-2">최근 대화</div>

        <div className="flex-1 overflow-y-auto px-2.5 flex flex-col gap-1">
          {sessions.map((s) => (
            <button key={s.id} onClick={() => setActiveSessionId(s.id)}
              className={`text-left px-3 py-2.5 rounded-[10px] transition-colors ${
                s.id === activeSessionId
                  ? "bg-card border border-blue-600/25 shadow-[0_1px_2px_rgba(15,23,42,0.05)]"
                  : "hover:bg-card/60"
              }`}>
              <div className={`text-[12.5px] truncate ${s.id === activeSessionId ? "font-bold text-[#030213]" : "font-semibold text-slate-600"}`}>
                {s.title}
              </div>
              <div className="text-[11px] text-slate-400 mt-0.5">
                {s.lastMessage}{s.meta ? ` · ${s.meta}` : ""}
              </div>
            </button>
          ))}
        </div>

        <div className="border-t border-black/8 p-4">
          <div className="text-[11px] font-bold text-slate-400 mb-2.5">추천 질문</div>
          <div className="flex flex-wrap gap-1.5">
            {SIDEBAR_SUGGESTIONS.map((q) => (
              <button key={q} onClick={() => sendMessage(q)}
                className="px-2.5 py-1.5 bg-card border border-black/10 rounded-full text-[11.5px] font-semibold text-slate-600 hover:border-blue-300 transition-colors">
                {q}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* ── Main area ── */}
      <div className="flex-1 flex flex-col min-w-0">
        {/* Header */}
        <div className="flex items-center gap-2.5 px-6 py-4 border-b border-black/8">
          <div className="relative w-10 h-10 rounded-full flex items-center justify-center text-white"
            style={{ background: "linear-gradient(135deg, #2563eb, #4f46e5)" }}>
            <Sparkles size={19} />
            <span className="absolute -right-px -bottom-px w-3 h-3 rounded-full border-2 border-white" style={{ background: "#16a34a" }} />
          </div>
          <div className="leading-tight">
            <div className="text-[15px] font-bold">튜너봇 · AI 상담 어시스턴트</div>
            <div className="text-xs font-semibold" style={{ color: "#16a34a" }}>응답 가능 · 평균 응답 1초</div>
          </div>
          <div className="ml-auto">
            <button className="inline-flex items-center gap-1.5 h-[34px] px-3 border border-black/12 rounded-lg bg-card text-slate-600 text-[12.5px] font-semibold hover:bg-slate-50 transition-colors">
              <Headset size={14} />
              상담사 연결
            </button>
          </div>
        </div>

        {/* Messages */}
        <div ref={scrollRef} className="flex-1 px-6 py-6 overflow-y-auto flex flex-col gap-5" style={{ background: "#f8fafc" }}>
          {messages.map((m) =>
            m.role === "user" ? (
              <div key={m.id} className="flex justify-end">
                <div className="max-w-[60%] bg-blue-600 text-white rounded-2xl rounded-tr-[5px] px-4 py-3 text-sm leading-relaxed">
                  {m.text}
                </div>
              </div>
            ) : (
              <BotBubble key={m.id} message={m} onToggleTts={toggleTts} variant="full" />
            )
          )}
          {botStatus === "thinking" && (
            <div className="flex gap-3 items-end">
              <div className="w-[34px] h-[34px] rounded-full flex items-center justify-center text-white shrink-0"
                style={{ background: "linear-gradient(135deg, #2563eb, #4f46e5)" }}>
                <Sparkles size={16} />
              </div>
              <div className="bg-card border border-black/8 rounded-2xl rounded-bl-[5px] px-4 py-3.5 flex gap-1.5 items-center">
                {[0, 0.18, 0.36].map((delay, i) => (
                  <span key={i} className="ct-typing-dot w-[7px] h-[7px] rounded-full bg-slate-400"
                    style={{ animation: "ctTyping 1.2s infinite ease-in-out", animationDelay: `${delay}s` }} />
                ))}
              </div>
            </div>
          )}
          {messages.length === 0 && (
            <div className="flex flex-col items-center justify-center flex-1 text-center">
              <div className="w-14 h-14 rounded-[16px] flex items-center justify-center text-white mb-4"
                style={{ background: "linear-gradient(135deg, #2563eb, #4f46e5)", boxShadow: "0 8px 18px rgba(37,99,235,0.32)" }}>
                <Sparkles size={28} />
              </div>
              <div className="text-lg font-extrabold mb-1.5">무엇이 궁금하세요?</div>
              <div className="text-sm text-slate-500 max-w-xs">
                FAQ·공지·가이드 문서를 검색해 근거와 함께 답변드려요.
              </div>
            </div>
          )}
        </div>

        {/* Input */}
        <div className="px-6 py-4 border-t border-black/8 flex items-center gap-3">
          <div className="flex-1 flex items-center gap-2.5 rounded-full px-[18px] pr-3 py-3" style={{ background: "#f3f3f5" }}>
            <input
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="메시지를 입력하거나 마이크를 눌러 말해보세요"
              className="flex-1 bg-transparent border-none outline-none text-sm text-[#030213] placeholder:text-slate-400"
            />
            <button onClick={startVoice}
              className="w-[34px] h-[34px] rounded-full flex items-center justify-center text-slate-500 hover:bg-slate-200 transition-colors">
              <Mic size={17} />
            </button>
          </div>
          <button onClick={handleSend} disabled={!input.trim()}
            className="w-[46px] h-[46px] rounded-full flex items-center justify-center text-white shrink-0 transition-all"
            style={{
              background: input.trim() ? "linear-gradient(135deg, #2563eb, #4f46e5)" : "#e2e8f0",
              color: input.trim() ? "#fff" : "#94a3b8",
            }}>
            <ArrowUp size={19} />
          </button>
        </div>
      </div>
    </div>
  );
}
