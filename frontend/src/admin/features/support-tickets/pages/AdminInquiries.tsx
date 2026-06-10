import { useState, useRef, useEffect } from "react";
import {
  Mail, Inbox, Send, Timer, Smile, Flame,
  ExternalLink, Save,
} from "lucide-react";
import { Button } from "@/app/components/ui/button";
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from "@/app/components/ui/select";
import AdminShell from "../../../components/AdminShell";
import {
  TEMPLATES, ASSIGNEES,
  type Inquiry, type InquiryStatus,
} from "../data/inquiriesData";
import * as adminTicketApi from "../api/adminTicketApi";
import "./admin-inquiries.css";

type TabKey = "pending" | "progress" | "answered" | "all";

const TABS: { key: TabKey; label: string }[] = [
  { key: "pending", label: "미답변" },
  { key: "progress", label: "처리중" },
  { key: "answered", label: "완료" },
  { key: "all", label: "전체" },
];

const STATUS_LABEL: Record<InquiryStatus, string> = {
  pending: "미답변", progress: "처리중", hold: "보류", answered: "답변완료",
};
const STATUS_CLS: Record<InquiryStatus, string> = {
  pending: "inq-st--pending", progress: "inq-st--progress",
  hold: "inq-st--hold", answered: "inq-st--answered",
};

const CAT_COLOR: Record<string, string> = {
  "결제": "role", "AI기능": "interview", "계정": "job",
  "기술문제": "pass", "기타": "free",
};

function Toast({ msg, tone }: { msg: string; tone: string }) {
  return <div className={`inq-toast inq-toast--${tone}`}>{msg}</div>;
}

export default function AdminInquiries() {
  const [items, setItems] = useState<Inquiry[]>([]);
  const [tab, setTab] = useState<TabKey>("pending");
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [reply, setReply] = useState("");
  const [toast, setToast] = useState<{ msg: string; tone: string } | null>(null);
  const chatRef = useRef<HTMLDivElement>(null);

  /* 초기 목록 로드 */
  useEffect(() => {
    adminTicketApi.getTickets().then((list) => {
      setItems(list);
      if (list.length > 0) setSelectedId(list[0].id);
    }).catch(() => flash("문의 목록을 불러오지 못했습니다.", "red"));
  }, []);

  /* 선택 변경 시 상세 로드 */
  useEffect(() => {
    if (selectedId == null) return;
    adminTicketApi.getTicketDetail(selectedId).then((detail) => {
      setItems((prev) => prev.map((i) => i.id === selectedId ? detail : i));
    }).catch(() => {/* 무시 */});
  }, [selectedId]);

  const selected = items.find((i) => i.id === selectedId) ?? items[0];

  /* counts */
  const pendingCount = items.filter((i) => i.status === "pending").length;
  const progressCount = items.filter((i) => i.status === "progress" || i.status === "hold").length;
  const answeredCount = items.filter((i) => i.status === "answered").length;
  const countMap: Record<TabKey, number> = {
    pending: pendingCount, progress: progressCount, answered: answeredCount, all: items.length,
  };

  const filtered = tab === "all"
    ? items
    : tab === "pending"
      ? items.filter((i) => i.status === "pending")
      : tab === "progress"
        ? items.filter((i) => i.status === "progress" || i.status === "hold")
        : items.filter((i) => i.status === "answered");

  /* scroll chat to bottom */
  useEffect(() => {
    if (chatRef.current) chatRef.current.scrollTop = chatRef.current.scrollHeight;
  }, [selected?.msgs.length, selectedId]);

  const flash = (msg: string, tone: string) => {
    setToast({ msg, tone });
    setTimeout(() => setToast(null), 2200);
  };

  /* update status/priority */
  const updateField = async (id: number, patch: { status?: string; priority?: boolean }) => {
    try {
      const apiPatch: { status?: string; priority?: string } = {};
      if (patch.status !== undefined) apiPatch.status = patch.status.toUpperCase();
      if (patch.priority !== undefined) apiPatch.priority = patch.priority ? "HIGH" : "NORMAL";
      const updated = await adminTicketApi.updateTicket(id, apiPatch);
      setItems((prev) => prev.map((i) => i.id === id ? updated : i));
    } catch {
      flash("상태 변경에 실패했습니다.", "red");
    }
  };

  /* send reply */
  const handleSend = async () => {
    if (!reply.trim() || selectedId == null) return;
    try {
      const updated = await adminTicketApi.reply(selectedId, reply, false);
      setItems((prev) => prev.map((i) => i.id === selectedId ? updated : i));
      setReply("");
      flash("답변을 전송했어요", "green");
    } catch {
      flash("답변 전송에 실패했습니다.", "red");
    }
  };

  /* save memo */
  const handleSaveMemo = async (memo: string) => {
    if (selectedId == null) return;
    try {
      await adminTicketApi.reply(selectedId, memo, true);
      setItems((prev) =>
        prev.map((i) => i.id === selectedId ? { ...i, memo } : i),
      );
      flash("메모를 저장했어요", "slate");
    } catch {
      flash("메모 저장에 실패했습니다.", "red");
    }
  };

  if (!selected) {
    return (
      <AdminShell active="inquiries" breadcrumb="문의 관리" title="문의 관리" icon={Mail} desc="회원 문의를 확인하고 답변합니다.">
        <p className="inq-empty">불러오는 중...</p>
      </AdminShell>
    );
  }

  const ck = CAT_COLOR[selected.cat] ?? "free";

  return (
    <AdminShell
      active="inquiries"
      breadcrumb="문의 관리"
      title="문의 관리"
      icon={Mail}
      desc="회원 문의를 확인하고 답변합니다."
    >
      {/* Stats */}
      <div className="inq-stats">
        {[
          { label: "미답변", value: pendingCount, icon: Inbox, cls: "inq-stat--amber" },
          { label: "오늘 답변", value: answeredCount, icon: Send, cls: "inq-stat--blue" },
          { label: "평균 응답", value: "–", icon: Timer, cls: "inq-stat--slate" },
          { label: "만족도", value: "–", icon: Smile, cls: "inq-stat--green" },
        ].map((s) => (
          <div key={s.label} className={`inq-stat ${s.cls}`}>
            <div className="inq-stat__ic"><s.icon /></div>
            <div>
              <div className="inq-stat__v">{s.value}</div>
              <div className="inq-stat__l">{s.label}</div>
            </div>
          </div>
        ))}
      </div>

      <div className="inq-body">
        {/* ── Left: list ── */}
        <div className="inq-list">
          <div className="inq-tabs">
            {TABS.map((t) => (
              <button
                key={t.key}
                className={`inq-tab ${tab === t.key ? "is-on" : ""}`}
                onClick={() => setTab(t.key)}
              >
                {t.label} <span className="inq-tab__ct">{countMap[t.key]}</span>
              </button>
            ))}
          </div>

          <div className="inq-rows">
            {filtered.map((i) => {
              const c = CAT_COLOR[i.cat] ?? "free";
              return (
                <div
                  key={i.id}
                  className={`inq-row ${selectedId === i.id ? "is-selected" : ""}`}
                  onClick={() => setSelectedId(i.id)}
                >
                  <div className="inq-row__top">
                    <span className="inq-row__cat" style={{ background: `var(--cat-${c}-bg)`, color: `var(--cat-${c}-fg)` }}>
                      {i.cat}
                    </span>
                    {i.priority && <span className="inq-row__urgent">🔥 긴급</span>}
                    <span className={`inq-row__st ${STATUS_CLS[i.status]}`}>{STATUS_LABEL[i.status]}</span>
                  </div>
                  <div className="inq-row__title">{i.title}</div>
                  <div className="inq-row__meta">{i.member} · {i.date}</div>
                </div>
              );
            })}
            {filtered.length === 0 && <p className="inq-empty">해당 조건의 문의가 없습니다.</p>}
          </div>
        </div>

        {/* ── Right: thread ── */}
        <div className="inq-thread">
          {/* 1) Header */}
          <div className="inq-thread__head">
            <div className="inq-thread__badges">
              <span className="inq-row__cat" style={{ background: `var(--cat-${ck}-bg)`, color: `var(--cat-${ck}-fg)` }}>
                {selected.cat}
              </span>
              {selected.priority && <span className="inq-row__urgent">🔥 긴급</span>}
              <span className={`inq-row__st ${STATUS_CLS[selected.status]}`}>{STATUS_LABEL[selected.status]}</span>
            </div>
            <h3 className="inq-thread__title">{selected.title}</h3>
            <div className="inq-thread__meta">{selected.member} · {selected.date}</div>
          </div>

          {/* 2) Controls */}
          <div className="inq-controls">
            <div className="inq-controls__field">
              <span className="inq-controls__label">상태</span>
              <Select
                value={selected.status}
                onValueChange={(v) => updateField(selectedId!, { status: v })}
              >
                <SelectTrigger className="h-8 text-xs"><SelectValue /></SelectTrigger>
                <SelectContent>
                  {(["pending", "progress", "hold", "answered"] as InquiryStatus[]).map((s) => (
                    <SelectItem key={s} value={s}>{STATUS_LABEL[s]}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="inq-controls__field">
              <span className="inq-controls__label">담당자</span>
              <Select
                value={selected.assignee}
                onValueChange={(v) => setItems((prev) => prev.map((i) => i.id === selectedId ? { ...i, assignee: v } : i))}
              >
                <SelectTrigger className="h-8 text-xs"><SelectValue /></SelectTrigger>
                <SelectContent>
                  {ASSIGNEES.map((a) => <SelectItem key={a} value={a}>{a}</SelectItem>)}
                </SelectContent>
              </Select>
            </div>
            <button
              className={`inq-controls__urgent ${selected.priority ? "is-on" : ""}`}
              onClick={() => updateField(selectedId!, { priority: !selected.priority })}
              title="긴급 토글"
            >
              <Flame />
            </button>
          </div>

          {/* 3) Member context */}
          <div className="inq-context">
            <span className="inq-pill">{selected.plan}</span>
            <span className="inq-pill">가입 {selected.joined}</span>
            <span className="inq-pill">{selected.lastPay || "결제 내역 없음"}</span>
            <a href="/admin/users" className="inq-context__link">회원 상세 <ExternalLink /></a>
          </div>

          {/* 4) Chat */}
          <div className="inq-chat" ref={chatRef}>
            {selected.msgs.map((m, i) => (
              <div key={i} className={`inq-msg ${m.who === "admin" ? "inq-msg--admin" : "inq-msg--user"}`}>
                <div className={`inq-msg__avatar ${m.who === "admin" ? "inq-msg__avatar--admin" : ""}`}>
                  {m.who === "admin" ? "관" : m.name[0]}
                </div>
                <div className="inq-msg__body">
                  <div className="inq-msg__name">{m.name} <span className="inq-msg__time">{m.time}</span></div>
                  <div className="inq-msg__bubble">{m.text}</div>
                </div>
              </div>
            ))}
            {selected.status === "answered" && (
              <div className="inq-chat__done">답변 완료된 문의입니다</div>
            )}
          </div>

          {/* 5) Internal memo */}
          <MemoBox memo={selected.memo} onSave={handleSaveMemo} />

          {/* 6) Reply */}
          <div className="inq-reply">
            <div className="inq-reply__tpls">
              {TEMPLATES.map((t) => (
                <button
                  key={t.label}
                  className="inq-reply__chip"
                  onClick={() => setReply(t.text)}
                >
                  {t.label}
                </button>
              ))}
            </div>
            <textarea
              className="inq-reply__textarea"
              value={reply}
              onChange={(e) => setReply(e.target.value)}
              placeholder="답변을 입력하세요"
            />
            <Button
              className="bg-gradient-to-r from-blue-600 to-indigo-600 text-white w-full"
              size="sm"
              disabled={!reply.trim()}
              onClick={handleSend}
            >
              <Send /> 답변 보내기
            </Button>
          </div>
        </div>
      </div>

      {toast && <Toast msg={toast.msg} tone={toast.tone} />}
    </AdminShell>
  );
}

/* ── Internal memo sub-component ── */
function MemoBox({ memo, onSave }: { memo: string; onSave: (v: string) => void }) {
  const [value, setValue] = useState(memo);

  useEffect(() => { setValue(memo); }, [memo]);

  return (
    <div className="inq-memo">
      <div className="inq-memo__head">
        <span className="inq-memo__label">내부 메모</span>
        <span className="inq-memo__tag">회원에게 보이지 않음</span>
      </div>
      <textarea
        className="inq-memo__textarea"
        value={value}
        onChange={(e) => setValue(e.target.value)}
        placeholder="내부 메모를 입력하세요"
      />
      <Button variant="outline" size="sm" onClick={() => onSave(value)}>
        <Save /> 메모 저장
      </Button>
    </div>
  );
}
