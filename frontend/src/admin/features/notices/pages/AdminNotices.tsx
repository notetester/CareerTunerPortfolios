import { useState, useEffect } from "react";
import {
  Megaphone, Plus, Pin, PinOff,
  Trash2, ArrowDownCircle, ArrowUpCircle, ArrowLeft, CalendarClock, Pencil,
} from "lucide-react";
import AdminShell from "../../../components/AdminShell";
import {
  AdminListFooter,
  AdminListToolbar,
  AdminSortableHeader,
  useAdminListTools,
  type AdminListColumn,
} from "../../../components/AdminListTools";
import { type Notice, type NoticeStatus } from "../data/noticesData";
import * as adminNoticeApi from "../api/adminNoticeApi";
import { ConfirmDialog } from "@/app/components/ui/confirm-dialog";
import NoticeBodyEditor from "../components/NoticeBodyEditor";
import { sanitizePostHtml } from "@/app/lib/postContent";
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

const NOTICE_COLUMNS: AdminListColumn<Notice>[] = [
  { id: "id", label: "번호", getText: (row) => row.id, sortable: true },
  { id: "title", label: "제목", getText: (row) => row.title, sortable: true },
  { id: "status", label: "상태", getText: (row) => STATUS_LABEL[row.status], sortable: true },
  { id: "views", label: "조회", getText: (row) => row.views, sortable: true },
  { id: "date", label: "게시일", getText: (row) => row.date, sortable: true },
];

type DialogState =
  | { type: "delete"; notice: Notice }
  | { type: "status"; notice: Notice; target: NoticeStatus }
  | { type: "pin"; notice: Notice };

/* ═══ 작성 폼 ═══ */
const CATS = ["일반", "점검", "기능 업데이트", "프로모션", "정책·약관"];

function NoticeComposeView({ editing, onBack, onSaved }: { editing: Notice | null; onBack: () => void; onSaved: () => void }) {
  const [cat, setCat] = useState(editing?.category ?? "일반");
  const [title, setTitle] = useState(editing?.title ?? "");
  const [body, setBody] = useState("");        // 본문 HTML (TipTap 출력)
  const [bodyLen, setBodyLen] = useState(0);   // 본문 평문 길이(검증용)
  const [pin, setPin] = useState(editing?.pinned ?? false);
  const [push, setPush] = useState(true);
  const [when, setWhen] = useState<"즉시" | "예약">(editing?.status === "scheduled" ? "예약" : "즉시");
  const [scheduleAt, setScheduleAt] = useState("");
  const [saving, setSaving] = useState(false);
  const [toast, setToast] = useState<{ msg: string; tone: string } | null>(null);

  const canSubmit = title.trim().length > 1 && bodyLen > 4;

  const flash = (msg: string, tone: string) => {
    setToast({ msg, tone });
    setTimeout(() => setToast(null), 2200);
  };

  const handleSubmit = async () => {
    if (!canSubmit || saving) return;
    setSaving(true);
    try {
      if (editing) {
        // 수정: 제목/본문/분류/고정만 갱신. 상태(게시/예약/임시)는 목록 토글이 관리하므로 보존(BE가 null 필드 coalesce).
        await adminNoticeApi.updateNotice(editing.id, {
          title,
          content: sanitizePostHtml(body),
          category: cat,
          isPinned: pin,
        });
        flash("공지가 수정되었습니다.", "green");
      } else {
        await adminNoticeApi.createNotice({
          title,
          content: sanitizePostHtml(body),
          status: when === "예약" ? "SCHEDULED" : "PUBLISHED",
          isPinned: pin,
          category: cat,
          thumbnailUrl: null,
        });
        flash("공지가 게시되었습니다.", "green");
      }
      setTimeout(() => onSaved(), 600);
    } catch {
      flash("저장에 실패했습니다.", "red");
    } finally {
      setSaving(false);
    }
  };

  return (
    <AdminShell
      active="notices" breadcrumb={editing ? "공지 수정" : "공지 작성"} title={editing ? "공지 수정" : "공지 작성"} icon={Megaphone}
      desc={editing ? "내용을 수정하고 저장하면 즉시 반영됩니다" : "게시하면 공지사항 목록과 (선택 시) 전체 알림으로 발송됩니다"}
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
            <NoticeBodyEditor
              initialContent={editing?.body ?? ""}
              onChange={(html, len) => { setBody(html); setBodyLen(len); }}
            />
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
          {!editing && (
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
          )}
        </aside>
      </div>

      <div className="av-composefoot">
        <div className="av-composefoot__in">
          <div className="av-composefoot__r">
            <button className="av-btn av-btn--ink" disabled={!canSubmit || saving}
              style={!canSubmit ? { opacity: 0.45, cursor: "default" } : undefined} onClick={handleSubmit}>
              {!editing && when === "예약" && <CalendarClock />}
              {editing ? "수정 저장" : when === "예약" ? "게시 예약" : "게시"}
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
  const [editing, setEditing] = useState<Notice | null>(null);   // null=새 공지, Notice=수정
  const [items, setItems] = useState<Notice[]>([]);
  const [filter, setFilter] = useState<FilterKey>("전체");
  const [toast, setToast] = useState<{ msg: string; tone: string } | null>(null);
  const [dialog, setDialog] = useState<DialogState | null>(null);

  const loadItems = () => {
    adminNoticeApi.getNotices().then(setItems)
      .catch(() => flash("공지 목록을 불러오지 못했습니다.", "red"));
  };

  useEffect(() => { loadItems(); }, []);

  const flash = (msg: string, tone: string) => {
    setToast({ msg, tone });
    setTimeout(() => setToast(null), 2200);
  };

  const handleConfirm = async () => {
    if (!dialog) return;
    try {
      if (dialog.type === "delete") {
        await adminNoticeApi.deleteNotice(dialog.notice.id);
        setItems((prev) => prev.filter((n) => n.id !== dialog.notice.id));
        flash("공지가 삭제되었습니다.", "green");
      } else if (dialog.type === "pin") {
        const updated = await adminNoticeApi.updateNotice(dialog.notice.id, { isPinned: !dialog.notice.pinned });
        setItems((prev) => prev.map((n) => (n.id === updated.id ? updated : n)));
        flash(updated.pinned ? "공지가 상단에 고정되었습니다." : "고정이 해제되었습니다.", "green");
      } else {
        const statusMap: Record<NoticeStatus, string> = { published: "PUBLISHED", draft: "DRAFT", scheduled: "SCHEDULED" };
        const updated = await adminNoticeApi.updateNotice(dialog.notice.id, { status: statusMap[dialog.target] });
        setItems((prev) => prev.map((n) => (n.id === updated.id ? updated : n)));
        flash(`공지가 ${STATUS_LABEL[dialog.target]}(으)로 변경되었습니다.`, "green");
      }
    } catch {
      flash("처리에 실패했습니다.", "red");
    }
    setDialog(null);
  };

  const filtered = items.filter((n) => {
    if (filter === "전체") return true;
    if (filter === "게시중") return n.status === "published";
    if (filter === "예약") return n.status === "scheduled";
    return n.status === "draft";
  });

  // 훅은 조건부 early-return(작성 뷰) 위에서 호출해 호출 순서를 고정한다.
  const list = useAdminListTools(filtered, {
    columns: NOTICE_COLUMNS,
    getRowId: (row) => row.id,
    defaultSortId: "id",
    defaultSortDir: "desc",
  });

  if (view === "compose") {
    return <NoticeComposeView
      editing={editing}
      onBack={() => { setView("list"); setEditing(null); }}
      onSaved={() => { setView("list"); setEditing(null); loadItems(); }}
    />;
  }

  return (
    <AdminShell
      active="notices" breadcrumb="공지사항" title="공지사항" icon={Megaphone}
      desc="서비스 공지 작성·게시 관리"
      actions={<button className="av-btn av-btn--ink" onClick={() => { setEditing(null); setView("compose"); }}><Plus /> 새 공지</button>}
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

        <AdminListToolbar state={list} fileName="admin_notices" />

        <table className="av-table">
          <thead>
            <tr>
              <AdminSortableHeader state={list} columnId="id">번호</AdminSortableHeader>
              <AdminSortableHeader state={list} columnId="title">제목</AdminSortableHeader>
              <AdminSortableHeader state={list} columnId="status">상태</AdminSortableHeader>
              <AdminSortableHeader state={list} columnId="views" className="r">조회</AdminSortableHeader>
              <AdminSortableHeader state={list} columnId="date" className="r">게시일</AdminSortableHeader>
              <th className="r">조치</th>
            </tr>
          </thead>
          <tbody>
            {list.visibleRows.map((n) => (
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
                    <button className="av-btn" title="수정"
                      onClick={() => { setEditing(n); setView("compose"); }}>
                      <Pencil />
                    </button>
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
            {list.visibleRows.length === 0 && (
              <tr><td colSpan={6} style={{ textAlign: "center", padding: "2rem" }}>현재 조건에 맞는 공지가 없습니다.</td></tr>
            )}
          </tbody>
        </table>

        <AdminListFooter state={list} />
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
