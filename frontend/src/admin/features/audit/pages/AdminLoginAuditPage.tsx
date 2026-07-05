import { useState } from "react";
import { LockKeyhole, RefreshCw, RotateCcw } from "lucide-react";
import AdminShell from "../../../components/AdminShell";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/app/components/ui/dialog";
import {
  AdminBulkBar,
  AdminDataGrid,
  AdminExportMenu,
  AdminModeSelect,
  AdminPagination,
  AdminSearchBar,
  useAdminList,
  type AdminColumn,
  type AdminFilterDef,
  type AdminSearchTypeOption,
} from "@/admin/components/grid";
import { getAdminLoginAuditPage } from "../api";
import type { AdminLoginAuditRow } from "../types";

/**
 * 로그인 감사 — 관리자 공통 그리드 프레임워크 파일럿 2호(조회 전용 도메인).
 *
 * 감사 로그는 일괄 작업이 없고 내보내기만 지원한다(TT 계약).
 * bulk bar 는 고정 슬롯 규약대로 자리를 유지하며, 선택은 '선택 내보내기' 범위로 쓰인다.
 */

const SEARCH_TYPES: AdminSearchTypeOption[] = [
  { value: "all", label: "전체(이메일+식별자+IP)" },
  { value: "email", label: "회원 이메일" },
  { value: "identifier", label: "입력 식별자" },
  { value: "ip", label: "IP 주소" },
];

const FILTER_DEFS: AdminFilterDef[] = [
  {
    key: "eventType",
    label: "이벤트",
    options: [
      { value: "LOGIN", label: "로그인" },
      { value: "LOGOUT", label: "로그아웃" },
      { value: "REFRESH", label: "토큰 갱신" },
    ],
  },
  {
    key: "authProvider",
    label: "제공자",
    options: [
      { value: "LOCAL", label: "LOCAL" },
      { value: "KAKAO", label: "KAKAO" },
      { value: "NAVER", label: "NAVER" },
      { value: "GOOGLE", label: "GOOGLE" },
    ],
  },
  {
    key: "result",
    label: "결과",
    options: [
      { value: "SUCCESS", label: "성공" },
      { value: "FAIL", label: "실패" },
    ],
  },
];

function formatDateTime(value: string | null | undefined): string {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "medium" }).format(date);
}

const AUDIT_COLUMNS: AdminColumn<AdminLoginAuditRow>[] = [
  {
    key: "createdAt",
    label: "발생 시각",
    sortable: true,
    render: (row) => <span className="whitespace-nowrap text-slate-600">{formatDateTime(row.createdAt)}</span>,
    clientSortValue: (row) => row.createdAt,
  },
  {
    key: "eventType",
    label: "이벤트",
    sortable: true,
    render: (row) => <span className="font-semibold text-slate-900">{row.eventType}</span>,
  },
  {
    key: "success",
    label: "결과",
    render: (row) => (
      <Badge className={row.success ? "bg-green-100 text-green-700" : "bg-red-100 text-red-700"}>
        {row.success ? "성공" : "실패"}
      </Badge>
    ),
    clientSortValue: (row) => row.success,
  },
  {
    key: "userEmail",
    label: "회원",
    sortable: true,
    render: (row) =>
      row.userEmail ? (
        <span>
          <span className="font-medium text-slate-800">{row.userEmail}</span>
          {row.userName && <span className="ml-1 text-xs text-slate-400">({row.userName})</span>}
        </span>
      ) : (
        <span className="text-slate-400">미식별</span>
      ),
    clientSortValue: (row) => row.userEmail ?? "",
  },
  {
    key: "loginIdentifier",
    label: "입력 식별자",
    render: (row) => <span className="text-slate-600">{row.loginIdentifier ?? "-"}</span>,
  },
  {
    key: "authProvider",
    label: "제공자/방식",
    sortable: true,
    render: (row) => (
      <span className="text-slate-600">
        {row.authProvider}
        {row.loginMethod && <span className="text-xs text-slate-400"> / {row.loginMethod}</span>}
      </span>
    ),
  },
  {
    key: "failReason",
    label: "실패 사유",
    render: (row) => (row.failReason ? <span className="text-red-600">{row.failReason}</span> : <span className="text-slate-300">-</span>),
  },
  {
    key: "ipAddress",
    label: "IP",
    sortable: true,
    render: (row) => <span className="tabular-nums text-slate-600">{row.ipAddress ?? "-"}</span>,
    clientSortValue: (row) => row.ipAddress ?? "",
  },
];

export function AdminLoginAuditPage() {
  const list = useAdminList<AdminLoginAuditRow>({
    storageKey: "admin-grid:audit-logins",
    fetcher: getAdminLoginAuditPage,
    rowKey: (row) => row.id,
    defaultSortBy: "createdAt",
    defaultSortDir: "DESC",
    filterKeys: ["eventType", "authProvider", "result"],
    columns: AUDIT_COLUMNS,
  });

  const [detailRow, setDetailRow] = useState<AdminLoginAuditRow | null>(null);

  return (
    <AdminShell
      active="security-audit"
      breadcrumb="로그인/보안 감사"
      title="로그인/보안 감사"
      icon={LockKeyhole}
      desc="로그인·로그아웃·토큰 갱신 이벤트를 통합 조회합니다. 감사 로그는 조회 전용이며 내보내기만 지원합니다."
      actions={(
        <Button variant="outline" onClick={() => list.refetch()} disabled={list.loading}>
          <RefreshCw className={`size-4 ${list.loading ? "animate-spin" : ""}`} />
          새로고침
        </Button>
      )}
    >
      <div className="space-y-4">
        <AdminSearchBar
          searchTypes={SEARCH_TYPES}
          filterDefs={FILTER_DEFS}
          dateLabel="발생일"
          applied={list.applied}
          onApply={(next) => list.applySearch(next)}
          onReset={list.resetSearch}
        />

        {list.error && (
          <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{list.error}</div>
        )}

        <div className="flex flex-wrap items-center gap-2">
          <div className="min-w-[260px] flex-1">
            <AdminBulkBar count={list.selection.count} onClear={list.selection.clear}>
              <span className="text-xs text-slate-500">감사 로그는 조회 전용입니다 — 선택 항목은 내보내기 범위로 사용됩니다.</span>
            </AdminBulkBar>
          </div>
          <div className="flex items-center gap-1.5">
            <AdminModeSelect mode={list.mode} onChange={list.setMode} disabled={list.loading} />
            {list.sorted && (
              <Button type="button" variant="ghost" size="sm" className="h-9" onClick={list.resetSort}>
                <RotateCcw className="size-4" />
                정렬 초기화
              </Button>
            )}
            <AdminExportMenu
              exportPath="/admin/audit/logins/export"
              getParams={list.exportParams}
              selectedIds={[...list.selection.ids]}
              disabled={list.loading}
            />
          </div>
        </div>

        <AdminDataGrid
          columns={AUDIT_COLUMNS}
          rows={list.rows}
          rowKey={(row) => row.id}
          loading={list.loading}
          sortBy={list.sortBy}
          sortDir={list.sortDir}
          onToggleSort={list.toggleSort}
          selectable
          selection={list.selection}
          onRowClick={(row) => setDetailRow(row)}
        />

        <AdminPagination
          total={list.total}
          page={list.page}
          totalPages={list.totalPages}
          size={list.size}
          onPageChange={list.setPage}
          onSizeChange={list.setSize}
          disabled={list.loading}
        />
      </div>

      {/* 이벤트 상세 — User-Agent/요청 URI 등 그리드에 담지 못한 전체 필드 */}
      <Dialog open={detailRow !== null} onOpenChange={(open) => { if (!open) setDetailRow(null); }}>
        <DialogContent className="w-[min(640px,92vw)] max-w-none">
          <DialogHeader>
            <DialogTitle>로그인 이벤트 #{detailRow?.id}</DialogTitle>
          </DialogHeader>
          {detailRow && (
            <dl className="grid gap-2 text-sm">
              <DetailField label="발생 시각" value={formatDateTime(detailRow.createdAt)} />
              <DetailField label="이벤트" value={`${detailRow.eventType} (${detailRow.success ? "성공" : "실패"})`} />
              <DetailField
                label="회원"
                value={detailRow.userEmail ? `${detailRow.userEmail}${detailRow.userName ? ` (${detailRow.userName})` : ""}` : "미식별"}
              />
              <DetailField label="입력 식별자" value={detailRow.loginIdentifier ?? "-"} />
              <DetailField label="제공자 / 방식" value={`${detailRow.authProvider} / ${detailRow.loginMethod ?? "-"}`} />
              <DetailField label="실패 사유" value={detailRow.failReason ?? "-"} />
              <DetailField label="IP 주소" value={detailRow.ipAddress ?? "-"} />
              <DetailField label="요청 URI" value={detailRow.requestUri ?? "-"} />
              <DetailField label="User-Agent" value={detailRow.userAgent ?? "-"} breakAll />
            </dl>
          )}
        </DialogContent>
      </Dialog>
    </AdminShell>
  );
}

function DetailField({ label, value, breakAll = false }: { label: string; value: string; breakAll?: boolean }) {
  return (
    <div className="rounded-lg bg-slate-50 px-3 py-2">
      <dt className="text-[11px] font-semibold uppercase text-slate-400">{label}</dt>
      <dd className={`mt-0.5 text-sm font-medium text-slate-800 ${breakAll ? "break-all" : "break-words"}`}>{value}</dd>
    </div>
  );
}
