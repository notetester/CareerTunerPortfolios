import { useCallback, useEffect, useState } from "react";
import { Activity, RotateCw } from "lucide-react";

import AdminShell from "../../../components/AdminShell";
import {
  getActivityLogs,
  getSecurityHistories,
  type ActivityLog,
  type SecurityHistory,
} from "../api";

type TabKey = "activity" | "security";
const PAGE_SIZE = 50;

const DOMAINS = ["", "AUTH", "ADMIN", "COMMUNITY", "MESSENGER", "PROFILE", "SUPPORT", "INTERVIEW", "APPLICATION", "BILLING", "GENERAL"];
const EVENT_TYPES = ["", "EMAIL_VERIFY", "RESET_PASSWORD", "PHONE_VERIFY", "FIND_ID", "PASSWORD_CHANGE"];

function fmt(value: string | null): string {
  if (!value) return "-";
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? value : d.toLocaleString("ko-KR");
}

function StatusPill({ ok }: { ok: boolean | null }) {
  if (ok === null) return <span className="text-slate-400">-</span>;
  return (
    <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${ok ? "bg-emerald-50 text-emerald-600" : "bg-red-50 text-red-600"}`}>
      {ok ? "성공" : "실패"}
    </span>
  );
}

export function AdminActivityLogsPage() {
  const [tab, setTab] = useState<TabKey>("activity");
  const [keyword, setKeyword] = useState("");
  const [domain, setDomain] = useState("");
  const [eventType, setEventType] = useState("");
  const [successOnly, setSuccessOnly] = useState<"" | "true" | "false">("");
  const [page, setPage] = useState(0);

  const [activity, setActivity] = useState<ActivityLog[]>([]);
  const [security, setSecurity] = useState<SecurityHistory[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const success = successOnly === "" ? undefined : successOnly === "true";
      if (tab === "activity") {
        const res = await getActivityLogs({ keyword, domain: domain || undefined, success, page, size: PAGE_SIZE });
        setActivity(res.items);
        setTotal(res.total);
      } else {
        const res = await getSecurityHistories({ keyword, eventType: eventType || undefined, success, page, size: PAGE_SIZE });
        setSecurity(res.items);
        setTotal(res.total);
      }
    } finally {
      setLoading(false);
    }
  }, [tab, keyword, domain, eventType, successOnly, page]);

  useEffect(() => {
    void load();
  }, [load]);

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));

  return (
    <AdminShell
      active="activity-logs"
      breadcrumb="보안 / 감사"
      title="활동 · 보안 로그"
      icon={Activity}
      desc="정적 리소스를 제외한 모든 요청을 자동 기록합니다(비회원 포함). 보안 이력은 인증·계정 민감 이벤트를 단계·행위자별로 추적합니다."
      actions={(
        <button type="button" className="av-btn" onClick={() => void load()} disabled={loading}>
          <RotateCw className={loading ? "animate-spin" : ""} /> 새로고침
        </button>
      )}
    >
      <div className="flex flex-wrap gap-2">
        {([["activity", "활동 로그(전수)"], ["security", "보안 이력 감사"]] as [TabKey, string][]).map(([key, label]) => (
          <button
            key={key}
            type="button"
            className={`av-btn ${tab === key ? "bg-slate-900 text-white" : ""}`}
            onClick={() => { setTab(key); setPage(0); }}
          >
            {label}
          </button>
        ))}
      </div>

      <div className="mt-4 flex flex-wrap items-center gap-2">
        <input
          className="av-input"
          placeholder="URI·코드·IP·requestId 검색"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          onKeyDown={(e) => { if (e.key === "Enter") { setPage(0); void load(); } }}
        />
        {tab === "activity" ? (
          <select className="av-input" value={domain} onChange={(e) => { setDomain(e.target.value); setPage(0); }}>
            {DOMAINS.map((d) => <option key={d} value={d}>{d === "" ? "전체 도메인" : d}</option>)}
          </select>
        ) : (
          <select className="av-input" value={eventType} onChange={(e) => { setEventType(e.target.value); setPage(0); }}>
            {EVENT_TYPES.map((t) => <option key={t} value={t}>{t === "" ? "전체 이벤트" : t}</option>)}
          </select>
        )}
        <select className="av-input" value={successOnly} onChange={(e) => { setSuccessOnly(e.target.value as "" | "true" | "false"); setPage(0); }}>
          <option value="">성공/실패 전체</option>
          <option value="true">성공만</option>
          <option value="false">실패만</option>
        </select>
        <button type="button" className="av-btn bg-slate-900 text-white" onClick={() => { setPage(0); void load(); }}>검색</button>
        <span className="ml-auto text-sm text-slate-500">총 {total.toLocaleString()}건</span>
      </div>

      <div className="mt-4 overflow-x-auto rounded-xl border border-slate-200 bg-card">
        {tab === "activity" ? (
          <table className="w-full min-w-[900px] text-sm">
            <thead>
              <tr className="border-b border-slate-200 text-left text-xs text-slate-500">
                <th className="px-3 py-2">시각</th>
                <th className="px-3 py-2">사용자</th>
                <th className="px-3 py-2">도메인</th>
                <th className="px-3 py-2">메서드/URI</th>
                <th className="px-3 py-2">코드</th>
                <th className="px-3 py-2">상태</th>
                <th className="px-3 py-2">응답(ms)</th>
                <th className="px-3 py-2">IP</th>
              </tr>
            </thead>
            <tbody>
              {activity.length === 0 && (
                <tr><td colSpan={8} className="px-3 py-8 text-center text-slate-400">기록이 없습니다.</td></tr>
              )}
              {activity.map((r) => (
                <tr key={r.id} className="border-b border-slate-100">
                  <td className="whitespace-nowrap px-3 py-2 text-xs text-slate-500">{fmt(r.createdAt)}</td>
                  <td className="px-3 py-2 font-mono">{r.userId ?? "게스트"}</td>
                  <td className="px-3 py-2"><span className="rounded bg-slate-100 px-1.5 py-0.5 text-xs">{r.activityDomain}</span></td>
                  <td className="px-3 py-2"><span className="font-mono text-xs text-slate-400">{r.httpMethod}</span> {r.requestUri}</td>
                  <td className="px-3 py-2 text-xs text-slate-500">{r.activityCode ?? "-"}</td>
                  <td className="px-3 py-2">{r.responseStatus ?? "-"} <StatusPill ok={r.success} /></td>
                  <td className="px-3 py-2 font-mono text-xs">{r.responseTimeMs ?? "-"}</td>
                  <td className="px-3 py-2 font-mono text-xs text-slate-500">{r.ipAddress ?? "-"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <table className="w-full min-w-[900px] text-sm">
            <thead>
              <tr className="border-b border-slate-200 text-left text-xs text-slate-500">
                <th className="px-3 py-2">시각</th>
                <th className="px-3 py-2">이벤트</th>
                <th className="px-3 py-2">단계</th>
                <th className="px-3 py-2">대상</th>
                <th className="px-3 py-2">행위자</th>
                <th className="px-3 py-2">상태</th>
                <th className="px-3 py-2">사유</th>
                <th className="px-3 py-2">IP</th>
              </tr>
            </thead>
            <tbody>
              {security.length === 0 && (
                <tr><td colSpan={8} className="px-3 py-8 text-center text-slate-400">기록이 없습니다.</td></tr>
              )}
              {security.map((r) => (
                <tr key={r.id} className="border-b border-slate-100">
                  <td className="whitespace-nowrap px-3 py-2 text-xs text-slate-500">{fmt(r.occurredAt)}</td>
                  <td className="px-3 py-2 font-medium">{r.eventType}</td>
                  <td className="px-3 py-2 text-xs text-slate-500">{r.eventStage ?? "-"}</td>
                  <td className="px-3 py-2 font-mono text-xs">{r.userId ?? r.inputIdentifier ?? r.targetEmail ?? "-"}</td>
                  <td className="px-3 py-2 font-mono text-xs">{r.actorUserId ?? "-"}</td>
                  <td className="px-3 py-2"><StatusPill ok={r.success} /></td>
                  <td className="px-3 py-2 text-xs text-slate-500">{r.failReason ?? "-"}</td>
                  <td className="px-3 py-2 font-mono text-xs text-slate-500">{r.ipAddress ?? "-"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <div className="mt-3 flex items-center justify-center gap-3 text-sm">
        <button type="button" className="av-btn" disabled={page <= 0} onClick={() => setPage((p) => Math.max(0, p - 1))}>이전</button>
        <span className="font-mono">{page + 1} / {totalPages}</span>
        <button type="button" className="av-btn" disabled={page >= totalPages - 1} onClick={() => setPage((p) => p + 1)}>다음</button>
      </div>
    </AdminShell>
  );
}
