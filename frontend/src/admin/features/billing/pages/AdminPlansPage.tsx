import { useEffect, useState } from "react";
import { Package, RefreshCw } from "lucide-react";
import AdminShell from "../../../components/AdminShell";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { getAdminPlans, type AdminPlans } from "../api";

const won = (n: number) => `${n.toLocaleString("ko-KR")}원`;

export function AdminPlansPage() {
  const [data, setData] = useState<AdminPlans | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      setData(await getAdminPlans());
    } catch (e) {
      setError(e instanceof Error ? e.message : "요금제 정보를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void load(); }, []);

  return (
    <AdminShell
      active="plans"
      breadcrumb="요금제 관리"
      title="요금제·크레딧 상품"
      icon={Package}
      desc="구독 요금제와 크레딧 충전 상품 구성을 조회합니다."
      actions={
        <Button variant="outline" onClick={() => void load()} disabled={loading}>
          <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
        </Button>
      }
    >
      {error && <div className="mb-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}

      <div className="grid gap-5 lg:grid-cols-2">
        <Card className="border-slate-200 bg-card">
          <CardHeader><CardTitle className="text-base">구독 요금제</CardTitle></CardHeader>
          <CardContent className="space-y-2">
            {(data?.plans ?? []).map((p) => (
              <div key={p.code} className="flex items-center justify-between gap-3 rounded-lg border border-slate-100 p-3">
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="font-semibold text-slate-900">{p.name}</span>
                    <span className="text-xs text-slate-400">{p.code}</span>
                    {!p.active && <Badge className="bg-slate-200 text-slate-600">비활성</Badge>}
                  </div>
                  <div className="truncate text-xs text-slate-500">{p.description}</div>
                </div>
                <div className="shrink-0 text-right">
                  <div className="font-black text-slate-900">{won(p.monthlyPrice)}</div>
                  <div className="text-xs text-slate-400">연 {won(p.yearlyPrice)}/월</div>
                </div>
              </div>
            ))}
            {(data?.plans?.length ?? 0) === 0 && !loading && <div className="rounded-lg bg-slate-50 p-4 text-sm text-slate-400">요금제가 없습니다.</div>}
          </CardContent>
        </Card>

        <Card className="border-slate-200 bg-card">
          <CardHeader><CardTitle className="text-base">크레딧 충전 상품</CardTitle></CardHeader>
          <CardContent className="space-y-2">
            {(data?.creditProducts ?? []).map((c) => (
              <div key={c.code} className="flex items-center justify-between gap-3 rounded-lg border border-slate-100 p-3">
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="font-semibold text-slate-900">{c.name}</span>
                    {c.badge && <Badge className="bg-amber-100 text-amber-700">{c.badge}</Badge>}
                    {!c.enabled && <Badge className="bg-slate-200 text-slate-600">비활성</Badge>}
                  </div>
                  <div className="text-xs text-slate-500">크레딧 {c.creditAmount}개</div>
                </div>
                <div className="shrink-0 font-black text-slate-900">{won(c.price)}</div>
              </div>
            ))}
            {(data?.creditProducts?.length ?? 0) === 0 && !loading && <div className="rounded-lg bg-slate-50 p-4 text-sm text-slate-400">상품이 없습니다.</div>}
          </CardContent>
        </Card>
      </div>
    </AdminShell>
  );
}
