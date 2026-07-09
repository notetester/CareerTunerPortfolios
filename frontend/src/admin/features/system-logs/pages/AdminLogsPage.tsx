import { useEffect, useState } from "react";
import { ScrollText, RefreshCw } from "lucide-react";
import AdminShell from "../../../components/AdminShell";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent } from "@/app/components/ui/card";
import { getAdminAiUsageLogs, type AdminAiUsageLogRow } from "../api";

const STATUS_FILTERS = [
  { value: "", label: "전체" },
  { value: "SUCCESS", label: "성공" },
  { value: "FAILED", label: "실패" },
];

const fmt = (v: string) => new Intl.DateTimeFormat("ko-KR", { dateStyle: "short", timeStyle: "medium" }).format(new Date(v));

export function AdminLogsPage() {
  const [rows, setRows] = useState<AdminAiUsageLogRow[]>([]);
  const [status, setStatus] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = async (nextStatus = status) => {
    setLoading(true);
    setError(null);
    try {
      setRows(await getAdminAiUsageLogs(nextStatus || undefined, 150));
    } catch (e) {
      setError(e instanceof Error ? e.message : "로그를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load("");
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <AdminShell
      active="logs"
      breadcrumb="시스템 로그"
      title="AI 사용 로그"
      icon={ScrollText}
      desc="전체 사용자의 AI 호출 이력과 실패를 시간순으로 조회합니다."
      actions={
        <Button variant="outline" onClick={() => void load()} disabled={loading}>
          <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
        </Button>
      }
    >
      <div className="mb-3 flex flex-wrap gap-2">
        {STATUS_FILTERS.map((f) => (
          <Button key={f.value} size="sm" variant={status === f.value ? "default" : "outline"}
            onClick={() => { setStatus(f.value); void load(f.value); }}>
            {f.label}
          </Button>
        ))}
      </div>

      {error && <div className="mb-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}

      <Card className="border-slate-200 bg-card">
        <CardContent className="p-0">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-slate-200 text-left text-xs font-semibold text-slate-500">
                  <th className="px-4 py-3">시각</th>
                  <th className="px-4 py-3">회원</th>
                  <th className="px-4 py-3">기능</th>
                  <th className="px-4 py-3">모델</th>
                  <th className="px-4 py-3">토큰/크레딧</th>
                  <th className="px-4 py-3">상태</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <tr key={r.id} className="border-b border-slate-100 align-top">
                    <td className="px-4 py-2.5 whitespace-nowrap text-xs text-slate-500">{fmt(r.createdAt)}</td>
                    <td className="px-4 py-2.5 text-xs text-slate-600">{r.userEmail ?? `#${r.userId ?? "-"}`}</td>
                    <td className="px-4 py-2.5 font-semibold text-slate-800">{r.featureType}</td>
                    <td className="px-4 py-2.5 text-xs text-slate-500">{r.model ?? "-"}</td>
                    <td className="px-4 py-2.5 text-xs text-slate-500">{r.tokenUsage ?? 0} / {r.creditUsed ?? 0}</td>
                    <td className="px-4 py-2.5">
                      <Badge className={r.status === "SUCCESS" ? "bg-green-100 text-green-700" : r.status === "FAILED" ? "bg-red-100 text-red-700" : "bg-slate-200 text-slate-600"}>
                        {r.status}
                      </Badge>
                      {r.errorMessage && <div className="mt-1 max-w-[280px] truncate text-xs text-red-600">{r.errorMessage}</div>}
                    </td>
                  </tr>
                ))}
                {rows.length === 0 && !loading && (
                  <tr><td colSpan={6} className="px-4 py-10 text-center text-sm text-slate-400">로그가 없습니다.</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>
    </AdminShell>
  );
}
