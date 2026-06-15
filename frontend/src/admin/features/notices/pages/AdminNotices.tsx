import { useState, useEffect } from "react";
import {
  Megaphone, Plus, Pin, PinOff, Search, ChevronLeft, ChevronRight,
  Trash2, ArrowDownCircle, ArrowUpCircle, ArrowLeft, Eye, Check, CalendarClock,
  Bold, Italic, Strikethrough, List, ListOrdered, Quote, Link2, ImageIcon, Table,
} from "lucide-react";
import AdminShell from "../../../components/AdminShell";
import { type Notice, type NoticeStatus, NOTICES } from "../data/noticesData";
// TODO: 백엔드 연동 시 주석 해제
// import * as adminNoticeApi from "../api/adminNoticeApi";
import { ConfirmDialog } from "@/app/components/ui/confirm-dialog";
import "./admin-notices.css";
import "./notice-compose.css";

/* ═══ 목록 필터 ═══ */
type FilterKey = "전체" | "게시중" | "예약" | "내림";

const STATUS_LABEL: Record<NoticeStatus, string> = {
  published: "게시중", draft: "내림", scheduled: "예약",
};
const STATUS_CLS: Record<NoticeStatus, string> = {
  published: "ok", draft: "off", scheduled: "blue",
};

type DialogState =
  | { type: "delete"; notice: Notice }
  | { type: "status"; notice: Notice; target: NoticeStatus }
  | { type: "pin"; notice: Notice };

/* ═══ 작성 폼 도구 ═══ */
const CATS = ["일반", "점검", "기능 업데이트", "프로모션", "정책·약관"];
const TOOLBAR: (string | null)[] = [
  "bold", "italic", "strikethrough", null, "list", "list-ordered", "quote", null, "link", "image", "table",
];
const ICON_MAP: Record<string, React.ElementType> = {
  bold: Bold, italic: Italic, strikethrough: Strikethrough,
  list: List, "list-ordered": ListOrdered, quote: Quote,
  link: Link2, image: ImageIcon, table: Table,
};

/* ═══ 작성 폼 ═══ */
function NoticeComposeView({ onBack, onCreated }: { onBack: () => void; onCreated: () => void }) {
  const [cat, setCat] = useState("일반");
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [pin, setPin] = useState(false);
  const [push, setPush] = useState(true);
  const [when, setWhen] = useState<"즉시" | "예약">("즉시");
  const [scheduleAt, setScheduleAt] = useState("");
  const [saving, setSaving] = useState(false);
  const [toast, setToast] = useState<{ msg: string; tone: string } | null>(null);

  const canSubmit = title.trim().length > 1 && body.trim().length > 4;

  const flash = (msg: string, tone: string) => {
    setToast({ msg, tone });
    setTimeout(() => setToast(null), 2200);
  };

  const handleSubmit = async () => {
    if (!canSubmit || saving) return;
    setSaving(true);
    // TODO: 백엔드 연동 시 adminNoticeApi.createNotice 로 교체
    flash("공지가 게시되었습니다.", "green");
    setTimeout(() => onCreated(), 600);
    setSaving(false);
  };

  return (
    <AdminShell
      active="notices" breadcrumb="공지 작성" title="공지 작성" icon={Megaphone}
      desc="게시하면 공지사항 목록과 (선택 시) 전체 알림으로 발송됩니다"
      actions={<button className="av-btn" onClick={onBack}><ArrowLeft /> 목록으로</button>}
    >
      <div className="av-form">
        <section className="av-panel">
          <div className="av-field">
            <div className="av-flabel">분류</div>
            <select className="av-select" style={{ width: 200 }} value={cat} onChange={(e) => setCat(e.target.value)}>
              {CATS.map((c) => <option key={c}>{c}</option>)}
            </select>
          </div>
          <div className="av-field">
            <div className="av-flabel">제목 <span className="av-count num">{title.length}/80</span></div>
            <input className="av-input" value={title} maxLength={80} onChange={(e) => setTitle(e.target.value)}
              placeholder="예: [점검] 6월 12일(목) 02:00~04:00 서비스 정기 점검 안내" />
            <div className="av-hint">점검·장애 공지는 머리말([점검], [장애])을 붙이면 목록에서 식별이 쉽습니다.</div>
          </div>
          <div className="av-field">
            <div className="av-flabel">본문</div>
            <div className="nc-toolbar">
              {TOOLBAR.map((t, i) => {
                if (!t) return <span key={i} className="nc-tooldiv" />;
                const Icon = ICON_MAP[t];
                return <button key={i} className="nc-tool" aria-label={t}>{Icon && <Icon />}</button>;
              })}
            </div>
            <textarea className="av-textarea nc-body" value={body} onChange={(e) => setBody(e.target.value)}
              placeholder={"공지 내용을 입력하세요.\n\n점검 공지라면 — 일시, 영향 범위(접속 불가/일부 기능), 사유를 순서대로 적어주세요."} />
          </div>
        </section>

        <aside className="av-rail">
          <section className="av-panel">
            <div className="av-mod__h"><span className="av-mod__t">게시 설정</span></div>
            <div style={{ padding: "12px 14px 14px" }}>
              <div className={`av-switchrow${pin ? " on" : ""}`} onClick={() => setPin(!pin)}>
                <span className="av-switch" />
                <span><span className="t">상단 고정</span><span className="s" style={{ display: "block" }}>목록 최상단에 핀 고정 (최대 3개)</span></span>
              </div>
              <div className={`av-switchrow${push ? " on" : ""}`} onClick={() => setPush(!push)}>
                <span className="av-switch" />
                <span><span className="t">전체 알림 발송</span><span className="s" style={{ display: "block" }}>모든 회원에게 NOTICE 타입 알림이 갑니다 — 점검·정책 변경 등 중요 공지에만 사용하세요</span></span>
              </div>
            </div>
          </section>
          <section className="av-panel">
            <div className="av-mod__h"><span className="av-mod__t">게시 시점</span></div>
            <div style={{ padding: "12px 14px 14px" }}>
              <div className="av-choices">
                <div className={`av-choice${when === "즉시" ? " on" : ""}`} onClick={() => setWhen("즉시")}>
                  <div className="t">즉시</div><div className="s">게시 버튼 클릭 시</div>
                </div>
                <div className={`av-choice${when === "예약" ? " on" : ""}`} onClick={() => setWhen("예약")}>
                  <div className="t">예약</div><div className="s">일시 지정</div>
                </div>
              </div>
              {when === "예약" && <input className="av-input num" style={{ marginTop: 8 }} type="datetime-local" value={scheduleAt} onChange={(e) => setScheduleAt(e.target.value)} />}
            </div>
          </section>
        </aside>
      </div>

      <div className="av-composefoot">
        <div className="av-composefoot__in">
          <span className="av-composefoot__draft num"><Check /> 임시저장됨 · 방금</span>
          <div className="av-composefoot__r">
            <button className="av-btn"><Eye /> 미리보기</button>
            <button className="av-btn">임시저장</button>
            <button className="av-btn av-btn--ink" disabled={!canSubmit || saving}
              style={!canSubmit ? { opacity: 0.45, cursor: "default" } : undefined} onClick={handleSubmit}>
              {when === "예약" && <CalendarClock />}
              {when === "예약" ? "게시 예약" : "게시"}
            </button>
          </div>
        </div>
      </div>
      {toast && <div className={`ntc-toast ntc-toast--${toast.tone}`}>{toast.msg}</div>}
    </AdminShell>
  );
}

/* ═══ 메인 (목록 + 작성 토글) ═══ */
export default function AdminNotices() {
  const [view, setView] = useState<"list" | "compose">("list");
  const [items, setItems] = useState<Notice[]>([]);
  const [filter, setFilter] = useState<FilterKey>("전체");
  const [toast, setToast] = useState<{ msg: string; tone: string } | null>(null);
  const [dialog, setDialog] = useState<DialogState | null>(null);

  const loadItems = () => {
    // TODO: 백엔드 연동 시 adminNoticeApi.getNotices().then(setItems) 로 교체
    setItems(NOTICES);
  };

  useEffect(() => { loadItems(); }, []);

  const flash = (msg: string, tone: string) => {
    setToast({ msg, tone });
    setTimeout(() => setToast(null), 2200);
  };

  const handleConfirm = async () => {
    if (!dialog) return;
    // TODO: 백엔드 연동 시 adminNoticeApi.deleteNotice/updateNotice 로 교체
    if (dialog.type === "delete") {
      setItems((prev) => prev.filter((n) => n.id !== dialog.notice.id));
      flash("공지가 삭제되었습니다.", "green");
    } else if (dialog.type === "pin") {
      const updated: Notice = { ...dialog.notice, pinned: !dialog.notice.pinned };
      setItems((prev) => prev.map((n) => (n.id === updated.id ? updated : n)));
      flash(updated.pinned ? "공지가 상단에 고정되었습니다." : "고정이 해제되었습니다.", "green");
    } else {
      const updated: Notice = { ...dialog.notice, status: dialog.target };
      setItems((prev) => prev.map((n) => (n.id === updated.id ? updated : n)));
      flash(`공지가 ${STATUS_LABEL[dialog.target]}(으)로 변경되었습니다.`, "green");
    }
    setDialog(null);
  };

  if (view === "compose") {
    return <NoticeComposeView onBack={() => setView("list")} onCreated={() => { setView("list"); loadItems(); }} />;
  }

  const filtered = items.filter((n) => {
    if (filter === "전체") return true;
    if (filter === "게시중") return n.status === "published";
    if (filter === "예약") return n.status === "scheduled";
    return n.status === "draft";
  });

  return (
    <AdminShell
      active="notices" breadcrumb="공지사항" title="공지사항" icon={Megaphone}
      desc="서비스 공지 작성·게시 관리"
      actions={<button className="av-btn av-btn--ink" onClick={() => setView("compose")}><Plus /> 새 공지</button>}
    >
      <section className="av-panel">
        <div className="av-filters">
          <span className="av-muted num">{filtered.length}건</span>
          <div className="right">
            <div className="av-seg">
              {(["전체", "게시중", "예약", "내림"] as FilterKey[]).map((f) => (
                <button key={f} className={filter === f ? "on" : ""} onClick={() => setFilter(f)}>{f}</button>
              ))}
            </div>
          </div>
        </div>

        <table className="av-table">
          <thead>
            <tr><th>번호</th><th>제목</th><th>상태</th><th className="r">조회</th><th className="r">게시일</th><th className="r">조치</th></tr>
          </thead>
          <tbody>
            {filtered.map((n) => (
              <tr key={n.id}>
                <td className="av-id num">{n.id}</td>
                <td>
                  <div className="av-cell__t" style={{ maxWidth: 560 }}>
                    {n.pinned && <span className="nv-pin"><Pin /> 고정</span>}
                    {n.title}
                  </div>
                </td>
                <td><span className={`av-st av-st--${STATUS_CLS[n.status]}`}>{STATUS_LABEL[n.status]}</span></td>
                <td className="r av-muted num">{n.views.toLocaleString()}</td>
                <td className="r av-muted num">{n.date}</td>
                <td className="r">
                  <div className="nv-actions">
                    <button className="av-btn" title={n.pinned ? "고정 해제" : "상단 고정"}
                      onClick={() => setDialog({ type: "pin", notice: n })}>
                      {n.pinned ? <PinOff /> : <Pin />}
                    </button>
                    {n.status === "published" && (
                      <button className="av-btn" title="내림" onClick={() => setDialog({ type: "status", notice: n, target: "draft" })}><ArrowDownCircle /></button>
                    )}
                    {n.status === "draft" && (
                      <button className="av-btn" title="게시" onClick={() => setDialog({ type: "status", notice: n, target: "published" })}><ArrowUpCircle /></button>
                    )}
                    <button className="av-btn" title="삭제" onClick={() => setDialog({ type: "delete", notice: n })}><Trash2 /></button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        <div className="av-foot">
          <span className="num">1–{filtered.length} / {items.length}건</span>
          <div className="av-pager">
            <button disabled aria-label="이전"><ChevronLeft /></button>
            <button aria-label="다음"><ChevronRight /></button>
          </div>
        </div>
      </section>

      {dialog && (() => {
        if (dialog.type === "delete") {
          return (
            <ConfirmDialog variant="danger" icon={<Trash2 />}
              title="이 공지를 삭제할까요?" description="삭제하면 되돌릴 수 없습니다."
              meta={[{ label: "제목", value: dialog.notice.title }, { label: "상태", value: STATUS_LABEL[dialog.notice.status] }, { label: "조회수", value: dialog.notice.views.toLocaleString() }]}
              confirmLabel="삭제" onConfirm={handleConfirm} onCancel={() => setDialog(null)} />
          );
        }
        if (dialog.type === "pin") {
          const willPin = !dialog.notice.pinned;
          return (
            <ConfirmDialog variant="info" icon={willPin ? <Pin /> : <PinOff />}
              title={willPin ? "이 공지를 상단에 고정할까요?" : "고정을 해제할까요?"}
              description={willPin ? "목록 최상단에 핀 고정됩니다 (최대 3개)." : "고정이 해제되어 일반 순서로 돌아갑니다."}
              meta={[{ label: "제목", value: dialog.notice.title }, { label: "현재", value: dialog.notice.pinned ? "고정됨" : "일반" }]}
              confirmLabel={willPin ? "고정" : "해제"} onConfirm={handleConfirm} onCancel={() => setDialog(null)} />
          );
        }
        const isPublish = dialog.target === "published";
        return (
          <ConfirmDialog variant="warning" icon={isPublish ? <ArrowUpCircle /> : <ArrowDownCircle />}
            title={isPublish ? "이 공지를 게시할까요?" : "이 공지를 내릴까요?"}
            description={isPublish ? "게시하면 모든 사용자에게 공지가 노출됩니다." : "내리면 사용자에게 더 이상 보이지 않습니다."}
            meta={[{ label: "제목", value: dialog.notice.title }, { label: "현재 상태", value: STATUS_LABEL[dialog.notice.status] }]}
            confirmLabel={isPublish ? "게시" : "내림"} onConfirm={handleConfirm} onCancel={() => setDialog(null)} />
          );
      })()}

      {toast && <div className={`ntc-toast ntc-toast--${toast.tone}`}>{toast.msg}</div>}
    </AdminShell>
  );
}
