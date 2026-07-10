import { useCallback, useEffect, useState } from "react";
import { Bot, RotateCw, Trash2 } from "lucide-react";

import AdminShell from "../../../components/AdminShell";
import {
  deleteChatbotConversation,
  getChatbotConversations,
  getChatbotQuotaPolicy,
  updateChatbotQuotaPolicy,
  type ChatbotConversationRow,
  type ChatbotQuotaPolicy,
} from "../api";

function fmt(v: string | null): string {
  if (!v) return "-";
  const d = new Date(v);
  return Number.isNaN(d.getTime()) ? v : d.toLocaleString("ko-KR");
}

export function AdminChatbotGovernancePage() {
  const [policy, setPolicy] = useState<ChatbotQuotaPolicy | null>(null);
  const [enabled, setEnabled] = useState(false);
  const [dailyLimit, setDailyLimit] = useState(100);
  const [rows, setRows] = useState<ChatbotConversationRow[]>([]);
  const [userFilter, setUserFilter] = useState("");
  const [loading, setLoading] = useState(false);
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);

  const loadPolicy = useCallback(async () => {
    try {
      const p = await getChatbotQuotaPolicy();
      setPolicy(p);
      setEnabled(p.enabled);
      setDailyLimit(p.dailyLimit);
    } catch {
      setMsg({ ok: false, text: "쿼터 정책을 불러오지 못했습니다." });
    }
  }, []);

  const loadRows = useCallback(async () => {
    setLoading(true);
    try {
      const uid = userFilter.trim() ? Number(userFilter.trim()) : undefined;
      setRows(await getChatbotConversations({ userId: Number.isNaN(uid as number) ? undefined : uid, limit: 300 }));
    } finally {
      setLoading(false);
    }
  }, [userFilter]);

  useEffect(() => { void loadPolicy(); }, [loadPolicy]);
  useEffect(() => { void loadRows(); }, [loadRows]);

  useEffect(() => {
    if (!msg) return;
    const t = setTimeout(() => setMsg(null), 2600);
    return () => clearTimeout(t);
  }, [msg]);

  const policyDirty = !!policy && (enabled !== policy.enabled || dailyLimit !== policy.dailyLimit);

  const savePolicy = async () => {
    try {
      const p = await updateChatbotQuotaPolicy({ enabled, dailyLimit: Math.min(1000000, Math.max(1, dailyLimit || 1)) });
      setPolicy(p);
      setEnabled(p.enabled);
      setDailyLimit(p.dailyLimit);
      setMsg({ ok: true, text: "챗봇 쿼터 정책을 저장했어요." });
    } catch {
      setMsg({ ok: false, text: "저장 실패 — 잠시 후 다시 시도해 주세요." });
    }
  };

  const removeConversation = async (id: number) => {
    if (!window.confirm(`대화 세션 #${id} 를 삭제할까요? 되돌릴 수 없습니다.`)) return;
    try {
      await deleteChatbotConversation(id);
      setRows((prev) => prev.filter((r) => r.conversationId !== id));
      setMsg({ ok: true, text: `세션 #${id} 삭제됨` });
    } catch {
      setMsg({ ok: false, text: "삭제 실패 — 잠시 후 다시 시도해 주세요." });
    }
  };

  return (
    <AdminShell
      active="chatbot-governance"
      breadcrumb="AI/분석 운영 / 챗봇 거버넌스"
      title="챗봇 거버넌스"
      icon={Bot}
      desc="AI 챗봇의 일일 사용 쿼터 정책과 대화 세션을 관리합니다. 쿼터는 끄면(OFF) 무제약, 켜면(ON) 로그인 사용자 1인당 하루 한도를 넘는 요청을 차단합니다."
      actions={(
        <button type="button" className="av-btn" onClick={() => void loadRows()} disabled={loading}>
          <RotateCw className={loading ? "animate-spin" : ""} /> 새로고침
        </button>
      )}
    >
      {msg && (
        <div className={`mb-3 rounded-lg px-3 py-2 text-sm ${msg.ok ? "bg-emerald-50 text-emerald-700" : "bg-rose-50 text-rose-700"}`}>
          {msg.text}
        </div>
      )}

      {/* 쿼터 정책 */}
      <section className="mb-5 max-w-xl rounded-xl border border-slate-200 bg-card p-5">
        <div className="flex items-center justify-between border-b border-slate-100 pb-4">
          <div>
            <div className="text-sm font-semibold text-slate-800">일일 사용 쿼터</div>
            <div className="text-xs text-slate-500">
              {enabled ? "로그인 사용자 1인 하루 한도 집행" : "무제약 — 하루 사용량 제한 없음(현재 동작)"}
            </div>
          </div>
          <button
            type="button" role="switch" aria-checked={enabled}
            onClick={() => setEnabled((v) => !v)}
            className={`relative h-6 w-11 rounded-full transition-colors ${enabled ? "bg-emerald-500" : "bg-slate-300"}`}
          >
            <span className={`absolute top-0.5 h-5 w-5 rounded-full bg-white transition-transform ${enabled ? "translate-x-5" : "translate-x-0.5"}`} />
          </button>
        </div>
        <div className={`mt-4 ${enabled ? "" : "opacity-50"}`}>
          <label className="flex flex-col gap-1">
            <span className="av-flabel">1인 하루 허용 질문 수</span>
            <input type="number" min={1} max={1000000} className="av-input" style={{ width: 160 }} disabled={!enabled}
                   value={dailyLimit} onChange={(e) => setDailyLimit(Number(e.target.value))} />
            <span className="av-hint">오늘 사용량이 이 수에 도달하면 다음 요청부터 차단</span>
          </label>
        </div>
        <div className="mt-5 flex items-center gap-2">
          <button type="button" className="av-btn bg-slate-900 text-white" disabled={!policyDirty} onClick={() => void savePolicy()}>저장</button>
          {policy && (
            <span className="text-xs text-slate-400">현재: {policy.enabled ? `ON · 하루 ${policy.dailyLimit}회` : "OFF(무제약)"}</span>
          )}
        </div>
      </section>

      {/* 대화 세션 */}
      <section className="rounded-xl border border-slate-200 bg-card p-4">
        <div className="mb-3 flex flex-wrap items-center gap-2">
          <h3 className="text-sm font-semibold text-slate-800">대화 세션</h3>
          <input className="av-input" style={{ width: 160 }} placeholder="사용자 ID 필터" value={userFilter}
                 onChange={(e) => setUserFilter(e.target.value)} />
          <span className="text-xs text-slate-400">{rows.length}건</span>
        </div>
        <div className="max-h-[60vh] overflow-auto">
          <table className="w-full min-w-[640px] text-sm">
            <thead className="sticky top-0 z-10 bg-card">
              <tr className="border-b border-slate-200 text-left text-xs text-slate-500">
                <th className="px-3 py-2">세션 ID</th>
                <th className="px-3 py-2">사용자</th>
                <th className="px-3 py-2">메시지 수</th>
                <th className="px-3 py-2">최근 활동</th>
                <th className="px-3 py-2">동작</th>
              </tr>
            </thead>
            <tbody>
              {rows.length === 0 && (
                <tr><td colSpan={5} className="px-3 py-8 text-center text-slate-400">대화 세션이 없습니다.</td></tr>
              )}
              {rows.map((r) => (
                <tr key={r.conversationId} className="border-b border-slate-100">
                  <td className="px-3 py-2 font-mono text-xs">{r.conversationId}</td>
                  <td className="px-3 py-2 font-mono text-xs">{r.userId ?? "익명"}</td>
                  <td className="px-3 py-2 text-xs">{r.messageCount ?? "-"}</td>
                  <td className="px-3 py-2 text-xs text-slate-500">{fmt(r.updatedAt)}</td>
                  <td className="px-3 py-2">
                    <button type="button" className="av-btn text-xs text-rose-600" onClick={() => void removeConversation(r.conversationId)}>
                      <Trash2 size={13} /> 삭제
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </AdminShell>
  );
}
