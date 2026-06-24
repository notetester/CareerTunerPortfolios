import { useEffect, useMemo, useRef, useState } from "react";
import { RefreshCw, Search, ShieldCheck } from "lucide-react";
import AdminShell from "../../../components/AdminShell";
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
import type { AdminAccountRow, AdminPermissionAuditRow, AdminPermissionGroupRow, AdminPermissionPolicyRow } from "../types";

function formatDateTime(value: string | null | undefined): string {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(date);
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
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const selectedRef = useRef<number | null>(null);
  selectedRef.current = selectedId;

  const selected = useMemo(() => admins.find((item) => item.id === selectedId) ?? detail, [admins, detail, selectedId]);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const [nextAdmins, nextPermissions, nextGroups, nextAudit] = await Promise.all([
        getSuperAdmins(keyword),
        getSuperPermissions(),
        getSuperGroups(),
        getSuperAudit(selectedRef.current ?? undefined),
      ]);
      setAdmins(nextAdmins);
      setPermissions(nextPermissions);
      setGroups(nextGroups);
      setAudit(nextAudit);
      const nextSelectedId = selectedRef.current ?? nextAdmins[0]?.id ?? null;
      setSelectedId(nextSelectedId);
      if (nextSelectedId) {
        const nextDetail = await getSuperAdminDetail(nextSelectedId);
        setDetail(nextDetail);
        setRole(nextDetail.role);
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
    setDetail(nextDetail);
    setRole(nextDetail.role);
    setAudit(nextAudit);
  };

  useEffect(() => {
    void load();
  }, []);

  const runMutation = async (work: () => Promise<AdminAccountRow>, doneMessage: string) => {
    if (!selectedId) return;
    if (!reason.trim()) {
      setError("권한 변경 사유를 입력해 주세요.");
      return;
    }
    setError(null);
    setMessage(null);
    try {
      const updated = await work();
      setMessage(doneMessage);
      await reloadDetail(updated.id);
      await load();
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "권한 변경에 실패했습니다.");
    }
  };

  return (
    <AdminShell
      active="super-admin"
      breadcrumb="super 관리자"
      title="super 관리자 권한 관리"
      icon={ShieldCheck}
      desc="관리자 역할, 메뉴별 권한, 권한 그룹, 변경 이력을 super 관리자만 관리합니다."
      actions={<Button variant="outline" onClick={() => void load()} disabled={loading}><RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />새로고침</Button>}
    >
      <div className="grid gap-5 xl:grid-cols-[380px_minmax(0,1fr)]">
        <section className="space-y-4">
          <Card className="border-slate-200 bg-card">
            <CardContent className="space-y-3 p-4">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
                <Input value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="관리자 이메일/이름 검색" className="pl-9" />
              </div>
              <Button className="w-full bg-blue-600 text-white hover:bg-blue-700" onClick={() => void load()}>관리자 검색</Button>
            </CardContent>
          </Card>

          {error && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}
          {message && <div className="rounded-lg border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-700">{message}</div>}

          <div className="space-y-2">
            {admins.map((admin) => (
              <button
                key={admin.id}
                type="button"
                className={`w-full rounded-lg border bg-card p-3 text-left ${selectedId === admin.id ? "border-blue-300 ring-2 ring-blue-100" : "border-slate-200 hover:border-blue-200"}`}
                onClick={() => {
                  selectedRef.current = admin.id;
                  setSelectedId(admin.id);
                  void reloadDetail(admin.id);
                }}
              >
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <div className="truncate text-sm font-bold text-slate-950">{admin.name}</div>
                    <div className="truncate text-xs text-slate-500">{admin.email}</div>
                  </div>
                  <Badge className={admin.role === "SUPER_ADMIN" ? "bg-purple-100 text-purple-700" : "bg-blue-100 text-blue-700"}>{admin.role}</Badge>
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
                        setSelectedId(user.id);
                        setDetail(user);
                        setRole(user.role);
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
                  <Badge className={selected.role === "SUPER_ADMIN" ? "bg-purple-100 text-purple-700" : "bg-blue-100 text-blue-700"}>{selected.role}</Badge>
                </CardTitle>
                <p className="text-sm text-slate-500">{selected.email} / 최근 로그인 {formatDateTime(selected.lastLoginAt)}</p>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="grid gap-2 md:grid-cols-[1fr_1fr_auto]">
                  <select value={role} onChange={(event) => setRole(event.target.value)} className="h-10 rounded-md border border-slate-200 px-3 text-sm">
                    <option value="USER">USER</option>
                    <option value="ADMIN">ADMIN</option>
                    <option value="SUPER_ADMIN">SUPER_ADMIN</option>
                  </select>
                  <Input value={reason} onChange={(event) => setReason(event.target.value)} placeholder="변경 사유" />
                  <Button className="bg-blue-600 text-white hover:bg-blue-700" onClick={() => void runMutation(() => updateSuperAdminRole(selected.id, role, reason), "역할을 변경했습니다.")}>역할 저장</Button>
                </div>

                <div className="grid gap-4 lg:grid-cols-2">
                  <div className="space-y-2">
                    <div className="text-sm font-bold text-slate-900">메뉴별 권한</div>
                    <select value={permissionCode} onChange={(event) => setPermissionCode(event.target.value)} className="h-10 w-full rounded-md border border-slate-200 px-3 text-sm">
                      <option value="">권한 선택</option>
                      {permissions.filter((item) => item.active).map((item) => <option key={item.permissionCode} value={item.permissionCode}>{item.displayName}</option>)}
                    </select>
                    <div className="flex gap-2">
                      <Button variant="outline" disabled={!permissionCode} onClick={() => void runMutation(() => grantSuperPermission(selected.id, permissionCode, reason), "권한을 부여했습니다.")}>부여</Button>
                      <Button variant="outline" disabled={!permissionCode} onClick={() => void runMutation(() => revokeSuperPermission(selected.id, permissionCode, reason), "권한을 회수했습니다.")}>회수</Button>
                    </div>
                    <div className="flex flex-wrap gap-2">
                      {detail?.permissions?.map((item) => <Badge key={item.id} className="bg-blue-100 text-blue-700">{item.displayName}</Badge>)}
                    </div>
                  </div>

                  <div className="space-y-2">
                    <div className="text-sm font-bold text-slate-900">권한 그룹</div>
                    <select value={groupCode} onChange={(event) => setGroupCode(event.target.value)} className="h-10 w-full rounded-md border border-slate-200 px-3 text-sm">
                      <option value="">그룹 선택</option>
                      {groups.filter((item) => item.active).map((item) => <option key={item.groupCode} value={item.groupCode}>{item.displayName}</option>)}
                    </select>
                    <div className="flex gap-2">
                      <Button variant="outline" disabled={!groupCode} onClick={() => void runMutation(() => assignSuperGroup(selected.id, groupCode, reason), "그룹을 부여했습니다.")}>부여</Button>
                      <Button variant="outline" disabled={!groupCode} onClick={() => void runMutation(() => revokeSuperGroup(selected.id, groupCode, reason), "그룹을 회수했습니다.")}>회수</Button>
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
    </AdminShell>
  );
}
