import { useState, useEffect, useCallback } from "react";
import {
  PlugZap, ScanText, Inbox, ChevronRight, CheckCircle2, AlertCircle,
} from "lucide-react";
// TODO: 백엔드 연동 시 주석 해제
// import * as moderationApi from "../api/moderationApi";
import type { ModerationTestResult } from "../api/moderationApi";
import "./moderation-settings.css";

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

/* ══════════════════════════════════════════════════════════
   메인 패널
   ══════════════════════════════════════════════════════════ */
export default function ModerationSettingsPanel({ flash }: { flash: (msg: string) => void }) {
  /* 설정 상태 */
  const [mode, setMode] = useState("NORMAL");
  const [th, setTh] = useState(0.80);
  const [custom, setCustom] = useState(false);
  const [changedAt, setChangedAt] = useState("");
  const [serverDown, setServerDown] = useState(false);

  /* UI 상태 */
  const [advOpen, setAdvOpen] = useState(false);
  const [pending, setPending] = useState<typeof PRESETS[number] | null>(null);
  const [toast, setToast] = useState<{ ok: boolean; msg: string } | null>(null);

  /* 테스트 상태 */
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<{ r: ModerationTestResult; th: number } | null>(null);
  const [prevRes, setPrevRes] = useState<{ r: ModerationTestResult; th: number } | null>(null);

  /* 설정 로드 */
  const loadSettings = useCallback(() => {
    // TODO: 백엔드 연동 시 moderationApi.getModerationSettings() 로 교체
    setMode("NORMAL");
    setTh(0.80);
    setChangedAt("2026-06-10T09:00:00");
    setCustom(false);
    setServerDown(false);
  }, []);

  useEffect(() => { loadSettings(); }, [loadSettings]);

  /* 토스트 자동 소멸 */
  useEffect(() => {
    if (!toast) return;
    const t = setTimeout(() => setToast(null), 2600);
    return () => clearTimeout(t);
  }, [toast]);

  const cur = PRESETS.find((p) => p.k === mode) ?? PRESETS[1];

  /* 프리셋 적용 */
  const applyPreset = async (p: typeof PRESETS[number]) => {
    setPending(null);
    // TODO: 백엔드 연동 시 moderationApi.updateModerationSettings 로 교체
    setMode(p.k);
    setTh(p.th);
    setCustom(false);
    setChangedAt(new Date().toISOString());
    setToast({ ok: true, msg: `'${p.name}' 모드 적용됨 \u2014 임계값 ${p.th.toFixed(2)}` });
  };

  /* 고급 임계값 변경 */
  const applyTh = async (v: number) => {
    setTh(v);
    const isCustom = v !== cur.th;
    setCustom(isCustom);
    // TODO: 백엔드 연동 시 moderationApi.updateModerationSettings 로 교체
    setChangedAt(new Date().toISOString());
  };

  /* 판정 테스트 */
  const runTest = async () => {
    if (loading || serverDown || !body.trim()) return;
    setLoading(true);
    if (result) setPrevRes(result);
    // TODO: 백엔드 연동 시 moderationApi.testModeration 로 교체
    setTimeout(() => {
      const hasKeyword = /욕|씨|바보|광고|스팸|홍보|링크/.test(body);
      const r: ModerationTestResult = {
        toxic: hasKeyword,
        category: hasKeyword ? "abuse" : "normal",
        confidence: hasKeyword ? 0.85 + Math.random() * 0.1 : 0.05 + Math.random() * 0.15,
        elapsedMs: 800 + Math.floor(Math.random() * 400),
      };
      setResult({ r, th });
      setLoading(false);
    }, 1200);
  };

  const fmtDate = (iso: string) => {
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
                className={`md-pre${on ? " on" : ""}`}
                onClick={() => { if (!on) setPending(p); }}
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

      {/* 테스트 콘솔 */}
      <div className="md-sec">
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
      </div>

      {/* 검토 대기 목록 placeholder */}
      <section className="av-panel md-sec" aria-label="검토 대기 목록">
        <div className="av-mod__h">
          <span className="av-mod__t">검토 대기 목록 <span className="md-v2">v2 예정</span></span>
          <span className="av-mod__s">toxic 판정이지만 임계값 미달로 게시 중인 글</span>
        </div>
        <div className="md-queue__empty">
          <Inbox size={18} />
          <p>여기에 확신도가 임계값에 못 미친 글이 모여요.<br />운영자가 직접 숨김/유지를 결정하는 화면으로 준비 중입니다.</p>
        </div>
      </section>

      {/* 확인 다이얼로그 */}
      {pending && (
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
