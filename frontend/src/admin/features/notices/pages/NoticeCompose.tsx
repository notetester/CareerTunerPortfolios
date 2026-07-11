import { useState } from "react";
import { useNavigate } from "react-router";
import { Megaphone, ArrowLeft, CalendarClock } from "lucide-react";
import AdminShell from "../../../components/AdminShell";
import { useAdminDomainAuthorization } from "../../../auth/useAdminAuthorization";
import * as adminNoticeApi from "../api/adminNoticeApi";
import "./notice-compose.css";

const CATS = ["일반", "점검", "기능 업데이트", "프로모션", "정책·약관"];

export default function NoticeCompose() {
  const { canCreate } = useAdminDomainAuthorization("CONTENT");
  const navigate = useNavigate();
  const [cat, setCat] = useState("일반");
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [pin, setPin] = useState(false);
  const [when, setWhen] = useState<"즉시" | "예약">("즉시");
  const [scheduleAt, setScheduleAt] = useState("");
  const [saving, setSaving] = useState(false);
  const [toast, setToast] = useState<{ msg: string; tone: string } | null>(null);

  const canSubmit = title.trim().length > 1
    && body.trim().length > 4
    && (when === "즉시" || Boolean(scheduleAt));

  const flash = (msg: string, tone: string) => {
    setToast({ msg, tone });
    setTimeout(() => setToast(null), 2200);
  };

  const handleSubmit = async () => {
    if (!canCreate || !canSubmit || saving) return;
    setSaving(true);
    try {
      await adminNoticeApi.createNotice({
        title,
        content: body,
        category: cat,
        status: when === "예약" ? "SCHEDULED" : "PUBLISHED",
        isPinned: pin,
        thumbnailUrl: null,
        scheduledAt: when === "예약" ? scheduleAt : null,
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
      desc="공지사항을 작성해 즉시 게시하거나 지정한 시각에 예약 게시합니다."
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
              <button
                type="button"
                className={`av-switchrow${pin ? " on" : ""}`}
                style={{ width: "100%", background: "transparent", color: "inherit", textAlign: "left" }}
                role="switch"
                aria-checked={pin}
                onClick={() => setPin(!pin)}
              >
                <span className="av-switch" />
                <span>
                  <span className="t">상단 고정</span>
                  <span className="s" style={{ display: "block" }}>
                    목록 최상단에 핀 고정 (최대 3개)
                  </span>
                </span>
              </button>
            </div>
          </section>

          <section className="av-panel">
            <div className="av-mod__h"><span className="av-mod__t">게시 시점</span></div>
            <div style={{ padding: "12px 14px 14px" }}>
              <div className="av-choices">
                <button
                  type="button"
                  className={`av-choice${when === "즉시" ? " on" : ""}`}
                  style={{ color: "inherit", textAlign: "left" }}
                  aria-pressed={when === "즉시"}
                  onClick={() => setWhen("즉시")}
                >
                  <span className="t" style={{ display: "block" }}>즉시</span>
                  <span className="s" style={{ display: "block" }}>게시 버튼 클릭 시</span>
                </button>
                <button
                  type="button"
                  className={`av-choice${when === "예약" ? " on" : ""}`}
                  style={{ color: "inherit", textAlign: "left" }}
                  aria-pressed={when === "예약"}
                  onClick={() => setWhen("예약")}
                >
                  <span className="t" style={{ display: "block" }}>예약</span>
                  <span className="s" style={{ display: "block" }}>일시 지정</span>
                </button>
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
          <div className="av-composefoot__r">
            {canCreate && (
              <button
                className="av-btn av-btn--ink"
                disabled={!canSubmit || saving}
                style={!canSubmit ? { opacity: 0.45, cursor: "default" } : undefined}
                onClick={handleSubmit}
              >
                {when === "예약" && <CalendarClock />}
                {when === "예약" ? "게시 예약" : "게시"}
              </button>
            )}
          </div>
        </div>
      </div>

      {toast && <div className={`ntc-toast ntc-toast--${toast.tone}`}>{toast.msg}</div>}
    </AdminShell>
  );
}
