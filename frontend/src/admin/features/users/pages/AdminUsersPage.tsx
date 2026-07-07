import { useEffect, useRef, useState, type ReactElement } from "react";
import { LockKeyhole, MailCheck, RefreshCw, RotateCcw, Search, ShieldAlert, Users, type LucideIcon } from "lucide-react";
import AdminShell from "../../../components/AdminShell";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/app/components/ui/alert-dialog";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent } from "@/app/components/ui/card";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/app/components/ui/dialog";
import { Input } from "@/app/components/ui/input";
import { toast } from "@/features/notification/components/toast";
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
import { AdminLoginAuditPage } from "@/admin/features/audit/pages/AdminLoginAuditPage";
import { bulkUpdateAdminUserStatus, getAdminUserDetail, getAdminUsers, getAdminUsersPage } from "../api";
import type { AdminUserDetail, AdminUserRow, AdminUserStatus } from "../types";
import { UserDetailPanel } from "../components/UserDetailPanel";
import { STATUS_OPTIONS, formatDateTime, statusLabel, statusTone } from "../components/userDisplay";

/**
 * 회원 관리 — 관리자 공통 그리드 프레임워크 파일럿 1호.
 *
 * TT 이식 규약: 서버/클라이언트 듀얼 로드 모드, 이중 화이트리스트 검색,
 * 활성 컬럼만 ▲빨강/▼파랑 정렬 표시, 고정 슬롯 bulk bar, 형식×범위 내보내기.
 * 기존 회원 상세 컨텍스트·상태 변경은 행 클릭 → 상세 모달(UserDetailPanel)로 유지한다.
 */

const SEARCH_TYPES: AdminSearchTypeOption[] = [
  { value: "all", label: "전체(이메일+이름)" },
  { value: "email", label: "이메일" },
  { value: "name", label: "이름" },
];

const FILTER_DEFS: AdminFilterDef[] = [
  { key: "status", label: "상태", options: STATUS_OPTIONS.map((option) => ({ value: option.value, label: option.label })) },
  {
    key: "role",
    label: "권한",
    options: [
      { value: "USER", label: "USER" },
      { value: "ADMIN", label: "ADMIN" },
      { value: "SUPER_ADMIN", label: "SUPER_ADMIN" },
    ],
  },
];

const USER_COLUMNS: AdminColumn<AdminUserRow>[] = [
  {
    key: "email",
    label: "이메일",
    sortable: true,
    render: (row) => <span className="font-semibold text-slate-900">{row.email}</span>,
  },
  { key: "name", label: "이름", sortable: true },
  {
    key: "status",
    label: "상태",
    sortable: true,
    render: (row) => <Badge className={statusTone[row.status]}>{statusLabel(row.status)}</Badge>,
  },
  { key: "role", label: "권한", sortable: true },
  { key: "plan", label: "요금제", sortable: true },
  {
    key: "credit",
    label: "크레딧",
    sortable: true,
    cellClassName: "text-right tabular-nums",
    headerClassName: "text-right",
  },
  {
    key: "loginFailCount",
    label: "로그인 실패",
    sortable: true,
    cellClassName: "tabular-nums",
    render: (row) => (
      <span className={row.loginFailCount > 0 ? "text-red-600" : "text-slate-500"}>
        {row.loginFailCount}회{row.failedLoginCount > 0 ? ` (연속 ${row.failedLoginCount})` : ""}
      </span>
    ),
    clientSortValue: (row) => row.loginFailCount,
  },
  {
    key: "lastLoginAt",
    label: "최근 로그인",
    sortable: true,
    render: (row) => <span className="text-slate-500">{formatDateTime(row.lastLoginAt)}</span>,
    clientSortValue: (row) => row.lastLoginAt ?? "",
  },
  {
    key: "createdAt",
    label: "가입일",
    sortable: true,
    render: (row) => <span className="text-slate-500">{formatDateTime(row.createdAt)}</span>,
    clientSortValue: (row) => row.createdAt,
  },
];

interface AdminUsersPageProps {
  active?: string;
  breadcrumb?: string;
  title?: string;
  icon?: LucideIcon;
  desc?: string;
  initialStatus?: string;
}

export function AdminUsersPage({
  active = "members",
  breadcrumb = "회원 관리",
  title = "회원 관리",
  icon: PageIcon = Users,
  desc = "회원 목록을 검색·정렬·일괄 처리하고, 행을 클릭하면 로그인/보안 이력과 상태 변경을 포함한 상세 컨텍스트를 확인합니다.",
  initialStatus = "",
}: AdminUsersPageProps = {}) {
  const list = useAdminList<AdminUserRow>({
    storageKey: `admin-grid:users:${active}`,
    fetcher: getAdminUsersPage,
    rowKey: (row) => row.id,
    defaultSortBy: "createdAt",
    defaultSortDir: "DESC",
    filterKeys: ["status", "role"],
    initialFilters: initialStatus ? { status: initialStatus } : {},
    columns: USER_COLUMNS,
  });

  // ── 상세 모달(행 클릭) ──
  const [detail, setDetail] = useState<AdminUserDetail | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const detailIdRef = useRef<number | null>(null);

  const openDetail = async (row: AdminUserRow) => {
    detailIdRef.current = row.id;
    setDetailOpen(true);
    setDetail(null);
    setDetailLoading(true);
    try {
      const next = await getAdminUserDetail(row.id);
      if (detailIdRef.current === row.id) setDetail(next);
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "회원 상세 정보를 불러오지 못했습니다.");
      setDetailOpen(false);
    } finally {
      if (detailIdRef.current === row.id) setDetailLoading(false);
    }
  };

  const reloadDetail = async (id: number) => {
    try {
      const next = await getAdminUserDetail(id);
      if (detailIdRef.current === id) setDetail(next);
    } catch {
      // 상세 재조회 실패는 목록 갱신으로 대체된다.
    }
  };

  // ── 일괄 상태 변경 ──
  const [bulkStatus, setBulkStatus] = useState<AdminUserStatus>("ACTIVE");
  const [bulkReason, setBulkReason] = useState("");
  const [bulkBlockedUntil, setBulkBlockedUntil] = useState("");
  const [bulkConfirmOpen, setBulkConfirmOpen] = useState(false);
  const [bulkSaving, setBulkSaving] = useState(false);

  const requestBulk = () => {
    if (list.selection.count === 0) {
      toast.warning("선택된 회원이 없습니다.");
      return;
    }
    if (!bulkReason.trim()) {
      toast.warning("일괄 상태 변경 사유를 입력해 주세요.");
      return;
    }
    setBulkConfirmOpen(true);
  };

  const runBulk = async () => {
    setBulkSaving(true);
    try {
      const result = await bulkUpdateAdminUserStatus([...list.selection.ids], {
        status: bulkStatus,
        reason: bulkReason.trim(),
        blockedUntil: bulkStatus === "BLOCKED" && bulkBlockedUntil ? `${bulkBlockedUntil}:00` : null,
      });
      toast.success(
        `상태 일괄 변경 완료 — 대상 ${result.requested}건 / 변경 ${result.updated}건 / 건너뜀 ${result.skipped}건`,
      );
      setBulkConfirmOpen(false);
      setBulkReason("");
      setBulkBlockedUntil("");
      list.selection.clear();
      list.refetch();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "일괄 상태 변경에 실패했습니다.");
    } finally {
      setBulkSaving(false);
    }
  };

  return (
    <AdminShell
      active={active}
      breadcrumb={breadcrumb}
      title={title}
      icon={PageIcon}
      desc={desc}
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
          dateLabel="가입일"
          applied={list.applied}
          onApply={(next) => list.applySearch(next)}
          onReset={list.resetSearch}
        />

        {list.error && (
          <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{list.error}</div>
        )}

        {/* 툴바: [좌] bulk bar 고정 슬롯 / [우] 로드 방식 → 정렬 초기화(조건부) → 내보내기 */}
        <div className="flex flex-wrap items-center gap-2">
          <div className="min-w-[260px] flex-1">
            <AdminBulkBar count={list.selection.count} onClear={list.selection.clear}>
              <select
                value={bulkStatus}
                onChange={(event) => setBulkStatus(event.target.value as AdminUserStatus)}
                className="h-8 rounded-md border border-blue-200 bg-card px-2 text-sm"
                aria-label="일괄 변경할 상태"
              >
                {STATUS_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
              {bulkStatus === "BLOCKED" && (
                <Input
                  type="datetime-local"
                  value={bulkBlockedUntil}
                  onChange={(event) => setBulkBlockedUntil(event.target.value)}
                  className="h-8 w-[190px]"
                  aria-label="차단 만료 시각"
                />
              )}
              <Input
                value={bulkReason}
                onChange={(event) => setBulkReason(event.target.value)}
                placeholder="변경 사유(필수)"
                className="h-8 w-[180px]"
              />
              <Button
                type="button"
                size="sm"
                className="h-8 bg-blue-600 text-white hover:bg-blue-700"
                onClick={requestBulk}
                disabled={bulkSaving}
              >
                상태 일괄 변경
              </Button>
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
              exportPath="/admin/users/export"
              getParams={list.exportParams}
              selectedIds={[...list.selection.ids]}
              disabled={list.loading}
            />
          </div>
        </div>

        <AdminDataGrid
          columns={USER_COLUMNS}
          rows={list.rows}
          rowKey={(row) => row.id}
          loading={list.loading}
          sortBy={list.sortBy}
          sortDir={list.sortDir}
          onToggleSort={list.toggleSort}
          selectable
          selection={list.selection}
          onRowClick={(row) => void openDetail(row)}
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

      {/* 회원 상세 모달 — 기존 상세 컨텍스트·상태 변경 기능 유지 */}
      <Dialog open={detailOpen} onOpenChange={(open) => { if (!open) { setDetailOpen(false); detailIdRef.current = null; } }}>
        <DialogContent className="max-h-[85vh] w-[min(1080px,92vw)] max-w-none overflow-y-auto">
          <DialogHeader>
            <DialogTitle>회원 상세</DialogTitle>
          </DialogHeader>
          {detailLoading && (
            <div className="flex items-center justify-center gap-2 py-12 text-sm text-slate-500">
              <RefreshCw className="size-4 animate-spin" />
              상세 정보를 불러오는 중입니다…
            </div>
          )}
          {!detailLoading && detail && (
            <UserDetailPanel
              detail={detail}
              onUpdated={async (updated) => {
                list.refetch();
                await reloadDetail(updated.id);
              }}
            />
          )}
        </DialogContent>
      </Dialog>

      {/* 일괄 상태 변경 확인 */}
      <AlertDialog open={bulkConfirmOpen} onOpenChange={setBulkConfirmOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>선택한 회원 상태를 일괄 변경할까요?</AlertDialogTitle>
            <AlertDialogDescription>
              선택한 {list.selection.count}명의 상태를 {statusLabel(bulkStatus)} 상태로 변경합니다.
              본인 계정이 포함되어 있으면 서버가 요청을 거부합니다.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={bulkSaving}>취소</AlertDialogCancel>
            <AlertDialogAction
              className="bg-blue-600 text-white hover:bg-blue-700"
              disabled={bulkSaving}
              onClick={(event) => {
                event.preventDefault();
                void runBulk();
              }}
            >
              {bulkSaving && <RefreshCw className="size-4 animate-spin" />}
              일괄 변경 확정
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </AdminShell>
  );
}

export function AdminBlockedUsersPage() {
  return (
    <AdminUsersPage
      active="blocked-users"
      breadcrumb="차단 관리"
      title="차단 회원 관리"
      icon={ShieldAlert}
      desc="차단된 계정과 차단 만료 시각, 차단 사유, 상태 변경 이력을 전용 화면에서 확인하고 해제합니다."
      initialStatus="BLOCKED"
    />
  );
}

/** 로그인/보안 감사 — 이벤트 단위 로그인 감사 그리드(파일럿 2호)로 교체. */
export function AdminSecurityAuditPage() {
  return <AdminLoginAuditPage />;
}

export function AdminEmailAuditPage() {
  return (
    <UserContextExplorer
      active="email-audit"
      breadcrumb="이메일 감사"
      title="이메일 인증/재설정 감사"
      icon={MailCheck}
      desc="이메일 인증과 비밀번호 재설정 토큰 발급, 사용, 만료 이력을 사용자 단위로 확인합니다."
    />
  );
}

/**
 * 사용자 단위 컨텍스트 탐색(레거시 마스터-디테일) — 이메일 감사처럼
 * '회원을 먼저 고르고 이력을 보는' 화면이 계속 사용한다.
 */
function UserContextExplorer({
  active,
  breadcrumb,
  title,
  icon: PageIcon,
  desc,
  initialStatus = "",
}: Required<Pick<AdminUsersPageProps, "active" | "breadcrumb" | "title" | "desc">> & {
  icon: LucideIcon;
  initialStatus?: string;
}): ReactElement {
  const [rows, setRows] = useState<AdminUserRow[]>([]);
  const [keyword, setKeyword] = useState("");
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [detail, setDetail] = useState<AdminUserDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const selectedIdRef = useRef<number | null>(null);
  selectedIdRef.current = selectedId;

  const loadRows = async () => {
    setLoading(true);
    setError(null);
    try {
      const nextRows = await getAdminUsers({ keyword, status: initialStatus });
      const current = selectedIdRef.current;
      const nextSelectedId =
        current !== null && nextRows.some((row) => row.id === current) ? current : nextRows[0]?.id ?? null;
      selectedIdRef.current = nextSelectedId;
      setRows(nextRows);
      setSelectedId(nextSelectedId);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "회원 목록을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  };

  const loadDetail = async (id: number) => {
    try {
      const next = await getAdminUserDetail(id);
      if (selectedIdRef.current === id) setDetail(next);
    } catch (requestError) {
      if (selectedIdRef.current === id) {
        setError(requestError instanceof Error ? requestError.message : "회원 상세 정보를 불러오지 못했습니다.");
      }
    }
  };

  useEffect(() => {
    void loadRows();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    setDetail(null);
    if (selectedId !== null) void loadDetail(selectedId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedId]);

  return (
    <AdminShell
      active={active}
      breadcrumb={breadcrumb}
      title={title}
      icon={PageIcon}
      desc={desc}
      actions={(
        <Button variant="outline" onClick={() => void loadRows()} disabled={loading}>
          <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
          새로고침
        </Button>
      )}
    >
      <div className="grid gap-5 lg:grid-cols-[340px_minmax(0,1fr)]">
        <section className="space-y-3">
          <Card className="border-slate-200 bg-card">
            <CardContent className="space-y-3 p-4">
              <form
                onSubmit={(event) => {
                  event.preventDefault();
                  void loadRows();
                }}
              >
                <div className="relative">
                  <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
                  <Input
                    value={keyword}
                    onChange={(event) => setKeyword(event.target.value)}
                    placeholder="이름 또는 이메일 검색"
                    className="pl-9"
                  />
                </div>
                <Button type="submit" className="mt-3 w-full bg-blue-600 text-white hover:bg-blue-700">
                  검색
                </Button>
              </form>
            </CardContent>
          </Card>

          {error && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}

          <div className="space-y-2">
            {rows.map((row) => (
              <button
                key={row.id}
                type="button"
                className={`w-full rounded-lg border bg-card p-3 text-left transition-colors ${
                  selectedId === row.id ? "border-blue-300 ring-2 ring-blue-100" : "border-slate-200 hover:border-blue-200"
                }`}
                onClick={() => setSelectedId(row.id)}
              >
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <div className="truncate text-sm font-bold text-slate-950">{row.name}</div>
                    <div className="truncate text-xs text-slate-500">{row.email}</div>
                  </div>
                  <Badge className={statusTone[row.status]}>{statusLabel(row.status)}</Badge>
                </div>
              </button>
            ))}
            {!loading && rows.length === 0 && (
              <div className="rounded-lg bg-slate-50 p-4 text-center text-sm text-slate-500">
                현재 조건에 맞는 회원이 없습니다.
              </div>
            )}
          </div>
        </section>

        <section className="min-w-0">
          {!detail ? (
            <Card className="border-slate-200 bg-card">
              <CardContent className="p-8 text-center text-sm text-slate-500">
                {loading ? "불러오는 중입니다…" : "회원을 선택해 주세요."}
              </CardContent>
            </Card>
          ) : (
            <UserDetailPanel
              detail={detail}
              onUpdated={async (updated) => {
                setRows((items) => items.map((item) => (item.id === updated.id ? updated : item)));
                await loadDetail(updated.id);
              }}
            />
          )}
        </section>
      </div>
    </AdminShell>
  );
}
