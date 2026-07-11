import { useCallback, useEffect, useState } from "react";
import { Gift, RotateCw } from "lucide-react";

import AdminShell from "../../../components/AdminShell";
import { useAdminDomainAuthorization } from "@/admin/auth/useAdminAuthorization";
import {
  createCoupon,
  createLevelPolicy,
  deleteLevelPolicy,
  getCoupons,
  getLevelPolicies,
  getRewardHistory,
  getRewardRules,
  issueCoupon,
  toggleRewardRule,
  updateLevelPolicy,
  updateRewardRule,
  type Coupon,
  type LevelPolicy,
  type RewardHistoryRow,
  type RewardRule,
} from "../api";

function fmt(value: string | null): string {
  if (!value) return "-";
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? value : d.toLocaleString("ko-KR");
}

export function AdminRewardsPage() {
  const { canCreate, canUpdate, canDelete } = useAdminDomainAuthorization("BILLING");
  const [msg, setMsg] = useState<string | null>(null);
  const flash = (m: string) => {
    setMsg(m);
    setTimeout(() => setMsg(null), 2500);
  };

  return (
    <AdminShell
      active="rewards"
      breadcrumb="결제/구독 / 리워드"
      title="리워드/레벨 관리"
      icon={Gift}
      desc="사용자 활동(글·댓글·지원건·로그인·결제) 기반 포인트/크레딧 적립, 레벨업 보상, 쿠폰을 관리합니다. 적립 크레딧은 크레딧 원장에 REWARD로 기록됩니다."
      actions={null}
    >
      {msg && <div className="mb-3 rounded-lg bg-emerald-50 px-3 py-2 text-sm text-emerald-700">{msg}</div>}
      <RulesSection flash={flash} canUpdate={canUpdate} />
      <LevelsSection flash={flash} canCreate={canCreate} canUpdate={canUpdate} canDelete={canDelete} />
      <CouponsSection flash={flash} canCreate={canCreate} canUpdate={canUpdate} />
      <HistorySection />
    </AdminShell>
  );
}

/* ── 적립 규칙 ── */
function RulesSection({ flash, canUpdate }: { flash: (m: string) => void; canUpdate: boolean }) {
  const [rules, setRules] = useState<RewardRule[]>([]);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setRules(await getRewardRules());
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => { void load(); }, [load]);

  const toggle = async (r: RewardRule) => {
    if (!canUpdate) return;
    await toggleRewardRule(r.id, !r.enabled);
    flash(`'${r.name}' ${!r.enabled ? "적립 ON" : "적립 OFF"}`);
    await load();
  };

  const saveValues = async (r: RewardRule, point: number, credit: number, cap: number | null) => {
    if (!canUpdate) return;
    await updateRewardRule(r.id, {
      name: r.name, pointAmount: point, creditAmount: credit, dailyCap: cap,
      enabled: r.enabled, description: r.description, sortOrder: r.sortOrder,
    });
    flash(`'${r.name}' 값 저장됨`);
    await load();
  };

  return (
    <section className="mb-5 rounded-xl border border-slate-200 bg-card p-4">
      <div className="mb-3 flex items-center justify-between">
        <h3 className="text-sm font-semibold text-slate-800">적립 규칙 (이벤트별 on/off · 값)</h3>
        <button type="button" className="av-btn text-xs" onClick={() => void load()} disabled={loading}>
          <RotateCw size={13} className={loading ? "animate-spin" : ""} /> 새로고침
        </button>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full min-w-[820px] text-sm">
          <thead>
            <tr className="border-b border-slate-200 text-left text-xs text-slate-500">
              <th className="px-3 py-2">이벤트 / 규칙</th>
              <th className="px-3 py-2">포인트</th>
              <th className="px-3 py-2">크레딧</th>
              <th className="px-3 py-2">일일 캡</th>
              <th className="px-3 py-2">상태</th>
              <th className="px-3 py-2">동작</th>
            </tr>
          </thead>
          <tbody>
            {rules.map((r) => <RuleRow key={r.id} row={r} canUpdate={canUpdate} onToggle={toggle} onSave={saveValues} />)}
            {rules.length === 0 && <tr><td colSpan={6} className="px-3 py-6 text-center text-slate-400">규칙이 없습니다.</td></tr>}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function RuleRow({
  row, canUpdate, onToggle, onSave,
}: {
  row: RewardRule;
  canUpdate: boolean;
  onToggle: (r: RewardRule) => Promise<void>;
  onSave: (r: RewardRule, point: number, credit: number, cap: number | null) => Promise<void>;
}) {
  const [point, setPoint] = useState(String(row.pointAmount));
  const [credit, setCredit] = useState(String(row.creditAmount));
  const [cap, setCap] = useState(row.dailyCap == null ? "" : String(row.dailyCap));
  const dirty = point !== String(row.pointAmount) || credit !== String(row.creditAmount)
    || cap !== (row.dailyCap == null ? "" : String(row.dailyCap));
  return (
    <tr className="border-b border-slate-100">
      <td className="px-3 py-2">
        <div className="font-mono text-xs font-semibold text-slate-800">{row.eventCode}</div>
        <div className="text-xs text-slate-400">{row.name}</div>
      </td>
      <td className="px-3 py-2"><input className="av-input w-20" readOnly={!canUpdate} value={point} onChange={(e) => setPoint(e.target.value)} /></td>
      <td className="px-3 py-2"><input className="av-input w-20" readOnly={!canUpdate} value={credit} onChange={(e) => setCredit(e.target.value)} /></td>
      <td className="px-3 py-2"><input className="av-input w-20" readOnly={!canUpdate} placeholder="무제한" value={cap} onChange={(e) => setCap(e.target.value)} /></td>
      <td className="px-3 py-2">
        {canUpdate ? (
          <button type="button"
            className={`rounded-full px-2 py-0.5 text-xs font-semibold ${row.enabled ? "bg-emerald-50 text-emerald-600" : "bg-slate-100 text-slate-500"}`}
            onClick={() => void onToggle(row)}>
            {row.enabled ? "적립 ON" : "적립 OFF"}
          </button>
        ) : <span className="text-xs text-slate-500">{row.enabled ? "적립 ON" : "적립 OFF"}</span>}
      </td>
      <td className="px-3 py-2">
        {canUpdate && (
          <button type="button" className="av-btn text-xs" disabled={!dirty}
            onClick={() => void onSave(row, Number(point) || 0, Number(credit) || 0, cap.trim() === "" ? null : Number(cap))}>
            저장
          </button>
        )}
      </td>
    </tr>
  );
}

/* ── 레벨 정책 ── */
const EMPTY_LEVEL = { level: "", levelName: "", minPoint: "", levelupCredit: "", levelupCouponCode: "", benefitNote: "" };

function LevelsSection({
  flash, canCreate, canUpdate, canDelete,
}: {
  flash: (m: string) => void;
  canCreate: boolean;
  canUpdate: boolean;
  canDelete: boolean;
}) {
  const [levels, setLevels] = useState<LevelPolicy[]>([]);
  const [draft, setDraft] = useState({ ...EMPTY_LEVEL });

  const load = useCallback(async () => { setLevels(await getLevelPolicies()); }, []);
  useEffect(() => { void load(); }, [load]);

  const create = async () => {
    if (!canCreate) return;
    if (!draft.level || !draft.levelName.trim()) { flash("레벨 번호와 이름을 입력하세요."); return; }
    await createLevelPolicy({
      level: Number(draft.level), levelName: draft.levelName, minPoint: Number(draft.minPoint) || 0,
      levelupCredit: Number(draft.levelupCredit) || 0,
      levelupCouponCode: draft.levelupCouponCode.trim() || null,
      benefitNote: draft.benefitNote.trim() || null, active: true,
    });
    flash(`레벨 ${draft.level} 추가됨`);
    setDraft({ ...EMPTY_LEVEL });
    await load();
  };

  const toggleActive = async (l: LevelPolicy) => {
    if (!canUpdate) return;
    await updateLevelPolicy(l.id, { ...l, levelupCouponCode: l.levelupCouponCode, benefitNote: l.benefitNote, active: !l.active });
    await load();
  };

  const remove = async (l: LevelPolicy) => {
    if (!canDelete) return;
    await deleteLevelPolicy(l.id);
    flash(`레벨 ${l.level} 삭제됨`);
    await load();
  };

  return (
    <section className="mb-5 rounded-xl border border-slate-200 bg-card p-4">
      <h3 className="mb-3 text-sm font-semibold text-slate-800">레벨 정책 (임계 포인트 · 레벨업 보상)</h3>
      <div className="overflow-x-auto">
        <table className="w-full min-w-[760px] text-sm">
          <thead>
            <tr className="border-b border-slate-200 text-left text-xs text-slate-500">
              <th className="px-3 py-2">레벨</th>
              <th className="px-3 py-2">이름</th>
              <th className="px-3 py-2">최소 포인트</th>
              <th className="px-3 py-2">레벨업 크레딧</th>
              <th className="px-3 py-2">쿠폰</th>
              <th className="px-3 py-2">상태</th>
              <th className="px-3 py-2">동작</th>
            </tr>
          </thead>
          <tbody>
            {levels.map((l) => (
              <tr key={l.id} className="border-b border-slate-100">
                <td className="px-3 py-2 font-mono">Lv.{l.level}</td>
                <td className="px-3 py-2">{l.levelName}</td>
                <td className="px-3 py-2">{l.minPoint.toLocaleString("ko-KR")}</td>
                <td className="px-3 py-2">{l.levelupCredit}</td>
                <td className="px-3 py-2 font-mono text-xs">{l.levelupCouponCode ?? "-"}</td>
                <td className="px-3 py-2">
                  {canUpdate ? <button type="button"
                    className={`rounded-full px-2 py-0.5 text-xs font-semibold ${l.active ? "bg-emerald-50 text-emerald-600" : "bg-slate-100 text-slate-500"}`}
                    onClick={() => void toggleActive(l)}>{l.active ? "활성" : "비활성"}</button>
                    : <span className="text-xs text-slate-500">{l.active ? "활성" : "비활성"}</span>}
                </td>
                <td className="px-3 py-2">
                  {canDelete && <button type="button" className="av-btn text-xs text-rose-600" onClick={() => void remove(l)}>삭제</button>}
                </td>
              </tr>
            ))}
            {levels.length === 0 && <tr><td colSpan={7} className="px-3 py-6 text-center text-slate-400">레벨 정책이 없습니다.</td></tr>}
          </tbody>
        </table>
      </div>
      {canCreate && <div className="mt-3 grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-6">
        <input className="av-input" placeholder="레벨(숫자)" value={draft.level} onChange={(e) => setDraft({ ...draft, level: e.target.value })} />
        <input className="av-input" placeholder="이름" value={draft.levelName} onChange={(e) => setDraft({ ...draft, levelName: e.target.value })} />
        <input className="av-input" placeholder="최소 포인트" value={draft.minPoint} onChange={(e) => setDraft({ ...draft, minPoint: e.target.value })} />
        <input className="av-input" placeholder="레벨업 크레딧" value={draft.levelupCredit} onChange={(e) => setDraft({ ...draft, levelupCredit: e.target.value })} />
        <input className="av-input" placeholder="쿠폰 코드(선택)" value={draft.levelupCouponCode} onChange={(e) => setDraft({ ...draft, levelupCouponCode: e.target.value })} />
        <button type="button" className="av-btn bg-slate-900 text-white" onClick={() => void create()}>레벨 추가</button>
      </div>}
    </section>
  );
}

/* ── 쿠폰 ── */
const EMPTY_COUPON = { code: "", name: "", discountType: "CREDIT", discountValue: "", minPurchase: "" };

function CouponsSection({
  flash, canCreate, canUpdate,
}: {
  flash: (m: string) => void;
  canCreate: boolean;
  canUpdate: boolean;
}) {
  const [coupons, setCoupons] = useState<Coupon[]>([]);
  const [draft, setDraft] = useState({ ...EMPTY_COUPON });

  const load = useCallback(async () => { setCoupons((await getCoupons("", 1, 50)).items); }, []);
  useEffect(() => { void load(); }, [load]);

  const create = async () => {
    if (!canCreate) return;
    if (!draft.code.trim() || !draft.name.trim()) { flash("쿠폰 코드와 이름을 입력하세요."); return; }
    await createCoupon({
      code: draft.code, name: draft.name, discountType: draft.discountType,
      discountValue: Number(draft.discountValue) || 0, minPurchase: Number(draft.minPurchase) || 0,
      validFrom: null, validUntil: null, maxIssue: null, enabled: true,
    });
    flash(`쿠폰 ${draft.code.toUpperCase()} 생성됨`);
    setDraft({ ...EMPTY_COUPON });
    await load();
  };

  const issue = async (c: Coupon) => {
    if (!canCreate) return;
    const raw = window.prompt(`'${c.code}' 쿠폰을 발급할 사용자 ID`);
    if (!raw) return;
    const userId = Number(raw);
    if (!userId) { flash("사용자 ID가 올바르지 않습니다."); return; }
    try {
      await issueCoupon(c.id, userId);
      flash(`쿠폰 ${c.code} → 사용자 ${userId} 발급됨`);
      await load();
    } catch {
      flash("발급 실패 — 수량 소진 또는 오류");
    }
  };

  return (
    <section className="mb-5 rounded-xl border border-slate-200 bg-card p-4">
      <h3 className="mb-3 text-sm font-semibold text-slate-800">쿠폰 (CREDIT 즉시적립 · PERCENT/AMOUNT 결제할인)</h3>
      <div className="overflow-x-auto">
        <table className="w-full min-w-[760px] text-sm">
          <thead>
            <tr className="border-b border-slate-200 text-left text-xs text-slate-500">
              <th className="px-3 py-2">코드</th>
              <th className="px-3 py-2">이름</th>
              <th className="px-3 py-2">유형</th>
              <th className="px-3 py-2">값</th>
              <th className="px-3 py-2">발급수</th>
              <th className="px-3 py-2">상태</th>
              <th className="px-3 py-2">동작</th>
            </tr>
          </thead>
          <tbody>
            {coupons.map((c) => (
              <tr key={c.id} className="border-b border-slate-100">
                <td className="px-3 py-2 font-mono text-xs font-semibold">{c.code}</td>
                <td className="px-3 py-2">{c.name}</td>
                <td className="px-3 py-2 text-xs">{c.discountType}</td>
                <td className="px-3 py-2">{c.discountValue}{c.discountType === "PERCENT" ? "%" : ""}</td>
                <td className="px-3 py-2 text-xs">{c.issuedCount}{c.maxIssue ? ` / ${c.maxIssue}` : ""}</td>
                <td className="px-3 py-2">
                  <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${c.enabled ? "bg-emerald-50 text-emerald-600" : "bg-slate-100 text-slate-500"}`}>
                    {c.enabled ? "활성" : "비활성"}
                  </span>
                </td>
                <td className="px-3 py-2">{canCreate && <button type="button" className="av-btn text-xs" onClick={() => void issue(c)}>발급</button>}</td>
              </tr>
            ))}
            {coupons.length === 0 && <tr><td colSpan={7} className="px-3 py-6 text-center text-slate-400">쿠폰이 없습니다.</td></tr>}
          </tbody>
        </table>
      </div>
      {canCreate && <div className="mt-3 grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-6">
        <input className="av-input" placeholder="코드" value={draft.code} onChange={(e) => setDraft({ ...draft, code: e.target.value })} />
        <input className="av-input" placeholder="이름" value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} />
        <select className="av-input" value={draft.discountType} onChange={(e) => setDraft({ ...draft, discountType: e.target.value })}>
          <option value="CREDIT">CREDIT(크레딧 적립)</option>
          <option value="PERCENT">PERCENT(% 할인)</option>
          <option value="AMOUNT">AMOUNT(정액 할인)</option>
        </select>
        <input className="av-input" placeholder="값" value={draft.discountValue} onChange={(e) => setDraft({ ...draft, discountValue: e.target.value })} />
        <input className="av-input" placeholder="최소결제(원)" value={draft.minPurchase} onChange={(e) => setDraft({ ...draft, minPurchase: e.target.value })} />
        <button type="button" className="av-btn bg-slate-900 text-white" onClick={() => void create()}>쿠폰 생성</button>
      </div>}
    </section>
  );
}

/* ── 리워드 이력 ── */
function HistorySection() {
  const [rows, setRows] = useState<RewardHistoryRow[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [keyword, setKeyword] = useState("");
  const [eventCode, setEventCode] = useState("");
  const size = 20;

  const load = useCallback(async (p: number, kw: string, ev: string) => {
    const res = await getRewardHistory({ keyword: kw || undefined, eventCode: ev || undefined, page: p, size });
    setRows(res.items);
    setTotal(res.total);
    setPage(res.page);
  }, []);
  useEffect(() => { void load(1, "", ""); }, [load]);

  const totalPages = Math.max(1, Math.ceil(total / size));

  return (
    <section className="mb-5 rounded-xl border border-slate-200 bg-card p-4">
      <h3 className="mb-3 text-sm font-semibold text-slate-800">리워드 적립 이력</h3>
      <div className="mb-3 flex flex-wrap items-center gap-2">
        <input className="av-input" placeholder="이메일·이름·사유 검색" value={keyword} onChange={(e) => setKeyword(e.target.value)} />
        <select className="av-input" value={eventCode} onChange={(e) => setEventCode(e.target.value)}>
          <option value="">전체 이벤트</option>
          <option value="COMMUNITY_POST_CREATE">글 작성</option>
          <option value="COMMUNITY_COMMENT_CREATE">댓글 작성</option>
          <option value="APPLICATION_CASE_READY">지원건 완료</option>
          <option value="DAILY_LOGIN">일일 로그인</option>
          <option value="CREDIT_PURCHASE">크레딧 구매</option>
          <option value="LEVEL_UP">레벨업</option>
        </select>
        <button type="button" className="av-btn" onClick={() => void load(1, keyword, eventCode)}>검색</button>
      </div>
      <div className="max-h-[50vh] overflow-auto rounded-lg border border-slate-100">
        <table className="w-full min-w-[820px] text-sm">
          <thead className="sticky top-0 z-10 bg-card">
            <tr className="border-b border-slate-200 text-left text-xs text-slate-500">
              <th className="px-3 py-2">시각</th>
              <th className="px-3 py-2">사용자</th>
              <th className="px-3 py-2">이벤트</th>
              <th className="px-3 py-2">포인트</th>
              <th className="px-3 py-2">크레딧</th>
              <th className="px-3 py-2">레벨</th>
              <th className="px-3 py-2">사유</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((h) => (
              <tr key={h.id} className="border-b border-slate-100">
                <td className="px-3 py-2 text-xs text-slate-500">{fmt(h.createdAt)}</td>
                <td className="px-3 py-2 text-xs">{h.userName}<span className="text-slate-400"> ({h.userEmail})</span></td>
                <td className="px-3 py-2 font-mono text-xs">{h.eventCode}</td>
                <td className="px-3 py-2 text-emerald-600">+{h.pointDelta}</td>
                <td className="px-3 py-2 text-sky-600">{h.creditDelta > 0 ? `+${h.creditDelta}` : "-"}</td>
                <td className="px-3 py-2 text-xs">{h.levelBefore !== h.levelAfter ? `Lv.${h.levelBefore}→${h.levelAfter}` : `Lv.${h.levelAfter ?? "-"}`}</td>
                <td className="px-3 py-2 text-xs text-slate-500">{h.reason ?? "-"}</td>
              </tr>
            ))}
            {rows.length === 0 && <tr><td colSpan={7} className="px-3 py-8 text-center text-slate-400">이력이 없습니다.</td></tr>}
          </tbody>
        </table>
      </div>
      {total > 0 && (
        <div className="mt-3 flex items-center justify-between text-sm">
          <span className="text-slate-500">총 {total.toLocaleString("ko-KR")}건</span>
          <div className="flex items-center gap-2">
            <button type="button" className="av-btn text-xs" disabled={page <= 1} onClick={() => void load(page - 1, keyword, eventCode)}>이전</button>
            <span className="text-xs">{page} / {totalPages}</span>
            <button type="button" className="av-btn text-xs" disabled={page >= totalPages} onClick={() => void load(page + 1, keyword, eventCode)}>다음</button>
          </div>
        </div>
      )}
    </section>
  );
}
