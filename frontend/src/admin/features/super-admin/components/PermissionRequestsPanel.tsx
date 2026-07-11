import { useCallback, useEffect, useState } from "react";
import { Check, RotateCw, X } from "lucide-react";

import { approvePermissionRequest, getPermissionRequests, rejectPermissionRequest } from "../api";
import type { AdminPermissionRequest } from "../types";

function fmt(value: string | null): string {
  if (!value) return "-";
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? value : d.toLocaleString("ko-KR");
}

/** 관리자 권한 요청/승인 워크플로우 패널 — 대기 요청 목록 + 승인/거절. */
export function PermissionRequestsPanel({ onChanged }: { onChanged?: () => void }) {
  const [status, setStatus] = useState("PENDING");
  const [rows, setRows] = useState<AdminPermissionRequest[]>([]);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);

  const load = useCallback(async () => {
    setBusy(true);
    try {
      setRows(await getPermissionRequests(status));
    } finally {
      setBusy(false);
    }
  }, [status]);

  useEffect(() => {
    void load();
  }, [load]);

  const flash = (m: string) => {
    setMsg(m);
    setTimeout(() => setMsg(null), 2500);
  };

  const decide = async (id: number, approve: boolean) => {
    if (approve) {
      await approvePermissionRequest(id);
      flash("요청을 승인하고 권한을 부여했습니다.");
    } else {
      const reason = window.prompt("거절 사유(선택)") ?? undefined;
      await rejectPermissionRequest(id, reason);
      flash("요청을 거절했습니다.");
    }
    await load();
    onChanged?.();
  };

  return (
    <section className="rounded-xl border border-slate-200 bg-card p-4">
      <div className="flex items-center justify-between gap-2">
        <h3 className="text-sm font-semibold text-slate-800">권한 요청 / 승인</h3>
        <div className="flex items-center gap-2">
          <select className="av-input" value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="PENDING">대기</option>
            <option value="APPROVED">승인됨</option>
            <option value="REJECTED">거절됨</option>
            <option value="">전체</option>
          </select>
          <button type="button" className="av-btn" onClick={() => void load()} disabled={busy}>
            <RotateCw className={busy ? "animate-spin" : ""} />
          </button>
        </div>
      </div>
      {msg && <div className="mt-2 rounded-lg bg-emerald-50 px-3 py-2 text-sm text-emerald-700">{msg}</div>}
      <div className="mt-3 overflow-x-auto">
        <table className="w-full min-w-[720px] text-sm">
          <thead>
            <tr className="border-b border-slate-200 text-left text-xs text-slate-500">
              <th className="py-2 pr-3">요청일</th>
              <th className="py-2 pr-3">대상 관리자</th>
              <th className="py-2 pr-3">권한</th>
              <th className="py-2 pr-3">사유</th>
              <th className="py-2 pr-3">상태</th>
              <th className="py-2">처리</th>
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 && (
              <tr><td colSpan={6} className="py-6 text-center text-slate-400">요청이 없습니다.</td></tr>
            )}
            {rows.map((r) => (
              <tr key={r.id} className="border-b border-slate-100">
                <td className="py-2 pr-3 text-xs text-slate-500">{fmt(r.createdAt)}</td>
                <td className="py-2 pr-3">
                  <div className="font-medium text-slate-800">{r.userName ?? `#${r.userId}`}</div>
                  <div className="text-xs text-slate-400">{r.userEmail}</div>
                </td>
                <td className="py-2 pr-3">
                  <span className="rounded bg-slate-100 px-1.5 py-0.5 font-mono text-xs">{r.permissionCode}</span>
                </td>
                <td className="py-2 pr-3 text-xs text-slate-500">{r.description ?? "-"}</td>
                <td className="py-2 pr-3">
                  <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${
                    r.status === "PENDING" ? "bg-amber-50 text-amber-600"
                      : r.status === "APPROVED" ? "bg-emerald-50 text-emerald-600" : "bg-red-50 text-red-600"}`}>
                    {r.status}
                  </span>
                </td>
                <td className="py-2">
                  {r.status === "PENDING" ? (
                    <div className="flex gap-1">
                      <button type="button" className="av-btn text-xs" onClick={() => void decide(r.id, true)}>
                        <Check className="h-3.5 w-3.5" /> 승인
                      </button>
                      <button type="button" className="av-btn text-xs" onClick={() => void decide(r.id, false)}>
                        <X className="h-3.5 w-3.5" /> 거절
                      </button>
                    </div>
                  ) : (
                    <span className="text-xs text-slate-400">처리 완료</span>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
