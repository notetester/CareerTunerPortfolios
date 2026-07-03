import { useEffect, useState } from "react";
import { History, RefreshCw } from "lucide-react";
import AdminShell from "../../../components/AdminShell";
import {
  AdminListFooter,
  AdminListToolbar,
  AdminSortableHeader,
  useAdminListTools,
  type AdminListColumn,
} from "../../../components/AdminListTools";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent } from "@/app/components/ui/card";
import { getAdminActionLogs } from "../api";
import type { AdminActionLogRow } from "../types";

function formatDateTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(date);
}

const ACTION_LOG_COLUMNS: AdminListColumn<AdminActionLogRow>[] = [
  { id: "createdAt", label: "시각", getText: (row) => formatDateTime(row.createdAt), sortable: true },
  { id: "actor", label: "관리자", getText: (row) => row.actorEmail ?? `#${row.actorUserId ?? "-"}`, sortable: true },
  { id: "target", label: "대상", getText: (row) => row.targetEmail ?? (row.targetUserId ? `#${row.targetUserId}` : "-"), sortable: true },
  { id: "action", label: "액션", getText: (row) => row.actionType, sortable: true },
  { id: "type", label: "타입", getText: (row) => row.targetType, sortable: true },
  { id: "reason", label: "사유", getText: (row) => row.reason ?? "-", sortable: true },
  { id: "ip", label: "IP", getText: (row) => row.ipAddress ?? "-", sortable: true },
];

export function AdminActionLogsPage() {
  const [rows, setRows] = useState<AdminActionLogRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const list = useAdminListTools(rows, {
    columns: ACTION_LOG_COLUMNS,
    getRowId: (row) => row.id,
    defaultSortId: "createdAt",
    defaultSortDir: "desc",
  });

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      setRows(await getAdminActionLogs({ limit: 500 }));
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
          <AdminListToolbar state={list} fileName="admin_action_logs" />
          {error && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}
          <div className="overflow-x-auto">
            <table className="av-table min-w-[900px]">
              <thead className="border-b bg-slate-50 text-xs uppercase text-slate-500">
                <tr>
                  <AdminSortableHeader state={list} columnId="createdAt">시각</AdminSortableHeader>
                  <AdminSortableHeader state={list} columnId="actor">관리자</AdminSortableHeader>
                  <AdminSortableHeader state={list} columnId="target">대상</AdminSortableHeader>
                  <AdminSortableHeader state={list} columnId="action">액션</AdminSortableHeader>
                  <AdminSortableHeader state={list} columnId="type">타입</AdminSortableHeader>
                  <AdminSortableHeader state={list} columnId="reason">사유</AdminSortableHeader>
                </tr>
              </thead>
              <tbody>
                {list.visibleRows.map((row) => (
                  <tr key={row.id} className="border-b last:border-0">
                    <td className="px-3 py-3 text-slate-500">{formatDateTime(row.createdAt)}</td>
                    <td className="px-3 py-3">{row.actorEmail ?? `#${row.actorUserId ?? "-"}`}</td>
                    <td className="px-3 py-3">{row.targetEmail ?? (row.targetUserId ? `#${row.targetUserId}` : "-")}</td>
                    <td className="px-3 py-3"><Badge className="bg-blue-100 text-blue-700">{row.actionType}</Badge></td>
                    <td className="px-3 py-3 text-slate-600">{row.targetType}</td>
                    <td className="px-3 py-3 text-slate-600">{row.reason ?? "-"}</td>
                  </tr>
                ))}
                {!list.visibleRows.length && (
                  <tr><td colSpan={6} className="px-3 py-8 text-center text-slate-500">기록된 관리자 액션이 없습니다.</td></tr>
                )}
              </tbody>
            </table>
          </div>
          <AdminListFooter state={list} />
        </CardContent>
      </Card>
    </AdminShell>
  );
}
