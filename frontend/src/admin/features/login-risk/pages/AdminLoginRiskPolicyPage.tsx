import { useCallback, useEffect, useState } from "react";
import { ShieldAlert } from "lucide-react";

import AdminShell from "../../../components/AdminShell";
import { getLoginRiskPolicy, updateLoginRiskPolicy, type LoginRiskPolicy } from "../api";
import { useAdminDomainAuthorization } from "@/admin/auth/useAdminAuthorization";

export function AdminLoginRiskPolicyPage() {
  const { canUpdate } = useAdminDomainAuthorization("SECURITY");
  const [policy, setPolicy] = useState<LoginRiskPolicy | null>(null);
  const [enabled, setEnabled] = useState(true);
  const [maxFailed, setMaxFailed] = useState(5);
  const [lockMinutes, setLockMinutes] = useState(10);
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    try {
      const p = await getLoginRiskPolicy();
      setPolicy(p);
      setEnabled(p.enabled);
      setMaxFailed(p.maxFailedCount);
      setLockMinutes(p.lockMinutes);
    } catch {
      setMsg({ ok: false, text: "정책을 불러오지 못했습니다." });
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  useEffect(() => {
    if (!msg) return;
    const t = setTimeout(() => setMsg(null), 2600);
    return () => clearTimeout(t);
  }, [msg]);

  const dirty = !!policy && (enabled !== policy.enabled || maxFailed !== policy.maxFailedCount || lockMinutes !== policy.lockMinutes);

  const save = async () => {
    if (!canUpdate) return;
    setSaving(true);
    try {
      const clampedMax = Math.min(1000, Math.max(1, maxFailed || 1));
      const clampedLock = Math.min(525600, Math.max(1, lockMinutes || 1));
      const p = await updateLoginRiskPolicy({ enabled, maxFailedCount: clampedMax, lockMinutes: clampedLock });
      setPolicy(p);
      setEnabled(p.enabled);
      setMaxFailed(p.maxFailedCount);
      setLockMinutes(p.lockMinutes);
      setMsg({ ok: true, text: "로그인 잠금 정책을 저장했어요." });
    } catch {
      setMsg({ ok: false, text: "저장 실패 — 잠시 후 다시 시도해 주세요." });
    } finally {
      setSaving(false);
    }
  };

  return (
    <AdminShell
      active="login-risk-policy"
      breadcrumb="보안 / 로그인 잠금"
      title="로그인 위험도 정책"
      icon={ShieldAlert}
      desc="비밀번호 연속 실패 시 계정을 자동 잠금하는 브루트포스 방어 정책입니다. 끄면(OFF) 무제약, 켜면(ON) 임계 초과 시 지정 시간 동안 잠급니다. 기본값(5회/10분)은 기존 동작과 동일합니다."
    >
      {msg && (
        <div className={`mb-3 rounded-lg px-3 py-2 text-sm ${msg.ok ? "bg-emerald-50 text-emerald-700" : "bg-rose-50 text-rose-700"}`}>
          {msg.text}
        </div>
      )}

      <section className="max-w-xl rounded-xl border border-slate-200 bg-card p-5">
        {/* 토글 */}
        <div className="flex items-center justify-between border-b border-slate-100 pb-4">
          <div>
            <div className="text-sm font-semibold text-slate-800">자동 잠금 사용</div>
            <div className="text-xs text-slate-500">
              {enabled ? "연속 실패 시 자동 잠금(집행)" : "무제약 — 실패 횟수는 집계하되 잠그지 않음"}
            </div>
          </div>
          <button
            type="button"
            role="switch"
            aria-checked={enabled}
            disabled={!canUpdate}
            onClick={() => { if (canUpdate) setEnabled((v) => !v); }}
            className={`relative h-6 w-11 rounded-full transition-colors ${enabled ? "bg-emerald-500" : "bg-slate-300"}`}
          >
            <span className={`absolute top-0.5 h-5 w-5 rounded-full bg-white transition-transform ${enabled ? "translate-x-5" : "translate-x-0.5"}`} />
          </button>
        </div>

        {/* 임계/잠금시간 */}
        <div className={`mt-4 grid grid-cols-1 gap-4 sm:grid-cols-2 ${enabled ? "" : "opacity-50"}`}>
          <label className="flex flex-col gap-1">
            <span className="av-flabel">잠금 트리거 실패 횟수</span>
            <input type="number" min={1} max={1000} className="av-input" disabled={!enabled || !canUpdate}
                   value={maxFailed} onChange={(e) => setMaxFailed(Number(e.target.value))} />
            <span className="av-hint">연속 실패가 이 횟수에 도달하면 잠금</span>
          </label>
          <label className="flex flex-col gap-1">
            <span className="av-flabel">잠금 유지 시간 (분)</span>
            <input type="number" min={1} max={525600} className="av-input" disabled={!enabled || !canUpdate}
                   value={lockMinutes} onChange={(e) => setLockMinutes(Number(e.target.value))} />
            <span className="av-hint">잠금 후 이 시간이 지나면 자동 해제</span>
          </label>
        </div>

        <div className="mt-5 flex items-center gap-2">
          {canUpdate && <button type="button" className="av-btn bg-slate-900 text-white" disabled={!dirty || saving} onClick={() => void save()}>
            {saving ? "저장 중…" : "저장"}
          </button>}
          {policy && (
            <span className="text-xs text-slate-400">
              현재: {policy.enabled ? `ON · ${policy.maxFailedCount}회 / ${policy.lockMinutes}분` : "OFF(무제약)"}
            </span>
          )}
        </div>
      </section>
    </AdminShell>
  );
}
