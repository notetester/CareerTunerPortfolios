import { useEffect, useState, useCallback } from "react";
import {
  Bot, Sparkles, SearchX, Info, RefreshCw, Check, Tag, LoaderCircle, MessageSquare,
} from "lucide-react";
import AdminShell from "../../../components/AdminShell";
import { ApiError } from "@/app/lib/api";
import * as api from "../api/adminUnansweredApi";
import * as panelApi from "../api/adminChatbotPanelApi";
import type { UnansweredCluster, UnansweredStatus } from "../types/adminUnanswered";
import type { ChatbotMetrics, ReferencePage } from "../types/adminChatbotPanel";
import MetricBand from "../components/MetricBand";
import ThresholdStrip from "../components/ThresholdStrip";
import ReferenceTable from "../components/ReferenceTable";
import ConversationDrill from "../components/ConversationDrill";
import { PERIOD_TABS, rangeOf, type PeriodKey } from "../components/period";
import { useAdminDomainAuthorization } from "../../../auth/useAdminAuthorization";
import "./admin-ai-support.css";

const STATUS_TABS: { key: UnansweredStatus; label: string }[] = [
  { key: "NEW", label: "신규" },
  { key: "REVIEWED", label: "검토중" },
  { key: "CONVERTED", label: "처리완료" },
  { key: "DISMISSED", label: "무시" },
];

const STATUS_TAG: Record<string, [string, string]> = {
  NEW: ["ais-tag--new", "신규"],
  REVIEWED: ["ais-tag--rev", "검토중"],
  CONVERTED: ["ais-tag--done", "처리완료"],
  DISMISSED: ["ais-tag--dis", "무시"],
};

const CAT_KR = ["일반", "계정", "결제", "AI기능", "면접"] as const;
const DB_TO_KR: Record<string, string> = {
  general: "일반", account: "계정", payment: "결제", ai_feature: "AI기능", interview: "면접",
  GENERAL: "일반", ACCOUNT: "계정", PAYMENT: "결제", AI_FEATURE: "AI기능", INTERVIEW: "면접",
};
const KR_TO_DB: Record<string, string> = {
  "일반": "general", "계정": "account", "결제": "payment", "AI기능": "ai_feature", "면접": "interview",
};

const REF_PAGE_SIZE = 10;

function relativeTime(iso: string): string {
  if (!iso) return "";
  const then = new Date(iso).getTime();
  if (Number.isNaN(then)) return "";
  const diff = Date.now() - then;
  const m = Math.floor(diff / 60000);
  if (m < 1) return "방금 전";
  if (m < 60) return `${m}분 전`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h}시간 전`;
  return `${Math.floor(h / 24)}일 전`;
}

const simText = (s: number | null) => (s == null ? "—" : s.toFixed(2));

type DraftState = "idle" | "loading" | "ready" | "error";
type Toast = { msg: string; tone: "ok" | "err" | "info" };
type MainTab = "gap" | "ref";

export default function AdminAiSupport() {
  const aiAuthorization = useAdminDomainAuthorization("AI");
  const contentAuthorization = useAdminDomainAuthorization("CONTENT");
  const [status, setStatus] = useState<UnansweredStatus>("NEW");
  const [clusters, setClusters] = useState<UnansweredCluster[]>([]);
  const [loading, setLoading] = useState(false);
  const [sel, setSel] = useState<number | null>(null);

  const [draftState, setDraftState] = useState<DraftState>("idle");
  const [draftEdit, setDraftEdit] = useState<{ q: string; a: string; cat: string } | null>(null);
  const [saving, setSaving] = useState(false);
  const [embedBusy, setEmbedBusy] = useState(false);
  const [toast, setToast] = useState<Toast | null>(null);

  // 3단계-2 상태
  const [period, setPeriod] = useState<PeriodKey>("week");
  const [metrics, setMetrics] = useState<ChatbotMetrics | null>(null);
  const [tab, setTab] = useState<MainTab>("gap");
  // 임계값 슬라이더(정수 50~95, 표시 0.{v}) — 미리보기 + 상세 막대 임계선 동기화.
  const [thr, setThr] = useState(50);
  const [drillId, setDrillId] = useState<number | null>(null);

  // 참조 대화 탭
  const [refData, setRefData] = useState<ReferencePage | null>(null);
  const [refPage, setRefPage] = useState(0);
  const [refLoading, setRefLoading] = useState(false);
  const [refError, setRefError] = useState<string | null>(null);

  const flash = useCallback((msg: string, tone: Toast["tone"]) => {
    setToast({ msg, tone });
    window.setTimeout(() => setToast(null), 2600);
  }, []);

  const load = useCallback(async (st: UnansweredStatus, keepSel = false) => {
    setLoading(true);
    try {
      const list = await api.getUnanswered(st);
      setClusters(list);
      if (!keepSel) {
        setSel(list.length ? list[0].id : null);
        setDraftState("idle");
        setDraftEdit(null);
      }
    } catch (e) {
      setClusters([]);
      flash(e instanceof ApiError ? e.message : "목록을 불러오지 못했습니다.", "err");
    } finally {
      setLoading(false);
    }
  }, [flash]);

  useEffect(() => { void load(status); }, [status, load]);

  // 메트릭 — 기간 변경 시 재조회. 실패해도 화면은 유지(카드 null = "수집 중").
  useEffect(() => {
    let alive = true;
    const { from, to } = rangeOf(period);
    panelApi.getMetrics(from, to)
      .then((m) => { if (alive) setMetrics(m); })
      .catch(() => { if (alive) setMetrics(null); });
    return () => { alive = false; };
  }, [period]);

  // 참조 대화 — ref 탭 활성 + 기간/페이지 변경 시 조회.
  useEffect(() => {
    if (tab !== "ref") return;
    let alive = true;
    const { from, to } = rangeOf(period);
    setRefLoading(true);
    setRefError(null);
    panelApi.getReferences(from, to, refPage, REF_PAGE_SIZE)
      .then((d) => { if (alive) setRefData(d); })
      .catch((e) => { if (alive) setRefError(e instanceof ApiError ? e.message : "참조 대화를 불러오지 못했습니다."); })
      .finally(() => { if (alive) setRefLoading(false); });
    return () => { alive = false; };
  }, [tab, period, refPage]);

  // 기간이 바뀌면 참조 페이지를 처음으로.
  useEffect(() => { setRefPage(0); }, [period]);

  const cluster = clusters.find((c) => c.id === sel) ?? null;
  const gapCount = clusters.length;
  const refTotal = refData?.total ?? null;

  function pick(c: UnansweredCluster) {
    setSel(c.id);
    setDraftState("idle");
    setDraftEdit(null);
  }

  async function genDraft() {
    if (!aiAuthorization.canCreate || !cluster) return;
    setDraftState("loading");
    try {
      const d = await api.generateDraft(cluster.id);
      setDraftEdit({
        q: d.question,
        a: d.answer,
        cat: DB_TO_KR[cluster.category ?? ""] ?? "일반",
      });
      setDraftState("ready");
    } catch (e) {
      setDraftState("error");
      flash(e instanceof ApiError ? e.message : "초안 생성에 실패했습니다.", "err");
    }
  }

  async function registerFaq() {
    if (!contentAuthorization.canCreate || !cluster || !draftEdit) return;
    if (draftEdit.q.trim().length < 5 || draftEdit.a.trim().length < 10) {
      flash("질문·답변을 조금 더 작성해 주세요.", "err");
      return;
    }
    setSaving(true);
    try {
      await api.convertToFaq(cluster.id, {
        category: KR_TO_DB[draftEdit.cat] ?? "general",
        question: draftEdit.q.trim(),
        answer: draftEdit.a.trim(),
      });
      flash("FAQ로 등록했습니다. 임베딩 갱신 후 챗봇이 답합니다.", "ok");
      await load(status);
    } catch (e) {
      flash(e instanceof ApiError ? e.message : "FAQ 등록에 실패했습니다.", "err");
    } finally {
      setSaving(false);
    }
  }

  async function changeStatus(next: "REVIEWED" | "DISMISSED") {
    if (!aiAuthorization.canUpdate || !cluster) return;
    try {
      await api.updateStatus(cluster.id, next);
      flash(next === "REVIEWED" ? "검토중으로 표시했습니다." : "무시 처리했습니다.", "info");
      await load(status);
    } catch (e) {
      flash(e instanceof ApiError ? e.message : "상태 변경에 실패했습니다.", "err");
    }
  }

  async function refreshEmbeddings() {
    if (!contentAuthorization.canUpdate) return;
    setEmbedBusy(true);
    try {
      const r = await api.embedAllFaqs(false);
      flash(`임베딩 갱신 완료 (${r.embeddedCount}건)`, "ok");
    } catch (e) {
      flash(e instanceof ApiError ? e.message : "임베딩 갱신에 실패했습니다.", "err");
    } finally {
      setEmbedBusy(false);
    }
  }

  const actions = (
    <>
      <div className="ais-period">
        {PERIOD_TABS.map((p) => (
          <button key={p.key} className={period === p.key ? "on" : ""} onClick={() => setPeriod(p.key)}>
            {p.label}
          </button>
        ))}
      </div>
      {contentAuthorization.canUpdate && <button className="ais-btn ais-btn--ink" onClick={refreshEmbeddings} disabled={embedBusy}>
        <RefreshCw className={embedBusy ? "ais-spin" : ""} />
        {embedBusy ? "갱신 중…" : "임베딩 갱신"}
      </button>}
    </>
  );

  const editable = cluster != null && (cluster.status === "NEW" || cluster.status === "REVIEWED");
  // 상세 "왜 못 답했나" 막대의 임계선 = 슬라이더 값(미리보기). 0~1.
  const thrFraction = thr / 100;

  return (
    <AdminShell
      active="ai-support"
      breadcrumb="AI 상담 운영"
      title="AI 상담 운영"
      icon={Bot}
      desc="챗봇이 FAQ를 참조해 답한 대화를 검토하고, 답하지 못한 질문을 FAQ로 전환합니다."
      actions={actions}
    >
      <div className="ais">
        {/* 메트릭 밴드 */}
        <MetricBand metrics={metrics} />

        {/* RAG 설정 스트립 — 임계값 미리보기 */}
        <ThresholdStrip value={thr} onChange={setThr} />

        {/* 메인 탭 */}
        <div className="ais-seg ais-tabs">
          <button className={tab === "gap" ? "on" : ""} onClick={() => setTab("gap")}>
            FAQ 공백 {gapCount}
          </button>
          <button className={tab === "ref" ? "on" : ""} onClick={() => setTab("ref")}>
            참조 대화{refTotal != null ? ` ${refTotal.toLocaleString("ko-KR")}` : ""}
          </button>
        </div>

        {tab === "gap" ? (
          <>
            {/* 상태 필터 */}
            <div className="ais-seg">
              {STATUS_TABS.map((t) => (
                <button key={t.key} className={status === t.key ? "on" : ""} onClick={() => setStatus(t.key)}>
                  {t.label}
                </button>
              ))}
            </div>

            <div className="ais-md">
              {/* 리스트 */}
              <section className="ais-panel">
                <div className="ais-panel__h">
                  <span className="ais-panel__t">답하지 못한 질문 군집</span>
                  <span className="ais-panel__s">{loading ? "불러오는 중…" : `${clusters.length}개 · 빈도순`}</span>
                </div>
                <div className="ais-list">
                  {!loading && clusters.length === 0 && (
                    <div className="ais-empty">해당 상태의 질문 군집이 없습니다.</div>
                  )}
                  {clusters.map((c) => {
                    const [tc, tl] = STATUS_TAG[c.status] ?? ["ais-tag--new", c.status];
                    return (
                      <button key={c.id} className={`ais-row${sel === c.id ? " sel" : ""}`} onClick={() => pick(c)}>
                        <div className="ais-row__top">
                          <span className="ais-row__q">{c.question}</span>
                          <span className="ais-cnt num">{c.frequency}<small>건</small></span>
                        </div>
                        <div className="ais-row__meta">
                          <span className={`ais-tag ${tc}`}>{tl}</span>
                          <span className="ais-sim">최고 유사도 <b className="num">{simText(c.topSimilarity)}</b></span>
                          <span style={{ marginLeft: "auto" }}>{relativeTime(c.lastSeen)}</span>
                        </div>
                      </button>
                    );
                  })}
                </div>
              </section>

              {/* 상세 */}
              <section className="ais-panel ais-det">
                {!cluster ? (
                  <div className="ais-empty">왼쪽에서 질문 군집을 선택하세요.</div>
                ) : (
                  <>
                    <div className="ais-det__h">
                      {cluster.category && <span className="ais-det__cat">{DB_TO_KR[cluster.category] ?? cluster.category}</span>}
                      <div className="ais-det__q">{cluster.question}</div>
                      <div className="ais-det__sub">
                        <span><b className="num">{cluster.frequency}건</b>의 유사 질문</span>
                        <span>최근 <b>{relativeTime(cluster.lastSeen)}</b></span>
                        <span>대표 ID <b className="num">#{cluster.id}</b></span>
                      </div>
                    </div>

                    {/* 왜 답하지 못했나 */}
                    <div className="ais-sec">
                      <div className="ais-sec__t">왜 답하지 못했나</div>
                      <div className="ais-why">
                        <SearchX />
                        <div className="ais-why__b">
                          {cluster.bestFaqQuestion ? (
                            (cluster.topSimilarity ?? 0) < thrFraction ? (
                              <>가장 가까운 FAQ <b>“{cluster.bestFaqQuestion}”</b>와의 유사도가{" "}
                                <b className="num">{simText(cluster.topSimilarity)}</b>로, 임계값{" "}
                                <b className="num">{thrFraction.toFixed(2)}</b>에 미치지 못해 답변을 보류했습니다.</>
                            ) : (
                              /* 임계값은 넘었지만 라우터(FAQ·상담·면접준비 3신호 비교)가 FAQ 즉답 대신 다른 경로를 고른 경우 */
                              <>가장 가까운 FAQ <b>“{cluster.bestFaqQuestion}”</b>와의 유사도가{" "}
                                <b className="num">{simText(cluster.topSimilarity)}</b>로 임계값을 넘었지만, 질문 성격상
                                FAQ 즉답 대신 상담 경로로 응답되어 FAQ 답변으로 이어지지 않았습니다.</>
                            )
                          ) : (
                            <>참조할 만큼 가까운 FAQ가 없어 답변을 보류했습니다.</>
                          )}
                        </div>
                      </div>
                      <div className="ais-bar">
                        <span className="ais-bar__fill" style={{ width: `${Math.max(0, Math.min(1, cluster.topSimilarity ?? 0)) * 100}%` }} />
                        <span className="ais-bar__thr" style={{ left: `${thr}%` }} />
                      </div>
                      <div className="ais-barx"><span>0.00</span><span>임계값 {thrFraction.toFixed(2)}</span><span>1.00</span></div>
                    </div>

                    {/* 묶인 질문 */}
                    <div className="ais-sec">
                      <div className="ais-sec__t">묶인 사용자 질문</div>
                      <div className="ais-utt">
                        {cluster.variants.map((v, i) => (
                          <div className="ais-utt__row" key={i}>
                            <span className="ais-utt__t">“{v.question}”</span>
                            <span className="ais-utt__n num">{v.count}건</span>
                          </div>
                        ))}
                      </div>
                    </div>

                    {/* 폴백 메시지 + 발생 대화 드릴 */}
                    <div className="ais-sec">
                      <div className="ais-sec__t">챗봇이 보낸 응답 (폴백)</div>
                      <div className="ais-bub">
                        <span className="ais-bub__av"><Bot /></span>
                        <div>
                          <div className="ais-bub__body">관련 안내를 찾지 못했어요. 1:1 문의를 남겨주시면 정확히 도와드릴게요.</div>
                          <div className="ais-bub__tag"><Info />FAQ 미적중 시 자동 폴백 메시지</div>
                        </div>
                      </div>
                      <div style={{ marginTop: 12 }}>
                        <button className="ais-drill-btn" onClick={() => setDrillId(cluster.id)}>
                          <MessageSquare />이 질문이 나온 대화 보기
                        </button>
                      </div>
                    </div>

                    {/* CTA / 초안 */}
                    {editable ? (
                      <div className="ais-cta">
                        {draftState === "idle" || draftState === "error" ? (
                          <>
                            <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                              {aiAuthorization.canCreate && <button className="ais-btn ais-btn--ink" onClick={genDraft}>
                                <Sparkles />이 질문으로 FAQ 초안 작성
                              </button>}
                              {aiAuthorization.canUpdate && cluster.status === "NEW" && (
                                <button className="ais-btn" onClick={() => changeStatus("REVIEWED")}>검토중으로</button>
                              )}
                              {aiAuthorization.canUpdate && <button className="ais-btn" onClick={() => changeStatus("DISMISSED")}>무시</button>}
                            </div>
                            <div className="ais-cta__hint">묶인 질문과 참고 FAQ를 바탕으로 답변 초안을 생성합니다. 검토 후 등록하세요.</div>
                          </>
                        ) : draftState === "loading" ? (
                          <div className="ais-genwait">
                            <LoaderCircle className="ais-spin" />
                            <span>{cluster.frequency}건의 질문을 분석해 답변 초안을 작성하는 중…</span>
                          </div>
                        ) : draftEdit ? (
                          <>
                            <div className="ais-draft">
                              <div className="ais-draft__h">
                                <Sparkles />AI가 작성한 FAQ 초안<span className="by">검토 후 수정 가능</span>
                              </div>
                              <div className="ais-draft__body">
                                <p className="ais-dl"><Tag style={{ width: 12, height: 12, verticalAlign: "-1px" }} /> 카테고리</p>
                                {contentAuthorization.canCreate ? (
                                  <>
                                    <select
                                      className="ais-in"
                                      value={draftEdit.cat}
                                      onChange={(e) => setDraftEdit({ ...draftEdit, cat: e.target.value })}
                                      style={{ maxWidth: 220 }}
                                    >
                                      {CAT_KR.map((k) => <option key={k} value={k}>{k}</option>)}
                                    </select>
                                    <p className="ais-dl" style={{ marginTop: 14 }}>질문</p>
                                    <input
                                      className="ais-in"
                                      value={draftEdit.q}
                                      onChange={(e) => setDraftEdit({ ...draftEdit, q: e.target.value })}
                                    />
                                    <p className="ais-dl" style={{ marginTop: 14 }}>답변</p>
                                    <textarea
                                      className="ais-in ais-ta"
                                      value={draftEdit.a}
                                      onChange={(e) => setDraftEdit({ ...draftEdit, a: e.target.value })}
                                    />
                                  </>
                                ) : (
                                  <>
                                    <p className="ais-in">{draftEdit.cat}</p>
                                    <p className="ais-dl" style={{ marginTop: 14 }}>질문</p>
                                    <p className="ais-in">{draftEdit.q}</p>
                                    <p className="ais-dl" style={{ marginTop: 14 }}>답변</p>
                                    <p className="ais-in ais-ta">{draftEdit.a}</p>
                                  </>
                                )}
                              </div>
                              <div className="ais-draft__foot">
                                {aiAuthorization.canCreate && <button className="ais-btn" onClick={genDraft} disabled={saving}>
                                  <RefreshCw />다시 생성
                                </button>}
                                {contentAuthorization.canCreate && <div style={{ marginLeft: "auto" }}>
                                  <button className="ais-btn ais-btn--ink" onClick={registerFaq} disabled={saving}>
                                    <Check />{saving ? "등록 중…" : "FAQ로 등록"}
                                  </button>
                                </div>}
                              </div>
                            </div>
                            <div className="ais-cta__hint" style={{ marginTop: 10 }}>
                              등록하면 FAQ 관리에 추가되고, 다음 임베딩 갱신부터 챗봇이 이 질문에 답합니다.
                            </div>
                          </>
                        ) : null}
                      </div>
                    ) : (
                      <div className="ais-cta">
                        <div className="ais-cta__hint">
                          {cluster.status === "CONVERTED" ? "이미 FAQ로 전환된 군집입니다." : "무시 처리된 군집입니다."}
                        </div>
                      </div>
                    )}
                  </>
                )}
              </section>
            </div>
          </>
        ) : (
          <ReferenceTable
            data={refData}
            loading={refLoading}
            error={refError}
            page={refPage}
            size={REF_PAGE_SIZE}
            onPage={setRefPage}
          />
        )}

        {toast && <div className={`ais-toast ais-toast--${toast.tone}`}>{toast.msg}</div>}
      </div>

      {drillId != null && (
        <ConversationDrill clusterId={drillId} onClose={() => setDrillId(null)} />
      )}
    </AdminShell>
  );
}
