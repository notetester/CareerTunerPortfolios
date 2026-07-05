import { useCallback, useEffect, useState } from "react";
import { Building2, Check, RefreshCw, X } from "lucide-react";
import AdminShell from "@/admin/components/AdminShell";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent } from "@/app/components/ui/card";
import { Textarea } from "@/app/components/ui/textarea";
import {
  AdminListFooter,
  AdminListToolbar,
  AdminSortableHeader,
  useAdminListTools,
  type AdminListColumn,
} from "@/admin/components/AdminListTools";
import {
  APPLICATION_STATUS_LABELS,
  type CompanyApplication,
  type CompanyApplicationStatus,
} from "@/features/company/types/company";
import {
  approveCompanyApplication,
  listCompanyApplications,
  rejectCompanyApplication,
} from "../api";

const STATUS_FILTERS: Array<{ value: string; label: string }> = [
  { value: "PENDING", label: "검토 중" },
  { value: "", label: "전체" },
  { value: "APPROVED", label: "승인" },
  { value: "REJECTED", label: "반려" },
];

const STATUS_BADGE_CLASS: Record<CompanyApplicationStatus, string> = {
  PENDING: "bg-amber-100 text-amber-700",
  APPROVED: "bg-green-100 text-green-700",
  REJECTED: "bg-red-100 text-red-700",
};

function formatDateTime(value: string | null | undefined): string {
  if (!value) return "-";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString("ko-KR");
}

const APPLICATION_COLUMNS: AdminListColumn<CompanyApplication>[] = [
  { id: "companyName", label: "기업명", getText: (row) => row.companyName, sortable: true },
  { id: "applicant", label: "신청자", getText: (row) => `${row.applicantName ?? "-"} ${row.applicantEmail ?? ""}`.trim(), sortable: true },
  { id: "businessNumber", label: "사업자번호", getText: (row) => row.businessNumber ?? "-", sortable: true },
  { id: "contact", label: "연락처", getText: (row) => row.contact, sortable: true },
  { id: "createdAt", label: "신청일", getText: (row) => formatDateTime(row.createdAt), sortValue: (row) => row.createdAt, sortable: true },
  { id: "status", label: "상태", getText: (row) => APPLICATION_STATUS_LABELS[row.status], sortable: true },
];

/** 기업 계정 신청 승인/반려 콘솔 — 승인 시 신청자 role 이 COMPANY 로 전환된다. */
export function AdminCompanyApplicationsPage() {
  const [statusFilter, setStatusFilter] = useState("PENDING");
  const [rows, setRows] = useState<CompanyApplication[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [processing, setProcessing] = useState(false);
  /** 반려 모달 대상. null 이면 닫힘. */
  const [rejectTarget, setRejectTarget] = useState<CompanyApplication | null>(null);
  const [rejectReason, setRejectReason] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setRows(await listCompanyApplications(statusFilter || undefined));
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "신청 목록을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, [statusFilter]);

  const list = useAdminListTools(rows, {
    columns: APPLICATION_COLUMNS,
    getRowId: (row) => row.id,
    defaultSortId: "createdAt",
    defaultSortDir: "desc",
  });

  useEffect(() => {
    void load();
  }, [load]);

  const approve = async (application: CompanyApplication) => {
    if (!window.confirm(`'${application.companyName}' 신청을 승인할까요?\n승인 즉시 신청자 계정이 기업(COMPANY) 계정으로 전환됩니다.`)) return;
    setProcessing(true);
    setError(null);
    try {
      await approveCompanyApplication(application.id);
      setMessage(`'${application.companyName}' 신청을 승인했습니다.`);
      await load();
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "승인 처리에 실패했습니다.");
    } finally {
      setProcessing(false);
    }
  };

  const reject = async () => {
    if (!rejectTarget) return;
    if (!rejectReason.trim()) {
      setError("반려 사유를 입력해 주세요.");
      return;
    }
    setProcessing(true);
    setError(null);
    try {
      await rejectCompanyApplication(rejectTarget.id, rejectReason.trim());
      setMessage(`'${rejectTarget.companyName}' 신청을 반려했습니다.`);
      setRejectTarget(null);
      setRejectReason("");
      await load();
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "반려 처리에 실패했습니다.");
    } finally {
      setProcessing(false);
    }
  };

  return (
    <AdminShell
      active="company-applications"
      breadcrumb="기업 신청 관리"
      title="기업 계정 신청"
      icon={Building2}
      desc="일반 회원의 기업 계정 전환 신청을 검토합니다. 승인하면 계정이 COMPANY 로 전환되고 채용공고를 등록할 수 있습니다."
      actions={
        <Button variant="outline" onClick={() => void load()} disabled={loading}>
          <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
          새로고침
        </Button>
      }
    >
      <div className="space-y-4">
        {error && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}
        {message && <div className="rounded-lg border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-700">{message}</div>}

        {/* 상태 필터 */}
        <div className="flex flex-wrap gap-2">
          {STATUS_FILTERS.map((filter) => (
            <Button
              key={filter.value}
              size="sm"
              variant={statusFilter === filter.value ? "default" : "outline"}
              className={statusFilter === filter.value ? "bg-blue-600 text-white hover:bg-blue-700" : ""}
              onClick={() => setStatusFilter(filter.value)}
            >
              {filter.label}
            </Button>
          ))}
        </div>

        {/* 신청 그리드(간단 목록) */}
        <Card className="border-slate-200 bg-card">
          <CardContent className="space-y-4 pt-6">
            <AdminListToolbar state={list} fileName="admin_company_applications" />
            {loading ? (
              <p className="py-10 text-center text-sm text-slate-500">불러오는 중...</p>
            ) : rows.length === 0 ? (
              <p className="py-10 text-center text-sm text-slate-500">해당 상태의 신청이 없습니다.</p>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-slate-200 text-left text-xs text-slate-500">
                      <AdminSortableHeader state={list} columnId="companyName">기업명</AdminSortableHeader>
                      <AdminSortableHeader state={list} columnId="applicant">신청자</AdminSortableHeader>
                      <AdminSortableHeader state={list} columnId="businessNumber">사업자번호</AdminSortableHeader>
                      <AdminSortableHeader state={list} columnId="contact">연락처</AdminSortableHeader>
                      <AdminSortableHeader state={list} columnId="createdAt">신청일</AdminSortableHeader>
                      <AdminSortableHeader state={list} columnId="status">상태</AdminSortableHeader>
                      <th className="px-2 py-2 text-right">처리</th>
                    </tr>
                  </thead>
                  <tbody>
                    {list.visibleRows.map((application) => (
                      <tr key={application.id} className="border-b border-slate-100 align-top">
                        <td className="px-2 py-3">
                          <div className="font-medium text-slate-900">{application.companyName}</div>
                          {application.description && (
                            <p className="mt-1 max-w-md text-xs text-slate-500">{application.description}</p>
                          )}
                          {application.status === "REJECTED" && application.rejectReason && (
                            <p className="mt-1 text-xs text-red-600">반려 사유: {application.rejectReason}</p>
                          )}
                        </td>
                        <td className="px-2 py-3 text-slate-700">
                          <div>{application.applicantName ?? "-"}</div>
                          <div className="text-xs text-slate-500">{application.applicantEmail ?? ""}</div>
                        </td>
                        <td className="px-2 py-3 text-slate-700">{application.businessNumber ?? "-"}</td>
                        <td className="px-2 py-3 text-slate-700">{application.contact}</td>
                        <td className="px-2 py-3 text-slate-500">{formatDateTime(application.createdAt)}</td>
                        <td className="px-2 py-3">
                          <Badge className={STATUS_BADGE_CLASS[application.status]}>
                            {APPLICATION_STATUS_LABELS[application.status]}
                          </Badge>
                        </td>
                        <td className="px-2 py-3">
                          {application.status === "PENDING" && (
                            <div className="flex justify-end gap-1.5">
                              <Button size="sm" className="bg-green-600 text-white hover:bg-green-700" disabled={processing} onClick={() => void approve(application)}>
                                <Check className="size-4" />
                                승인
                              </Button>
                              <Button size="sm" variant="outline" disabled={processing} onClick={() => { setRejectTarget(application); setRejectReason(""); }}>
                                <X className="size-4" />
                                반려
                              </Button>
                            </div>
                          )}
                        </td>
                      </tr>
                    ))}
                    {list.visibleRows.length === 0 && (
                      <tr>
                        <td colSpan={7} className="px-2 py-8 text-center text-sm text-slate-400">검색 조건에 맞는 신청이 없습니다.</td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            )}
            <AdminListFooter state={list} />
          </CardContent>
        </Card>
      </div>

      {/* 반려 모달 — 사유 필수 */}
      {rejectTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="w-full max-w-md rounded-xl bg-white p-6 shadow-xl">
            <h2 className="text-base font-semibold text-slate-900">신청 반려 — {rejectTarget.companyName}</h2>
            <p className="mt-1 text-sm text-slate-500">반려 사유는 신청자에게 알림으로 전달됩니다.</p>
            <Textarea
              className="mt-3 min-h-28"
              value={rejectReason}
              onChange={(event) => setRejectReason(event.target.value)}
              placeholder="예) 사업자등록번호를 확인할 수 없습니다. 정확한 번호로 다시 신청해 주세요."
            />
            <div className="mt-4 flex justify-end gap-2">
              <Button variant="outline" disabled={processing} onClick={() => setRejectTarget(null)}>취소</Button>
              <Button className="bg-red-600 text-white hover:bg-red-700" disabled={processing || !rejectReason.trim()} onClick={() => void reject()}>
                반려 확정
              </Button>
            </div>
          </div>
        </div>
      )}
    </AdminShell>
  );
}

export default AdminCompanyApplicationsPage;
