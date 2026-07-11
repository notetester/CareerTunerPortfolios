import { useEffect, useState } from "react";
import { Play, RefreshCw, SlidersHorizontal } from "lucide-react";
import AdminShell from "../../../components/AdminShell";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Input } from "@/app/components/ui/input";
import { Textarea } from "@/app/components/ui/textarea";
import { useAdminDomainAuthorization } from "@/admin/auth/useAdminAuthorization";
import { getAdminPolicies, runAdminPolicy, updateAdminPolicy } from "../api";
import type { AdminSystemPolicyRow } from "../types";

function formatDateTime(value: string | null): string {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(date);
}

export function AdminPoliciesPage() {
  const policyAuthorization = useAdminDomainAuthorization("POLICY");
  const [rows, setRows] = useState<AdminSystemPolicyRow[]>([]);
  const [editing, setEditing] = useState<Record<string, { configJson: string; scheduleType: string; active: boolean; reason: string }>>({});
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const nextRows = await getAdminPolicies();
      setRows(nextRows);
      setEditing(Object.fromEntries(nextRows.map((row) => [row.policyCode, {
        configJson: row.configJson,
        scheduleType: row.scheduleType,
        active: row.active,
        reason: "",
      }])));
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "운영 정책을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const updateEdit = (code: string, patch: Partial<{ configJson: string; scheduleType: string; active: boolean; reason: string }>) => {
    setEditing((items) => ({ ...items, [code]: { ...items[code], ...patch } }));
  };

  const save = async (code: string) => {
    if (!policyAuthorization.canUpdate) {
      setError("운영 정책을 수정할 권한이 없습니다.");
      return;
    }
    const item = editing[code];
    if (!item) return;
    setMessage(null);
    setError(null);
    try {
      JSON.parse(item.configJson);
      await updateAdminPolicy(code, item);
      setMessage(`${code} 정책을 저장했습니다.`);
      await load();
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "정책 저장에 실패했습니다. JSON 형식도 확인해 주세요.");
    }
  };

  const run = async (code: string) => {
    if (!policyAuthorization.canUpdate) {
      setError("운영 정책을 실행할 권한이 없습니다.");
      return;
    }
    setMessage(null);
    setError(null);
    try {
      const result = await runAdminPolicy(code, editing[code]?.reason);
      setMessage(result.message);
      await load();
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "정책 실행 요청에 실패했습니다.");
    }
  };

  return (
    <AdminShell
      active="policies"
      breadcrumb="운영 정책"
      title="운영 정책 관리"
      icon={SlidersHorizontal}
      desc="휴면 계정, 로그인 잠금, 이메일 토큰 정리, AI 사용 이력 보관 정책을 권한 범위에 따라 조회·관리합니다."
      actions={<Button variant="outline" onClick={() => void load()} disabled={loading}><RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />새로고침</Button>}
    >
      <div className="space-y-4">
        {error && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}
        {message && <div className="rounded-lg border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-700">{message}</div>}
        {rows.map((row) => {
          const edit = editing[row.policyCode];
          return (
            <Card key={row.policyCode} className="border-slate-200 bg-card">
              <CardHeader>
                <CardTitle className="flex items-center justify-between gap-3 text-base">
                  <span>{row.displayName}</span>
                  <Badge className={row.active ? "bg-green-100 text-green-700" : "bg-slate-200 text-slate-700"}>{row.active ? "사용" : "중지"}</Badge>
                </CardTitle>
                <p className="text-sm text-slate-500">{row.description}</p>
              </CardHeader>
              <CardContent className="space-y-3">
                <div className="grid gap-3 md:grid-cols-3">
                  <Input disabled={!policyAuthorization.canUpdate} value={edit?.scheduleType ?? ""} onChange={(event) => updateEdit(row.policyCode, { scheduleType: event.target.value })} placeholder="실행 주기" />
                  <select disabled={!policyAuthorization.canUpdate} value={String(edit?.active ?? false)} onChange={(event) => updateEdit(row.policyCode, { active: event.target.value === "true" })} className="h-10 rounded-md border border-slate-200 px-3 text-sm disabled:cursor-not-allowed disabled:bg-slate-100">
                    <option value="true">사용</option>
                    <option value="false">중지</option>
                  </select>
                  <Input disabled={!policyAuthorization.canUpdate} value={edit?.reason ?? ""} onChange={(event) => updateEdit(row.policyCode, { reason: event.target.value })} placeholder="변경/실행 사유" />
                </div>
                <Textarea
                  value={edit?.configJson ?? ""}
                  disabled={!policyAuthorization.canUpdate}
                  onChange={(event) => updateEdit(row.policyCode, { configJson: event.target.value })}
                  className="min-h-28 font-mono text-xs"
                />
                <div className="flex flex-wrap items-center justify-between gap-3 text-xs text-slate-500">
                  <span>마지막 실행: {formatDateTime(row.lastRunAt)} / {row.lastRunStatus ?? "-"}</span>
                  {policyAuthorization.canUpdate && (
                    <div className="flex gap-2">
                      <Button variant="outline" onClick={() => void run(row.policyCode)}><Play className="size-4" />수동 실행 요청</Button>
                      <Button className="bg-blue-600 text-white hover:bg-blue-700" onClick={() => void save(row.policyCode)}>저장</Button>
                    </div>
                  )}
                </div>
              </CardContent>
            </Card>
          );
        })}
      </div>
    </AdminShell>
  );
}
