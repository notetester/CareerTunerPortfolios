import { useCallback, useEffect, useState } from "react";
import { Download, MailWarning, RotateCw } from "lucide-react";

import AdminShell from "../../../components/AdminShell";
import { getEmailAudit, type EmailAuditRow } from "../api";

const PURPOSES = [
  { v: "", label: "전체 목적" },
  { v: "VERIFY", label: "이메일 인증" },
  { v: "RESET_PW", label: "비밀번호 재설정" },
];
const STATUSES = [
  { v: "", label: "전체 상태" },
  { v: "PENDING", label: "대기(유효)" },
  { v: "USED", label: "사용됨" },
  { v: "EXPIRED", label: "만료" },
];

const STATUS_CLS: Record<string, string> = {
  PENDING: "bg-amber-50 text-amber-600",
  USED: "bg-emerald-50 text-emerald-600",
  EXPIRED: "bg-slate-100 text-slate-500",
};

function fmt(v: string | null): string {
  if (!v) return "-";
  const d = new Date(v);
  return Number.isNaN(d.getTime()) ? v : d.toLocaleString("ko-KR");
}

export function AdminEmailAuditLogPage() {
  const [rows, setRows] = useState<EmailAuditRow[]>([]);
  const [email, setEmail] = useState("");
  const [purpose, setPurpose] = useState("");
  const [status, setStatus] = useState("");
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setRows(await getEmailAudit({ email, purpose, status, limit: 300 }));
    } finally {
      setLoading(false);
    }
  }, [email, purpose, status]);

  useEffect(() => { void load(); }, [load]);

  const exportCsv = () => {
    const header = ["id", "email", "purpose", "status", "createdAt", "expiredAt", "usedAt", "userId"];
    const esc = (s: unknown) => `"${String(s ?? "").replace(/"/g, '""')}"`;
    const lines = [
      header.join(","),
      ...rows.map((r) => [r.id, r.email, r.purpose, r.status, r.createdAt, r.expiredAt, r.usedAt ?? "", r.userId ?? ""].map(esc).join(",")),
    ];
    const blob = new Blob(["﻿" + lines.join("\r\n")], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `email-audit-${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <AdminShell
      active="email-audit-log"
      breadcrumb="이메일 감사 / 전역"
      title="이메일 발급 감사 (전역)"
      icon={MailWarning}
      desc="전체 계정에 걸친 이메일 인증·비밀번호 재설정 토큰 발급 이력을 검색합니다. 스프레이성 재설정 요청 등 이상 징후 탐지용 — 사용자 단위 감사와 별개 축입니다. (토큰 값은 노출하지 않습니다.)"
      actions={(
        <div className="flex gap-2">
          <button type="button" className="av-btn" onClick={() => void load()} disabled={loading}>
            <RotateCw className={loading ? "animate-spin" : ""} /> 새로고침
          </button>
          <button type="button" className="av-btn" onClick={exportCsv} disabled={rows.length === 0}>
            <Download size={14} /> CSV
          </button>
        </div>
      )}
    >
      <div className="mb-4 flex flex-wrap items-center gap-2">
        <input className="av-input" placeholder="이메일 부분 검색" value={email}
               onChange={(e) => setEmail(e.target.value)} />
        <select className="av-input" value={purpose} onChange={(e) => setPurpose(e.target.value)}>
          {PURPOSES.map((p) => <option key={p.v} value={p.v}>{p.label}</option>)}
        </select>
        <select className="av-input" value={status} onChange={(e) => setStatus(e.target.value)}>
          {STATUSES.map((s) => <option key={s.v} value={s.v}>{s.label}</option>)}
        </select>
        <span className="text-xs text-slate-400">{rows.length}건</span>
      </div>

      <div className="max-h-[65vh] overflow-auto rounded-xl border border-slate-200 bg-white">
        <table className="w-full min-w-[820px] text-sm">
          <thead className="sticky top-0 z-10 bg-white">
            <tr className="border-b border-slate-200 text-left text-xs text-slate-500">
              <th className="px-3 py-2">이메일</th>
              <th className="px-3 py-2">목적</th>
              <th className="px-3 py-2">상태</th>
              <th className="px-3 py-2">발급</th>
              <th className="px-3 py-2">만료</th>
              <th className="px-3 py-2">사용</th>
              <th className="px-3 py-2">사용자</th>
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 && (
              <tr><td colSpan={7} className="px-3 py-8 text-center text-slate-400">발급 이력이 없습니다.</td></tr>
            )}
            {rows.map((r) => (
              <tr key={r.id} className="border-b border-slate-100">
                <td className="px-3 py-2 font-mono text-xs">{r.email}</td>
                <td className="px-3 py-2 text-xs">{r.purpose === "RESET_PW" ? "비밀번호 재설정" : "이메일 인증"}</td>
                <td className="px-3 py-2">
                  <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${STATUS_CLS[r.status] ?? "bg-slate-100"}`}>
                    {r.status}
                  </span>
                </td>
                <td className="px-3 py-2 text-xs text-slate-500">{fmt(r.createdAt)}</td>
                <td className="px-3 py-2 text-xs text-slate-500">{fmt(r.expiredAt)}</td>
                <td className="px-3 py-2 text-xs text-slate-500">{fmt(r.usedAt)}</td>
                <td className="px-3 py-2 font-mono text-xs">{r.userId ?? "-"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </AdminShell>
  );
}
