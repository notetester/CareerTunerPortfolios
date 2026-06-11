import { useState } from "react";
import { useNavigate } from "react-router";
import { CircleHelp, ArrowLeft, Eye, Check, CornerDownRight } from "lucide-react";
import AdminShell from "../../../components/AdminShell";
import { FAQ_CATEGORIES, type FaqCategory } from "../data/faqData";
import * as adminFaqApi from "../api/adminFaqApi";
import "./faq-compose.css";

const COMPOSE_CATS = ["AI 분석", "결제·크레딧", "계정", "커뮤니티", "코칭"] as const;

const SIMILAR_SAMPLES = [
  { q: "무료 분석 횟수는 언제 충전되나요?", v: "조회 2,841" },
  { q: "프로 플랜 환불 규정이 궁금해요", v: "조회 1,976" },
];

/* 작성 화면 카테고리 → DB 카테고리 매핑 */
const COMPOSE_TO_DB: Record<string, FaqCategory> = {
  "AI 분석": "AI기능",
  "결제·크레딧": "결제",
  "계정": "계정",
  "커뮤니티": "일반",
  "코칭": "면접",
};

export default function FaqCompose() {
  const navigate = useNavigate();
  const [cat, setCat] = useState<string>("결제·크레딧");
  const [q, setQ] = useState("");
  const [a, setA] = useState("");
  const [visible, setVisible] = useState(true);
  const [saving, setSaving] = useState(false);
  const [toast, setToast] = useState<{ msg: string; tone: string } | null>(null);

  const canSubmit = q.trim().length > 4 && a.trim().length > 9;
  const showDup = q.trim().length > 1;

  const flash = (msg: string, tone: string) => {
    setToast({ msg, tone });
    setTimeout(() => setToast(null), 2200);
  };

  const handleSubmit = async () => {
    if (!canSubmit || saving) return;
    setSaving(true);
    try {
      await adminFaqApi.createFaq({
        cat: COMPOSE_TO_DB[cat] ?? "일반",
        q,
        a,
        on: visible,
      });
      flash("FAQ가 등록되었습니다.", "green");
      setTimeout(() => navigate("/admin/faq"), 800);
    } catch {
      flash("저장에 실패했습니다.", "red");
    } finally {
      setSaving(false);
    }
  };

  return (
    <AdminShell
      active="faq"
      breadcrumb="FAQ 작성"
      title="FAQ 작성"
      icon={CircleHelp}
      desc="고객센터 자주 묻는 질문 등록"
      actions={
        <button className="av-btn" onClick={() => navigate("/admin/faq")}>
          <ArrowLeft /> 목록으로
        </button>
      }
    >
      <div className="av-form">
        {/* 본문 영역 */}
        <section className="av-panel">
          <div className="av-field">
            <div className="av-flabel">카테고리</div>
            <div className="fc-cat">
              {COMPOSE_CATS.map((c) => (
                <button
                  key={c}
                  className={`fc-pill${cat === c ? " on" : ""}`}
                  onClick={() => setCat(c)}
                >
                  {c}
                </button>
              ))}
            </div>
          </div>

          <div className="av-field">
            <div className="av-flabel">
              질문 <span className="av-count num">{q.length}/100</span>
            </div>
            <input
              className="av-input"
              value={q}
              maxLength={100}
              onChange={(e) => setQ(e.target.value)}
              placeholder="사용자가 검색하는 말투 그대로 — 예: 크레딧이 차감됐는데 분석이 안 됐어요"
            />
            {showDup && (
              <div className="fc-dup">
                <div className="fc-dup__h">비슷한 FAQ가 이미 있어요 — 중복이면 기존 항목을 수정하세요</div>
                {SIMILAR_SAMPLES.map((s) => (
                  <a key={s.q} href="#" onClick={(e) => e.preventDefault()}>
                    <CornerDownRight />{s.q}<span className="v num">{s.v}</span>
                  </a>
                ))}
              </div>
            )}
          </div>

          <div className="av-field">
            <div className="av-flabel">답변 <span className="opt">— 두괄식으로, 결론 먼저</span></div>
            <textarea
              className="av-textarea"
              style={{ minHeight: 200 }}
              value={a}
              onChange={(e) => setA(e.target.value)}
              placeholder={"답변을 입력하세요.\n\n좋은 답변 = 결론 1문장 + 조건·예외 + 안 될 때 다음 행동(문의 링크)."}
            />
          </div>
        </section>

        {/* 우측 레일 */}
        <aside className="av-rail">
          <section className="av-panel">
            <div className="av-mod__h"><span className="av-mod__t">노출 설정</span></div>
            <div style={{ padding: "12px 14px 14px" }}>
              <div
                className={`av-switchrow${visible ? " on" : ""}`}
                onClick={() => setVisible(!visible)}
              >
                <span className="av-switch" />
                <span>
                  <span className="t">고객센터에 노출</span>
                  <span className="s" style={{ display: "block" }}>
                    끄면 저장만 되고 사용자에게 보이지 않아요
                  </span>
                </span>
              </div>
            </div>
          </section>
          <section className="av-panel">
            <div className="av-mod__h"><span className="av-mod__t">작성 팁</span></div>
            <div className="av-note" style={{ marginTop: 12 }}>
              문의 관리에서 <b>같은 질문이 3회 이상</b> 들어온 주제는 FAQ로 만드는 게 좋아요.
              지난주 최다: &ldquo;크레딧 차감 후 분석 실패&rdquo; 7건.
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
              등록
            </button>
          </div>
        </div>
      </div>

      {toast && <div className={`faq-toast faq-toast--${toast.tone}`}>{toast.msg}</div>}
    </AdminShell>
  );
}
