import { useState, useEffect, useCallback } from "react";
import {
  PlugZap, ScanText, Inbox, ChevronRight, CheckCircle2, AlertCircle, EyeOff, RefreshCw,
} from "lucide-react";
import * as moderationApi from "../api/moderationApi";
import type { ModerationSettingData, ModerationTestResult } from "../api/moderationApi";
import type { ModerationReviewAction, ModerationReviewQueueItem } from "../types/moderation";
import "./moderation-settings.css";
import { useAdminAuthorization } from "@/admin/auth/useAdminAuthorization";

/* ── 프리셋 정의 ── */
const PRESETS = [
  {
    k: "STRICT", name: "엄격", th: 0.60,
    desc: "조금이라도 의심되면 숨겨요. 민감한 시기(채용 시즌\u00b7이슈 발생)에 권장.",
    guide: "확신도 0.60 이상이면 자동 숨김. 경계 표현\u00b7풍자도 숨겨질 수 있어 검토 부담이 커져요.",
  },
  {
    k: "NORMAL", name: "보통", th: 0.80,
    desc: "명백한 위반만 자동으로 숨기고, 애매한 글은 사람이 봐요. 기본값.",
    guide: "확신도 0.80 이상만 자동 숨김. 거친 푸념\u00b7날 선 비판은 통과 \u2014 가이드라인의 '금지는 좁게' 원칙과 맞아요.",
  },
  {
    k: "LENIENT", name: "관대", th: 0.90,
    desc: "AI가 거의 확실할 때만 개입해요. 표현 위축을 최소화.",
    guide: "확신도 0.90 이상만 자동 숨김. 누락이 늘 수 있으니 신고 처리 인력이 충분할 때 권장.",
  },
];

const CAT_LABELS: Record<string, { text: string; cls: string }> = {
  abuse:  { text: "욕설\u00b7공격", cls: "md-cat--abuse" },
  spam:   { text: "스팸\u00b7도배", cls: "md-cat--spam" },
  ad:     { text: "광고",         cls: "md-cat--ad" },
  normal: { text: "정상",         cls: "md-cat--normal" },
};

/* ── Confidence 바 ── */
function MdBar({ conf, th }: { conf: number; th: number }) {
  const color = conf >= th ? "var(--av-red)" : "#16a34a";
  return (
    <div>
      <div className="md-bar">
        <div className="md-bar__track" />
        <span className="md-bar__th num" data-v={th.toFixed(2)} style={{ left: `${th * 100}%` }} />
        <span className="md-bar__marker num" data-v={conf.toFixed(2)} style={{ left: `${conf * 100}%`, background: color }} />
      </div>
      <div className="md-bar__scale num"><span>0</span><span>0.5</span><span>1.0</span></div>
    </div>
  );
}

/* ── 결과 카드 ── */
function MdResult({ r, th, label, prev }: {
  r: ModerationTestResult; th: number; label?: string; prev?: boolean;
}) {
  const hidden = r.toxic && r.confidence >= th;
  const cat = CAT_LABELS[r.category] ?? { text: r.category, cls: "" };
  return (
    <div className={`md-res${prev ? " prev" : ""}`}>
      <div className="md-res__row">
        <span className={`md-cat ${cat.cls}`}>{cat.text}</span>
        {label && <span className="av-st av-st--off" style={{ fontSize: 11 }}>{label}</span>}
        <span className="md-res__ms num">{r.elapsedMs.toLocaleString()}ms &middot; 임계값 {th.toFixed(2)}</span>
      </div>
      <div className="md-res__verdict">
        <span>toxic <b>{r.toxic ? "예" : "아니오"}</b></span>
        <span>처리 <b className={hidden ? "hide-yes" : "hide-no"}>{hidden ? "자동 숨김" : "게시 유지"}</b></span>
      </div>
      <MdBar conf={r.confidence} th={th} />
    </div>
  );
}

/* ── 숫자 입력(포커스 아웃 시 클램프 후 저장) ── */
function NumBox({ label, value, min, max, hint, onCommit, disabled = false }: {
  label: string; value: number; min: number; max: number; hint: string;
  onCommit: (v: number) => void;
  disabled?: boolean;
}) {
  const [v, setV] = useState(value);
  useEffect(() => { setV(value); }, [value]);
  return (
    <label style={{ display: "flex", flexDirection: "column", gap: 6 }}>
      <span className="av-flabel">{label}</span>
      <input
        type="number" min={min} max={max}
        disabled={disabled}
        className="av-input" style={{ width: 130 }}
        value={v}
        onChange={(e) => setV(Number(e.target.value))}
        onBlur={() => {
          const c = Math.min(max, Math.max(min, Number(v) || min));
          if (c !== value) onCommit(c);
          else setV(value);
        }}
      />
      <span className="av-hint">{hint}</span>
    </label>
  );
}

/* ══════════════════════════════════════════════════════════
   메인 패널
   ══════════════════════════════════════════════════════════ */
export default function ModerationSettingsPanel({ flash }: { flash: (msg: string) => void }) {
  const authorization = useAdminAuthorization();
  const canConfigure = authorization.can("AI_UPDATE");
  const canTest = authorization.can("AI_CREATE");
  const canKeepReview = authorization.can("AI_UPDATE");
  const canHideReview = canKeepReview && authorization.can("CONTENT_UPDATE");
  /* 설정 상태 */
  const [mode, setMode] = useState("NORMAL");
  const [th, setTh] = useState(0.80);
  const [custom, setCustom] = useState(false);
  const [changedAt, setChangedAt] = useState("");
  const [serverDown, setServerDown] = useState(false);

  /* 사용자 제재 설정 (게시글 숨김과 별개 임계) */
  const [sanction, setSanction] = useState(3);
  const [blockDays, setBlockDays] = useState(7);

  /* 작성 rate-limit(도배 방지) + 신고 누적 블러 임계 */
  const [reportBlur, setReportBlur] = useState(3);
  const [postWin, setPostWin] = useState(60);
  const [postMax, setPostMax] = useState(10);
  const [commentWin, setCommentWin] = useState(60);
  const [commentMax, setCommentMax] = useState(20);
  const [inquiryWin, setInquiryWin] = useState(600);
  const [inquiryMax, setInquiryMax] = useState(5);

  /* UI 상태 */
  const [advOpen, setAdvOpen] = useState(false);
  const [pending, setPending] = useState<typeof PRESETS[number] | null>(null);
  const [toast, setToast] = useState<{ ok: boolean; msg: string } | null>(null);

  /* 임계 미만 toxic 결과 수동 검토 큐 */
  const [queue, setQueue] = useState<ModerationReviewQueueItem[]>([]);
  const [queuePage, setQueuePage] = useState(1);
  const [queueTotal, setQueueTotal] = useState(0);
  const [queueHasNext, setQueueHasNext] = useState(false);
  const [queueLoading, setQueueLoading] = useState(true);
  const [queueError, setQueueError] = useState("");
  const [queueReloadKey, setQueueReloadKey] = useState(0);
  const [queueActionId, setQueueActionId] = useState<number | null>(null);
  const [reviewPending, setReviewPending] = useState<{
    item: ModerationReviewQueueItem;
    action: ModerationReviewAction;
  } | null>(null);

  /* 테스트 상태 */
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<{ r: ModerationTestResult; th: number } | null>(null);
  const [prevRes, setPrevRes] = useState<{ r: ModerationTestResult; th: number } | null>(null);

  /* 설정 로드 */
  const loadSettings = useCallback(() => {
    moderationApi.getModerationSettings()
      .then((s) => {
        setMode(s.strictness);
        setTh(s.hideThreshold);
        setSanction(s.sanctionThreshold);
        setBlockDays(s.blockDays);
        setReportBlur(s.reportBlurThreshold);
        setPostWin(s.postRateWindowSeconds);
        setPostMax(s.postRateMax);
        setCommentWin(s.commentRateWindowSeconds);
        setCommentMax(s.commentRateMax);
        setInquiryWin(s.inquiryRateWindowSeconds);
        setInquiryMax(s.inquiryRateMax);
        setChangedAt(s.updatedAt);
        setCustom(false);
        setServerDown(false);
      })
      .catch(() => setServerDown(true));
  }, []);

  useEffect(() => { loadSettings(); }, [loadSettings]);

  const loadReviewQueue = useCallback(async () => {
    setQueueLoading(true);
    setQueueError("");
    try {
      const result = await moderationApi.getModerationReviewQueue(queuePage, 5);
      setQueue(result.items);
      setQueueTotal(result.total);
      setQueueHasNext(result.hasNext);
    } catch (error) {
      setQueueError(error instanceof Error ? error.message : "검토 대기 목록을 불러오지 못했습니다.");
    } finally {
      setQueueLoading(false);
    }
  }, [queuePage]);

  useEffect(() => { void loadReviewQueue(); }, [loadReviewQueue, queueReloadKey]);

  /* 토스트 자동 소멸 */
  useEffect(() => {
    if (!toast) return;
    const t = setTimeout(() => setToast(null), 2600);
    return () => clearTimeout(t);
  }, [toast]);

  const cur = PRESETS.find((p) => p.k === mode) ?? PRESETS[1];

  /* 프리셋 적용 */
  const applyPreset = async (p: typeof PRESETS[number]) => {
    if (!canConfigure) return;
    setPending(null);
    try {
      const updated = await moderationApi.updateModerationSettings({
        strictness: p.k,
        hideThreshold: p.th,
      });
      setMode(updated.strictness);
      setTh(updated.hideThreshold);
      setCustom(false);
      setChangedAt(updated.updatedAt);
      setQueuePage(1);
      setQueueReloadKey((key) => key + 1);
      setToast({ ok: true, msg: `'${p.name}' 모드 적용됨 \u2014 임계값 ${p.th.toFixed(2)}` });
    } catch {
      setToast({ ok: false, msg: "저장 실패 \u2014 검열 서버에 연결할 수 없어요. 잠시 후 다시 시도해주세요." });
    }
  };

  /* 고급 임계값 변경 */
  const applyTh = async (v: number) => {
    if (!canConfigure) return;
    setTh(v);
    const isCustom = v !== cur.th;
    setCustom(isCustom);
    try {
      const updated = await moderationApi.updateModerationSettings({ hideThreshold: v });
      setChangedAt(updated.updatedAt);
      setQueuePage(1);
      setQueueReloadKey((key) => key + 1);
    } catch {
      setToast({ ok: false, msg: "임계값 저장 실패" });
    }
  };

  /* 사용자 제재 설정 저장 (누적 임계 / 차단 기간) */
  const applySanction = async (next: { sanctionThreshold?: number; blockDays?: number }) => {
    if (!canConfigure) return;
    try {
      const updated = await moderationApi.updateModerationSettings(next);
      setSanction(updated.sanctionThreshold);
      setBlockDays(updated.blockDays);
      setChangedAt(updated.updatedAt);
      setToast({ ok: true, msg: "사용자 제재 설정이 저장됐어요." });
    } catch {
      setToast({ ok: false, msg: "제재 설정 저장 실패 — 잠시 후 다시 시도해주세요." });
    }
  };

  /* 작성 제한 / 신고 블러 저장 */
  const applyRate = async (next: Parameters<typeof moderationApi.updateModerationSettings>[0], okMsg: string) => {
    if (!canConfigure) return;
    try {
      const u = await moderationApi.updateModerationSettings(next);
      setReportBlur(u.reportBlurThreshold);
      setPostWin(u.postRateWindowSeconds); setPostMax(u.postRateMax);
      setCommentWin(u.commentRateWindowSeconds); setCommentMax(u.commentRateMax);
      setInquiryWin(u.inquiryRateWindowSeconds); setInquiryMax(u.inquiryRateMax);
      setChangedAt(u.updatedAt);
      setToast({ ok: true, msg: okMsg });
    } catch {
      setToast({ ok: false, msg: "저장 실패 — 잠시 후 다시 시도해주세요." });
    }
  };

  /* 판정 테스트 */
  const runTest = async () => {
    if (!canTest || loading || serverDown || !body.trim()) return;
    setLoading(true);
    if (result) setPrevRes(result);
    try {
      const r = await moderationApi.testModeration({
        title: title || undefined,
        content: body,
      });
      setResult({ r, th });
    } catch {
      flash("판정 테스트에 실패했습니다.");
    } finally {
      setLoading(false);
    }
  };

  const applyReviewDecision = async () => {
    if (!reviewPending || queueActionId !== null) return;
    const { item, action } = reviewPending;
    if (action === "HIDE" ? !canHideReview : !canKeepReview) return;
    setQueueActionId(item.postId);
    try {
      await moderationApi.decideModerationReview(item.postId, action);
      setReviewPending(null);
      setToast({
        ok: true,
        msg: action === "HIDE" ? "게시글을 숨김 처리했습니다." : "게시 유지 결정을 저장했습니다.",
      });
      if (queue.length === 1 && queuePage > 1) {
        setQueuePage((page) => page - 1);
      } else {
        setQueueReloadKey((key) => key + 1);
      }
    } catch (error) {
      setToast({
        ok: false,
        msg: error instanceof Error ? error.message : "검토 결정을 저장하지 못했습니다.",
      });
    } finally {
      setQueueActionId(null);
    }
  };

  const fmtDate = (iso: string | null | undefined) => {
    if (!iso) return "-";
    const d = new Date(iso);
    return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, "0")}.${String(d.getDate()).padStart(2, "0")} ${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
  };

  return (
    <div className="md-settings">
      {/* 서버 연결 실패 배너 */}
      {serverDown && (
        <div className="md-banner" role="alert">
          <PlugZap size={15} />
          <div>
            <div className="t">검열 서버에 연결할 수 없습니다</div>
            <div className="s">
              글 작성과 게시는 정상 동작하며, 이 시간 동안 작성된 글은 복구 후 자동으로 재처리됩니다.
              설정 변경과 테스트만 잠시 제한돼요.
            </div>
          </div>
        </div>
      )}

      {/* 현재 상태 카드 */}
      <section className="av-panel md-now" aria-label="현재 상태">
        <div>
          <div className="md-now__k">현재 모드</div>
          <div className="md-now__v">{cur.name}{custom ? " (직접 조정)" : ""}</div>
          <div className="md-now__s">{custom ? "프리셋에서 임계값만 변경된 상태" : cur.desc.split(".")[0]}</div>
        </div>
        <div>
          <div className="md-now__k">자동 숨김 임계값</div>
          <div className="md-now__v num">{th.toFixed(2)}</div>
          <div className="md-now__s">toxic 확신도가 이 값 이상이면 즉시 숨김</div>
        </div>
        <div>
          <div className="md-now__k">마지막 변경</div>
          <div className="md-now__v num" style={{ fontSize: 14, marginTop: 8 }}>{fmtDate(changedAt)}</div>
          <div className="md-now__s">변경은 전체 게시글 검열에 즉시 적용</div>
        </div>
      </section>

      {/* 엄격도 프리셋 */}
      <div className="md-sec">
        <div className="md-sec__h">
          <h2>엄격도 프리셋</h2>
          <span className="s">변경 시 확인을 거쳐 즉시 적용돼요</span>
        </div>
        <div className="md-presets">
          {PRESETS.map((p) => {
            const on = mode === p.k && !custom;
            return (
              <button
                key={p.k}
                disabled={!canConfigure}
                className={`md-pre${on ? " on" : ""}`}
                onClick={() => { if (canConfigure && !on) setPending(p); }}
              >
                <div className="md-pre__top">
                  <span className="md-pre__t">{p.name}</span>
                  {on && <span className="md-pre__live">적용 중</span>}
                </div>
                <div className="md-pre__s">{p.desc}</div>
                <div className="md-pre__th">
                  <b className="num">{p.th.toFixed(2)}</b>
                  <span>이상 자동 숨김</span>
                </div>
                <div className="md-pre__guide"><b>판정 지침</b> &mdash; {p.guide}</div>
              </button>
            );
          })}
        </div>
      </div>

      {/* 고급 설정 */}
      <section className="av-panel md-sec" aria-label="고급 설정">
        <button
          className={`md-adv__head${advOpen ? " open" : ""}`}
          onClick={() => setAdvOpen((v) => !v)}
        >
          <ChevronRight size={14} />
          고급 설정
          <span className="s">&mdash; 임계값 직접 조정</span>
        </button>
        {advOpen && (
          <div className="md-adv__body">
            <div className="md-adv__row">
              <span className="av-muted num">0.50</span>
              <input
                type="range" min="0.5" max="0.95" step="0.05"
                disabled={!canConfigure}
                value={th}
                onChange={(e) => applyTh(Number(e.target.value))}
              />
              <span className="av-muted num">0.95</span>
              <span className="md-adv__val num">{th.toFixed(2)}</span>
            </div>
            <div className="av-hint">
              임계값 이상의 확신도로 toxic 판정된 글이 자동 숨김됩니다.
              그 미만은 게시가 유지되고 검토 대기 목록에 쌓여요.
            </div>
          </div>
        )}
      </section>

      {/* 사용자 제재 (검열 누적 → 자동 차단) */}
      <div className="md-sec">
        <div className="md-sec__h">
          <h2>사용자 제재</h2>
          <span className="s">숨김 글이 누적된 사용자를 자동 차단 — 게시글 숨김 임계와 별개</span>
        </div>
        <section className="av-panel md-fields" style={{ display: "flex", gap: 24, flexWrap: "wrap" }} aria-label="사용자 제재">
          <label style={{ display: "flex", flexDirection: "column", gap: 6 }}>
            <span className="av-flabel">제재 임계 (누적 숨김 글 수)</span>
            <input
              type="number" min={1} max={100}
              disabled={!canConfigure}
              className="av-input" style={{ width: 120 }}
              value={sanction}
              onChange={(e) => setSanction(Number(e.target.value))}
              onBlur={(e) => {
                const v = Math.min(100, Math.max(1, Number(e.target.value) || 1));
                if (v !== sanction) applySanction({ sanctionThreshold: v });
              }}
            />
            <span className="av-hint">숨김 글이 이 개수 이상이면 자동 차단</span>
          </label>
          <label style={{ display: "flex", flexDirection: "column", gap: 6 }}>
            <span className="av-flabel">차단 기간 (일)</span>
            <input
              type="number" min={1} max={3650}
              disabled={!canConfigure}
              className="av-input" style={{ width: 120 }}
              value={blockDays}
              onChange={(e) => setBlockDays(Number(e.target.value))}
              onBlur={(e) => {
                const v = Math.min(3650, Math.max(1, Number(e.target.value) || 1));
                if (v !== blockDays) applySanction({ blockDays: v });
              }}
            />
            <span className="av-hint">자동 차단 시 이 기간 동안 이용 제한</span>
          </label>
        </section>
      </div>

      {/* 작성 제한(도배 방지) & 신고 누적 블러 */}
      <div className="md-sec">
        <div className="md-sec__h">
          <h2>작성 제한 &amp; 신고 블러</h2>
          <span className="s">짧은 시간 대량 작성(도배)을 막고, 신고가 쌓인 글을 자동으로 가려요 — 변경은 즉시 적용</span>
        </div>
        <section className="av-panel md-fields" style={{ display: "flex", flexDirection: "column", gap: 20 }} aria-label="작성 제한 및 신고 블러">
          {/* 신고 누적 블러 */}
          <div style={{ display: "flex", gap: 24, flexWrap: "wrap", alignItems: "flex-end" }}>
            <NumBox
              label="신고 누적 블러 임계 (건)"
              disabled={!canConfigure}
              value={reportBlur} min={1} max={1000}
              hint="이 수 이상 신고되면 비작성자에게 자동 블러(작성자·클릭 시 해제)"
              onCommit={(v) => applyRate({ reportBlurThreshold: v }, "신고 블러 임계가 저장됐어요.")}
            />
          </div>

          {/* 게시글 rate-limit */}
          <div>
            <div className="av-flabel" style={{ marginBottom: 8, fontWeight: 600 }}>게시글 작성 제한</div>
            <div style={{ display: "flex", gap: 24, flexWrap: "wrap", alignItems: "flex-end" }}>
              <NumBox label="윈도 (초)" value={postWin} min={1} max={86400}
                disabled={!canConfigure}
                hint="이 시간 창 안의 작성 수를 셈" onCommit={(v) => applyRate({ postRateWindowSeconds: v }, "게시글 제한이 저장됐어요.")} />
              <NumBox label="허용 건수 (0=비활성)" value={postMax} min={0} max={100000}
                disabled={!canConfigure}
                hint="윈도 내 이 수 이상이면 429 차단" onCommit={(v) => applyRate({ postRateMax: v }, "게시글 제한이 저장됐어요.")} />
            </div>
          </div>

          {/* 댓글 rate-limit */}
          <div>
            <div className="av-flabel" style={{ marginBottom: 8, fontWeight: 600 }}>댓글 작성 제한</div>
            <div style={{ display: "flex", gap: 24, flexWrap: "wrap", alignItems: "flex-end" }}>
              <NumBox label="윈도 (초)" value={commentWin} min={1} max={86400}
                disabled={!canConfigure}
                hint="이 시간 창 안의 작성 수를 셈" onCommit={(v) => applyRate({ commentRateWindowSeconds: v }, "댓글 제한이 저장됐어요.")} />
              <NumBox label="허용 건수 (0=비활성)" value={commentMax} min={0} max={100000}
                disabled={!canConfigure}
                hint="윈도 내 이 수 이상이면 429 차단" onCommit={(v) => applyRate({ commentRateMax: v }, "댓글 제한이 저장됐어요.")} />
            </div>
          </div>

          {/* 문의 rate-limit */}
          <div>
            <div className="av-flabel" style={{ marginBottom: 8, fontWeight: 600 }}>
              문의 작성 제한 <span className="md-v2">즉시 집행</span>
            </div>
            <div style={{ display: "flex", gap: 24, flexWrap: "wrap", alignItems: "flex-end" }}>
              <NumBox label="윈도 (초)" value={inquiryWin} min={1} max={86400}
                disabled={!canConfigure}
                hint="이 시간 창 안의 문의 작성 수를 셈" onCommit={(v) => applyRate({ inquiryRateWindowSeconds: v }, "문의 제한 정책이 저장됐어요.")} />
              <NumBox label="허용 건수 (0=비활성)" value={inquiryMax} min={0} max={100000}
                disabled={!canConfigure}
                hint="윈도 내 이 수 이상이면 429로 차단" onCommit={(v) => applyRate({ inquiryRateMax: v }, "문의 제한 정책이 저장됐어요.")} />
            </div>
          </div>
        </section>
      </div>

      {/* 테스트 콘솔 */}
      {canTest && <div className="md-sec">
        <div className="md-sec__h">
          <h2>테스트 콘솔</h2>
          <span className="s">현재 설정으로 즉석 판정 &mdash; 설정을 바꾼 뒤 같은 문장을 다시 판정해보세요</span>
        </div>
        <section className="av-panel md-console">
          <div className="md-console__in">
            <div className="av-flabel">제목 <span className="opt">선택</span></div>
            <input
              className="av-input"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="게시글 제목"
            />
            <div className="av-flabel" style={{ marginTop: 12 }}>본문</div>
            <textarea
              className="av-textarea"
              style={{ minHeight: 110 }}
              value={body}
              onChange={(e) => setBody(e.target.value)}
              placeholder="판정해볼 게시글 본문을 입력하세요"
            />
            <div style={{ display: "flex", gap: 8, marginTop: 11, alignItems: "center" }}>
              <button
                className="av-btn av-btn--ink"
                style={{ height: 32, padding: "0 14px", opacity: (serverDown || !body.trim()) ? 0.45 : 1 }}
                disabled={serverDown || !body.trim() || loading}
                onClick={runTest}
              >
                {loading ? <span className="md-spin" /> : <ScanText size={14} />}
                {loading ? "판정 중\u2026" : "판정 테스트"}
              </button>
              {serverDown && <span className="av-muted">서버 복구 후 테스트할 수 있어요</span>}
              {!serverDown && loading && <span className="av-muted num">AI 판정에 1~3초 걸려요</span>}
            </div>
          </div>

          <div className="md-console__out">
            <div className="md-out__head">
              <span className="md-out__t">판정 결과</span>
              {result && !loading && (
                <span className="md-out__s num">방금 &middot; 임계값 {result.th.toFixed(2)} 기준</span>
              )}
            </div>
            {loading ? (
              <div className="md-skel">
                <i style={{ width: 90, height: 20, borderRadius: 99 }} />
                <i style={{ width: "60%", height: 12, marginTop: 12 }} />
                <i style={{ width: "100%", height: 6, marginTop: 18, borderRadius: 99 }} />
                <i style={{ width: "30%", height: 9, marginTop: 10 }} />
              </div>
            ) : !result ? (
              <div className="md-empty">
                <ScanText size={18} />
                <p>아직 판정한 글이 없어요.<br />왼쪽에 문장을 입력하고 판정 테스트를 눌러보세요.</p>
              </div>
            ) : (
              <div>
                <MdResult r={result.r} th={result.th} />
                {prevRes && (
                  <div style={{ marginTop: 10 }}>
                    <div className="md-out__head" style={{ marginBottom: 6 }}>
                      <span className="md-out__t" style={{ color: "var(--av-ink-3)" }}>직전 판정</span>
                      <span className="md-out__s num">임계값 {prevRes.th.toFixed(2)} 기준</span>
                    </div>
                    <MdResult r={prevRes.r} th={prevRes.th} prev />
                  </div>
                )}
              </div>
            )}
          </div>
        </section>
      </div>}

      {/* 임계 미만 toxic 결과 수동 검토 큐 */}
      <section className="av-panel md-sec" aria-label="검토 대기 목록">
        <div className="av-mod__h">
          <div>
            <span className="av-mod__t">검토 대기 목록</span>
            <span className="md-queue__count">{queueTotal.toLocaleString()}건</span>
            <div className="av-mod__s">toxic 판정이지만 임계값 {th.toFixed(2)} 미만으로 게시 중인 글</div>
          </div>
          <button
            className="av-btn md-queue__refresh"
            onClick={() => setQueueReloadKey((key) => key + 1)}
            disabled={queueLoading}
            aria-label="검토 대기 목록 새로고침"
          >
            <RefreshCw className={queueLoading ? "md-queue__spin" : ""} />
            새로고침
          </button>
        </div>
        {queueLoading ? (
          <div className="md-queue__loading" role="status">검토 대기 목록을 불러오는 중입니다.</div>
        ) : queueError ? (
          <div className="md-queue__error" role="alert">
            <AlertCircle />
            <span>{queueError}</span>
            <button className="av-btn" onClick={() => setQueueReloadKey((key) => key + 1)}>다시 시도</button>
          </div>
        ) : queue.length === 0 ? (
          <div className="md-queue__empty">
            <Inbox size={18} />
            <p>현재 직접 검토할 경계 판정 게시글이 없습니다.</p>
          </div>
        ) : (
          <>
            <div className="md-queue__list">
              {queue.map((item) => (
                <article className="md-queue__item" key={item.postId}>
                  <div className="md-queue__item-head">
                    <div className="md-queue__title-wrap">
                      <h3>{item.title}</h3>
                      <div className="md-queue__meta">
                        <span>{item.category}</span>
                        <span>{item.authorName}</span>
                        <span>검열 {fmtDate(item.moderatedAt)}</span>
                      </div>
                    </div>
                    <div className="md-queue__score">
                      <span>{item.aiCategory ?? "분류 없음"}</span>
                      <b className="num">{item.confidence.toFixed(2)}</b>
                    </div>
                  </div>
                  <p className="md-queue__preview">{item.contentPreview || "본문 미리보기가 없습니다."}</p>
                  {(canKeepReview || canHideReview) && <div className="md-queue__actions">
                    <span className="av-hint">결정 후 목록에서 빠지며 같은 검열 결과에는 다시 나타나지 않습니다.</span>
                    {canKeepReview && (
                      <button
                        className="av-btn"
                        disabled={queueActionId !== null}
                        onClick={() => setReviewPending({ item, action: "KEEP" })}
                      >
                        <CheckCircle2 /> 게시 유지
                      </button>
                    )}
                    {canHideReview && (
                      <button
                        className="av-btn md-queue__hide"
                        disabled={queueActionId !== null}
                        onClick={() => setReviewPending({ item, action: "HIDE" })}
                      >
                        <EyeOff /> 숨김
                      </button>
                    )}
                  </div>}
                </article>
              ))}
            </div>
            <div className="md-queue__pager" aria-label="검토 대기 목록 페이지">
              <button className="av-btn" disabled={queuePage <= 1} onClick={() => setQueuePage((page) => page - 1)}>이전</button>
              <span className="num">{queuePage} / {Math.max(1, Math.ceil(queueTotal / 5))}</span>
              <button className="av-btn" disabled={!queueHasNext} onClick={() => setQueuePage((page) => page + 1)}>다음</button>
            </div>
          </>
        )}
      </section>

      {/* 수동 검토 결정 확인 */}
      {reviewPending && (reviewPending.action === "HIDE" ? canHideReview : canKeepReview) && (
        <div className="md-dim" onClick={(e) => {
          if (e.target === e.currentTarget && queueActionId === null) setReviewPending(null);
        }}>
          <div className="md-dlg" role="dialog" aria-modal="true" aria-labelledby="review-decision-title">
            <div className="md-dlg__t" id="review-decision-title">
              {reviewPending.action === "HIDE" ? "이 게시글을 숨길까요?" : "게시 유지로 결정할까요?"}
            </div>
            <div className="md-dlg__s">
              <b>{reviewPending.item.title}</b><br />
              {reviewPending.action === "HIDE"
                ? "게시글은 HIDDEN 상태로 전환되고 작성자에게 숨김 알림이 한 번 전송됩니다. 이후 기존 검열 목록에서 복원 또는 삭제할 수 있습니다."
                : "게시 상태는 그대로 유지됩니다. 이 검열 결과의 수동 결정은 저장되며 검토 대기 목록에 다시 나타나지 않습니다."}
            </div>
            <div className="md-dlg__r">
              <button className="av-btn" disabled={queueActionId !== null} onClick={() => setReviewPending(null)}>취소</button>
              <button
                className={`av-btn ${reviewPending.action === "HIDE" ? "md-queue__hide" : "av-btn--ink"}`}
                disabled={queueActionId !== null}
                onClick={() => void applyReviewDecision()}
              >
                {queueActionId !== null ? "저장 중…" : reviewPending.action === "HIDE" ? "숨김 결정" : "게시 유지 결정"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 확인 다이얼로그 */}
      {pending && canConfigure && (
        <div className="md-dim" onClick={(e) => { if (e.target === e.currentTarget) setPending(null); }}>
          <div className="md-dlg" role="dialog" aria-modal="true">
            <div className="md-dlg__t">&lsquo;{pending.name}&rsquo; 모드로 변경할까요?</div>
            <div className="md-dlg__s">
              자동 숨김 임계값이 <b className="num">{th.toFixed(2)} &rarr; {pending.th.toFixed(2)}</b>로 바뀌고,
              <b> 전체 게시글 검열에 즉시 적용됩니다.</b> 이미 게시된 글은 다시 판정하지 않아요.
            </div>
            <div className="md-dlg__r">
              <button className="av-btn" onClick={() => setPending(null)}>취소</button>
              <button className="av-btn av-btn--ink" onClick={() => applyPreset(pending)}>변경 적용</button>
            </div>
          </div>
        </div>
      )}

      {/* 토스트 */}
      {toast && (
        <div className={`md-toast ${toast.ok ? "ok" : "err"}`}>
          {toast.ok ? <CheckCircle2 size={14} /> : <AlertCircle size={14} />}
          {toast.msg}
        </div>
      )}
    </div>
  );
}
