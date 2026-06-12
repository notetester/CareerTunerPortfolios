import { useState, useEffect } from "react";
import {
  BookOpen, Eye, Check, X, Plus, ChevronUp, ChevronDown, CalendarClock, Save,
} from "lucide-react";
import AdminShell from "../../../components/AdminShell";
import {
  getGuidelines, createGuideline, updateGuideline, publishGuideline,
  type AdminGuidelineResponse, type GuidelineRule, type GuidelineParams,
} from "../api/adminGuidelineApi";
import "../styles/admin-guidelines.css";

const SANCTIONS = ["삭제 + 단계 제재", "즉시 영구 제한", "삭제만 (제재 없음)"];

const PARAM_DEFS: { k: keyof GuidelineParams; l: string; u: string; d: string; def: number }[] = [
  { k: "blind", l: "임시 블라인드 기준", u: "건 누적 시", d: "이 수의 신고가 모이면 검토 전까지 글이 가려져요. 낮을수록 신고 남용에 취약해요.", def: 3 },
  { k: "sla", l: "검토 처리 기한", u: "시간 이내", d: "영업일 기준. 블라인드 상태가 유지되는 최대 시간이에요.", def: 24 },
  { k: "expire", l: "제재 기록 소멸", u: "일 후", d: "이 기간 동안 추가 위반이 없으면 주의·경고 기록이 사라져요.", def: 90 },
  { k: "s1", l: "1차 경고 — 글쓰기 정지", u: "일", d: "주의 이후 같은 항목 재위반 시 적용돼요.", def: 7 },
  { k: "s2", l: "2차 경고 — 글쓰기 정지", u: "일", d: "댓글 포함 모든 작성이 제한돼요.", def: 30 },
  { k: "appeal", l: "이의신청 가능 기간", u: "일 이내", d: "조치 안내를 받은 날 기준. 다른 운영자가 재검토해요.", def: 30 },
];

const DEFAULT_PARAMS: GuidelineParams = { blind: 3, sla: 24, expire: 90, s1: 7, s2: 30, appeal: 30 };
const DEFAULT_RULES: GuidelineRule[] = [
  { t: "개인 특정·신상 노출", s: 0, b: "실명, 연락처, 또는 부서·직급·시기 조합으로 누구인지 알 수 있는 서술." },
  { t: "인신공격·혐오 표현", s: 0, b: "특정 이용자나 집단을 향한 모욕·위협, 출신·성별·연령 등에 대한 비하." },
  { t: "허위 사실·조작된 후기", s: 0, b: "경험하지 않은 전형의 후기, 의도적인 평판 조작." },
  { t: "광고·스팸·도배", s: 0, b: "영리 목적의 홍보, 동일 내용 반복 게시, 외부 유도 링크." },
  { t: "불법 정보·기밀 유출", s: 1, b: "법령 위반 콘텐츠, 기업의 미공개 기밀·내부 문서 유출." },
];
const DEFAULT_OKS = ["회사·전형에 대한 부정적 평가", "면접 질문·과정 복기", "연봉·처우 등 민감한 주제의 토론", "날 선 의견과 반박 (내용을 향한 비판)"];
const DEFAULT_NOS = ["특정인을 알아볼 수 있게 쓰는 것", "인신공격·혐오 표현", "지어낸 후기", "회사의 미공개 기밀"];

function parseOr<T>(json: string | null | undefined, fallback: T): T {
  if (!json) return fallback;
  try { return JSON.parse(json); } catch { return fallback; }
}

export default function AdminGuidelines() {
  const [versions, setVersions] = useState<AdminGuidelineResponse[]>([]);
  const [editId, setEditId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);

  // 폼 상태
  const [summary, setSummary] = useState("");
  const [lede, setLede] = useState("");
  const [oks, setOks] = useState<string[]>(DEFAULT_OKS);
  const [nos, setNos] = useState<string[]>(DEFAULT_NOS);
  const [rules, setRules] = useState<GuidelineRule[]>(DEFAULT_RULES);
  const [params, setParams] = useState<GuidelineParams>(DEFAULT_PARAMS);
  const [when, setWhen] = useState<"즉시" | "예약">("즉시");
  const [versionLabel, setVersionLabel] = useState("v1.1");

  const [toast, setToast] = useState<{ msg: string; tone: string } | null>(null);
  const [saving, setSaving] = useState(false);
  const [lastSaved, setLastSaved] = useState<string | null>(null);

  const flash = (msg: string, tone: string) => {
    setToast({ msg, tone });
    setTimeout(() => setToast(null), 2200);
  };

  useEffect(() => {
    loadVersions();
  }, []);

  const loadVersions = async () => {
    try {
      const list = await getGuidelines();
      setVersions(list);
      // 초안이 있으면 로드, 없으면 새 초안 준비
      const draft = list.find((g) => g.status === "DRAFT");
      if (draft) {
        loadGuidelineToForm(draft);
      } else {
        // 게시본 기반으로 새 버전 준비
        const published = list.find((g) => g.status === "PUBLISHED");
        if (published) {
          loadGuidelineToForm(published);
          setEditId(null);
          const vNum = parseFloat(published.versionLabel.replace("v", "")) + 0.1;
          setVersionLabel(`v${vNum.toFixed(1)}`);
          setSummary("");
        }
      }
    } catch {
      // API 미연결 — 기본값 사용
    } finally {
      setLoading(false);
    }
  };

  const loadGuidelineToForm = (g: AdminGuidelineResponse) => {
    setEditId(g.id);
    setVersionLabel(g.versionLabel);
    setSummary(g.summary || "");
    setLede(g.lede || "");
    setOks(parseOr(g.oksJson, DEFAULT_OKS));
    setNos(parseOr(g.nosJson, DEFAULT_NOS));
    setRules(parseOr(g.rulesJson, DEFAULT_RULES));
    setParams(parseOr(g.paramsJson, DEFAULT_PARAMS));
    setWhen(g.enforceType === "SCHEDULED" ? "예약" : "즉시");
  };

  const handleSave = async () => {
    if (saving) return;
    setSaving(true);
    try {
      const data = {
        versionLabel,
        summary,
        lede,
        oks,
        nos,
        rules,
        params,
        enforceType: when === "예약" ? "SCHEDULED" : "IMMEDIATE",
      };
      if (editId) {
        await updateGuideline(editId, data);
      } else {
        const created = await createGuideline(data);
        setEditId(created.id);
      }
      setLastSaved(new Date().toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" }));
      flash("임시저장되었습니다.", "green");
      loadVersions();
    } catch {
      flash("저장에 실패했습니다.", "red");
    } finally {
      setSaving(false);
    }
  };

  const handlePublish = async () => {
    if (!editId || saving) return;
    setSaving(true);
    try {
      // 먼저 저장
      await updateGuideline(editId, {
        versionLabel, summary, lede, oks, nos, rules, params,
        enforceType: when === "예약" ? "SCHEDULED" : "IMMEDIATE",
      });
      await publishGuideline(editId);
      flash("가이드라인이 게시되었습니다.", "green");
      loadVersions();
    } catch {
      flash("게시에 실패했습니다.", "red");
    } finally {
      setSaving(false);
    }
  };

  // 룰 조작
  const upRule = (i: number, k: keyof GuidelineRule, v: string | number) =>
    setRules((p) => p.map((r, j) => j === i ? { ...r, [k]: v } : r));
  const moveRule = (i: number, dir: -1 | 1) => {
    const ni = i + dir;
    if (ni < 0 || ni >= rules.length) return;
    setRules((p) => {
      const next = [...p];
      [next[i], next[ni]] = [next[ni], next[i]];
      return next;
    });
  };

  // 변경된 파라미터
  const changed = PARAM_DEFS.filter((p) => params[p.k] !== p.def);

  // 버전 상태 표시
  const publishedVersion = versions.find((v) => v.status === "PUBLISHED");

  if (loading) return (
    <AdminShell active="reports" breadcrumb="콘텐츠 관리" title="커뮤니티 가이드라인" icon={BookOpen} desc="커뮤니티 운영 정책 문서 관리">
      <div className="age-loading">불러오는 중...</div>
    </AdminShell>
  );

  return (
    <AdminShell
      active="terms"
      breadcrumb="약관 관리"
      title="커뮤니티 가이드라인 수정"
      icon={BookOpen}
      desc="운영 문서 — 개정 시 시행 7일 전 공지가 자동 생성됩니다"
      actions={
        <a className="adm__actions-btn" href="/community" target="_blank" rel="noopener noreferrer" style={{
          display: "inline-flex", alignItems: "center", gap: 6, padding: "7px 14px",
          fontSize: 12.5, fontWeight: 600, border: "1px solid #e2e8f0", borderRadius: 7,
          background: "#fff", color: "#475569", textDecoration: "none", whiteSpace: "nowrap",
        }}><Eye style={{ width: 13, height: 13 }} />현재 게시본 보기</a>
      }
    >
      <div className="age-form">
        {/* ── 메인 패널 ── */}
        <div className="age-main">
          <section className="age-panel">
            <div className="age-panel__h">
              <span>커뮤니티 가이드라인 — {versionLabel} {editId ? "작성 중" : "새 버전"}</span>
              {publishedVersion && (
                <span className="sub">기준: {publishedVersion.versionLabel} (게시중)</span>
              )}
            </div>

            {/* 개정 요약 */}
            <div className="age-field">
              <div className="age-flabel">개정 요약<span className="opt">— 공지사항 고지에 그대로 사용됩니다</span></div>
              <input className="age-input" value={summary} onChange={(e) => setSummary(e.target.value)}
                placeholder="예: 블라인드 기준 완화 (3건 → 5건)" />
            </div>

            {/* 머리말 */}
            <div className="age-field">
              <div className="age-flabel">머리말<span className="opt">— 운영 철학을 설명하는 도입부</span></div>
              <textarea className="age-textarea" value={lede} onChange={(e) => setLede(e.target.value)} />
            </div>

            {/* 허용·금지 예시 */}
            <div className="age-field">
              <div className="age-flabel">허용·금지 예시<span className="opt">— "이런 글, 써도 괜찮아요" 섹션</span></div>
              <div className="age-ex">
                <div className="age-ex__col">
                  <div className="age-ex__h ok"><Check />괜찮아요<span className="c">{oks.length}</span></div>
                  {oks.map((x, i) => (
                    <div className="age-ex__row" key={i}>
                      <input value={x} onChange={(e) => setOks((p) => p.map((v, j) => j === i ? e.target.value : v))} />
                      <button aria-label="삭제" onClick={() => setOks((p) => p.filter((_, j) => j !== i))}><X /></button>
                    </div>
                  ))}
                  <button className="age-ex__add" onClick={() => setOks((p) => [...p, ""])}>+ 예시 추가</button>
                </div>
                <div className="age-ex__col">
                  <div className="age-ex__h no"><X />안 돼요<span className="c">{nos.length}</span></div>
                  {nos.map((x, i) => (
                    <div className="age-ex__row" key={i}>
                      <input value={x} onChange={(e) => setNos((p) => p.map((v, j) => j === i ? e.target.value : v))} />
                      <button aria-label="삭제" onClick={() => setNos((p) => p.filter((_, j) => j !== i))}><X /></button>
                    </div>
                  ))}
                  <button className="age-ex__add" onClick={() => setNos((p) => [...p, ""])}>+ 예시 추가</button>
                </div>
              </div>
            </div>

            {/* 금지 항목 */}
            <div className="age-field">
              <div className="age-flabel">금지 항목<span className="opt">— 항목마다 제재 수위를 지정하세요</span></div>
              {rules.map((r, i) => (
                <div className="age-rule" key={i}>
                  <div className="age-rule__h">
                    <span className="age-rule__no">{String(i + 1).padStart(2, "0")}</span>
                    <input className="age-rule__t" value={r.t} onChange={(e) => upRule(i, "t", e.target.value)} placeholder="항목 제목" />
                    <select className="age-rule__sel" value={r.s} onChange={(e) => upRule(i, "s", Number(e.target.value))}>
                      {SANCTIONS.map((s, j) => <option key={j} value={j}>{s}</option>)}
                    </select>
                    <div className="age-rule__tools">
                      <button aria-label="위로" onClick={() => moveRule(i, -1)}><ChevronUp /></button>
                      <button aria-label="아래로" onClick={() => moveRule(i, 1)}><ChevronDown /></button>
                      <button aria-label="삭제" onClick={() => setRules((p) => p.filter((_, j) => j !== i))}><X /></button>
                    </div>
                  </div>
                  <textarea className="age-rule__b" value={r.b} onChange={(e) => upRule(i, "b", e.target.value)}
                    placeholder="무엇이 금지되는지, 무엇은 괜찮은지 경계를 함께 적어주세요" />
                </div>
              ))}
              <button className="age-add" onClick={() => setRules((p) => [...p, { t: "", s: 0, b: "" }])}>
                <Plus />금지 항목 추가
              </button>
              <div className="age-hint">금지 항목이 늘어날수록 커뮤니티는 위축돼요. 기존 항목으로 처리할 수 없는 피해인지 먼저 확인하세요.</div>
            </div>

            {/* 운영 파라미터 */}
            <div className="age-field">
              <div className="age-flabel">운영 파라미터<span className="opt">— 게시본의 신고 처리·제재 단계 수치에 반영됩니다</span></div>
              <div className="age-params">
                {PARAM_DEFS.map((p) => (
                  <div className={"age-param" + (params[p.k] !== p.def ? " changed" : "")} key={p.k}>
                    <div className="age-param__l">{p.l}</div>
                    <div className="age-param__row">
                      <input type="number" min="1" value={params[p.k]}
                        onChange={(e) => setParams((s) => ({ ...s, [p.k]: Number(e.target.value) || 1 }))} />
                      <span className="u">{p.u}</span>
                    </div>
                    <div className="age-param__d">{p.d}</div>
                  </div>
                ))}
              </div>
            </div>

            {/* 시행 시점 */}
            <div className="age-field">
              <div className="age-flabel">시행 시점</div>
              <div className="age-choices">
                <div className={"age-choice" + (when === "즉시" ? " on" : "")} onClick={() => setWhen("즉시")}>
                  <div className="t">즉시 시행</div><div className="s">게시와 동시에 효력</div>
                </div>
                <div className={"age-choice" + (when === "예약" ? " on" : "")} onClick={() => setWhen("예약")}>
                  <div className="t">예약 시행</div><div className="s">7일 후 자동 게시</div>
                </div>
              </div>
              <div className="age-hint">개정 가이드라인은 시행 7일 전 공지사항으로 안내돼요. 제재가 강해지는 변경은 시행 전 위반 글에 소급 적용되지 않습니다.</div>
            </div>
          </section>
        </div>

        {/* ── 우측 레일 ── */}
        <aside className="age-rail">
          {/* 변경 수치 diff */}
          <section className="age-panel">
            <div className="age-panel__h">
              <span>이번 개정에서 바뀐 수치</span>
              <span className="sub">{changed.length}건</span>
            </div>
            <div className="age-diff" style={{ paddingTop: 10 }}>
              {changed.length === 0
                ? <div className="age-diff__empty">아직 변경된 파라미터가 없어요.</div>
                : changed.map((p) => (
                  <div className="age-diff__row" key={p.k}>
                    <span className="age-diff__k">{p.l}</span>
                    <span className="age-diff__v"><span className="old">{p.def}</span><span className="new">{params[p.k]}</span></span>
                  </div>
                ))}
            </div>
          </section>

          {/* 버전 이력 */}
          <section className="age-panel">
            <div className="age-panel__h"><span>버전 이력</span></div>
            <div className="age-ver">
              {editId && (
                <div className="age-ver__item">
                  <div className="age-ver__info">
                    <div className="age-ver__title"><b>{versionLabel}</b>{summary || "작성 중"}</div>
                    <div className="age-ver__date">작성 중</div>
                  </div>
                  <span className="age-ver__badge draft">작성 중</span>
                </div>
              )}
              {versions.filter((v) => v.status !== "DRAFT").map((v) => (
                <div className="age-ver__item" key={v.id}>
                  <div className="age-ver__info">
                    <div className="age-ver__title"><b>{v.versionLabel}</b>{v.summary || ""}</div>
                    <div className="age-ver__date">
                      {v.status === "PUBLISHED" ? "시행" : "종료"}{" "}
                      {v.publishedAt ? v.publishedAt.slice(0, 10).replace(/-/g, ".") : ""}
                    </div>
                  </div>
                  <span className={`age-ver__badge ${v.status === "PUBLISHED" ? "live" : "archived"}`}>
                    {v.status === "PUBLISHED" ? "게시중" : "종료"}
                  </span>
                </div>
              ))}
            </div>
          </section>

          {/* 체크리스트 */}
          <section className="age-panel">
            <div className="age-panel__h"><span>개정 체크리스트</span></div>
            <div className="age-note">
              파라미터 변경은 게시 즉시 <b>신고 처리 시스템에 자동 반영</b>돼요. 블라인드 기준을 낮추기 전에
              최근 30일 허위 신고 비율을 <b>콘텐츠 관리</b>에서 확인하세요.
            </div>
          </section>
        </aside>
      </div>

      {/* ── 스티키 푸터 ── */}
      <div className="age-foot">
        <span className="age-foot__draft">
          {lastSaved && <><Check />임시저장됨 · {lastSaved}</>}
        </span>
        <div className="age-foot__right">
          <button className="age-btn" onClick={handleSave} disabled={saving}>
            <Save />{saving ? "저장 중..." : "임시저장"}
          </button>
          <button className="age-btn age-btn--ink" onClick={handlePublish} disabled={saving || !editId}>
            <CalendarClock />{when === "예약" ? "게시 예약" : "게시"}
          </button>
        </div>
      </div>

      {/* 토스트 */}
      {toast && <div className={`age-toast age-toast--${toast.tone}`}>{toast.msg}</div>}
    </AdminShell>
  );
}
