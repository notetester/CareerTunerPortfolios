import { useState, useEffect, useCallback } from "react";
import {
  Scale, Eye, Check, CalendarClock, Plus,
  ChevronUp, ChevronDown, X, BookOpen, Save, Trash2, AlertTriangle,
} from "lucide-react";
import { Link } from "react-router";
import AdminShell from "../../../components/AdminShell";
import {
  getVersions, getVersionDetail, createDraft, saveDraft, publishVersion, deleteVersion,
  type LegalDocType, type AdminLegalVersionSummary, type AdminLegalClause,
} from "../api/adminLegalApi";
import "./admin-terms.css";

const DOC_TABS: { key: LegalDocType; label: string }[] = [
  { key: "terms", label: "이용약관" },
  { key: "privacy", label: "개인정보처리방침" },
  { key: "marketing", label: "마케팅 수신 동의" },
];

const BADGE_LABEL: Record<string, string> = {
  live: "게시중", next: "예정", old: "종료", draft: "작성 중",
};

interface ClauseRow { title: string; body: string; }

/** 토스트 톤 — green=성공, red=실패, amber=경고(성공했으나 주의). */
type ToastTone = "green" | "red" | "amber";

const TOAST_BG: Record<ToastTone, string> = {
  green: "#16a34a",
  red: "#dc2626",
  amber: "#d97706",
};

function fmtDate(value: string | null): string {
  return value ? value.slice(0, 10).replace(/-/g, ".") : "";
}

/** 다음 버전 라벨 추정: 게시본 vX.Y → vX.(Y+1). 없으면 v1.0. */
function nextVersionLabel(versions: AdminLegalVersionSummary[]): string {
  const published = versions.find((v) => v.status === "PUBLISHED");
  if (!published) return "v1.0";
  const m = /v(\d+)\.(\d+)/.exec(published.versionLabel);
  if (!m) return "v1.0";
  return `v${m[1]}.${Number(m[2]) + 1}`;
}

export default function AdminTerms() {
  const [docType, setDocType] = useState<LegalDocType>("terms");
  const [versions, setVersions] = useState<AdminLegalVersionSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  // 편집 대상 (DRAFT)
  const [editId, setEditId] = useState<number | null>(null);
  const [versionLabel, setVersionLabel] = useState("v1.0");
  const [summary, setSummary] = useState("");
  const [isAdverse, setIsAdverse] = useState(false);
  const [clauses, setClauses] = useState<ClauseRow[]>([]);
  const [when, setWhen] = useState<"즉시" | "예약">("예약");
  const [effectiveDate, setEffectiveDate] = useState("");

  const [lastSaved, setLastSaved] = useState<string | null>(null);
  const [toast, setToast] = useState<{ msg: string; tone: ToastTone } | null>(null);

  // 게시는 성공했지만 리드타임이 부족한 경우(차단 아님)를 띄우는 경고 배너.
  const [publishWarning, setPublishWarning] = useState<string | null>(null);

  const flash = (msg: string, tone: ToastTone, ms = 2400) => {
    setToast({ msg, tone });
    setTimeout(() => setToast(null), ms);
  };

  const resetForm = useCallback((vs: AdminLegalVersionSummary[]) => {
    setEditId(null);
    setVersionLabel(nextVersionLabel(vs));
    setSummary("");
    setIsAdverse(false);
    setClauses([]);
    setWhen("예약");
    setEffectiveDate("");
    setLastSaved(null);
    setPublishWarning(null);
  }, []);

  const fillFromDraft = useCallback(async (id: number) => {
    const detail = await getVersionDetail(id);
    setEditId(detail.id);
    setVersionLabel(detail.versionLabel);
    setSummary(detail.summary ?? "");
    setIsAdverse(detail.isAdverse);
    setClauses(detail.clauses.map((c) => ({ title: c.title, body: c.body })));
    setWhen(detail.effectiveDate ? "예약" : "즉시");
    setEffectiveDate(detail.effectiveDate ? detail.effectiveDate.slice(0, 10) : "");
  }, []);

  const loadVersions = useCallback(async (dt: LegalDocType) => {
    setLoading(true);
    try {
      const list = await getVersions(dt);
      setVersions(list);
      const draft = list.find((v) => v.status === "DRAFT");
      if (draft) await fillFromDraft(draft.id);
      else resetForm(list);
    } catch {
      setVersions([]);
      resetForm([]);
      flash("버전 목록을 불러오지 못했습니다.", "red");
    } finally {
      setLoading(false);
    }
  }, [fillFromDraft, resetForm]);

  useEffect(() => {
    loadVersions(docType);
  }, [docType, loadVersions]);

  // ── 조항 편집 ──
  const update = (i: number, k: keyof ClauseRow, v: string) =>
    setClauses((p) => p.map((c, j) => (j === i ? { ...c, [k]: v } : c)));
  const add = () => setClauses((p) => [...p, { title: "", body: "" }]);
  const remove = (i: number) => setClauses((p) => p.filter((_, j) => j !== i));
  const moveUp = (i: number) => {
    if (i === 0) return;
    setClauses((p) => {
      const next = [...p];
      [next[i - 1], next[i]] = [next[i], next[i - 1]];
      return next;
    });
  };
  const moveDown = (i: number) =>
    setClauses((p) => {
      if (i >= p.length - 1) return p;
      const next = [...p];
      [next[i], next[i + 1]] = [next[i + 1], next[i]];
      return next;
    });

  // ── 액션 ──
  const buildDraftPayload = () => ({
    versionLabel: versionLabel.trim() || "v1.0",
    summary: summary.trim() || null,
    isAdverse,
    effectiveDate: when === "예약" && effectiveDate ? `${effectiveDate}T00:00:00` : null,
    clauses: clauses.map<AdminLegalClause>((c, i) => ({
      seq: i + 1,
      title: c.title.trim(),
      body: c.body,
    })),
  });

  /** 초안이 없으면 새로 만들고, 있으면 PUT. 저장된 버전 id 반환. */
  const persistDraft = async (): Promise<number> => {
    let id = editId;
    if (id == null) {
      const created = await createDraft(docType, { cloneFromCurrent: false });
      id = created.id;
      setEditId(id);
    }
    await saveDraft(id, buildDraftPayload());
    return id;
  };

  const handleSave = async () => {
    if (saving) return;
    setSaving(true);
    try {
      await persistDraft();
      setLastSaved(new Date().toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" }));
      flash("임시저장되었습니다.", "green");
      await loadVersions(docType);
    } catch {
      flash("저장에 실패했습니다.", "red");
    } finally {
      setSaving(false);
    }
  };

  const handleCloneCurrent = async () => {
    if (saving || editId != null) return;
    setSaving(true);
    try {
      const created = await createDraft(docType, { cloneFromCurrent: true });
      await fillFromDraft(created.id);
      await getVersions(docType).then(setVersions);
      flash("현행 조항을 복제한 초안을 만들었습니다.", "green");
    } catch {
      flash("초안 생성에 실패했습니다.", "red");
    } finally {
      setSaving(false);
    }
  };

  const handlePublish = async () => {
    if (saving) return;
    if (clauses.length === 0) {
      flash("조항이 없는 버전은 게시할 수 없습니다.", "red");
      return;
    }
    if (when === "예약" && !effectiveDate) {
      flash("예약 시행은 시행일을 지정해야 합니다.", "red");
      return;
    }
    setSaving(true);
    setPublishWarning(null);
    try {
      const id = await persistDraft();
      const res = await publishVersion(id, {
        effectiveDate: when === "예약" && effectiveDate ? `${effectiveDate}T00:00:00` : null,
      });
      // loadVersions 가 resetForm 으로 publishWarning 을 초기화하므로, 경고는 재로드 이후에 세팅한다.
      await loadVersions(docType);
      // 백엔드가 계산한 리드타임 부족 경고(불리 변경 30일/일반 7일). 게시는 성공이므로 차단 아님.
      if (res.warning) {
        setPublishWarning(res.warning);
        flash(res.warning, "amber", 5000);
      } else {
        flash(when === "예약" ? "게시 예약되었습니다." : "게시되었습니다.", "green");
      }
    } catch (e: unknown) {
      flash(e instanceof Error ? e.message : "게시에 실패했습니다.", "red");
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    if (saving || editId == null) return;
    setSaving(true);
    try {
      await deleteVersion(editId);
      flash("초안을 삭제했습니다.", "green");
      await loadVersions(docType);
    } catch {
      flash("삭제에 실패했습니다. (게시본은 삭제할 수 없습니다)", "red");
    } finally {
      setSaving(false);
    }
  };

  const publishedVersion = versions.find((v) => v.badge === "live");
  const docLabel = DOC_TABS.find((d) => d.key === docType)?.label ?? "";

  return (
    <AdminShell
      active="terms"
      breadcrumb="약관 관리"
      title="약관 관리"
      icon={Scale}
      desc="법적 문서 작성·개정 — 게시 7일 전 공지 의무 (불리한 변경 30일)"
      actions={
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <div className="av-seg">
            {DOC_TABS.map((d) => (
              <button key={d.key} className={docType === d.key ? "on" : ""} onClick={() => setDocType(d.key)}>
                {d.label}
              </button>
            ))}
          </div>
          <Link to="/admin/terms/guidelines" style={{
            display: "inline-flex", alignItems: "center", gap: 6,
            padding: "6px 12px", fontSize: 12.5, fontWeight: 600, border: "1px solid #e2e8f0",
            borderRadius: 7, background: "#fff", color: "#475569", textDecoration: "none",
            whiteSpace: "nowrap",
          }}>
            <BookOpen style={{ width: 13, height: 13 }} /> 커뮤니티 가이드라인
          </Link>
        </div>
      }
    >
      <div className="av-form">
        {/* 본문 영역 */}
        <section className="av-panel">
          <div className="av-mod__h" style={{ paddingBottom: 12 }}>
            <span className="av-mod__t">
              {docLabel} — {versionLabel} {editId ? "작성 중" : "새 버전"}
            </span>
            <span className="av-mod__s">
              {publishedVersion ? `기준: ${publishedVersion.versionLabel} (게시중)` : "현재 게시본 없음"}
            </span>
          </div>

          {loading ? (
            <div style={{ padding: "40px 0", textAlign: "center", color: "var(--av-ink-3)" }}>
              불러오는 중…
            </div>
          ) : (
            <>
              <div className="av-field" style={{ borderTop: "1px solid var(--av-line-soft)" }}>
                <div className="av-flabel">버전 라벨</div>
                <input
                  className="av-input"
                  value={versionLabel}
                  onChange={(e) => setVersionLabel(e.target.value)}
                  placeholder="예: v2.4"
                  style={{ maxWidth: 160 }}
                />
              </div>

              <div className="av-field">
                <div className="av-flabel">
                  개정 요약 <span className="opt">— 공지사항·이메일 고지에 그대로 사용됩니다</span>
                </div>
                <input
                  className="av-input"
                  value={summary}
                  onChange={(e) => setSummary(e.target.value)}
                  placeholder="예: 크레딧 환불 규정 명확화 (제12조)"
                />
              </div>

              <div className="av-field">
                <label className="av-flabel" style={{ display: "flex", alignItems: "center", gap: 8, cursor: "pointer" }}>
                  <input
                    type="checkbox"
                    checked={isAdverse}
                    onChange={(e) => setIsAdverse(e.target.checked)}
                  />
                  회원에게 불리한 변경 <span className="opt">— 시행일 30일 전 공지 의무 (법무 확인 필수)</span>
                </label>
              </div>

              <div className="av-field">
                <div className="av-flabel">조항</div>
                {clauses.length === 0 && (
                  <div style={{ padding: "12px 0", fontSize: 12.5, color: "var(--av-ink-3)" }}>
                    아직 조항이 없습니다. 아래에서 추가하거나 현행 조항을 복제하세요.
                  </div>
                )}
                {clauses.map((c, i) => (
                  <div className="tv-clause" key={i}>
                    <div className="tv-clause__h">
                      <span className="tv-clause__no num">제{i + 1}조</span>
                      <input
                        className="tv-clause__t"
                        value={c.title}
                        onChange={(e) => update(i, "title", e.target.value)}
                        placeholder="조항 제목"
                      />
                      <div className="tv-clause__tools">
                        <button aria-label="위로" onClick={() => moveUp(i)}><ChevronUp /></button>
                        <button aria-label="아래로" onClick={() => moveDown(i)}><ChevronDown /></button>
                        <button aria-label="삭제" onClick={() => remove(i)}><X /></button>
                      </div>
                    </div>
                    <textarea
                      className="tv-clause__b"
                      value={c.body}
                      onChange={(e) => update(i, "body", e.target.value)}
                      placeholder="조항 본문 — 줄바꿈으로 항(1. 2. 3.)을 구분하세요"
                    />
                  </div>
                ))}
                <button className="tv-add" onClick={add}>
                  <Plus /> 조항 추가
                </button>
                {editId == null && publishedVersion && (
                  <button className="tv-add" onClick={handleCloneCurrent} disabled={saving} style={{ marginTop: 8 }}>
                    <Plus /> 현행 조항 복제해서 시작
                  </button>
                )}
              </div>

              <div className="av-field">
                <div className="av-flabel">시행 시점</div>
                <div className="av-choices">
                  <div
                    className={`av-choice${when === "즉시" ? " on" : ""}`}
                    onClick={() => setWhen("즉시")}
                  >
                    <div className="t">즉시 시행</div>
                    <div className="s">게시와 동시에 효력</div>
                  </div>
                  <div
                    className={`av-choice${when === "예약" ? " on" : ""}`}
                    onClick={() => setWhen("예약")}
                  >
                    <div className="t">예약 시행</div>
                    <div className="s">시행일 지정</div>
                  </div>
                </div>
                {when === "예약" && (
                  <input
                    type="date"
                    className="av-input"
                    value={effectiveDate}
                    onChange={(e) => setEffectiveDate(e.target.value)}
                    style={{ marginTop: 10, maxWidth: 200 }}
                  />
                )}
                <div className="av-hint">
                  개정 약관은 시행 7일 전(불리한 변경은 30일 전) 공지해야 합니다. 리드타임이 부족하면 게시 시 경고가 표시됩니다.
                </div>
                {publishWarning && (
                  <div
                    role="alert"
                    style={{
                      marginTop: 10, display: "flex", gap: 8, alignItems: "flex-start",
                      padding: "10px 12px", borderRadius: 8, fontSize: 12.5, lineHeight: 1.5,
                      color: "#92400e", background: "#fffbeb", border: "1px solid #fde68a",
                    }}
                  >
                    <AlertTriangle style={{ width: 15, height: 15, flexShrink: 0, marginTop: 1 }} />
                    <span>{publishWarning}</span>
                  </div>
                )}
              </div>
            </>
          )}
        </section>

        {/* 우측 레일 */}
        <aside className="av-rail">
          <section className="av-panel">
            <div className="av-mod__h">
              <span className="av-mod__t">버전 이력</span>
              <span className="av-mod__s">{docLabel}</span>
            </div>
            <div className="av-list tv-ver">
              {versions.length === 0 && (
                <div style={{ padding: "12px 0", fontSize: 12.5, color: "var(--av-ink-3)" }}>
                  버전이 없습니다.
                </div>
              )}
              {versions.map((ver) => (
                <a
                  key={ver.id}
                  href="#"
                  onClick={(e) => {
                    e.preventDefault();
                    if (ver.status === "DRAFT") fillFromDraft(ver.id);
                  }}
                >
                  <span style={{ minWidth: 0, flex: 1 }}>
                    <span className="av-list__t">
                      <b className="num" style={{ fontWeight: 700, marginRight: 6 }}>{ver.versionLabel}</b>
                      {ver.summary ?? (ver.status === "DRAFT" ? "작성 중" : "")}
                    </span>
                    <span className="av-list__s num" style={{ display: "block", marginTop: 2 }}>
                      {ver.badge === "next" ? "시행 예정 " : ver.badge === "old" ? "종료 " : "시행 "}
                      {fmtDate(ver.effectiveDate)}
                    </span>
                  </span>
                  <span className={`tv-ver__badge ${ver.badge}`}>{BADGE_LABEL[ver.badge]}</span>
                </a>
              ))}
            </div>
          </section>

          <section className="av-panel">
            <div className="av-mod__h"><span className="av-mod__t">개정 체크리스트</span></div>
            <div className="av-note" style={{ marginTop: 12 }}>
              <b>법무 검토</b> 완료 후 게시하세요. 게시하면 <b>전 회원 NOTICE 알림</b>이 발송되고,
              미동의 회원은 다음 로그인 시 동의 절차를 거칩니다.
            </div>
            {editId != null && (
              <button
                className="av-btn"
                onClick={handleDelete}
                disabled={saving}
                style={{ marginTop: 12, color: "#dc2626", borderColor: "#fecaca" }}
              >
                <Trash2 style={{ width: 13, height: 13 }} /> 초안 삭제
              </button>
            )}
          </section>
        </aside>
      </div>

      {/* 스티키 푸터 */}
      <div className="av-composefoot">
        <div className="av-composefoot__in">
          <span className="av-composefoot__draft num">
            {lastSaved && <><Check /> 임시저장됨 · {lastSaved}</>}
          </span>
          <div className="av-composefoot__r">
            <a className="av-btn" href={`/legal/${docType}`} target="_blank" rel="noopener noreferrer">
              <Eye /> 미리보기
            </a>
            <button className="av-btn" onClick={handleSave} disabled={saving || loading}>
              <Save /> {saving ? "저장 중…" : "임시저장"}
            </button>
            <button className="av-btn av-btn--ink" onClick={handlePublish} disabled={saving || loading}>
              <CalendarClock /> {when === "예약" ? "게시 예약" : "게시"}
            </button>
          </div>
        </div>
      </div>

      {/* 토스트 */}
      {toast && (
        <div
          style={{
            position: "fixed", bottom: 84, left: "50%", transform: "translateX(-50%)",
            padding: "10px 18px", borderRadius: 8, fontSize: 13, fontWeight: 600, zIndex: 1000,
            color: "#fff", background: TOAST_BG[toast.tone],
            boxShadow: "0 4px 12px rgba(0,0,0,0.15)",
          }}
        >
          {toast.msg}
        </div>
      )}
    </AdminShell>
  );
}
