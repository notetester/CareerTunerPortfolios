import { useEffect, useState } from "react";
import { History, RefreshCw, Search } from "lucide-react";
import AdminShell from "../../../components/AdminShell";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent } from "@/app/components/ui/card";
import { Input } from "@/app/components/ui/input";
import { getAdminActionLogs } from "../api";
import type { AdminActionLogRow } from "../types";

function formatDateTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(date);
}

export function AdminActionLogsPage() {
  const [rows, setRows] = useState<AdminActionLogRow[]>([]);
  const [keyword, setKeyword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      setRows(await getAdminActionLogs({ keyword, limit: 150 }));
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "액션 로그를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  return (
    <AdminShell
      active="action-logs"
      breadcrumb="운영 감사"
      title="관리자 액션 로그"
      icon={History}
      desc="회원 상태 변경, 권한 부여, 운영 정책 수정처럼 관리자 권한으로 수행한 작업을 추적합니다."
      actions={<Button variant="outline" onClick={() => void load()} disabled={loading}><RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />새로고침</Button>}
    >
      <Card className="border-slate-200 bg-card">
        <CardContent className="space-y-4 p-4">
          <div className="flex flex-col gap-2 md:flex-row">
            <div className="relative flex-1">
              <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
              <Input value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="관리자/대상 이메일, 액션, 사유 검색" className="pl-9" />
            </div>
            <Button className="bg-blue-600 text-white hover:bg-blue-700" onClick={() => void load()}>검색</Button>
          </div>
          {error && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}
          <div className="overflow-x-auto">
            <table className="w-full min-w-[900px] text-left text-sm">
              <thead className="border-b bg-slate-50 text-xs uppercase text-slate-500">
                <tr>
                  <th className="px-3 py-3">시각</th>
                  <th className="px-3 py-3">관리자</th>
                  <th className="px-3 py-3">대상</th>
                  <th className="px-3 py-3">액션</th>
                  <th className="px-3 py-3">타입</th>
                  <th className="px-3 py-3">사유</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => (
                  <tr key={row.id} className="border-b last:border-0">
                    <td className="px-3 py-3 text-slate-500">{formatDateTime(row.createdAt)}</td>
                    <td className="px-3 py-3">{row.actorEmail ?? `#${row.actorUserId ?? "-"}`}</td>
                    <td className="px-3 py-3">{row.targetEmail ?? (row.targetUserId ? `#${row.targetUserId}` : "-")}</td>
                    <td className="px-3 py-3"><Badge className="bg-blue-100 text-blue-700">{row.actionType}</Badge></td>
                    <td className="px-3 py-3 text-slate-600">{row.targetType}</td>
                    <td className="px-3 py-3 text-slate-600">{row.reason ?? "-"}</td>
                  </tr>
                ))}
                {!rows.length && (
                  <tr><td colSpan={6} className="px-3 py-8 text-center text-slate-500">기록된 관리자 액션이 없습니다.</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>
    </AdminShell>
  );
}
