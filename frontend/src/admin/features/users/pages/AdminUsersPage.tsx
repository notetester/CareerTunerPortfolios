import { useEffect, useRef, useState } from "react";
import { Plus, RefreshCw, RotateCcw, ShieldAlert, Trash2, Users, type LucideIcon } from "lucide-react";
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
import { useAdminAuthorization } from "@/admin/auth/useAdminAuthorization";
import {
  bulkSoftDeleteAdminUsers,
  bulkUpdateAdminUserStatus,
  createAdminUser,
  getAdminUserDetail,
  getAdminUsersPage,
} from "../api";
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

function generateTemporaryPassword(): string {
  const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
  const bytes = crypto.getRandomValues(new Uint8Array(12));
  return `Ct!7${Array.from(bytes, (value) => alphabet[value % alphabet.length]).join("")}`;
}

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
  const authorization = useAdminAuthorization();
  const canCreateUser = authorization.can("USER_CREATE");
  const canManageUserStatus = authorization.can("USER_UPDATE");
  const canSoftDeleteUser = authorization.can("USER_DELETE");
  const canShowCreateUser = canCreateUser && active === "members";
  const canSelectUsers = canManageUserStatus || canSoftDeleteUser;
  const bulkStatusOptions = STATUS_OPTIONS.filter((option) => option.value !== "DELETED");
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

  // ── 일반 회원 생성(USER_CREATE). 역할/요금제/초기 상태는 서버가 USER/FREE/ACTIVE로 고정한다. ──
  const [createOpen, setCreateOpen] = useState(false);
  const [createEmail, setCreateEmail] = useState("");
  const [createName, setCreateName] = useState("");
  const [createPassword, setCreatePassword] = useState("");
  const [creating, setCreating] = useState(false);

  const openCreate = () => {
    setCreateEmail("");
    setCreateName("");
    setCreatePassword(generateTemporaryPassword());
    setCreateOpen(true);
  };

  const runCreate = async () => {
    if (!canCreateUser) {
      setCreateOpen(false);
      toast.error("회원을 추가할 권한이 없습니다.");
      return;
    }
    if (!createEmail.trim() || !createName.trim() || createPassword.length < 8) {
      toast.warning("이메일, 이름, 8자 이상의 임시 비밀번호를 입력해 주세요.");
      return;
    }
    setCreating(true);
    try {
      const created = await createAdminUser({
        email: createEmail.trim(),
        name: createName.trim(),
        password: createPassword,
      });
      toast.success(`${created.email} 회원을 추가했습니다.`);
      setCreateOpen(false);
      list.refetch();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "회원을 추가하지 못했습니다.");
    } finally {
      setCreating(false);
    }
  };

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
  const [bulkDeleteConfirmOpen, setBulkDeleteConfirmOpen] = useState(false);
  const [bulkSaving, setBulkSaving] = useState(false);

  const requestBulk = () => {
    if (!canManageUserStatus) {
      toast.error("회원 상태를 변경할 권한이 없습니다.");
      return;
    }
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
    if (!canManageUserStatus) {
      setBulkConfirmOpen(false);
      toast.error("선택한 회원 상태를 변경할 권한이 없습니다.");
      return;
    }
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

  const requestBulkDelete = () => {
    if (!canSoftDeleteUser) {
      toast.error("회원을 소프트 삭제할 권한이 없습니다.");
      return;
    }
    if (list.selection.count === 0) {
      toast.warning("선택된 회원이 없습니다.");
      return;
    }
    if (!bulkReason.trim()) {
      toast.warning("일괄 삭제 사유를 입력해 주세요.");
      return;
    }
    setBulkDeleteConfirmOpen(true);
  };

  const runBulkDelete = async () => {
    if (!canSoftDeleteUser) {
      setBulkDeleteConfirmOpen(false);
      toast.error("회원을 소프트 삭제할 권한이 없습니다.");
      return;
    }
    setBulkSaving(true);
    try {
      const result = await bulkSoftDeleteAdminUsers([...list.selection.ids], bulkReason);
      toast.success(`소프트 삭제 완료 — 대상 ${result.requested}건 / 변경 ${result.updated}건 / 건너뜀 ${result.skipped}건`);
      setBulkDeleteConfirmOpen(false);
      setBulkReason("");
      list.selection.clear();
      list.refetch();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "회원을 일괄 삭제하지 못했습니다.");
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
        <>
          {canShowCreateUser && (
            <Button onClick={openCreate} className="bg-blue-600 text-white hover:bg-blue-700">
              <Plus className="size-4" /> 회원 추가
            </Button>
          )}
          <Button variant="outline" onClick={() => list.refetch()} disabled={list.loading}>
            <RefreshCw className={`size-4 ${list.loading ? "animate-spin" : ""}`} />
            새로고침
          </Button>
        </>
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
          {canSelectUsers && (
            <div className="min-w-[260px] flex-1">
              <AdminBulkBar count={list.selection.count} onClear={list.selection.clear}>
                {canManageUserStatus && (
                  <select
                    value={bulkStatus}
                    onChange={(event) => setBulkStatus(event.target.value as AdminUserStatus)}
                    className="h-8 rounded-md border border-blue-200 bg-card px-2 text-sm"
                    aria-label="일괄 변경할 상태"
                  >
                    {bulkStatusOptions.map((option) => (
                      <option key={option.value} value={option.value}>
                        {option.label}
                      </option>
                    ))}
                  </select>
                )}
                {canManageUserStatus && bulkStatus === "BLOCKED" && (
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
                  placeholder="변경·삭제 사유(필수)"
                  className="h-8 w-[180px]"
                />
                {canManageUserStatus && (
                  <Button
                    type="button"
                    size="sm"
                    className="h-8 bg-blue-600 text-white hover:bg-blue-700"
                    onClick={requestBulk}
                    disabled={bulkSaving}
                  >
                    상태 일괄 변경
                  </Button>
                )}
                {canSoftDeleteUser && (
                  <Button
                    type="button"
                    size="sm"
                    variant="destructive"
                    className="h-8"
                    onClick={requestBulkDelete}
                    disabled={bulkSaving}
                  >
                    <Trash2 className="size-3.5" /> 선택 삭제
                  </Button>
                )}
              </AdminBulkBar>
            </div>
          )}
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
          selectable={canSelectUsers}
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

      {/* USER_CREATE — 서버가 일반 USER/FREE/ACTIVE 계정으로만 생성한다. */}
      <Dialog open={canShowCreateUser && createOpen} onOpenChange={setCreateOpen}>
        <DialogContent className="w-[min(520px,92vw)]">
          <DialogHeader>
            <DialogTitle>일반 회원 추가</DialogTitle>
          </DialogHeader>
          <form
            className="space-y-4"
            onSubmit={(event) => {
              event.preventDefault();
              void runCreate();
            }}
          >
            <label className="block space-y-1.5 text-sm font-semibold text-foreground">
              이메일
              <Input
                type="email"
                autoComplete="off"
                value={createEmail}
                onChange={(event) => setCreateEmail(event.target.value)}
                placeholder="member@example.com"
                required
              />
            </label>
            <label className="block space-y-1.5 text-sm font-semibold text-foreground">
              이름
              <Input value={createName} onChange={(event) => setCreateName(event.target.value)} required maxLength={100} />
            </label>
            <label className="block space-y-1.5 text-sm font-semibold text-foreground">
              임시 비밀번호
              <div className="flex gap-2">
                <Input
                  type="text"
                  autoComplete="new-password"
                  value={createPassword}
                  onChange={(event) => setCreatePassword(event.target.value)}
                  minLength={8}
                  maxLength={64}
                  required
                />
                <Button type="button" variant="outline" onClick={() => setCreatePassword(generateTemporaryPassword())}>
                  재발급
                </Button>
              </div>
            </label>
            <div className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-600 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-300">
              초기 상태 <b>ACTIVE</b> · 역할 <b>USER</b> · 요금제 <b>FREE</b>로 생성됩니다.
            </div>
            <div className="flex justify-end gap-2">
              <Button type="button" variant="outline" disabled={creating} onClick={() => setCreateOpen(false)}>취소</Button>
              <Button type="submit" disabled={creating} className="bg-blue-600 text-white hover:bg-blue-700">
                {creating && <RefreshCw className="size-4 animate-spin" />}
                회원 추가
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>

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
      <AlertDialog open={canManageUserStatus && bulkConfirmOpen} onOpenChange={setBulkConfirmOpen}>
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

      <AlertDialog open={canSoftDeleteUser && bulkDeleteConfirmOpen} onOpenChange={setBulkDeleteConfirmOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>선택한 회원을 소프트 삭제할까요?</AlertDialogTitle>
            <AlertDialogDescription>
              선택한 {list.selection.count}명의 상태를 DELETED로 바꾸고 활성 로그인 세션을 폐기합니다.
              회원 데이터와 감사 이력은 보존됩니다.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={bulkSaving}>취소</AlertDialogCancel>
            <AlertDialogAction
              className="bg-red-600 text-white hover:bg-red-700"
              disabled={bulkSaving}
              onClick={(event) => {
                event.preventDefault();
                void runBulkDelete();
              }}
            >
              {bulkSaving && <RefreshCw className="size-4 animate-spin" />}
              소프트 삭제 확정
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
