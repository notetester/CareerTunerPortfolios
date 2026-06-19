import { useState, useEffect, useCallback } from "react";
import {
  Mail, Inbox, Send, Timer, Smile, Sparkles, Settings2,
  ChevronDown, Flame, User, Crown, CalendarDays, MessageSquare,
  ArrowUpRight, StickyNote, AlertTriangle, CornerDownLeft,
  CornerDownRight, RefreshCw, FileSearch, Zap, Info, WifiOff,
  RotateCw, CheckCircle2, UserPlus, PenLine, Wand2,
  Send as SendIcon,
} from "lucide-react";
import AdminShell from "../../../components/AdminShell";
import { type Inquiry, type InquiryMessage, TEMPLATES, ASSIGNEES, INQUIRIES } from "../data/inquiriesData";
import * as adminTicketApi from "../api/adminTicketApi";
import "./admin-inquiries.css";

type ListFilter = "미답변" | "처리중" | "완료" | "전체";
type AiSummaryState = "none" | "loading" | "ready" | "empty" | "error";
type AiDraftState = "none" | "loading" | "ready" | "error";

/* AI 회원 요약은 백엔드 API 미구현 — 표시용 mock 유지 */
const MOCK_SUMMARY = "가입 **3개월차** 사용자예요. 과거 **결제 관련 문의가 2회** 있었고 모두 해결됐습니다. 이번 문의는 **프로 결제 직후 AI 크레딧 미반영** 건으로, 결제 자체는 정상 완료된 것으로 보여요.";

export default function AdminInquiriesAI() {
  const [items, setItems] = useState<Inquiry[]>([]);
  const [filter, setFilter] = useState<ListFilter>("미답변");
  const [selected, setSelected] = useState<Inquiry | null>(null);
  const [toast, setToast] = useState<{ msg: string; tone: string } | null>(null);

  // AI states
  const [aiSummary, setAiSummary] = useState<AiSummaryState>("none");
  const [aiDraft, setAiDraft] = useState<AiDraftState>("none");
  const [replyText, setReplyText] = useState("");
  const [memo, setMemo] = useState("");
  const [aiAvailable, setAiAvailable] = useState(true);
  const [draftLoaded, setDraftLoaded] = useState(false);

  useEffect(() => {
    adminTicketApi.getTickets().then((data) => {
      setItems(data);
      if (data.length > 0) setSelected(data[0]);
    }).catch(() => {
      setItems(INQUIRIES);
      setSelected(INQUIRIES[0]);
    });
  }, []);

  const flash = (msg: string, tone: string) => {
    setToast({ msg, tone });
    setTimeout(() => setToast(null), 2200);
  };

  const selectInquiry = (inq: Inquiry) => {
    setSelected(inq);
    setAiSummary("none");
    setAiDraft("none");
    setReplyText("");
    setMemo(inq.memo || "");
    setDraftLoaded(false);
    // 메시지 포함 상세 로드
    adminTicketApi.getTicketDetail(inq.id)
      .then((detail) => {
        setSelected(detail);
        setMemo(detail.memo || "");
      })
      .catch(() => { /* 실패 시 목록에서 받은 데이터 유지 */ });
  };

  const generateSummary = useCallback(() => {
    setAiSummary("loading");
    setTimeout(() => {
      // simulate new user check
      if (selected && selected.joined && selected.joined >= "2026.06") {
        setAiSummary("empty");
      } else {
        setAiSummary("ready");
      }
    }, 1800);
  }, [selected]);

  const generateDraft = useCallback(() => {
    if (!selected) return;
    setAiDraft("loading");
    adminTicketApi.generateDraft(selected.id)
      .then((draft) => {
        setReplyText(draft);
        setDraftLoaded(true);
        setAiDraft("ready");
      })
      .catch(() => {
        setAiDraft("error");
        flash("AI 초안 생성에 실패했습니다. 잠시 후 다시 시도해 주세요.", "red");
      });
  }, [selected]);

  const useDraft = () => {
    // generateDraft()에서 이미 replyText에 실제 초안이 들어있음 — 확정만 처리
    setDraftLoaded(true);
  };

  const regenerateDraft = () => {
    if (!selected) return;
    setAiDraft("loading");
    adminTicketApi.generateDraft(selected.id)
      .then((draft) => {
        setReplyText(draft);
        setDraftLoaded(true);
        setAiDraft("ready");
      })
      .catch(() => {
        setAiDraft("error");
        flash("AI 초안 재생성에 실패했습니다.", "red");
      });
  };

  const handleSendReply = async () => {
    if (!selected || !replyText.trim()) return;
    try {
      const updated = await adminTicketApi.reply(selected.id, replyText);
      setItems((prev) => prev.map((i) => (i.id === updated.id ? updated : i)));
      setSelected(updated);
      flash("답변이 전송되었습니다.", "green");
      setReplyText("");
      setDraftLoaded(false);
      setAiDraft("none");
    } catch {
      flash("답변 전송에 실패했습니다.", "red");
    }
  };

  // Filtered list
  const filtered = items.filter((i) => {
    if (filter === "미답변") return i.status === "pending";
    if (filter === "처리중") return i.status === "progress";
    if (filter === "완료") return i.status === "answered";
    return true;
  });

  const pendingCount = items.filter((i) => i.status === "pending").length;

  return (
    <AdminShell
      active="inquiries"
      breadcrumb="문의 관리"
      title="문의 관리"
      icon={Mail}
      desc="회원 1:1 문의를 확인하고 상태·담당자를 관리하며 답변합니다"
      actions={
        <div className="flex items-center gap-2">
          <span className="inline-flex items-center gap-1.5 h-[34px] px-3 rounded-lg text-[12.5px] font-bold"
            style={{ background: "#eef2ff", border: "1px solid rgba(79,70,229,0.2)", color: "#4338ca" }}>
            <Sparkles size={14} className="text-indigo-600" />
            AI 보조 켜짐
          </span>
          <button className="av-btn" style={{ display: "inline-flex", alignItems: "center", gap: 6 }}>
            <Settings2 size={15} />
            자동응답 설정
          </button>
        </div>
      }
    >
      {/* ── Stats ── */}
      <div className="grid grid-cols-4 gap-3.5 mb-5">
        {[
          { icon: Inbox, label: "미답변", value: `${pendingCount}` },
          { icon: Send, label: "오늘 답변", value: "14" },
          { icon: Timer, label: "평균 응답", value: "5.2시간" },
          { icon: Smile, label: "만족도", value: "96%" },
        ].map(({ icon: Icon, label, value }) => (
          <div key={label} className="bg-card border border-black/10 rounded-xl shadow-sm p-4">
            <div className="text-[12.5px] text-[#717182] flex items-center gap-1.5">
              <Icon size={14} />{label}
            </div>
            <div className="text-2xl font-extrabold tracking-tight mt-1.5">{value}</div>
          </div>
        ))}
      </div>

      {/* ── Grid: list + thread ── */}
      <div className="grid gap-5 items-start" style={{ gridTemplateColumns: "340px 1fr" }}>

        {/* ── Inquiry List Panel ── */}
        <div className="bg-card border border-black/10 rounded-xl shadow-sm overflow-hidden">
          {/* filter tabs */}
          <div className="flex items-center gap-2 px-4 py-3 border-b border-black/10">
            <div className="inline-flex rounded-[10px] p-0.5" style={{ background: "#ececf0" }}>
              {(["미답변", "처리중", "완료", "전체"] as ListFilter[]).map((f) => (
                <button key={f} onClick={() => setFilter(f)}
                  className={`text-xs px-2.5 py-1 rounded-[7px] transition-colors ${
                    filter === f
                      ? "bg-card text-[#030213] font-semibold shadow-[0_1px_2px_rgba(15,23,42,0.06)]"
                      : "text-[#717182]"
                  }`}>
                  {f}
                </button>
              ))}
            </div>
            <span className="ml-auto text-[12.5px] text-[#717182]">
              <b className="text-[#030213]">{filtered.length}</b>건
            </span>
          </div>

          {/* items */}
          <div className="flex flex-col">
            {filtered.map((inq) => (
              <button key={inq.id} onClick={() => selectInquiry(inq)}
                className={`text-left px-4 py-3 border-b border-black/10 last:border-b-0 transition-colors ${
                  selected?.id === inq.id ? "bg-blue-50" : "hover:bg-slate-50"
                }`}>
                <div className="flex items-center gap-1.5 mb-1">
                  <span className="text-[11px] font-bold px-2 py-0.5 rounded-full" style={{ background: "#ececf0", color: "#717182" }}>{inq.cat}</span>
                  {inq.priority && (
                    <span className="text-[10.5px] font-extrabold px-1.5 py-0.5 rounded-full inline-flex items-center gap-0.5"
                      style={{ background: "#fef2f2", color: "#d4183d" }}>
                      <Flame size={10} />긴급
                    </span>
                  )}
                  <span className="ml-auto text-[11px] font-bold px-2 py-0.5 rounded-full"
                    style={{
                      background: inq.status === "answered" ? "#f0fdf4" : inq.status === "progress" ? "#eff6ff" : "#fffbeb",
                      color: inq.status === "answered" ? "#15803d" : inq.status === "progress" ? "#1d4ed8" : "#b45309",
                    }}>
                    {inq.status === "answered" ? "답변완료" : inq.status === "progress" ? "처리중" : "미답변"}
                  </span>
                </div>
                <div className="text-[13.5px] font-semibold truncate">{inq.title}</div>
                <div className="text-[11.5px] text-[#717182] mt-0.5 flex gap-2">
                  <span>{inq.member}</span><span>·</span><span>{inq.date.slice(-5)}</span>
                </div>
              </button>
            ))}
          </div>
        </div>

        {/* ── Thread Panel ── */}
        {selected ? (
          <div className="bg-card border border-black/10 rounded-xl shadow-sm overflow-hidden">
            {/* Thread header */}
            <div className="px-5 py-4 border-b border-black/10">
              <div className="flex items-center gap-2 mb-2">
                <span className="text-[11px] font-bold px-2 py-0.5 rounded-full" style={{ background: "#ececf0", color: "#717182" }}>{selected.cat}</span>
                {selected.priority && (
                  <span className="text-[10.5px] font-extrabold px-1.5 py-0.5 rounded-full inline-flex items-center gap-0.5"
                    style={{ background: "#fef2f2", color: "#d4183d" }}>
                    <Flame size={10} />긴급
                  </span>
                )}
                <span className="text-[11px] font-bold px-2 py-0.5 rounded-full"
                  style={{ background: "#fffbeb", color: "#b45309" }}>
                  {selected.status === "answered" ? "답변완료" : selected.status === "progress" ? "처리중" : "미답변"}
                </span>
              </div>
              <div className="text-[17px] font-bold">{selected.title}</div>
              <div className="text-xs text-[#717182] mt-1.5 flex gap-2.5">
                <span className="inline-flex items-center gap-1"><User size={12} /> {selected.member}</span>
                <span>{selected.date}</span>
              </div>
            </div>

            {/* Controls bar */}
            <div className="flex items-center gap-2 flex-wrap px-5 py-3 border-b border-black/10" style={{ background: "#f8fafc" }}>
              <span className="text-[11px] font-semibold text-[#717182]">상태</span>
              <span className="inline-flex items-center gap-2 h-8 px-2.5 rounded-md border border-black/10 bg-card text-[12.5px] font-semibold">
                {selected.status === "answered" ? "답변완료" : selected.status === "progress" ? "처리중" : "미답변"}
                <ChevronDown size={12} className="text-[#717182]" />
              </span>
              <span className="text-[11px] font-semibold text-[#717182] ml-1">담당자</span>
              <span className="inline-flex items-center gap-2 h-8 px-2.5 rounded-md border border-black/10 bg-card text-[12.5px] font-semibold">
                {selected.assignee || "미지정"}
                <ChevronDown size={12} className="text-[#717182]" />
              </span>
              {selected.priority && (
                <span className="ml-auto inline-flex items-center gap-1 h-8 px-3 rounded-md text-xs font-semibold"
                  style={{ background: "#fef2f2", color: "#d4183d" }}>
                  <Flame size={14} />긴급 해제
                </span>
              )}
            </div>

            {/* Member context + AI summary */}
            <div className="px-5 py-3.5 border-b border-black/10">
              {/* context chips */}
              <div className="flex gap-2 flex-wrap items-center">
                <ContextChip icon={<Crown size={14} className="text-blue-600" />} label="요금제" value={selected.plan || "무료"} />
                <ContextChip icon={<CalendarDays size={14} className="text-blue-600" />} label="가입" value={selected.joined ? "3개월차" : "최근"} />
                <ContextChip icon={<MessageSquare size={14} className="text-blue-600" />} label="문의 이력" value="총 3건" />
                <span className="ml-auto text-xs font-semibold text-blue-600 inline-flex items-center gap-1 cursor-pointer hover:underline">
                  회원 상세<ArrowUpRight size={13} />
                </span>
              </div>

              {/* AI Summary */}
              {!aiAvailable ? (
                <AiDisconnectedBox onRetry={() => setAiAvailable(true)} />
              ) : aiSummary === "none" ? (
                <AiCallCard
                  icon={<Sparkles size={18} />}
                  title="AI 회원 요약"
                  desc="과거 문의·이용 기록을 2~3줄로 정리해요"
                  actionLabel="요약 생성"
                  onAction={generateSummary}
                />
              ) : aiSummary === "loading" ? (
                <AiLoadingBox label="AI 회원 요약" text="요약 생성 중…" />
              ) : aiSummary === "empty" ? (
                <AiNewUserBox />
              ) : aiSummary === "ready" ? (
                <AiSummaryReady />
              ) : null}
            </div>

            {/* Conversation */}
            <div className="px-5 py-4 flex flex-col gap-3.5">
              {selected.msgs.map((m, i) => (
                <MessageBubble key={i} msg={m} />
              ))}
            </div>

            {/* Internal memo */}
            <div className="px-5 py-3.5 border-t border-black/10" style={{ background: "#fffdf5" }}>
              <div className="text-xs font-bold mb-2 flex items-center gap-1.5" style={{ color: "#92400e" }}>
                <StickyNote size={14} />
                내부 메모
                <span className="font-semibold text-[10.5px] text-[#717182] bg-[#ececf0] rounded-full px-1.5 py-0.5">회원에게 보이지 않음</span>
              </div>
              <textarea
                value={memo}
                onChange={(e) => setMemo(e.target.value)}
                placeholder="처리 경위, 인계 사항 등 운영자끼리 공유할 메모를 남기세요."
                className="w-full min-h-[42px] p-2.5 rounded-md border bg-card text-[12.5px] leading-[1.5] placeholder:text-slate-400 resize-y"
                style={{ borderColor: "#fde68a" }}
              />
            </div>

            {/* AI Draft */}
            <div className="px-5 py-4 border-t border-black/10">
              {!aiAvailable ? null : aiDraft === "none" ? (
                <AiCallCard
                  icon={<PenLine size={18} />}
                  title="AI 답변 초안"
                  desc="문의에 맞는 답변 초안을 만들어드려요"
                  actionLabel="초안 생성"
                  onAction={generateDraft}
                />
              ) : aiDraft === "loading" ? (
                <AiDraftLoading />
              ) : aiDraft === "ready" ? (
                <AiDraftReady draft={replyText} onUseDraft={useDraft} onRegenerate={regenerateDraft} />
              ) : null}
            </div>

            {/* Reply composer */}
            <div className="px-5 py-4 border-t border-black/10" style={{ background: "#f8fafc" }}>
              <div className="text-xs font-bold mb-2.5 flex items-center gap-1.5">
                <CornerDownRight size={14} className="text-blue-600" />
                답변 작성
              </div>

              {/* Quick reply templates */}
              <div className="flex gap-1.5 flex-wrap mb-2.5">
                {TEMPLATES.map((t) => (
                  <button key={t.label} onClick={() => setReplyText(t.text)}
                    className="inline-flex items-center gap-1 text-[11.5px] font-semibold px-2.5 py-1 rounded-full border border-black/10 bg-card text-slate-600 hover:border-blue-300 transition-colors">
                    <Zap size={12} />{t.label}
                  </button>
                ))}
              </div>

              <textarea
                value={replyText}
                onChange={(e) => setReplyText(e.target.value)}
                placeholder="답변을 작성하세요…"
                className="w-full min-h-[84px] p-3 rounded-md border border-black/10 bg-card text-[13px] leading-[1.65] text-slate-700 placeholder:text-slate-400 resize-y"
              />

              <div className="flex items-center justify-between mt-2.5 gap-3">
                {draftLoaded && (
                  <span className="text-[11.5px] text-[#717182] inline-flex items-center gap-1">
                    <Info size={13} />
                    AI 초안을 불러왔어요. 내용을 확인·수정한 뒤 전송하세요.
                  </span>
                )}
                <button onClick={handleSendReply} disabled={!replyText.trim()}
                  className="ml-auto inline-flex items-center gap-1.5 h-[38px] px-[18px] rounded-lg text-white text-[13px] font-bold transition-opacity disabled:opacity-40"
                  style={{ background: "linear-gradient(135deg, #2563eb, #4f46e5)" }}>
                  답변 보내기
                  <SendIcon size={15} />
                </button>
              </div>
            </div>
          </div>
        ) : (
          <div className="bg-card border border-black/10 rounded-xl shadow-sm p-12 text-center text-slate-400">
            문의를 선택하세요
          </div>
        )}
      </div>

      {toast && <div className={`inq-toast inq-toast--${toast.tone}`}>{toast.msg}</div>}
    </AdminShell>
  );
}

/* ══════════ Sub-components ══════════ */

function ContextChip({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
  return (
    <span className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-[10px] border border-black/10 text-xs" style={{ background: "#f8fafc" }}>
      {icon}
      <span className="text-[#717182]">{label}</span>
      <span className="font-bold">{value}</span>
    </span>
  );
}

function MessageBubble({ msg }: { msg: InquiryMessage }) {
  const isUser = msg.who === "user";
  return (
    <div className="flex gap-2.5 max-w-[88%]">
      <span className="w-[34px] h-[34px] rounded-full flex items-center justify-center text-white text-[13px] font-bold shrink-0"
        style={{ background: isUser ? "#2563eb" : "#030213" }}>
        {msg.name[0]}
      </span>
      <div className="rounded-xl p-3 border border-black/10" style={{ background: isUser ? "#f8fafc" : "#fff" }}>
        <div className="text-xs font-bold mb-1 flex items-center gap-1.5">
          {msg.name}
          <span className="text-[10.5px] text-[#717182] font-normal">{msg.time}</span>
        </div>
        <div className="text-[13px] leading-relaxed text-slate-600">{msg.text}</div>
      </div>
    </div>
  );
}

function AiCallCard({ icon, title, desc, actionLabel, onAction }: {
  icon: React.ReactNode; title: string; desc: string; actionLabel: string; onAction: () => void;
}) {
  return (
    <div className="mt-3 rounded-[10px] p-3 flex items-center gap-3"
      style={{ background: "#f7f8ff", border: "1px solid rgba(79,70,229,0.16)" }}>
      <span className="shrink-0 w-[38px] h-[38px] rounded-[10px] flex items-center justify-center text-indigo-600"
        style={{ background: "#eef2ff" }}>
        {icon}
      </span>
      <div className="flex-1 min-w-0">
        <div className="text-[13px] font-bold" style={{ color: "#4338ca" }}>{title}</div>
        <div className="text-[11.5px] text-[#717182] leading-[1.5]">{desc}</div>
      </div>
      <button onClick={onAction}
        className="shrink-0 inline-flex items-center gap-1.5 h-[34px] px-3 rounded-lg text-[12.5px] font-bold bg-card border hover:bg-indigo-50 transition-colors"
        style={{ borderColor: "rgba(79,70,229,0.3)", color: "#4338ca" }}>
        <Wand2 size={14} />{actionLabel}
      </button>
    </div>
  );
}

function AiLoadingBox({ label, text }: { label: string; text: string }) {
  return (
    <div className="mt-3 rounded-[10px] p-3" style={{ background: "#f7f8ff", border: "1px solid rgba(79,70,229,0.16)" }}>
      <div className="flex items-center gap-1.5 mb-3">
        <Sparkles size={15} className="text-indigo-600" />
        <span className="text-[12.5px] font-bold" style={{ color: "#4338ca" }}>{label}</span>
        <span className="ml-auto inline-flex items-center gap-1.5 text-[11.5px] font-semibold" style={{ color: "#4338ca" }}>
          <span className="w-3.5 h-3.5 rounded-full border-2 border-indigo-200 inline-block"
            style={{ borderTopColor: "#4f46e5", animation: "ctSpin .7s linear infinite" }} />
          {text}
        </span>
      </div>
      <div className="flex flex-col gap-2">
        <div className="ct-sk h-[11px] w-[96%]" />
        <div className="ct-sk h-[11px] w-[88%]" />
        <div className="ct-sk h-[11px] w-[64%]" />
      </div>
    </div>
  );
}

function AiNewUserBox() {
  return (
    <div className="mt-3 rounded-[10px] p-3" style={{ background: "#f7f8ff", border: "1px solid rgba(79,70,229,0.16)" }}>
      <div className="flex items-center gap-1.5 mb-2.5">
        <Sparkles size={15} className="text-indigo-600" />
        <span className="text-[12.5px] font-bold" style={{ color: "#4338ca" }}>AI 회원 요약</span>
      </div>
      <div className="flex items-start gap-2.5 px-0.5 py-1.5">
        <span className="shrink-0 w-9 h-9 rounded-[10px] bg-card border border-black/8 text-slate-400 flex items-center justify-center">
          <UserPlus size={17} />
        </span>
        <div>
          <div className="text-[13px] font-bold mb-0.5">이력이 없는 신규 사용자예요</div>
          <div className="text-xs leading-relaxed text-[#717182]">가입 직후라 참고할 과거 문의가 없어요. 첫 문의이니 기본 안내로 응대해 주세요.</div>
        </div>
      </div>
    </div>
  );
}

function AiSummaryReady() {
  return (
    <div className="mt-3 rounded-[10px] p-3" style={{ background: "#f7f8ff", border: "1px solid rgba(79,70,229,0.16)" }}>
      <div className="flex items-center gap-1.5 mb-1.5">
        <Sparkles size={15} className="text-indigo-600" />
        <span className="text-[12.5px] font-bold" style={{ color: "#4338ca" }}>AI 회원 요약</span>
        <span className="text-[10.5px] font-semibold text-slate-400 bg-card border border-black/8 rounded-full px-2 py-0.5">민감정보 제외</span>
        <span className="ml-auto inline-flex items-center gap-1 text-[11px] text-slate-400">
          <RotateCw size={12} />방금 생성
        </span>
      </div>
      <div className="text-[13px] leading-[1.65] text-slate-600"
        dangerouslySetInnerHTML={{ __html: MOCK_SUMMARY.replace(/\*\*(.*?)\*\*/g, '<b class="text-[#030213]">$1</b>') }} />
    </div>
  );
}

function AiDraftLoading() {
  return (
    <div>
      <div className="flex items-center gap-2 mb-2.5">
        <Sparkles size={15} className="text-indigo-600" />
        <span className="text-[12.5px] font-bold" style={{ color: "#4338ca" }}>AI 답변 초안</span>
        <span className="ml-auto inline-flex items-center gap-1.5 text-[11.5px] font-semibold" style={{ color: "#4338ca" }}>
          <span className="w-3.5 h-3.5 rounded-full border-2 border-indigo-200 inline-block"
            style={{ borderTopColor: "#4f46e5", animation: "ctSpin .7s linear infinite" }} />
          초안 작성 중…
        </span>
      </div>
      <div className="rounded-[10px] p-3" style={{ background: "#f7f8ff", border: "1px solid rgba(79,70,229,0.16)" }}>
        <div className="bg-card border border-black/8 rounded-[9px] p-3 flex flex-col gap-2">
          <div className="ct-sk h-[11px] w-[94%]" />
          <div className="ct-sk h-[11px] w-[99%]" />
          <div className="ct-sk h-[11px] w-[72%]" />
        </div>
        <div className="flex gap-1.5 mt-2.5">
          <div className="ct-sk h-[22px] w-[128px] rounded-full" />
          <div className="ct-sk h-[22px] w-[108px] rounded-full" />
        </div>
      </div>
      <div className="text-[11px] text-slate-400 mt-2 flex items-center gap-1.5">
        <FileSearch size={13} />FAQ·공지 문서를 검색하고 있어요
      </div>
    </div>
  );
}

function AiDraftReady({ draft, onUseDraft, onRegenerate }: { draft: string; onUseDraft: () => void; onRegenerate: () => void }) {
  return (
    <div>
      <div className="flex items-center gap-2 mb-2.5">
        <Sparkles size={15} className="text-indigo-600" />
        <span className="text-[12.5px] font-bold" style={{ color: "#4338ca" }}>AI 답변 초안</span>
        <span className="inline-flex items-center gap-1 text-[10.5px] font-extrabold px-2 py-0.5 rounded-full"
          style={{ color: "#b45309", background: "#fffbeb", border: "1px solid #fde68a" }}>
          <AlertTriangle size={11} />검토 필요
        </span>
        <span className="ml-auto text-[11px] text-slate-400">AI 생성 · 그대로 전송 금지</span>
      </div>
      <div className="rounded-[10px] p-3" style={{ background: "#f7f8ff", border: "1px solid rgba(79,70,229,0.16)" }}>
        <div className="bg-card border border-black/8 rounded-[9px] p-3 text-[13px] leading-[1.7] text-slate-700 whitespace-pre-wrap">
          {draft}
        </div>
        <div className="flex items-center gap-2 mt-3 flex-wrap">
          <button onClick={onUseDraft}
            className="inline-flex items-center gap-1.5 h-9 px-3.5 rounded-lg bg-[#030213] text-white text-[12.5px] font-bold hover:bg-[#1a1a2e] transition-colors">
            <CornerDownLeft size={15} />이 초안 사용
          </button>
          <button onClick={onRegenerate}
            className="inline-flex items-center gap-1.5 h-9 px-3 rounded-lg border border-black/12 bg-card text-slate-600 text-[12.5px] font-semibold hover:bg-slate-50 transition-colors">
            <RefreshCw size={14} />다시 생성
          </button>
          <button className="inline-flex items-center gap-1.5 h-9 px-3 rounded-lg bg-transparent text-[#717182] text-[12.5px] font-semibold hover:bg-slate-50 transition-colors">
            <FileSearch size={14} />근거 보기
          </button>
        </div>
      </div>
    </div>
  );
}

function AiDisconnectedBox({ onRetry }: { onRetry: () => void }) {
  return (
    <div className="mt-3">
      <div className="rounded-[10px] p-3.5 flex items-start gap-3"
        style={{ background: "#fffbeb", border: "1px solid #fde68a" }}>
        <span className="shrink-0 w-9 h-9 rounded-[10px] bg-card border flex items-center justify-center text-amber-700"
          style={{ borderColor: "#fde68a" }}>
          <WifiOff size={17} />
        </span>
        <div className="flex-1">
          <div className="text-[13px] font-bold mb-0.5" style={{ color: "#92400e" }}>AI 보조를 사용할 수 없어요</div>
          <div className="text-xs leading-relaxed" style={{ color: "#854d0e" }}>지금은 AI 요약·초안 생성이 어려워요. 평소처럼 직접 답변해 주세요.</div>
        </div>
        <button onClick={onRetry}
          className="shrink-0 inline-flex items-center gap-1 h-[30px] px-2.5 rounded-lg text-[11.5px] font-semibold bg-card hover:bg-amber-50 transition-colors"
          style={{ border: "1px solid #fcd34d", color: "#b45309" }}>
          <RotateCw size={13} />다시 시도
        </button>
      </div>
      <div className="flex items-center gap-1.5 mt-3.5 mb-2.5 text-xs font-semibold" style={{ color: "#16a34a" }}>
        <CheckCircle2 size={14} />빠른 답변 템플릿은 그대로 사용할 수 있어요
      </div>
      <div className="flex gap-1.5 flex-wrap">
        {TEMPLATES.map((t) => (
          <span key={t.label} className="inline-flex items-center gap-1 text-[11.5px] font-semibold px-2.5 py-1 rounded-full border border-black/10 bg-card text-slate-600">
            <Zap size={12} />{t.label}
          </span>
        ))}
      </div>
    </div>
  );
}
