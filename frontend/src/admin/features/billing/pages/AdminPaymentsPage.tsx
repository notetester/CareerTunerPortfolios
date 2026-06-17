import { useEffect, useState } from "react";
import { CreditCard, RefreshCw } from "lucide-react";
import AdminShell from "../../../components/AdminShell";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent } from "@/app/components/ui/card";
import {
  getAdminPayments, getAdminPaymentSummary,
  type AdminPaymentRow, type AdminPaymentSummary,
} from "../api";

const STATUS_FILTERS = [
  { value: "", label: "전체" },
  { value: "PAID", label: "결제완료" },
  { value: "READY", label: "대기" },
  { value: "CANCELED", label: "취소" },
];

const won = (n: number) => `${n.toLocaleString("ko-KR")}원`;
const fmt = (v: string | null) => (v ? new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(new Date(v)) : "-");

export function AdminPaymentsPage() {
  const [rows, setRows] = useState<AdminPaymentRow[]>([]);
  const [summary, setSummary] = useState<AdminPaymentSummary | null>(null);
  const [status, setStatus] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = async (nextStatus = status) => {
    setLoading(true);
    setError(null);
    try {
      const [list, sum] = await Promise.all([getAdminPayments(nextStatus || undefined), getAdminPaymentSummary()]);
      setRows(list);
      setSummary(sum);
    } catch (e) {
      setError(e instanceof Error ? e.message : "결제 내역을 불러오지 못했습니다.");
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
      active="payments"
      breadcrumb="결제 관리"
      title="결제 관리"
      icon={CreditCard}
      desc="구독·크레딧 결제 내역과 매출을 조회합니다."
      actions={
        <Button variant="outline" onClick={() => void load()} disabled={loading}>
          <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
        </Button>
      }
    >
      <div className="mb-4 grid gap-3 sm:grid-cols-3">
        <SummaryCard label="전체 결제" value={`${summary?.totalCount ?? 0}건`} />
        <SummaryCard label="결제 완료" value={`${summary?.paidCount ?? 0}건`} />
        <SummaryCard label="누적 매출" value={won(summary?.totalRevenue ?? 0)} highlight />
      </div>

      <div className="mb-3 flex flex-wrap gap-2">
        {STATUS_FILTERS.map((f) => (
          <Button
            key={f.value}
            size="sm"
            variant={status === f.value ? "default" : "outline"}
            onClick={() => { setStatus(f.value); void load(f.value); }}
          >
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
                  <th className="px-4 py-3">회원</th>
                  <th className="px-4 py-3">상품</th>
                  <th className="px-4 py-3">금액</th>
                  <th className="px-4 py-3">상태</th>
                  <th className="px-4 py-3">결제일</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <tr key={r.id} className="border-b border-slate-100">
                    <td className="px-4 py-3">
                      <div className="font-semibold text-slate-800">{r.userName ?? "-"}</div>
                      <div className="text-xs text-slate-500">{r.userEmail ?? "-"}</div>
                    </td>
                    <td className="px-4 py-3 text-slate-700">
                      {r.plan ? `${r.plan} 구독` : r.creditAmount ? `크레딧 ${r.creditAmount}개` : r.productCode}
                    </td>
                    <td className="px-4 py-3 font-bold text-slate-900">{won(r.amount)}</td>
                    <td className="px-4 py-3">
                      <Badge className={r.status === "PAID" ? "bg-green-100 text-green-700" : r.status === "READY" ? "bg-amber-100 text-amber-700" : "bg-slate-200 text-slate-600"}>
                        {r.status}
                      </Badge>
                    </td>
                    <td className="px-4 py-3 text-xs text-slate-500">{fmt(r.paidAt ?? r.createdAt)}</td>
                  </tr>
                ))}
                {rows.length === 0 && !loading && (
                  <tr><td colSpan={5} className="px-4 py-10 text-center text-sm text-slate-400">결제 내역이 없습니다.</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>
    </AdminShell>
  );
}

function SummaryCard({ label, value, highlight }: { label: string; value: string; highlight?: boolean }) {
  return (
    <Card className="border-slate-200 bg-card">
      <CardContent className="p-4">
        <div className="text-xs font-semibold text-slate-500">{label}</div>
        <div className={`mt-1 text-2xl font-black ${highlight ? "text-blue-600" : "text-slate-900"}`}>{value}</div>
      </CardContent>
    </Card>
  );
}
