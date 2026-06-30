import { useState, useEffect } from "react";
import {
  CircleHelp, Plus, Search, ChevronLeft, ChevronRight, ChevronUp, ChevronDown,
  Trash2, Eye, EyeOff, ArrowLeft, Check, CornerDownRight, RefreshCw,
} from "lucide-react";
import AdminShell from "../../../components/AdminShell";
import { FAQ_CATEGORIES, type Faq, type FaqCategory } from "../data/faqData";
import * as adminFaqApi from "../api/adminFaqApi";
import { ConfirmDialog } from "@/app/components/ui/confirm-dialog";
import "./admin-faq.css";
import "./faq-compose.css";

type DialogState =
  | { type: "delete"; faq: Faq }
  | { type: "toggle"; faq: Faq };

/* ═══ 작성 폼 ═══ */
const COMPOSE_CATS = ["AI 분석", "결제·크레딧", "계정", "커뮤니티", "코칭"] as const;
const SIMILAR_SAMPLES = [
  { q: "무료 분석 횟수는 언제 충전되나요?", v: "조회 2,841" },
  { q: "프로 플랜 환불 규정이 궁금해요", v: "조회 1,976" },
];
const COMPOSE_TO_DB: Record<string, FaqCategory> = {
  "AI 분석": "AI기능", "결제·크레딧": "결제", "계정": "계정", "커뮤니티": "일반", "코칭": "면접",
};

function FaqComposeView({ onBack, onCreated }: { onBack: () => void; onCreated: () => void }) {
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
      setTimeout(() => onCreated(), 600);
    } catch {
      flash("저장에 실패했습니다.", "red");
    } finally {
      setSaving(false);
    }
  };

  return (
    <AdminShell
      active="faq" breadcrumb="FAQ 작성" title="FAQ 작성" icon={CircleHelp}
      desc="고객센터 자주 묻는 질문 등록"
      actions={<button className="av-btn" onClick={onBack}><ArrowLeft /> 목록으로</button>}
    >
      <div className="av-form">
        <section className="av-panel">
          <div className="av-field">
            <div className="av-flabel">카테고리</div>
            <div className="fc-cat">
              {COMPOSE_CATS.map((c) => (
                <button key={c} className={`fc-pill${cat === c ? " on" : ""}`} onClick={() => setCat(c)}>{c}</button>
              ))}
            </div>
          </div>

          <div className="av-field">
            <div className="av-flabel">질문 <span className="av-count num">{q.length}/100</span></div>
            <input className="av-input" value={q} maxLength={100} onChange={(e) => setQ(e.target.value)}
              placeholder="사용자가 검색하는 말투 그대로 — 예: 크레딧이 차감됐는데 분석이 안 됐어요" />
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
            <textarea className="av-textarea" style={{ minHeight: 200 }} value={a} onChange={(e) => setA(e.target.value)}
              placeholder={"답변을 입력하세요.\n\n좋은 답변 = 결론 1문장 + 조건·예외 + 안 될 때 다음 행동(문의 링크)."} />
          </div>
        </section>

        <aside className="av-rail">
          <section className="av-panel">
            <div className="av-mod__h"><span className="av-mod__t">노출 설정</span></div>
            <div style={{ padding: "12px 14px 14px" }}>
              <div className={`av-switchrow${visible ? " on" : ""}`} onClick={() => setVisible(!visible)}>
                <span className="av-switch" />
                <span><span className="t">고객센터에 노출</span><span className="s" style={{ display: "block" }}>끄면 저장만 되고 사용자에게 보이지 않아요</span></span>
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

      <div className="av-composefoot">
        <div className="av-composefoot__in">
          <span className="av-composefoot__draft num"><Check /> 임시저장됨 · 방금</span>
          <div className="av-composefoot__r">
            <button className="av-btn"><Eye /> 미리보기</button>
            <button className="av-btn">임시저장</button>
            <button className="av-btn av-btn--ink" disabled={!canSubmit || saving}
              style={!canSubmit ? { opacity: 0.45, cursor: "default" } : undefined} onClick={handleSubmit}>
              등록
            </button>
          </div>
        </div>
      </div>
      {toast && <div className={`faq-toast faq-toast--${toast.tone}`}>{toast.msg}</div>}
    </AdminShell>
  );
}

/* ═══ 메인 (목록 + 작성 토글) ═══ */
export default function AdminFaq() {
  const [view, setView] = useState<"list" | "compose">("list");
  const [items, setItems] = useState<Faq[]>([]);
  const [catFilter, setCatFilter] = useState("전체");
  const [query, setQuery] = useState("");
  const [toast, setToast] = useState<{ msg: string; tone: string } | null>(null);
  const [dialog, setDialog] = useState<DialogState | null>(null);
  const [embedding, setEmbedding] = useState(false);

  const handleEmbedAll = async () => {
    if (embedding) return;
    setEmbedding(true);
    try {
      const { embeddedCount } = await adminFaqApi.embedAllFaqs(true);
      flash(`${embeddedCount}개 FAQ 임베딩 완료`, "green");
    } catch {
      flash("임베딩에 실패했습니다.", "red");
    } finally {
      setEmbedding(false);
    }
  };

  const loadItems = () => {
    adminFaqApi.getFaqs().then(setItems)
      .catch(() => flash("FAQ 목록을 불러오지 못했습니다.", "red"));
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
        await adminFaqApi.deleteFaq(dialog.faq.id);
        setItems((prev) => prev.filter((f) => f.id !== dialog.faq.id));
        flash("FAQ가 삭제되었습니다.", "green");
      } else {
        const updated = await adminFaqApi.updateFaq(dialog.faq.id, {
          cat: dialog.faq.cat,
          q: dialog.faq.q,
          a: dialog.faq.a,
          on: !dialog.faq.on,
        });
        setItems((prev) => prev.map((f) => (f.id === updated.id ? updated : f)));
        flash(updated.on ? "FAQ가 노출로 변경되었습니다." : "FAQ가 비노출로 변경되었습니다.", "green");
      }
    } catch {
      flash("처리에 실패했습니다.", "red");
    }
    setDialog(null);
  };

  // 순서 변경은 전체 목록 기준으로만(필터/검색 중엔 부분목록이라 전역 순서와 안 맞음 → 숨김).
  const reorderable = catFilter === "전체" && !query.trim();

  // ↑↓ 이동: items 에서 인접 스왑 후, 새 위치(index)를 sort_order 로 영속한다.
  // 첫 이동 때 0 베이스라인이 0..n-1 로 정규화되고, 이후엔 바뀐 항목만 PUT 된다(기존값=index면 스킵).
  const move = async (index: number, dir: -1 | 1) => {
    const target = index + dir;
    if (target < 0 || target >= items.length) return;
    const next = [...items];
    [next[index], next[target]] = [next[target], next[index]];
    setItems(next);
    try {
      const updates = next.flatMap((f, i) =>
        f.sortOrder === i ? [] : [adminFaqApi.updateFaq(f.id, { cat: f.cat, q: f.q, a: f.a, on: f.on, sortOrder: i })]);
      await Promise.all(updates);
      loadItems();
      flash("순서를 변경했습니다.", "green");
    } catch {
      flash("순서 변경에 실패했습니다.", "red");
      loadItems();
    }
  };

  if (view === "compose") {
    return <FaqComposeView onBack={() => setView("list")} onCreated={() => { setView("list"); loadItems(); }} />;
  }

  const filtered = items.filter((f) => {
    if (catFilter !== "전체" && f.cat !== catFilter) return false;
    if (query && !f.q.toLowerCase().includes(query.toLowerCase())) return false;
    return true;
  });

  const catOptions = ["전체", ...FAQ_CATEGORIES];

  return (
    <AdminShell
      active="faq" breadcrumb="FAQ 관리" title="FAQ 관리" icon={CircleHelp}
      desc="고객센터 자주 묻는 질문 관리"
      actions={
        <>
          <button className="av-btn" onClick={handleEmbedAll} disabled={embedding}>
            <RefreshCw className={embedding ? "spin" : ""} /> {embedding ? "임베딩 중…" : "챗봇 임베딩 갱신"}
          </button>
          <button className="av-btn av-btn--ink" onClick={() => setView("compose")}><Plus /> 새 FAQ</button>
        </>
      }
    >
      <section className="av-panel">
        <div className="av-filters">
          <div className="av-search">
            <Search />
            <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="질문 검색" />
          </div>
          <div className="right">
            <div className="av-seg">
              {catOptions.map((c) => (
                <button key={c} className={catFilter === c ? "on" : ""} onClick={() => setCatFilter(c)}>{c}</button>
              ))}
            </div>
          </div>
        </div>

        <table className="av-table">
          <thead>
            <tr>
              <th>ID</th><th>질문</th><th>카테고리</th><th>상태</th><th className="r">수정일</th><th className="r">조치</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((f, i) => (
              <tr key={f.id}>
                <td className="av-id num">#{f.id}</td>
                <td><div className="av-cell__t" style={{ maxWidth: 520 }}>{f.q}</div></td>
                <td className="av-muted" style={{ whiteSpace: "nowrap" }}>{f.cat}</td>
                <td>
                  {f.on
                    ? <span className="av-st av-st--ok">노출</span>
                    : <span className="av-st av-st--off">비노출</span>}
                </td>
                <td className="r av-muted num">–</td>
                <td className="r">
                  <div className="faq-actions">
                    {reorderable && (
                      <>
                        <button className="av-btn" title="위로" disabled={i === 0} onClick={() => move(i, -1)}>
                          <ChevronUp />
                        </button>
                        <button className="av-btn" title="아래로" disabled={i === filtered.length - 1} onClick={() => move(i, 1)}>
                          <ChevronDown />
                        </button>
                      </>
                    )}
                    <button className="av-btn" title={f.on ? "비노출" : "노출"}
                      onClick={() => setDialog({ type: "toggle", faq: f })}>
                      {f.on ? <EyeOff /> : <Eye />}
                    </button>
                    <button className="av-btn" title="삭제" onClick={() => setDialog({ type: "delete", faq: f })}>
                      <Trash2 />
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        <div className="av-foot">
          <span className="num">{filtered.length}건 표시 · 전체 {items.length}건</span>
          <span />
        </div>
      </section>

      {dialog && (() => {
        if (dialog.type === "delete") {
          return (
            <ConfirmDialog variant="danger" icon={<Trash2 />}
              title="이 FAQ를 삭제할까요?" description="삭제하면 고객센터에서 완전히 제거되며 되돌릴 수 없어요."
              meta={[{ label: "질문", value: dialog.faq.q }, { label: "카테고리", value: dialog.faq.cat }]}
              confirmLabel="삭제" onConfirm={handleConfirm} onCancel={() => setDialog(null)} />
          );
        }
        const willShow = !dialog.faq.on;
        return (
          <ConfirmDialog variant="warning" icon={willShow ? <Eye /> : <EyeOff />}
            title={willShow ? "이 FAQ를 노출할까요?" : "이 FAQ를 비노출로 변경할까요?"}
            description={willShow ? "고객센터에 즉시 노출됩니다." : "고객센터에서 더 이상 보이지 않습니다."}
            meta={[{ label: "질문", value: dialog.faq.q }, { label: "현재 상태", value: dialog.faq.on ? "노출" : "비노출" }]}
            confirmLabel={willShow ? "노출" : "비노출"} onConfirm={handleConfirm} onCancel={() => setDialog(null)} />
        );
      })()}

      {toast && <div className={`faq-toast faq-toast--${toast.tone}`}>{toast.msg}</div>}
    </AdminShell>
  );
}
