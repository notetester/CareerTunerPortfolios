import { useEffect, useState } from "react";
import { ClipboardCheck, RefreshCw, Search } from "lucide-react";
import AdminShell from "../../../components/AdminShell";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent } from "@/app/components/ui/card";
import { Input } from "@/app/components/ui/input";
import { getAdminConsents } from "../api";
import type { AdminConsentView } from "../types";

const consentTypes = ["", "TERMS", "PRIVACY", "AI_DATA", "RESUME_ANALYSIS", "MARKETING"];
const consentStatuses = [
  { value: "", label: "전체 상태" },
  { value: "AGREED", label: "동의" },
  { value: "REVOKED", label: "미동의/철회" },
];
const consentSources = ["", "REGISTER", "USER", "REVOKE"];

export function AdminConsentsPage() {
  const [rows, setRows] = useState<AdminConsentView[]>([]);
  const [keyword, setKeyword] = useState("");
  const [consentType, setConsentType] = useState("");
  const [status, setStatus] = useState("");
  const [source, setSource] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      setRows(await getAdminConsents({ keyword, consentType, status, source, from, to, limit: 200 }));
    } catch (err) {
      setError(err instanceof Error ? err.message : "동의 이력을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  return (
    <AdminShell
      active="consents"
      breadcrumb="동의 관리"
      title="동의 이력 관리"
      icon={ClipboardCheck}
      desc="서비스 약관, 개인정보, AI 데이터, 이력서 분석, 마케팅 동의와 철회 이력을 문서 버전별로 조회합니다."
      actions={(
        <Button variant="outline" onClick={() => void load()} disabled={loading}>
          <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
          새로고침
        </Button>
      )}
    >
      <div className="space-y-4">
        <Card className="border-slate-200 bg-card">
          <CardContent className="grid gap-3 p-4 lg:grid-cols-[minmax(220px,1fr)_150px_150px_140px_150px_150px_120px]">
            <div className="relative">
              <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
              <Input value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="이메일 또는 사용자 ID 검색" className="pl-9" />
            </div>
            <select value={consentType} onChange={(event) => setConsentType(event.target.value)} className="h-10 rounded-md border border-slate-200 px-3 text-sm">
              {consentTypes.map((type) => <option key={type || "ALL"} value={type}>{type || "전체 동의"}</option>)}
            </select>
            <select value={status} onChange={(event) => setStatus(event.target.value)} className="h-10 rounded-md border border-slate-200 px-3 text-sm">
              {consentStatuses.map((item) => <option key={item.value || "ALL"} value={item.value}>{item.label}</option>)}
            </select>
            <select value={source} onChange={(event) => setSource(event.target.value)} className="h-10 rounded-md border border-slate-200 px-3 text-sm">
              {consentSources.map((item) => <option key={item || "ALL"} value={item}>{item || "전체 출처"}</option>)}
            </select>
            <Input type="date" value={from} onChange={(event) => setFrom(event.target.value)} title="시작일" />
            <Input type="date" value={to} onChange={(event) => setTo(event.target.value)} title="종료일" />
            <Button className="bg-blue-600 text-white hover:bg-blue-700" onClick={() => void load()}>
              검색
            </Button>
            <Button
              variant="outline"
              className="lg:col-span-7"
              onClick={() => {
                setKeyword("");
                setConsentType("");
                setStatus("");
                setSource("");
                setFrom("");
                setTo("");
              }}
            >
              필터 초기화
            </Button>
          </CardContent>
        </Card>

        {error && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}

        <Card className="border-slate-200 bg-card">
          <CardContent className="overflow-x-auto p-0">
            <table className="w-full min-w-[900px] text-left text-sm">
              <thead className="border-b border-slate-200 bg-slate-50 text-xs font-bold text-slate-500">
                <tr>
                  <th className="px-4 py-3">ID</th>
                  <th className="px-4 py-3">사용자</th>
                  <th className="px-4 py-3">동의 유형</th>
                  <th className="px-4 py-3">문서 버전</th>
                  <th className="px-4 py-3">상태</th>
                  <th className="px-4 py-3">출처</th>
                  <th className="px-4 py-3">동의일</th>
                  <th className="px-4 py-3">철회일</th>
                  <th className="px-4 py-3">생성일</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => (
                  <tr key={row.id} className="border-b border-slate-100">
                    <td className="px-4 py-3 font-semibold text-slate-700">{row.id}</td>
                    <td className="px-4 py-3">
                      <div className="font-semibold text-slate-900">#{row.userId}</div>
                      <div className="text-xs text-slate-500">{row.userEmail || "-"}</div>
                    </td>
                    <td className="px-4 py-3">{row.consentType}</td>
                    <td className="px-4 py-3">{row.consentVersion || "-"}</td>
                    <td className="px-4 py-3">
                      <Badge className={row.agreed && !row.revokedAt ? "bg-green-100 text-green-700" : "bg-slate-200 text-slate-700"}>
                        {row.agreed && !row.revokedAt ? "동의" : "미동의/철회"}
                      </Badge>
                    </td>
                    <td className="px-4 py-3">{row.source || "-"}</td>
                    <td className="px-4 py-3">{formatDate(row.agreedAt)}</td>
                    <td className="px-4 py-3">{formatDate(row.revokedAt)}</td>
                    <td className="px-4 py-3">{formatDate(row.createdAt)}</td>
                  </tr>
                ))}
                {!loading && rows.length === 0 && (
                  <tr>
                    <td colSpan={9} className="px-4 py-8 text-center text-slate-500">동의 이력이 없습니다.</td>
                  </tr>
                )}
              </tbody>
            </table>
          </CardContent>
        </Card>
      </div>
    </AdminShell>
  );
}

function formatDate(value?: string | null): string {
  if (!value) return "-";
  return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}
