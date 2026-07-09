import { useState } from "react";
import { useNavigate } from "react-router";
import {
  Megaphone, ArrowLeft, Eye, Check, CalendarClock,
  Bold, Italic, Strikethrough, List, ListOrdered, Quote, Link2, ImageIcon, Table,
} from "lucide-react";
import AdminShell from "../../../components/AdminShell";
import * as adminNoticeApi from "../api/adminNoticeApi";
import "./notice-compose.css";

const CATS = ["일반", "점검", "기능 업데이트", "프로모션", "정책·약관"];

const TOOLBAR: (string | null)[] = [
  "bold", "italic", "strikethrough", null, "list", "list-ordered", "quote", null, "link", "image", "table",
];
const ICON_MAP: Record<string, React.ElementType> = {
  bold: Bold, italic: Italic, strikethrough: Strikethrough,
  list: List, "list-ordered": ListOrdered, quote: Quote,
  link: Link2, image: ImageIcon, table: Table,
};

export default function NoticeCompose() {
  const navigate = useNavigate();
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
    try {
      await adminNoticeApi.createNotice({
        title,
        content: body,
        status: when === "예약" ? "SCHEDULED" : "PUBLISHED",
        isPinned: pin,
        thumbnailUrl: null,
      });
      flash("공지가 게시되었습니다.", "green");
      setTimeout(() => navigate("/admin/notices"), 800);
    } catch {
      flash("저장에 실패했습니다.", "red");
    } finally {
      setSaving(false);
    }
  };

  return (
    <AdminShell
      active="notices"
      breadcrumb="공지 작성"
      title="공지 작성"
      icon={Megaphone}
      desc="게시하면 공지사항 목록과 (선택 시) 전체 알림으로 발송됩니다"
      actions={
        <button className="av-btn" onClick={() => navigate("/admin/notices")}>
          <ArrowLeft /> 목록으로
        </button>
      }
    >
      <div className="av-form">
        {/* 본문 영역 */}
        <section className="av-panel">
          <div className="av-field">
            <div className="av-flabel">분류</div>
            <select
              className="av-select"
              style={{ width: 200 }}
              value={cat}
              onChange={(e) => setCat(e.target.value)}
            >
              {CATS.map((c) => <option key={c}>{c}</option>)}
            </select>
          </div>

          <div className="av-field">
            <div className="av-flabel">
              제목 <span className="av-count num">{title.length}/80</span>
            </div>
            <input
              className="av-input"
              value={title}
              maxLength={80}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="예: [점검] 6월 12일(목) 02:00~04:00 서비스 정기 점검 안내"
            />
            <div className="av-hint">
              점검·장애 공지는 머리말([점검], [장애])을 붙이면 목록에서 식별이 쉽습니다.
            </div>
          </div>

          <div className="av-field">
            <div className="av-flabel">본문</div>
            <div className="nc-toolbar">
              {TOOLBAR.map((t, i) => {
                if (!t) return <span key={i} className="nc-tooldiv" />;
                const Icon = ICON_MAP[t];
                return (
                  <button key={i} className="nc-tool" aria-label={t}>
                    {Icon && <Icon />}
                  </button>
                );
              })}
            </div>
            <textarea
              className="av-textarea nc-body"
              value={body}
              onChange={(e) => setBody(e.target.value)}
              placeholder={"공지 내용을 입력하세요.\n\n점검 공지라면 — 일시, 영향 범위(접속 불가/일부 기능), 사유를 순서대로 적어주세요."}
            />
          </div>
        </section>

        {/* 우측 레일 */}
        <aside className="av-rail">
          <section className="av-panel">
            <div className="av-mod__h"><span className="av-mod__t">게시 설정</span></div>
            <div style={{ padding: "12px 14px 14px" }}>
              <div
                className={`av-switchrow${pin ? " on" : ""}`}
                onClick={() => setPin(!pin)}
              >
                <span className="av-switch" />
                <span>
                  <span className="t">상단 고정</span>
                  <span className="s" style={{ display: "block" }}>
                    목록 최상단에 핀 고정 (최대 3개)
                  </span>
                </span>
              </div>
              <div
                className={`av-switchrow${push ? " on" : ""}`}
                onClick={() => setPush(!push)}
              >
                <span className="av-switch" />
                <span>
                  <span className="t">전체 알림 발송</span>
                  <span className="s" style={{ display: "block" }}>
                    모든 회원에게 NOTICE 타입 알림이 갑니다 — 점검·정책 변경 등 중요 공지에만 사용하세요
                  </span>
                </span>
              </div>
            </div>
          </section>

          <section className="av-panel">
            <div className="av-mod__h"><span className="av-mod__t">게시 시점</span></div>
            <div style={{ padding: "12px 14px 14px" }}>
              <div className="av-choices">
                <div
                  className={`av-choice${when === "즉시" ? " on" : ""}`}
                  onClick={() => setWhen("즉시")}
                >
                  <div className="t">즉시</div>
                  <div className="s">게시 버튼 클릭 시</div>
                </div>
                <div
                  className={`av-choice${when === "예약" ? " on" : ""}`}
                  onClick={() => setWhen("예약")}
                >
                  <div className="t">예약</div>
                  <div className="s">일시 지정</div>
                </div>
              </div>
              {when === "예약" && (
                <input
                  className="av-input num"
                  style={{ marginTop: 8 }}
                  type="datetime-local"
                  value={scheduleAt}
                  onChange={(e) => setScheduleAt(e.target.value)}
                />
              )}
            </div>
          </section>
        </aside>
      </div>

      {/* 스티키 푸터 */}
      <div className="av-composefoot">
        <div className="av-composefoot__in">
          <span className="av-composefoot__draft num">
            <Check /> 임시저장됨 · 방금
          </span>
          <div className="av-composefoot__r">
            <button className="av-btn"><Eye /> 미리보기</button>
            <button className="av-btn">임시저장</button>
            <button
              className="av-btn av-btn--ink"
              disabled={!canSubmit || saving}
              style={!canSubmit ? { opacity: 0.45, cursor: "default" } : undefined}
              onClick={handleSubmit}
            >
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
