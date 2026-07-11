import { useState, useEffect, useCallback } from "react";
import {
  Mail, Inbox, Send, Timer, Smile, Sparkles, Paperclip,
  ChevronDown, Flame, User, Crown, CalendarDays, MessageSquare,
  ArrowUpRight, StickyNote, AlertTriangle, CornerDownLeft,
  CornerDownRight, RefreshCw, FileSearch, Zap, Info, WifiOff,
  RotateCw, CheckCircle2, UserPlus, PenLine, Wand2,
  Send as SendIcon,
} from "lucide-react";
import AdminShell from "../../../components/AdminShell";
import {
  AdminListFooter,
  AdminListToolbar,
  useAdminListTools,
  type AdminListColumn,
} from "../../../components/AdminListTools";
import { type Inquiry, type InquiryStatus, type InquiryMessage, TEMPLATES, ASSIGNEES, INQUIRIES } from "../data/inquiriesData";
import * as adminTicketApi from "../api/adminTicketApi";
import { ConfirmDialog } from "@/app/components/ui/confirm-dialog";
import { useAdminDomainAuthorization } from "../../../auth/useAdminAuthorization";
import "./admin-inquiries.css";

type ListFilter = "미답변" | "처리중" | "완료" | "전체";
type AiSummaryState = "none" | "loading" | "ready" | "empty" | "error";
type AiDraftState = "none" | "loading" | "ready" | "error";

/** 티켓 상태별 표시(라벨·뱃지색) — 목록·상세 뱃지가 공유한다. */
const STATUS_META: Record<InquiryStatus, { label: string; cls: string }> = {
  pending:  { label: "미답변",   cls: "inq-st--pending" },
  progress: { label: "처리중",   cls: "inq-st--progress" },
  answered: { label: "답변완료", cls: "inq-st--answered" },
  hold:     { label: "보류",     cls: "inq-st--hold" },
  closed:   { label: "종료",     cls: "inq-st--closed" },
};

const INQUIRY_LIST_COLUMNS: AdminListColumn<Inquiry>[] = [
  { id: "id", label: "ID", getText: (row) => row.id, sortable: true },
  { id: "category", label: "분류", getText: (row) => row.cat, sortable: true },
  { id: "title", label: "문의", getText: (row) => row.title, sortable: true },
  { id: "member", label: "회원", getText: (row) => row.member, sortable: true },
  { id: "status", label: "상태", getText: (row) => STATUS_META[row.status]?.label ?? row.status, sortable: true },
  { id: "assignee", label: "담당자", getText: (row) => row.assignee, sortable: true },
  { id: "priority", label: "긴급", getText: (row) => (row.priority ? "긴급" : ""), sortable: true },
  { id: "plan", label: "요금제", getText: (row) => row.plan, sortable: true },
  { id: "date", label: "접수", getText: (row) => row.date, sortable: true },
];


export default function AdminInquiriesAI() {
  const { canCreate: canCreateAi } = useAdminDomainAuthorization("AI");
  const { canCreate: canCreateContent, canUpdate: canUpdateContent } = useAdminDomainAuthorization("CONTENT");
  const [items, setItems] = useState<Inquiry[]>([]);
  const [filter, setFilter] = useState<ListFilter>("미답변");
  const [selected, setSelected] = useState<Inquiry | null>(null);
  const [toast, setToast] = useState<{ msg: string; tone: string } | null>(null);
  const [bulkStatus, setBulkStatus] = useState<InquiryStatus | null>(null);

  // AI states
  const [aiSummary, setAiSummary] = useState<AiSummaryState>("none");
  const [summaryText, setSummaryText] = useState("");
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
    if (!canCreateAi || !selected) return;
    setAiSummary("loading");
    adminTicketApi.generateMemberSummary(selected.id)
      .then((text) => {
        const trimmed = (text || "").trim();
        if (!trimmed) {
          setAiSummary("empty");
          return;
        }
        setSummaryText(trimmed);
        setAiSummary("ready");
      })
      .catch(() => {
        setAiSummary("none");
        flash("AI 회원 요약 생성에 실패했습니다. 잠시 후 다시 시도해 주세요.", "red");
      });
  }, [canCreateAi, selected]);

  const generateDraft = useCallback(() => {
    if (!canCreateAi || !selected) return;
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
  }, [canCreateAi, selected]);

  const useDraft = () => {
    // generateDraft()에서 이미 replyText에 실제 초안이 들어있음 — 확정만 처리
    setDraftLoaded(true);
  };

  const regenerateDraft = () => {
    if (!canCreateAi || !selected) return;
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
    if (!canCreateContent || !selected || !replyText.trim()) return;
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

  // 상태 변경: BE PATCH /admin/tickets/{id}(status) 호출(BE 완비) → 목록·상세 동기화. priority/담당자는 별도(미배선).
  const changeStatus = async (status: string) => {
    if (!canUpdateContent || !selected || status === selected.status) return;
    try {
      const updated = await adminTicketApi.updateTicket(selected.id, { status });
      setItems((prev) => prev.map((i) => (i.id === updated.id ? updated : i)));
      setSelected(updated);
      flash("문의 상태를 변경했습니다.", "green");
    } catch {
      flash("상태 변경에 실패했습니다.", "red");
    }
  };

  const handleBulkStatusChange = async () => {
    if (!canUpdateContent || !bulkStatus) return;
    const targets = list.selectedRows.filter((item) => item.status !== bulkStatus);
    if (targets.length === 0) {
      flash("상태를 변경할 문의가 없습니다.", "slate");
      setBulkStatus(null);
      return;
    }
    try {
      const updatedRows = await Promise.all(targets.map((item) => adminTicketApi.updateTicket(item.id, { status: bulkStatus })));
      setItems((prev) => prev.map((item) => updatedRows.find((updated) => updated.id === item.id) ?? item));
      if (selected) {
        const updatedSelected = updatedRows.find((item) => item.id === selected.id);
        if (updatedSelected) setSelected(updatedSelected);
      }
      list.clearSelection();
      flash(`${targets.length}건의 상태를 변경했습니다.`, "green");
    } catch {
      flash("일괄 상태 변경에 실패했습니다.", "red");
    }
    setBulkStatus(null);
  };

  // 내부 메모 저장: BE 는 is_internal 메시지로 저장(reply internal=true) → 상태변경·회원알림 없음. 상세는 최신 내부메모를 보여줌.
  const saveMemo = async () => {
    if (!canCreateContent || !selected || !memo.trim()) return;
    try {
      const updated = await adminTicketApi.reply(selected.id, memo.trim(), true);
      setSelected(updated);
      setMemo(updated.memo || "");
      flash("내부 메모를 저장했습니다.", "green");
    } catch {
      flash("메모 저장에 실패했습니다.", "red");
    }
  };

  // Filtered list
  const statusFiltered = items.filter((i) => {
    if (filter === "미답변") return i.status === "pending";
    if (filter === "처리중") return i.status === "progress";
    if (filter === "완료") return i.status === "answered";
    return true;
  });
  const list = useAdminListTools(statusFiltered, {
    columns: INQUIRY_LIST_COLUMNS,
    getRowId: (row) => row.id,
    defaultPageSize: 20,
    defaultSortId: "date",
    defaultSortDir: "desc",
  });

  const pendingCount = items.filter((i) => i.status === "pending").length;

  return (
    <AdminShell
      active="inquiries"
      breadcrumb="문의 관리"
      title="문의 관리"
      icon={Mail}
      desc="회원 1:1 문의를 확인하고 상태·담당자를 관리하며 답변합니다"
      actions={canCreateAi ? (
        <div className="flex items-center gap-2">
          <span className="inline-flex items-center gap-1.5 h-[34px] px-3 rounded-lg text-[12.5px] font-bold"
            style={{ background: "var(--accent-soft)", border: "1px solid rgba(79,70,229,0.2)", color: "var(--accent-2)" }}>
            <Sparkles size={14} className="text-indigo-600" />
            AI 보조 켜짐
          </span>
        </div>
      ) : undefined}
    >
      {/* ── Stats ── */}
      <div className="grid grid-cols-4 gap-3.5 mb-5">
        {[
          { icon: Inbox, label: "미답변", value: `${pendingCount}` },
          { icon: Send, label: "오늘 답변", value: "14" },
          { icon: Timer, label: "평균 응답", value: "5.2시간" },
          { icon: Smile, label: "만족도", value: "96%" },
        ].map(({ icon: Icon, label, value }) => (
          <div key={label} className="bg-card border border-border rounded-xl shadow-sm p-4">
            <div className="text-[12.5px] text-[var(--muted-foreground)] flex items-center gap-1.5">
              <Icon size={14} />{label}
            </div>
            <div className="text-2xl font-extrabold tracking-tight mt-1.5">{value}</div>
          </div>
        ))}
      </div>

      {/* ── Grid: list + thread ── */}
      <div className="grid gap-5 items-start" style={{ gridTemplateColumns: "420px 1fr" }}>

        {/* ── Inquiry List Panel ── */}
        <div className="bg-card border border-border rounded-xl shadow-sm overflow-hidden">
          {/* filter tabs */}
          <div className="flex items-center gap-2 px-4 py-3 border-b border-border">
            <div className="inline-flex rounded-[10px] p-0.5" style={{ background: "var(--muted)" }}>
              {(["미답변", "처리중", "완료", "전체"] as ListFilter[]).map((f) => (
                <button key={f} onClick={() => { setFilter(f); list.clearSelection(); }}
                  className={`text-xs px-2.5 py-1 rounded-[7px] transition-colors ${
                    filter === f
                      ? "bg-card text-foreground font-semibold shadow-[0_1px_2px_rgba(15,23,42,0.06)]"
                      : "text-[var(--muted-foreground)]"
                  }`}>
                  {f}
                </button>
              ))}
            </div>
            <button
              type="button"
              onClick={list.toggleVisibleRows}
              className="h-7 px-2 rounded-md border border-border bg-card text-[11.5px] font-semibold text-muted-foreground hover:bg-accent"
            >
              {list.allVisibleSelected ? "페이지 해제" : "페이지 선택"}
            </button>
            <span className="ml-auto text-[12.5px] text-[var(--muted-foreground)]">
              <b className="text-foreground">{list.filteredRows.length}</b>건
            </span>
          </div>

          <AdminListToolbar
            state={list}
            fileName="admin_inquiries"
            extraActions={canUpdateContent ? (
              <>
                <button type="button" onClick={() => setBulkStatus("progress")}>
                  <CornerDownRight /> 처리중
                </button>
                <button type="button" onClick={() => setBulkStatus("answered")}>
                  <CheckCircle2 /> 답변완료
                </button>
                <button type="button" onClick={() => setBulkStatus("closed")}>
                  <CornerDownLeft /> 종료
                </button>
              </>
            ) : undefined}
          />

          {/* items */}
          <div className="flex flex-col">
            {list.visibleRows.map((inq) => (
              <div key={inq.id}
                className={`flex items-stretch border-b border-border last:border-b-0 transition-colors ${
                  selected?.id === inq.id ? "bg-accent" : "hover:bg-accent"
                }`}>
                <label className="flex w-10 shrink-0 items-center justify-center" onClick={(event) => event.stopPropagation()}>
                  <input
                    type="checkbox"
                    aria-label="문의 선택"
                    checked={list.isSelected(inq)}
                    onChange={() => list.toggleRow(inq)}
                    className="h-[15px] w-[15px]"
                  />
                </label>
                <button type="button" onClick={() => selectInquiry(inq)} className="min-w-0 flex-1 px-2.5 py-3 text-left">
                  <div className="flex items-center gap-1.5 mb-1">
                    <span className="text-[11px] font-bold px-2 py-0.5 rounded-full" style={{ background: "var(--muted)", color: "var(--muted-foreground)" }}>{inq.cat}</span>
                    {inq.priority && (
                      <span className="text-[10.5px] font-extrabold px-1.5 py-0.5 rounded-full inline-flex items-center gap-0.5 inq-urgent">
                        <Flame size={10} />긴급
                      </span>
                    )}
                    <span className={`ml-auto text-[11px] font-bold px-2 py-0.5 rounded-full ${STATUS_META[inq.status].cls}`}>
                      {STATUS_META[inq.status].label}
                    </span>
                  </div>
                  <div className="truncate text-[13.5px] font-semibold">{inq.title}</div>
                  <div className="mt-0.5 flex gap-2 text-[11.5px] text-[var(--muted-foreground)]">
                    <span>{inq.member}</span><span>·</span><span>{inq.date.slice(-5)}</span>
                  </div>
                </button>
              </div>
            ))}
            {list.visibleRows.length === 0 && (
              <div className="px-4 py-10 text-center text-sm text-muted-foreground">현재 조건에 맞는 문의가 없습니다.</div>
            )}
          </div>

          <AdminListFooter state={list} />
        </div>

        {/* ── Thread Panel ── */}
        {selected ? (
          <div className="bg-card border border-border rounded-xl shadow-sm overflow-hidden">
            {/* Thread header */}
            <div className="px-5 py-4 border-b border-border">
              <div className="flex items-center gap-2 mb-2">
                <span className="text-[11px] font-bold px-2 py-0.5 rounded-full" style={{ background: "var(--muted)", color: "var(--muted-foreground)" }}>{selected.cat}</span>
                {selected.priority && (
                  <span className="text-[10.5px] font-extrabold px-1.5 py-0.5 rounded-full inline-flex items-center gap-0.5 inq-urgent">
                    <Flame size={10} />긴급
                  </span>
                )}
                <span className={`text-[11px] font-bold px-2 py-0.5 rounded-full ${STATUS_META[selected.status].cls}`}>
                  {STATUS_META[selected.status].label}
                </span>
              </div>
              <div className="text-[17px] font-bold">{selected.title}</div>
              <div className="text-xs text-[var(--muted-foreground)] mt-1.5 flex gap-2.5">
                <span className="inline-flex items-center gap-1"><User size={12} /> {selected.member}</span>
                <span>{selected.date}</span>
              </div>
            </div>

            {/* Controls bar */}
            <div className="flex items-center gap-2 flex-wrap px-5 py-3 border-b border-border" style={{ background: "var(--muted)" }}>
              <span className="text-[11px] font-semibold text-[var(--muted-foreground)]">상태</span>
              {canUpdateContent ? (
                <select
                  value={selected.status}
                  onChange={(e) => changeStatus(e.target.value)}
                  className="h-8 px-2.5 rounded-md border border-border bg-card text-[12.5px] font-semibold cursor-pointer"
                >
                  <option value="pending">미답변</option>
                  <option value="progress">처리중</option>
                  <option value="hold">보류</option>
                  <option value="answered">답변완료</option>
                  <option value="closed">종료</option>
                </select>
              ) : (
                <span className="text-xs font-semibold">{STATUS_META[selected.status].label}</span>
              )}
              <span className="text-[11px] font-semibold text-[var(--muted-foreground)] ml-1">담당자</span>
              <span className="inline-flex items-center gap-2 h-8 px-2.5 rounded-md border border-border bg-card text-[12.5px] font-semibold">
                {selected.assignee || "미지정"}
                <ChevronDown size={12} className="text-[var(--muted-foreground)]" />
              </span>
              {selected.priority && (
                <span className="ml-auto inline-flex items-center gap-1 h-8 px-3 rounded-md text-xs font-semibold inq-urgent">
                  <Flame size={14} />긴급 해제
                </span>
              )}
            </div>

            {/* Member context + AI summary */}
            <div className="px-5 py-3.5 border-b border-border">
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
              {canCreateAi && (!aiAvailable ? (
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
                <AiSummaryReady summary={summaryText} />
              ) : null)}
            </div>

            {/* Conversation */}
            <div className="px-5 py-4 flex flex-col gap-3.5">
              {selected.msgs.map((m, i) => (
                <MessageBubble key={i} msg={m} />
              ))}
            </div>

            {/* Internal memo */}
            {canCreateContent && <div className="px-5 py-3.5 border-t border-border inq-memo">
              <div className="text-xs font-bold mb-2 flex items-center gap-1.5 inq-amber-ink">
                <StickyNote size={14} />
                내부 메모
                <span className="font-semibold text-[10.5px] text-[var(--muted-foreground)] bg-[var(--muted)] rounded-full px-1.5 py-0.5">회원에게 보이지 않음</span>
              </div>
              <textarea
                value={memo}
                onChange={(e) => setMemo(e.target.value)}
                placeholder="처리 경위, 인계 사항 등 운영자끼리 공유할 메모를 남기세요."
                className="w-full min-h-[42px] p-2.5 rounded-md border bg-card text-[12.5px] leading-[1.5] placeholder:text-muted-foreground resize-y"
                style={{ borderColor: "#fde68a" }}
              />
              <div className="mt-2 flex justify-end">
                <button
                  onClick={saveMemo}
                  disabled={!memo.trim()}
                  className="h-8 px-3 rounded-md text-[12px] font-bold text-white disabled:opacity-45 disabled:cursor-default"
                  style={{ background: "#92400e" }}
                >
                  메모 저장
                </button>
              </div>
            </div>}

            {/* AI Draft */}
            {canCreateAi && <div className="px-5 py-4 border-t border-border">
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
            </div>}

            {/* Reply composer */}
            {canCreateContent && <div className="px-5 py-4 border-t border-border" style={{ background: "var(--muted)" }}>
              <div className="text-xs font-bold mb-2.5 flex items-center gap-1.5">
                <CornerDownRight size={14} className="text-blue-600" />
                답변 작성
              </div>

              {/* Quick reply templates */}
              <div className="flex gap-1.5 flex-wrap mb-2.5">
                {TEMPLATES.map((t) => (
                  <button key={t.label} onClick={() => setReplyText(t.text)}
                    className="inline-flex items-center gap-1 text-[11.5px] font-semibold px-2.5 py-1 rounded-full border border-border bg-card text-muted-foreground hover:border-blue-300 transition-colors">
                    <Zap size={12} />{t.label}
                  </button>
                ))}
              </div>

              <textarea
                value={replyText}
                onChange={(e) => setReplyText(e.target.value)}
                placeholder="답변을 작성하세요…"
                className="w-full min-h-[84px] p-3 rounded-md border border-border bg-card text-[13px] leading-[1.65] text-foreground placeholder:text-muted-foreground resize-y"
              />

              <div className="flex items-center justify-between mt-2.5 gap-3">
                {draftLoaded && (
                  <span className="text-[11.5px] text-[var(--muted-foreground)] inline-flex items-center gap-1">
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
            </div>}
          </div>
        ) : (
          <div className="bg-card border border-border rounded-xl shadow-sm p-12 text-center text-muted-foreground">
            문의를 선택하세요
          </div>
        )}
      </div>

      {toast && <div className={`inq-toast inq-toast--${toast.tone}`}>{toast.msg}</div>}
      {canUpdateContent && bulkStatus && (
        <ConfirmDialog
          variant={bulkStatus === "closed" ? "warning" : "success"}
          icon={bulkStatus === "answered" ? <CheckCircle2 /> : <CornerDownRight />}
          title={`선택한 문의 ${list.selectedCount}건을 ${STATUS_META[bulkStatus].label}(으)로 변경할까요?`}
          description="선택한 문의의 운영 상태가 일괄 변경됩니다. 이미 같은 상태인 문의는 제외됩니다."
          meta={[
            { label: "선택", value: `${list.selectedCount}건` },
            { label: "변경 상태", value: STATUS_META[bulkStatus].label },
          ]}
          confirmLabel="상태 변경"
          cancelLabel="취소"
          onConfirm={handleBulkStatusChange}
          onCancel={() => setBulkStatus(null)}
        />
      )}
    </AdminShell>
  );
}

/* ══════════ Sub-components ══════════ */

function ContextChip({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
  return (
    <span className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-[10px] border border-border text-xs" style={{ background: "var(--muted)" }}>
      {icon}
      <span className="text-[var(--muted-foreground)]">{label}</span>
      <span className="font-bold">{value}</span>
    </span>
  );
}

function fmtAttachSize(b: number) {
  return b >= 1048576 ? (b / 1048576).toFixed(1) + "MB" : Math.max(1, Math.round(b / 1024)) + "KB";
}

function MessageBubble({ msg }: { msg: InquiryMessage }) {
  const isUser = msg.who === "user";
  return (
    <div className="flex gap-2.5 max-w-[88%]">
      <span className="w-[34px] h-[34px] rounded-full flex items-center justify-center text-white text-[13px] font-bold shrink-0"
        style={{ background: isUser ? "#2563eb" : "#030213" }}>
        {msg.name[0]}
      </span>
      <div className="rounded-xl p-3 border border-border" style={{ background: isUser ? "var(--muted)" : "var(--card)" }}>
        <div className="text-xs font-bold mb-1 flex items-center gap-1.5">
          {msg.name}
          <span className="text-[10.5px] text-[var(--muted-foreground)] font-normal">{msg.time}</span>
        </div>
        <div className="text-[13px] leading-relaxed text-muted-foreground">{msg.text}</div>
        {msg.attachments && msg.attachments.length > 0 && (
          <div className="mt-1.5 flex flex-col gap-1">
            {msg.attachments.map((a) => (
              <button
                key={a.id}
                type="button"
                onClick={() => void adminTicketApi.downloadAttachment(a.id, a.name).catch(() => {})}
                className="inline-flex items-center gap-1.5 text-[12px] text-indigo-600 hover:underline text-left"
              >
                <Paperclip className="w-3 h-3 shrink-0" />
                <span className="break-all">{a.name}</span>
                <span className="opacity-70">({fmtAttachSize(a.size)})</span>
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function AiCallCard({ icon, title, desc, actionLabel, onAction }: {
  icon: React.ReactNode; title: string; desc: string; actionLabel: string; onAction: () => void;
}) {
  return (
    <div className="mt-3 rounded-[10px] p-3 flex items-center gap-3"
      style={{ background: "var(--accent-soft)", border: "1px solid rgba(79,70,229,0.16)" }}>
      <span className="shrink-0 w-[38px] h-[38px] rounded-[10px] flex items-center justify-center text-indigo-600"
        style={{ background: "var(--accent-soft)" }}>
        {icon}
      </span>
      <div className="flex-1 min-w-0">
        <div className="text-[13px] font-bold" style={{ color: "var(--accent-2)" }}>{title}</div>
        <div className="text-[11.5px] text-[var(--muted-foreground)] leading-[1.5]">{desc}</div>
      </div>
      <button onClick={onAction}
        className="shrink-0 inline-flex items-center gap-1.5 h-[34px] px-3 rounded-lg text-[12.5px] font-bold bg-card border hover:bg-indigo-50 transition-colors"
        style={{ borderColor: "rgba(79,70,229,0.3)", color: "var(--accent-2)" }}>
        <Wand2 size={14} />{actionLabel}
      </button>
    </div>
  );
}

function AiLoadingBox({ label, text }: { label: string; text: string }) {
  return (
    <div className="mt-3 rounded-[10px] p-3" style={{ background: "var(--accent-soft)", border: "1px solid rgba(79,70,229,0.16)" }}>
      <div className="flex items-center gap-1.5 mb-3">
        <Sparkles size={15} className="text-indigo-600" />
        <span className="text-[12.5px] font-bold" style={{ color: "var(--accent-2)" }}>{label}</span>
        <span className="ml-auto inline-flex items-center gap-1.5 text-[11.5px] font-semibold" style={{ color: "var(--accent-2)" }}>
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
    <div className="mt-3 rounded-[10px] p-3" style={{ background: "var(--accent-soft)", border: "1px solid rgba(79,70,229,0.16)" }}>
      <div className="flex items-center gap-1.5 mb-2.5">
        <Sparkles size={15} className="text-indigo-600" />
        <span className="text-[12.5px] font-bold" style={{ color: "var(--accent-2)" }}>AI 회원 요약</span>
      </div>
      <div className="flex items-start gap-2.5 px-0.5 py-1.5">
        <span className="shrink-0 w-9 h-9 rounded-[10px] bg-card border border-border text-muted-foreground flex items-center justify-center">
          <UserPlus size={17} />
        </span>
        <div>
          <div className="text-[13px] font-bold mb-0.5">이력이 없는 신규 사용자예요</div>
          <div className="text-xs leading-relaxed text-[var(--muted-foreground)]">가입 직후라 참고할 과거 문의가 없어요. 첫 문의이니 기본 안내로 응대해 주세요.</div>
        </div>
      </div>
    </div>
  );
}

function AiSummaryReady({ summary }: { summary: string }) {
  return (
    <div className="mt-3 rounded-[10px] p-3" style={{ background: "var(--accent-soft)", border: "1px solid rgba(79,70,229,0.16)" }}>
      <div className="flex items-center gap-1.5 mb-1.5">
        <Sparkles size={15} className="text-indigo-600" />
        <span className="text-[12.5px] font-bold" style={{ color: "var(--accent-2)" }}>AI 회원 요약</span>
        <span className="text-[10.5px] font-semibold text-muted-foreground bg-card border border-border rounded-full px-2 py-0.5">민감정보 제외</span>
        <span className="ml-auto inline-flex items-center gap-1 text-[11px] text-muted-foreground">
          <RotateCw size={12} />방금 생성
        </span>
      </div>
      <div className="text-[13px] leading-[1.65] text-muted-foreground"
        dangerouslySetInnerHTML={{ __html: summary.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/\*\*(.*?)\*\*/g, '<b class="text-foreground">$1</b>') }} />
    </div>
  );
}

function AiDraftLoading() {
  return (
    <div>
      <div className="flex items-center gap-2 mb-2.5">
        <Sparkles size={15} className="text-indigo-600" />
        <span className="text-[12.5px] font-bold" style={{ color: "var(--accent-2)" }}>AI 답변 초안</span>
        <span className="ml-auto inline-flex items-center gap-1.5 text-[11.5px] font-semibold" style={{ color: "var(--accent-2)" }}>
          <span className="w-3.5 h-3.5 rounded-full border-2 border-indigo-200 inline-block"
            style={{ borderTopColor: "#4f46e5", animation: "ctSpin .7s linear infinite" }} />
          초안 작성 중…
        </span>
      </div>
      <div className="rounded-[10px] p-3" style={{ background: "var(--accent-soft)", border: "1px solid rgba(79,70,229,0.16)" }}>
        <div className="bg-card border border-border rounded-[9px] p-3 flex flex-col gap-2">
          <div className="ct-sk h-[11px] w-[94%]" />
          <div className="ct-sk h-[11px] w-[99%]" />
          <div className="ct-sk h-[11px] w-[72%]" />
        </div>
        <div className="flex gap-1.5 mt-2.5">
          <div className="ct-sk h-[22px] w-[128px] rounded-full" />
          <div className="ct-sk h-[22px] w-[108px] rounded-full" />
        </div>
      </div>
      <div className="text-[11px] text-muted-foreground mt-2 flex items-center gap-1.5">
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
        <span className="text-[12.5px] font-bold" style={{ color: "var(--accent-2)" }}>AI 답변 초안</span>
        <span className="inline-flex items-center gap-1 text-[10.5px] font-extrabold px-2 py-0.5 rounded-full inq-amber-box inq-amber-ink">
          <AlertTriangle size={11} />검토 필요
        </span>
        <span className="ml-auto text-[11px] text-muted-foreground">AI 생성 · 그대로 전송 금지</span>
      </div>
      <div className="rounded-[10px] p-3" style={{ background: "var(--accent-soft)", border: "1px solid rgba(79,70,229,0.16)" }}>
        <div className="bg-card border border-border rounded-[9px] p-3 text-[13px] leading-[1.7] text-foreground whitespace-pre-wrap">
          {draft}
        </div>
        <div className="flex items-center gap-2 mt-3 flex-wrap">
          <button onClick={onUseDraft}
            className="inline-flex items-center gap-1.5 h-9 px-3.5 rounded-lg bg-foreground text-background text-[12.5px] font-bold hover:opacity-90 transition-opacity">
            <CornerDownLeft size={15} />이 초안 사용
          </button>
          <button onClick={onRegenerate}
            className="inline-flex items-center gap-1.5 h-9 px-3 rounded-lg border border-border bg-card text-muted-foreground text-[12.5px] font-semibold hover:bg-accent transition-colors">
            <RefreshCw size={14} />다시 생성
          </button>
          <button className="inline-flex items-center gap-1.5 h-9 px-3 rounded-lg bg-transparent text-[var(--muted-foreground)] text-[12.5px] font-semibold hover:bg-accent transition-colors">
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
      <div className="rounded-[10px] p-3.5 flex items-start gap-3 inq-amber-box">
        <span className="shrink-0 w-9 h-9 rounded-[10px] bg-card border border-border flex items-center justify-center text-amber-700">
          <WifiOff size={17} />
        </span>
        <div className="flex-1">
          <div className="text-[13px] font-bold mb-0.5 inq-amber-ink">AI 보조를 사용할 수 없어요</div>
          <div className="text-xs leading-relaxed inq-amber-ink">지금은 AI 요약·초안 생성이 어려워요. 평소처럼 직접 답변해 주세요.</div>
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
          <span key={t.label} className="inline-flex items-center gap-1 text-[11.5px] font-semibold px-2.5 py-1 rounded-full border border-border bg-card text-muted-foreground">
            <Zap size={12} />{t.label}
          </span>
        ))}
      </div>
    </div>
  );
}
