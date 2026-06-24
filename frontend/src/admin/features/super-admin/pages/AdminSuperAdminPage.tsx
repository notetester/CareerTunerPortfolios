import { useEffect, useMemo, useRef, useState } from "react";
import { RefreshCw, Search, ShieldCheck } from "lucide-react";
import AdminShell from "../../../components/AdminShell";
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
import type {
  AdminAccountRow,
  AdminPermissionAuditRow,
  AdminPermissionGroupRow,
  AdminPermissionPolicyRow,
} from "../types";

const ROLE_LABELS: Record<string, string> = {
  USER: "일반 사용자",
  ADMIN: "관리자",
  SUPER_ADMIN: "슈퍼 관리자",
};

const ROLE_PERMISSION_CODES: Record<string, string[]> = {
  USER: [],
  ADMIN: ["USER_READ", "PROFILE_READ", "CONSENT_READ", "AI_USAGE_READ", "SECURITY_LOG_READ", "USER_STATUS_WRITE"],
  SUPER_ADMIN: [
    "USER_READ",
    "PROFILE_READ",
    "CONSENT_READ",
    "AI_USAGE_READ",
    "SECURITY_LOG_READ",
    "USER_STATUS_WRITE",
    "POLICY_MANAGE",
    "ADMIN_PERMISSION_MANAGE",
  ],
};

const ROLE_GROUP_CODES: Record<string, string[]> = {
  USER: [],
  ADMIN: ["ADMIN_OPERATOR", "SECURITY_OPERATOR"],
  SUPER_ADMIN: ["ADMIN_OPERATOR", "SECURITY_OPERATOR", "SUPER_ADMIN_GROUP"],
};

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
  const [role, setRole] = useState("ADMIN");
  const [permissionCode, setPermissionCode] = useState("");
  const [groupCode, setGroupCode] = useState("");
  const [reason, setReason] = useState("");
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [successModalOpen, setSuccessModalOpen] = useState(false);
  const [successModalText, setSuccessModalText] = useState("변경이 완료됐습니다.");
  const [error, setError] = useState<string | null>(null);
  const selectedRef = useRef<number | null>(null);

  const selected = useMemo(() => admins.find((item) => item.id === selectedId) ?? detail, [admins, detail, selectedId]);
  const selectedRole = role || selected?.role || "USER";
  const allowedPermissionCodes = ROLE_PERMISSION_CODES[selectedRole] ?? [];
  const allowedGroupCodes = ROLE_GROUP_CODES[selectedRole] ?? [];
  const visiblePermissions = permissions.filter((item) => item.active && allowedPermissionCodes.includes(item.permissionCode));
  const visibleGroups = groups.filter((item) => item.active && allowedGroupCodes.includes(item.groupCode));
  const selectedGroup = visibleGroups.find((item) => item.groupCode === groupCode) ?? null;

  const selectUser = (user: AdminAccountRow) => {
    selectedRef.current = user.id;
    setSelectedId(user.id);
    setDetail(user);
    setRole(user.role);
    setPermissionCode("");
    setGroupCode("");
    setReason("");
  };

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const [nextAdmins, nextPermissions, nextGroups] = await Promise.all([
        getSuperAdmins(keyword),
        getSuperPermissions(),
        getSuperGroups(),
      ]);
      setAdmins(nextAdmins);
      setPermissions(nextPermissions);
      setGroups(nextGroups.filter((item) => item.groupCode !== "A_PART_OPERATOR"));

      const nextSelectedId = selectedRef.current ?? selectedId ?? nextAdmins[0]?.id ?? null;
      setSelectedId(nextSelectedId);
      if (nextSelectedId) {
        const [nextDetail, nextAudit] = await Promise.all([getSuperAdminDetail(nextSelectedId), getSuperAudit(nextSelectedId)]);
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
      setUsers(await searchSuperUsers(userKeyword));
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "사용자 검색에 실패했습니다.");
    }
  };

  const reloadDetail = async (userId: number) => {
    const [nextDetail, nextAudit] = await Promise.all([getSuperAdminDetail(userId), getSuperAudit(userId)]);
    selectedRef.current = userId;
    setSelectedId(userId);
    setDetail(nextDetail);
    setRole(nextDetail.role);
    setAudit(nextAudit);
  };

  useEffect(() => {
    void load();
  }, []);

  useEffect(() => {
    if (!allowedPermissionCodes.includes(permissionCode)) setPermissionCode("");
    if (!allowedGroupCodes.includes(groupCode)) setGroupCode("");
  }, [selectedRole, permissionCode, groupCode, allowedPermissionCodes, allowedGroupCodes]);

  const runMutation = async (work: (targetId: number) => Promise<AdminAccountRow>, doneMessage: string) => {
    const targetId = selected?.id ?? selectedId;
    if (!targetId) return;
    if (!reason.trim()) {
      setError("권한 변경 사유를 입력해 주세요.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const updated = await work(targetId);
      selectedRef.current = updated.id;
      setSuccessModalText(doneMessage);
      setSuccessModalOpen(true);
      await reloadDetail(updated.id);
      const nextAdmins = await getSuperAdmins(keyword);
      setAdmins(nextAdmins);
      setReason("");
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "권한 변경에 실패했습니다.");
    } finally {
      setSaving(false);
    }
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
                  <Badge className={roleBadgeClass(selectedRole)}>{ROLE_LABELS[selectedRole] ?? selectedRole}</Badge>
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
                  <Input value={reason} onChange={(event) => setReason(event.target.value)} placeholder="변경 사유" />
                  <Button
                    className="bg-blue-600 text-white hover:bg-blue-700"
                    disabled={saving}
                    onClick={() => void runMutation((targetId) => updateSuperAdminRole(targetId, role, reason), "변경이 완료됐습니다. 역할이 저장되었습니다.")}
                  >
                    역할 저장
                  </Button>
                </div>

                {selectedRole === "USER" && (
                  <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
                    USER 역할은 관리자 메뉴 접근 권한이 없는 일반 사용자입니다. 메뉴별 권한과 권한 그룹은 ADMIN 이상에서만 부여할 수 있습니다.
                  </div>
                )}

                <div className="grid gap-4 lg:grid-cols-2">
                  <div className="space-y-2">
                    <div className="text-sm font-bold text-slate-900">메뉴별 권한</div>
                    <select
                      value={permissionCode}
                      onChange={(event) => setPermissionCode(event.target.value)}
                      disabled={selectedRole === "USER"}
                      className="h-10 w-full rounded-md border border-slate-200 px-3 text-sm disabled:bg-slate-100"
                    >
                      <option value="">권한 선택</option>
                      {visiblePermissions.map((item) => <option key={item.permissionCode} value={item.permissionCode}>{item.displayName}</option>)}
                    </select>
                    <div className="flex gap-2">
                      <Button
                        variant="outline"
                        disabled={!permissionCode || saving}
                        onClick={() => void runMutation((targetId) => grantSuperPermission(targetId, permissionCode, reason), "변경이 완료됐습니다. 메뉴별 권한을 부여했습니다.")}
                      >
                        부여
                      </Button>
                      <Button
                        variant="outline"
                        disabled={!permissionCode || saving}
                        onClick={() => void runMutation((targetId) => revokeSuperPermission(targetId, permissionCode, reason), "변경이 완료됐습니다. 메뉴별 권한을 회수했습니다.")}
                      >
                        회수
                      </Button>
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
                      disabled={selectedRole === "USER"}
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
                        disabled={!groupCode || saving}
                        onClick={() => void runMutation((targetId) => assignSuperGroup(targetId, groupCode, reason), "변경이 완료됐습니다. 권한 그룹을 부여했습니다.")}
                      >
                        부여
                      </Button>
                      <Button
                        variant="outline"
                        disabled={!groupCode || saving}
                        onClick={() => void runMutation((targetId) => revokeSuperGroup(targetId, groupCode, reason), "변경이 완료됐습니다. 권한 그룹을 회수했습니다.")}
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
            <CardHeader><CardTitle className="text-base">권한 변경 이력</CardTitle></CardHeader>
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
    </AdminShell>
  );
}
