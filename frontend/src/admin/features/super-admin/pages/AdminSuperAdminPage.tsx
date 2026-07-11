import { useEffect, useMemo, useRef, useState } from "react";
import { ArrowUpDown, RefreshCw, Search, ShieldCheck } from "lucide-react";
import AdminShell from "../../../components/AdminShell";
import { PermissionRequestsPanel } from "../components/PermissionRequestsPanel";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/app/components/ui/alert-dialog";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Input } from "@/app/components/ui/input";
import {
  assignSuperGroup,
  getSuperAdminDetail,
  getSuperAdmins,
  getSuperAudit,
  getSuperGroups,
  getSuperPermissions,
  grantSuperPermission,
  revokeSuperGroup,
  revokeSuperPermission,
  searchSuperUsers,
  updateSuperAdminRole,
} from "../api";
import type { SuperSortParams } from "../api";
import type {
  AdminAccountRow,
  AdminPermissionAuditRow,
  AdminPermissionGroupRow,
  AdminPermissionPolicyRow,
} from "../types";
import {
  ADMIN_PERMISSION_ACTIONS,
  ADMIN_PERMISSION_CODES,
  ADMIN_PERMISSION_DOMAINS,
  adminPermissionCode,
} from "../../../auth/adminAccess";

const ROLE_LABELS: Record<string, string> = {
  USER: "일반 사용자",
  ADMIN: "관리자",
  SUPER_ADMIN: "슈퍼 관리자",
};

const ADMIN_ASSIGNABLE_PERMISSION_CODES = ADMIN_PERMISSION_CODES.filter(
  (code) => !code.startsWith("ADMIN_PERMISSION_"),
);

const ROLE_PERMISSION_CODES: Record<string, string[]> = {
  USER: [],
  ADMIN: [...ADMIN_ASSIGNABLE_PERMISSION_CODES],
  SUPER_ADMIN: [...ADMIN_PERMISSION_CODES],
};

const ROLE_GROUP_CODES: Record<string, string[]> = {
  USER: [],
  ADMIN: ["ADMIN_OPERATOR", "SECURITY_OPERATOR", "MEMBER_ADMIN", "AI_ADMIN", "BILLING_ADMIN", "CONTENT_ADMIN", "AUDIT_ADMIN"],
  SUPER_ADMIN: [
    "ADMIN_OPERATOR",
    "SECURITY_OPERATOR",
    "MEMBER_ADMIN",
    "AI_ADMIN",
    "BILLING_ADMIN",
    "CONTENT_ADMIN",
    "AUDIT_ADMIN",
    "POLICY_ADMIN",
    "SUPER_ADMIN_GROUP",
  ],
};

type SortDir = "ASC" | "DESC";

interface SortOption {
  value: string;
  label: string;
}

const ACCOUNT_SORT_OPTIONS: SortOption[] = [
  { value: "createdAt", label: "가입일" },
  { value: "lastLoginAt", label: "최근 로그인" },
  { value: "role", label: "역할" },
  { value: "name", label: "이름" },
  { value: "email", label: "이메일" },
];

const AUDIT_SORT_OPTIONS: SortOption[] = [
  { value: "createdAt", label: "변경일" },
  { value: "actionType", label: "액션" },
  { value: "actorEmail", label: "처리자" },
  { value: "targetEmail", label: "대상자" },
];

function formatDateTime(value: string | null | undefined): string {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(date);
}

function roleBadgeClass(roleValue: string): string {
  if (roleValue === "SUPER_ADMIN") return "bg-purple-100 text-purple-700";
  if (roleValue === "ADMIN") return "bg-blue-100 text-blue-700";
  return "bg-slate-200 text-slate-700";
}

function SortControls({
  options,
  sortBy,
  sortDir,
  onChange,
}: {
  options: SortOption[];
  sortBy: string;
  sortDir: SortDir;
  onChange: (sortBy: string, sortDir: SortDir) => void;
}) {
  return (
    <div className="flex flex-wrap gap-2">
      {options.map((option) => {
        const active = sortBy === option.value;
        const nextDir: SortDir = active && sortDir === "DESC" ? "ASC" : "DESC";
        return (
          <Button
            key={option.value}
            type="button"
            size="sm"
            variant={active ? "default" : "outline"}
            className={active ? "bg-slate-900 text-white hover:bg-slate-800" : ""}
            onClick={() => onChange(option.value, nextDir)}
          >
            <ArrowUpDown className="size-3" />
            {option.label}
            {active && <span className="text-[11px] opacity-80">{sortDir}</span>}
          </Button>
        );
      })}
    </div>
  );
}

export function AdminSuperAdminPage() {
  const [admins, setAdmins] = useState<AdminAccountRow[]>([]);
  const [users, setUsers] = useState<AdminAccountRow[]>([]);
  const [permissions, setPermissions] = useState<AdminPermissionPolicyRow[]>([]);
  const [groups, setGroups] = useState<AdminPermissionGroupRow[]>([]);
  const [audit, setAudit] = useState<AdminPermissionAuditRow[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [detail, setDetail] = useState<AdminAccountRow | null>(null);
  const [keyword, setKeyword] = useState("");
  const [userKeyword, setUserKeyword] = useState("");
  const [adminSortBy, setAdminSortBy] = useState("createdAt");
  const [adminSortDir, setAdminSortDir] = useState<SortDir>("DESC");
  const [userSortBy, setUserSortBy] = useState("createdAt");
  const [userSortDir, setUserSortDir] = useState<SortDir>("DESC");
  const [auditSortBy, setAuditSortBy] = useState("createdAt");
  const [auditSortDir, setAuditSortDir] = useState<SortDir>("DESC");
  const [role, setRole] = useState("ADMIN");
  const [groupCode, setGroupCode] = useState("");
  const [reason, setReason] = useState("");
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [successModalOpen, setSuccessModalOpen] = useState(false);
  const [successModalText, setSuccessModalText] = useState("변경이 완료됐습니다.");
  const [error, setError] = useState<string | null>(null);
  const selectedRef = useRef<number | null>(null);

  const selected = useMemo(() => admins.find((item) => item.id === selectedId) ?? detail, [admins, detail, selectedId]);
  const persistedRole = detail?.id === selectedId ? detail.role : (selected?.role ?? "USER");
  const roleDirty = role !== persistedRole;
  const allowedPermissionCodes = ROLE_PERMISSION_CODES[persistedRole] ?? [];
  const allowedGroupCodes = ROLE_GROUP_CODES[persistedRole] ?? [];
  const visibleGroups = groups.filter((item) => item.active && allowedGroupCodes.includes(item.groupCode));
  const selectedGroup = visibleGroups.find((item) => item.groupCode === groupCode) ?? null;
  const permissionCatalog = useMemo(
    () => new Map(permissions.filter((item) => item.active).map((item) => [item.permissionCode, item])),
    [permissions],
  );
  const grantedPermissionCodes = useMemo(
    () => new Set(detail?.permissions?.map((item) => item.permissionCode) ?? []),
    [detail?.permissions],
  );
  const adminSort = useMemo<SuperSortParams>(() => ({ sortBy: adminSortBy, sortDir: adminSortDir }), [adminSortBy, adminSortDir]);
  const userSort = useMemo<SuperSortParams>(() => ({ sortBy: userSortBy, sortDir: userSortDir }), [userSortBy, userSortDir]);
  const auditSort = useMemo<SuperSortParams>(() => ({ sortBy: auditSortBy, sortDir: auditSortDir }), [auditSortBy, auditSortDir]);

  const selectUser = (user: AdminAccountRow) => {
    selectedRef.current = user.id;
    setSelectedId(user.id);
    setDetail(user);
    setRole(user.role);
    setGroupCode("");
    setReason("");
  };

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const [nextAdmins, nextPermissions, nextGroups] = await Promise.all([
        getSuperAdmins(keyword, adminSort),
        getSuperPermissions(),
        getSuperGroups(),
      ]);
      setAdmins(nextAdmins);
      setPermissions(nextPermissions);
      setGroups(nextGroups.filter((item) => item.groupCode !== "A_PART_OPERATOR"));

      const nextSelectedId = selectedRef.current ?? selectedId ?? nextAdmins[0]?.id ?? null;
      setSelectedId(nextSelectedId);
      if (nextSelectedId) {
        const [nextDetail, nextAudit] = await Promise.all([getSuperAdminDetail(nextSelectedId), getSuperAudit(nextSelectedId, auditSort)]);
        setDetail(nextDetail);
        setAudit(nextAudit);
        setRole(nextDetail.role);
      } else {
        setDetail(null);
        setAudit([]);
      }
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "super 관리자 정보를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  };

  const searchUsers = async () => {
    setError(null);
    try {
      setUsers(await searchSuperUsers(userKeyword, userSort));
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "사용자 검색에 실패했습니다.");
    }
  };

  const reloadDetail = async (userId: number) => {
    const [nextDetail, nextAudit] = await Promise.all([getSuperAdminDetail(userId), getSuperAudit(userId, auditSort)]);
    selectedRef.current = userId;
    setSelectedId(userId);
    setDetail(nextDetail);
    setRole(nextDetail.role);
    setAudit(nextAudit);
  };

  useEffect(() => {
    void load();
  }, [adminSortBy, adminSortDir]);

  useEffect(() => {
    if (users.length > 0) {
      void searchUsers();
    }
  }, [userSortBy, userSortDir]);

  useEffect(() => {
    if (selectedId) {
      void reloadDetail(selectedId);
    }
  }, [auditSortBy, auditSortDir]);

  useEffect(() => {
    if (!allowedGroupCodes.includes(groupCode)) setGroupCode("");
  }, [persistedRole, groupCode, allowedGroupCodes]);

  const runMutation = async (work: (targetId: number) => Promise<AdminAccountRow>, doneMessage: string) => {
    const targetId = selected?.id ?? selectedId;
    if (!targetId) return;
    setSaving(true);
    setError(null);
    try {
      const updated = await work(targetId);
      selectedRef.current = updated.id;
      setSuccessModalText(doneMessage);
      setSuccessModalOpen(true);
      await reloadDetail(updated.id);
      const nextAdmins = await getSuperAdmins(keyword, adminSort);
      setAdmins(nextAdmins);
      setReason("");
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "권한 변경에 실패했습니다.");
    } finally {
      setSaving(false);
    }
  };

  const togglePermission = (code: string) => {
    if (roleDirty || persistedRole === "USER" || saving || !allowedPermissionCodes.includes(code) || !permissionCatalog.has(code)) return;
    const granted = grantedPermissionCodes.has(code);
    void runMutation(
      (targetId) => granted
        ? revokeSuperPermission(targetId, code, reason)
        : grantSuperPermission(targetId, code, reason),
      granted
        ? `변경이 완료됐습니다. ${code} 권한을 회수했습니다.`
        : `변경이 완료됐습니다. ${code} 권한을 부여했습니다.`,
    );
  };

  return (
    <AdminShell
      active="super-admin"
      breadcrumb="super 관리자"
      title="super 관리자 권한 관리"
      icon={ShieldCheck}
      desc="관리자 역할, 메뉴별 권한, 권한 그룹, 변경 이력을 super 관리자만 관리합니다."
      actions={(
        <Button variant="outline" onClick={() => void load()} disabled={loading}>
          <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
          새로고침
        </Button>
      )}
    >
      <div className="grid gap-5 xl:grid-cols-[380px_minmax(0,1fr)]">
        <section className="space-y-4">
          <Card className="border-slate-200 bg-card">
            <CardContent className="space-y-3 p-4">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
                <Input value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="관리자 이메일/이름 검색" className="pl-9" />
              </div>
              <Button className="w-full bg-blue-600 text-white hover:bg-blue-700" onClick={() => void load()}>
                관리자 검색
              </Button>
              <div className="space-y-2">
                <div className="text-xs font-semibold text-slate-500">관리자 목록 정렬</div>
                <SortControls
                  options={ACCOUNT_SORT_OPTIONS}
                  sortBy={adminSortBy}
                  sortDir={adminSortDir}
                  onChange={(nextSortBy, nextSortDir) => {
                    setAdminSortBy(nextSortBy);
                    setAdminSortDir(nextSortDir);
                  }}
                />
              </div>
            </CardContent>
          </Card>

          {error && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}

          <div className="space-y-2">
            {admins.map((admin) => (
              <button
                key={admin.id}
                type="button"
                className={`w-full rounded-lg border bg-card p-3 text-left ${selectedId === admin.id ? "border-blue-300 ring-2 ring-blue-100" : "border-slate-200 hover:border-blue-200"}`}
                onClick={() => {
                  selectUser(admin);
                  void reloadDetail(admin.id);
                }}
              >
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <div className="truncate text-sm font-bold text-slate-950">{admin.name}</div>
                    <div className="truncate text-xs text-slate-500">{admin.email}</div>
                  </div>
                  <Badge className={roleBadgeClass(admin.role)}>{admin.role}</Badge>
                </div>
              </button>
            ))}
          </div>
        </section>

        <section className="space-y-4">
          <Card className="border-slate-200 bg-card">
            <CardHeader>
              <CardTitle className="text-base">관리자 계정 승격/검색</CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              <div className="flex flex-col gap-2 md:flex-row">
                <Input value={userKeyword} onChange={(event) => setUserKeyword(event.target.value)} placeholder="일반 사용자 이메일/이름 검색" />
                <Button variant="outline" onClick={() => void searchUsers()}>사용자 검색</Button>
              </div>
              <div className="space-y-2">
                <div className="text-xs font-semibold text-slate-500">사용자 검색 결과 정렬</div>
                <SortControls
                  options={ACCOUNT_SORT_OPTIONS}
                  sortBy={userSortBy}
                  sortDir={userSortDir}
                  onChange={(nextSortBy, nextSortDir) => {
                    setUserSortBy(nextSortBy);
                    setUserSortDir(nextSortDir);
                  }}
                />
              </div>
              {users.length > 0 && (
                <div className="grid gap-2 md:grid-cols-2">
                  {users.map((user) => (
                    <button
                      key={user.id}
                      type="button"
                      className="rounded-lg border border-slate-200 p-3 text-left hover:border-blue-200"
                      onClick={() => {
                        selectUser(user);
                        void reloadDetail(user.id);
                      }}
                    >
                      <div className="font-semibold text-slate-900">{user.name}</div>
                      <div className="text-xs text-slate-500">{user.email} / {user.role}</div>
                    </button>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>

          {selected && (
            <Card className="border-slate-200 bg-card">
              <CardHeader>
                <CardTitle className="flex items-center justify-between gap-3 text-base">
                  <span>{selected.name} 권한 설정</span>
                  <Badge className={roleBadgeClass(persistedRole)}>{ROLE_LABELS[persistedRole] ?? persistedRole}</Badge>
                </CardTitle>
                <p className="text-sm text-slate-500">{selected.email} / 최근 로그인 {formatDateTime(selected.lastLoginAt)}</p>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="grid gap-2 md:grid-cols-[1fr_1fr_auto]">
                  <select value={role} onChange={(event) => setRole(event.target.value)} className="h-10 rounded-md border border-slate-200 px-3 text-sm">
                    <option value="USER">USER - 일반 사용자</option>
                    <option value="ADMIN">ADMIN - 관리자</option>
                    <option value="SUPER_ADMIN">SUPER_ADMIN - 슈퍼 관리자</option>
                  </select>
                  <Input value={reason} onChange={(event) => setReason(event.target.value)} placeholder="변경 사유(선택)" />
                  <Button
                    className="bg-blue-600 text-white hover:bg-blue-700"
                    disabled={saving || !roleDirty}
                    onClick={() => void runMutation((targetId) => updateSuperAdminRole(targetId, role, reason), "변경이 완료됐습니다. 역할이 저장되었습니다.")}
                  >
                    역할 저장
                  </Button>
                </div>

                {roleDirty && (
                  <div role="status" className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800 dark:border-amber-800 dark:bg-amber-950/30 dark:text-amber-200">
                    역할 변경을 먼저 저장해야 권한과 권한 그룹을 편집할 수 있습니다. 현재 편집 기준은 저장된 {ROLE_LABELS[persistedRole] ?? persistedRole} 역할입니다.
                  </div>
                )}

                {persistedRole === "USER" && (
                  <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800 dark:border-amber-800 dark:bg-amber-950/30 dark:text-amber-200">
                    USER 역할은 관리자 메뉴 접근 권한이 없는 일반 사용자입니다. 메뉴별 권한과 권한 그룹은 ADMIN 이상에서만 부여할 수 있습니다.
                  </div>
                )}

                <div className="grid gap-4 lg:grid-cols-2">
                  <div className="space-y-2">
                    <div className="text-sm font-bold text-slate-900">도메인·동작 권한</div>
                    <p className="text-xs text-slate-500">파란색은 직접 부여된 권한입니다. 셀을 눌러 해당 권한을 부여하거나 회수합니다.</p>
                    <div className="overflow-x-auto rounded-lg border border-slate-200">
                      <table className="w-full min-w-[430px] text-xs">
                        <thead>
                          <tr className="border-b border-slate-200 bg-slate-50 text-slate-500">
                            <th className="px-2 py-2 text-left">영역</th>
                            {ADMIN_PERMISSION_ACTIONS.map(({ code: action, label }) => (
                              <th key={action} className="px-2 py-2 text-center">{label}</th>
                            ))}
                          </tr>
                        </thead>
                        <tbody>
                          {ADMIN_PERMISSION_DOMAINS.map(({ code: domain, label }) => (
                            <tr key={domain} className="border-b border-slate-100 last:border-0">
                              <th className="px-2 py-2 text-left font-semibold text-slate-700">{label}</th>
                              {ADMIN_PERMISSION_ACTIONS.map(({ code: action }) => {
                                const code = adminPermissionCode(domain, action);
                                const granted = grantedPermissionCodes.has(code);
                                const assignable = allowedPermissionCodes.includes(code) && permissionCatalog.has(code);
                                return (
                                  <td key={code} className="px-1 py-1 text-center">
                                    <button
                                      type="button"
                                      className={`w-full rounded px-1 py-1.5 font-semibold ${
                                        granted ? "bg-blue-600 text-white" : "bg-slate-100 text-slate-600"
                                      } disabled:cursor-not-allowed disabled:opacity-40`}
                                      disabled={!assignable || saving || roleDirty || persistedRole === "USER"}
                                      title={roleDirty ? "역할 변경을 먼저 저장해 주세요." : (permissionCatalog.get(code)?.displayName ?? code)}
                                      onClick={() => togglePermission(code)}
                                    >
                                      {granted ? "부여됨" : "미부여"}
                                    </button>
                                  </td>
                                );
                              })}
                            </tr>
                          ))}
                          <tr>
                            <th className="px-2 py-2 text-left font-semibold text-slate-700">감사</th>
                            <td className="px-1 py-1 text-center" colSpan={4}>
                              <button
                                type="button"
                                className={`w-full rounded px-2 py-1.5 font-semibold ${
                                  grantedPermissionCodes.has("AUDIT_READ") ? "bg-blue-600 text-white" : "bg-slate-100 text-slate-600"
                                } disabled:cursor-not-allowed disabled:opacity-40`}
                                disabled={!allowedPermissionCodes.includes("AUDIT_READ") || !permissionCatalog.has("AUDIT_READ") || saving || roleDirty || persistedRole === "USER"}
                                title={roleDirty ? "역할 변경을 먼저 저장해 주세요." : (permissionCatalog.get("AUDIT_READ")?.displayName ?? "AUDIT_READ")}
                                onClick={() => togglePermission("AUDIT_READ")}
                              >
                                AUDIT_READ · {grantedPermissionCodes.has("AUDIT_READ") ? "부여됨" : "미부여"}
                              </button>
                            </td>
                          </tr>
                        </tbody>
                      </table>
                    </div>
                    <div className="flex flex-wrap gap-2">
                      {detail?.permissions?.map((item) => <Badge key={item.id} className="bg-blue-100 text-blue-700">{item.displayName}</Badge>)}
                    </div>
                  </div>

                  <div className="space-y-2">
                    <div className="text-sm font-bold text-slate-900">권한 그룹</div>
                    <select
                      value={groupCode}
                      onChange={(event) => setGroupCode(event.target.value)}
                      disabled={roleDirty || persistedRole === "USER"}
                      className="h-10 w-full rounded-md border border-slate-200 px-3 text-sm disabled:bg-slate-100"
                    >
                      <option value="">그룹 선택</option>
                      {visibleGroups.map((item) => <option key={item.groupCode} value={item.groupCode}>{item.displayName}</option>)}
                    </select>
                    {selectedGroup && (
                      <div className="rounded-lg bg-slate-50 p-3">
                        <div className="text-xs font-semibold text-slate-500">이 그룹에 포함된 메뉴 권한</div>
                        <div className="mt-2 flex flex-wrap gap-2">
                          {(selectedGroup.permissions ?? []).map((permission) => (
                            <Badge key={permission.permissionCode} className="bg-slate-200 text-slate-700">{permission.displayName}</Badge>
                          ))}
                        </div>
                      </div>
                    )}
                    <div className="flex gap-2">
                      <Button
                        variant="outline"
                        disabled={!groupCode || saving || roleDirty || persistedRole === "USER"}
                        onClick={() => {
                          if (roleDirty || persistedRole === "USER") return;
                          void runMutation((targetId) => assignSuperGroup(targetId, groupCode, reason), "변경이 완료됐습니다. 권한 그룹을 부여했습니다.");
                        }}
                      >
                        부여
                      </Button>
                      <Button
                        variant="outline"
                        disabled={!groupCode || saving || roleDirty || persistedRole === "USER"}
                        onClick={() => {
                          if (roleDirty || persistedRole === "USER") return;
                          void runMutation((targetId) => revokeSuperGroup(targetId, groupCode, reason), "변경이 완료됐습니다. 권한 그룹을 회수했습니다.");
                        }}
                      >
                        회수
                      </Button>
                    </div>
                    <div className="flex flex-wrap gap-2">
                      {detail?.groups?.map((item) => <Badge key={item.id} className="bg-green-100 text-green-700">{item.displayName}</Badge>)}
                    </div>
                  </div>
                </div>
              </CardContent>
            </Card>
          )}

          <Card className="border-slate-200 bg-card">
            <CardHeader className="space-y-3">
              <CardTitle className="text-base">권한 변경 이력</CardTitle>
              <SortControls
                options={AUDIT_SORT_OPTIONS}
                sortBy={auditSortBy}
                sortDir={auditSortDir}
                onChange={(nextSortBy, nextSortDir) => {
                  setAuditSortBy(nextSortBy);
                  setAuditSortDir(nextSortDir);
                }}
              />
            </CardHeader>
            <CardContent className="space-y-2">
              {audit.map((item) => (
                <div key={item.id} className="rounded-lg border border-slate-100 p-3 text-sm">
                  <div className="font-semibold text-slate-900">{item.actionType}</div>
                  <div className="mt-1 text-xs text-slate-500">{formatDateTime(item.createdAt)} / {item.actorEmail ?? "-"} → {item.targetEmail ?? "-"}</div>
                  <div className="mt-1 text-xs text-slate-600">{item.permissionCode ?? item.groupCode ?? "-"} / {item.reason ?? "사유 없음"}</div>
                </div>
              ))}
              {!audit.length && <div className="rounded-lg bg-slate-50 p-4 text-center text-sm text-slate-500">권한 변경 이력이 없습니다.</div>}
            </CardContent>
          </Card>
        </section>
      </div>

      <AlertDialog open={successModalOpen} onOpenChange={setSuccessModalOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>변경이 완료됐습니다</AlertDialogTitle>
            <AlertDialogDescription>{successModalText}</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogAction onClick={() => setSuccessModalOpen(false)}>확인</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
      <div className="mt-6">
        <PermissionRequestsPanel />
      </div>
    </AdminShell>
  );
}
