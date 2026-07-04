import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { CheckCircle2, LockKeyhole, MailCheck, RefreshCw, Search, ShieldAlert, Users, type LucideIcon } from "lucide-react";
import AdminShell from "../../../components/AdminShell";
import {
  AdminListFooter,
  AdminListToolbar,
  useAdminListTools,
  type AdminListColumn,
} from "../../../components/AdminListTools";
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
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Input } from "@/app/components/ui/input";
import { Textarea } from "@/app/components/ui/textarea";
import { getAdminUserDetail, getAdminUserLoginHistory, getAdminUsers, updateAdminUserStatus } from "../api";
import type { AdminUserDetail, AdminUserLoginHistoryRow, AdminUserRow, AdminUserStatus } from "../types";

const STATUS_OPTIONS: Array<{ value: AdminUserStatus; label: string }> = [
  { value: "ACTIVE", label: "활성" },
  { value: "DORMANT", label: "휴면" },
  { value: "BLOCKED", label: "차단" },
  { value: "DELETED", label: "탈퇴/삭제" },
];

const statusTone: Record<AdminUserStatus, string> = {
  ACTIVE: "bg-green-100 text-green-700",
  DORMANT: "bg-amber-100 text-amber-700",
  BLOCKED: "bg-red-100 text-red-700",
  DELETED: "bg-slate-200 text-slate-700",
};

const USER_LIST_COLUMNS: AdminListColumn<AdminUserRow>[] = [
  { id: "id", label: "ID", getText: (row) => row.id, sortable: true },
  { id: "name", label: "이름", getText: (row) => row.name, sortable: true },
  { id: "email", label: "이메일", getText: (row) => row.email, sortable: true },
  { id: "role", label: "권한", getText: (row) => row.role, sortable: true },
  { id: "status", label: "상태", getText: (row) => statusLabel(row.status), sortable: true },
  { id: "plan", label: "요금제", getText: (row) => row.plan, sortable: true },
  { id: "credit", label: "크레딧", getText: (row) => row.credit, sortable: true },
  { id: "lastLoginAt", label: "최근 로그인", getText: (row) => formatDateTime(row.lastLoginAt), sortable: true },
  { id: "blockedUntil", label: "차단 만료", getText: (row) => formatDateTime(row.blockedUntil), sortable: true },
  { id: "loginSuccessCount", label: "로그인 성공", getText: (row) => row.loginSuccessCount, sortable: true },
  { id: "loginFailCount", label: "로그인 실패", getText: (row) => row.loginFailCount, sortable: true },
  { id: "failedLoginCount", label: "연속 실패", getText: (row) => row.failedLoginCount, sortable: true },
  { id: "createdAt", label: "가입일", getText: (row) => formatDateTime(row.createdAt), sortable: true },
];

function formatDateTime(value: string | null | undefined): string {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(date);
}

function toLocalInputValue(value: string | null): string {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const offset = date.getTimezoneOffset() * 60000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 16);
}

function toIsoOrNull(value: string): string | null {
  return value ? `${value}:00` : null;
}

function statusLabel(status: AdminUserStatus): string {
  return STATUS_OPTIONS.find((option) => option.value === status)?.label ?? status;
}

function summarizeJson(value: string | null | undefined, emptyText = "미입력"): string {
  if (!value || value === "[]" || value === "{}") return emptyText;
  try {
    const parsed: unknown = JSON.parse(value);
    if (Array.isArray(parsed)) return parsed.length > 0 ? `${parsed.length}개 입력` : emptyText;
    if (parsed && typeof parsed === "object") return `${Object.keys(parsed).length}개 항목`;
    return "입력됨";
  } catch {
    return value.trim() ? "입력됨" : emptyText;
  }
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
  desc = "회원 상세 컨텍스트, 로그인/보안 이력, 인증 이력, 프로필 상태, AI 동의와 사용 이력을 함께 확인합니다.",
  initialStatus = "",
}: AdminUsersPageProps = {}) {
  const [rows, setRows] = useState<AdminUserRow[]>([]);
  const [detail, setDetail] = useState<AdminUserDetail | null>(null);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [keyword, setKeyword] = useState("");
  const [status, setStatus] = useState(initialStatus);
  const [role, setRole] = useState("");
  const [nextStatus, setNextStatus] = useState<AdminUserStatus>("ACTIVE");
  const [reason, setReason] = useState("");
  const [memo, setMemo] = useState("");
  const [blockedUntil, setBlockedUntil] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const selectedIdRef = useRef<number | null>(null);
  selectedIdRef.current = selectedId;

  const selected = useMemo(() => {
    if (selectedId === null) return null;
    return rows.find((row) => row.id === selectedId) ?? null;
  }, [rows, selectedId]);
  const list = useAdminListTools(rows, {
    columns: USER_LIST_COLUMNS,
    getRowId: (row) => row.id,
    defaultPageSize: 20,
    defaultSortId: "createdAt",
    defaultSortDir: "desc",
  });

  const loadRows = async () => {
    setLoading(true);
    setError(null);
    setSuccess(null);
    try {
      const nextRows = await getAdminUsers({ keyword, status, role });
      const currentSelectedId = selectedIdRef.current;
      const nextSelectedId =
        currentSelectedId !== null && nextRows.some((row) => row.id === currentSelectedId)
          ? currentSelectedId
          : nextRows[0]?.id ?? null;
      selectedIdRef.current = nextSelectedId;
      setRows(nextRows);
      setSelectedId(nextSelectedId);
      if (nextSelectedId === null || nextSelectedId !== currentSelectedId) {
        setDetail(null);
      }
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "회원 목록을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  };

  const loadDetail = async (id: number) => {
    setError(null);
    setSuccess(null);
    try {
      const nextDetail = await getAdminUserDetail(id);
      if (selectedIdRef.current === id) {
        setDetail(nextDetail);
        setNextStatus(nextDetail.user.status);
        setReason(nextDetail.user.blockedReason ?? "");
        setMemo("");
        setBlockedUntil(toLocalInputValue(nextDetail.user.blockedUntil));
      }
    } catch (requestError) {
      if (selectedIdRef.current === id) {
        setError(requestError instanceof Error ? requestError.message : "회원 상세 정보를 불러오지 못했습니다.");
      }
    }
  };

  useEffect(() => {
    void loadRows();
  }, []);

  useEffect(() => {
    if (selected?.id) {
      setDetail(null);
      void loadDetail(selected.id);
      return;
    }
    setDetail(null);
  }, [selected?.id]);

  const handleRequestStatusUpdate = () => {
    if (!detail) return;
    setError(null);
    setSuccess(null);
    if (nextStatus === detail.user.status && !memo.trim()) {
      setError("상태가 바뀌지 않았습니다. 상태를 변경하거나 관리자 메모를 입력해 주세요.");
      return;
    }
    if (!reason.trim()) {
      setError("상태 변경 사유를 입력해 주세요.");
      return;
    }
    if (nextStatus === "BLOCKED" && !blockedUntil) {
      setError("차단 상태로 변경할 때는 차단 만료 시각을 입력해 주세요.");
      return;
    }
    setConfirmOpen(true);
  };

  const handleUpdateStatus = async () => {
    if (!detail) return;
    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      const updated = await updateAdminUserStatus(detail.user.id, {
        status: nextStatus,
        reason: reason.trim(),
        memo: memo.trim(),
        blockedUntil: nextStatus === "BLOCKED" ? toIsoOrNull(blockedUntil) : null,
      });
      setRows((items) => items.map((item) => (item.id === updated.id ? updated : item)));
      await loadDetail(updated.id);
      setSuccess(`${updated.email} 회원 상태를 ${statusLabel(updated.status)} 상태로 저장했습니다.`);
      setConfirmOpen(false);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "회원 상태를 변경하지 못했습니다.");
    } finally {
      setSaving(false);
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
        <Button variant="outline" onClick={() => void loadRows()} disabled={loading}>
          <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
          새로고침
        </Button>
      )}
    >
      <div className="grid gap-5 lg:grid-cols-[380px_minmax(0,1fr)]">
        <section className="space-y-4">
          <Card className="border-slate-200 bg-card">
            <CardContent className="space-y-3 p-4">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
                <Input
                  value={keyword}
                  onChange={(event) => setKeyword(event.target.value)}
                  placeholder="이름 또는 이메일 검색"
                  className="pl-9"
                />
              </div>
              <div className="grid grid-cols-2 gap-2">
                <select value={status} onChange={(event) => setStatus(event.target.value)} className="h-10 rounded-md border border-slate-200 px-3 text-sm">
                  <option value="">전체 상태</option>
                  {STATUS_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
                </select>
                <select value={role} onChange={(event) => setRole(event.target.value)} className="h-10 rounded-md border border-slate-200 px-3 text-sm">
                  <option value="">전체 권한</option>
                  <option value="USER">USER</option>
                  <option value="ADMIN">ADMIN</option>
                  <option value="SUPER_ADMIN">SUPER_ADMIN</option>
                </select>
              </div>
              <Button className="w-full bg-blue-600 text-white hover:bg-blue-700" onClick={() => void loadRows()}>
                필터 적용
              </Button>
            </CardContent>
          </Card>

          {error && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}
          {success && (
            <div className="flex items-center gap-2 rounded-lg border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-700">
              <CheckCircle2 className="size-4" />
              {success}
            </div>
          )}

          <Card className="overflow-hidden border-slate-200 bg-card">
            <div className="flex items-center gap-2 border-b border-border px-4 py-3">
              <button
                type="button"
                onClick={list.toggleVisibleRows}
                className="h-8 rounded-md border border-border bg-card px-2.5 text-xs font-semibold text-muted-foreground hover:bg-accent"
              >
                {list.allVisibleSelected ? "페이지 해제" : "페이지 선택"}
              </button>
              <span className="ml-auto text-xs text-muted-foreground">
                <b className="text-foreground">{list.filteredRows.length}</b>건
              </span>
            </div>
            <AdminListToolbar state={list} fileName="admin_users" />
          </Card>

          <div className="space-y-2">
            {list.visibleRows.map((row) => (
              <div
                key={row.id}
                className={`flex items-stretch rounded-lg border bg-card transition-colors ${
                  selected?.id === row.id ? "border-blue-300 ring-2 ring-blue-100" : "border-slate-200 hover:border-blue-200"
                }`}
              >
                <label className="flex w-10 shrink-0 items-center justify-center" onClick={(event) => event.stopPropagation()}>
                  <input
                    type="checkbox"
                    aria-label="회원 선택"
                    checked={list.isSelected(row)}
                    onChange={() => list.toggleRow(row)}
                    className="h-[15px] w-[15px]"
                  />
                </label>
                <button
                  type="button"
                  className="min-w-0 flex-1 p-3 text-left"
                  onClick={() => {
                    selectedIdRef.current = row.id;
                    setSelectedId(row.id);
                    if (row.id !== selectedId) setDetail(null);
                  }}
                >
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      <div className="truncate text-sm font-bold text-slate-950">{row.name}</div>
                      <div className="truncate text-xs text-slate-500">{row.email}</div>
                    </div>
                    <Badge className={statusTone[row.status]}>{row.status}</Badge>
                  </div>
                  <div className="mt-2 grid grid-cols-3 gap-2 text-[11px] text-slate-500">
                    <span>성공 {row.loginSuccessCount}</span>
                    <span>실패 {row.loginFailCount}</span>
                    <span>연속 {row.failedLoginCount}</span>
                  </div>
                </button>
              </div>
            ))}
            {list.visibleRows.length === 0 && (
              <Card className="border-slate-200 bg-card">
                <CardContent className="p-8 text-center text-sm text-slate-500">현재 조건에 맞는 회원이 없습니다.</CardContent>
              </Card>
            )}
          </div>
          <AdminListFooter state={list} />
        </section>

        <section className="min-w-0 space-y-4">
          {!detail ? (
            <Card className="border-slate-200 bg-card">
              <CardContent className="p-8 text-center text-sm text-slate-500">회원을 선택해 주세요.</CardContent>
            </Card>
          ) : (
            <>
              <Card className="border-slate-200 bg-card">
                <CardHeader>
                  <CardTitle className="flex items-center justify-between gap-3 text-lg font-bold text-slate-950">
                    <span>{detail.user.name}</span>
                    <Badge className={statusTone[detail.user.status]}>{detail.user.status}</Badge>
                  </CardTitle>
                </CardHeader>
                <CardContent className="space-y-4">
                  <div className="grid gap-3 md:grid-cols-4">
                    <Info label="이메일" value={detail.user.email} />
                    <Info label="권한" value={detail.user.role} />
                    <Info label="요금제" value={detail.user.plan} />
                    <Info label="크레딧" value={String(detail.user.credit)} />
                    <Info label="이메일 인증" value={detail.user.emailVerified ? "완료" : "미완료"} />
                    <Info label="비밀번호 로그인" value={detail.user.passwordEnabled ? "가능" : "불가"} />
                    <Info label="최근 로그인" value={formatDateTime(detail.user.lastLoginAt)} />
                    <Info label="가입일" value={formatDateTime(detail.user.createdAt)} />
                    <Info label="연속 실패" value={`${detail.user.failedLoginCount}회`} />
                    <Info label="마지막 실패" value={formatDateTime(detail.user.lastFailedLoginAt)} />
                    <Info label="차단 만료" value={formatDateTime(detail.user.blockedUntil)} />
                    <Info label="상태 변경자" value={detail.user.statusChangedBy ? `#${detail.user.statusChangedBy}` : "-"} />
                  </div>

                  <div className="rounded-lg border border-slate-200 p-4">
                    <div className="mb-3 flex items-center gap-2 text-sm font-bold text-slate-900">
                      <ShieldAlert className="size-4 text-red-600" />
                      상태 변경
                    </div>
                    <div className="grid gap-3 md:grid-cols-2">
                      <select value={nextStatus} onChange={(event) => setNextStatus(event.target.value as AdminUserStatus)} className="h-10 rounded-md border border-slate-200 px-3 text-sm">
                        {STATUS_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
                      </select>
                      <Input type="datetime-local" value={blockedUntil} onChange={(event) => setBlockedUntil(event.target.value)} disabled={nextStatus !== "BLOCKED"} />
                    </div>
                    <Input className="mt-3" value={reason} onChange={(event) => setReason(event.target.value)} placeholder="상태 변경 사유" />
                    <Textarea className="mt-3" value={memo} onChange={(event) => setMemo(event.target.value)} placeholder="관리자 메모" />
                    <Button className="mt-3 bg-blue-600 text-white hover:bg-blue-700" onClick={handleRequestStatusUpdate} disabled={saving}>
                      {saving && <RefreshCw className="size-4 animate-spin" />}
                      상태 저장
                    </Button>
                  </div>
                </CardContent>
              </Card>

              <div className="grid gap-4 xl:grid-cols-2">
                <HistoryCard title="로그인/보안 이력">
                  <LoginHistorySection userId={detail.user.id} initial={detail.loginHistory} />
                </HistoryCard>
                <HistoryCard title="이메일 인증/비밀번호 재설정 이력">
                  {detail.emailVerifications.length ? detail.emailVerifications.map((item) => (
                    <div key={item.id} className="rounded-lg border border-slate-100 p-3 text-sm">
                      <div className="flex items-center justify-between gap-3">
                        <span className="font-semibold text-slate-900">{item.purpose}</span>
                        <Badge className={item.used ? "bg-green-100 text-green-700" : "bg-amber-100 text-amber-700"}>
                          {item.used ? "사용됨" : "미사용"}
                        </Badge>
                      </div>
                      <div className="mt-1 text-xs text-slate-500">발급 {formatDateTime(item.createdAt)} / 만료 {formatDateTime(item.expiredAt)}</div>
                      <div className="mt-1 text-xs text-slate-500">대상 이메일 {item.email}</div>
                    </div>
                  )) : <EmptyText text="인증 또는 재설정 이력이 없습니다." />}
                </HistoryCard>
              </div>

              <div className="grid gap-4 xl:grid-cols-2">
                <HistoryCard title="프로필 입력 상태">
                  {detail.profile ? (
                    <div className="grid gap-2 md:grid-cols-2">
                      <Info label="희망 직무" value={detail.profile.desiredJob ?? "미입력"} />
                      <Info label="희망 산업" value={detail.profile.desiredIndustry ?? "미입력"} />
                      <Info label="학력" value={summarizeJson(detail.profile.education)} />
                      <Info label="경력" value={summarizeJson(detail.profile.career)} />
                      <Info label="프로젝트/활동" value={summarizeJson(detail.profile.projects)} />
                      <Info label="기술/역량" value={summarizeJson(detail.profile.skills)} />
                      <Info label="자격증" value={summarizeJson(detail.profile.certificates)} />
                      <Info label="포트폴리오" value={summarizeJson(detail.profile.portfolioLinks)} />
                      <Info label="이력서 원문" value={detail.profile.resumeText ? "입력됨" : "미입력"} />
                      <Info label="자기소개" value={detail.profile.selfIntro ? "입력됨" : "미입력"} />
                    </div>
                  ) : <EmptyText text="프로필이 아직 생성되지 않았습니다." />}
                </HistoryCard>

                <HistoryCard title="AI 데이터 동의/사용 이력">
                  <div className="space-y-2">
                    {detail.consents.map((item) => (
                      <div key={item.id} className="rounded-lg border border-slate-100 p-3 text-sm">
                        <div className="flex items-center justify-between gap-3">
                          <span className="font-semibold text-slate-900">{item.consentType}</span>
                          <Badge className={item.agreed ? "bg-green-100 text-green-700" : "bg-slate-200 text-slate-700"}>
                            {item.agreed ? "동의" : "철회/미동의"}
                          </Badge>
                        </div>
                        <div className="mt-1 text-xs text-slate-500">{formatDateTime(item.createdAt)} / {item.source ?? "-"}</div>
                      </div>
                    ))}
                    {!detail.consents.length && <EmptyText text="동의 이력이 없습니다." />}
                  </div>
                  <div className="mt-3 space-y-2">
                    {detail.aiUsage.map((item) => (
                      <div key={item.id} className="rounded-lg border border-slate-100 p-3 text-sm">
                        <div className="flex items-center justify-between gap-3">
                          <span className="font-semibold text-slate-900">{item.featureType}</span>
                          <Badge className={item.status === "SUCCESS" ? "bg-green-100 text-green-700" : "bg-amber-100 text-amber-700"}>{item.status}</Badge>
                        </div>
                        <div className="mt-1 text-xs text-slate-500">{item.model ?? "모델 미기록"} / 토큰 {item.tokenUsage} / 크레딧 {item.creditUsed}</div>
                        {item.errorMessage && <div className="mt-1 text-xs text-red-600">{item.errorMessage}</div>}
                      </div>
                    ))}
                    {!detail.aiUsage.length && <EmptyText text="AI 사용 이력이 없습니다." />}
                  </div>
                </HistoryCard>
              </div>

              <div className="grid gap-4 xl:grid-cols-2">
                <HistoryCard title="상태 변경 이력">
                  {detail.statusHistory.length ? detail.statusHistory.map((item) => (
                    <div key={item.id} className="rounded-lg border border-slate-100 p-3 text-sm">
                      <div className="font-semibold text-slate-900">{item.previousStatus ?? "-"} → {item.newStatus}</div>
                      <div className="mt-1 text-xs text-slate-500">{formatDateTime(item.createdAt)} / 관리자 #{item.actorUserId ?? "SYSTEM"}</div>
                      {(item.reason || item.memo) && <div className="mt-1 text-xs text-slate-600">{item.reason ?? item.memo}</div>}
                    </div>
                  )) : <EmptyText text="상태 변경 이력이 없습니다." />}
                </HistoryCard>

                <HistoryCard title="Refresh Token 세션">
                  {detail.refreshTokens.length ? detail.refreshTokens.map((item) => (
                    <div key={item.id} className="rounded-lg border border-slate-100 p-3 text-sm">
                      <div className="flex items-center justify-between gap-3">
                        <span className="font-semibold text-slate-900">세션 #{item.id}</span>
                        <Badge className={item.revoked ? "bg-red-100 text-red-700" : "bg-green-100 text-green-700"}>
                          {item.revoked ? "폐기" : "활성"}
                        </Badge>
                      </div>
                      <div className="mt-1 text-xs text-slate-500">발급 {formatDateTime(item.createdAt)} / 만료 {formatDateTime(item.expiredAt)}</div>
                      <div className="mt-1 text-xs text-slate-500">IP {item.ipAddress ?? "-"} / User-Agent {item.userAgent ?? "-"}</div>
                    </div>
                  )) : <EmptyText text="저장된 세션 이력이 없습니다." />}
                </HistoryCard>
              </div>

              <AlertDialog open={confirmOpen} onOpenChange={setConfirmOpen}>
                <AlertDialogContent>
                  <AlertDialogHeader>
                    <AlertDialogTitle>회원 상태를 변경할까요?</AlertDialogTitle>
                    <AlertDialogDescription>
                      {detail.user.email} 회원을 {statusLabel(detail.user.status)} 상태에서 {statusLabel(nextStatus)} 상태로 변경합니다.
                      ACTIVE가 아닌 상태는 로그인과 서비스 사용에 영향을 줄 수 있습니다.
                    </AlertDialogDescription>
                  </AlertDialogHeader>
                  <AlertDialogFooter>
                    <AlertDialogCancel disabled={saving}>취소</AlertDialogCancel>
                    <AlertDialogAction
                      className="bg-blue-600 text-white hover:bg-blue-700"
                      disabled={saving}
                      onClick={(event) => {
                        event.preventDefault();
                        void handleUpdateStatus();
                      }}
                    >
                      {saving && <RefreshCw className="size-4 animate-spin" />}
                      변경 확정
                    </AlertDialogAction>
                  </AlertDialogFooter>
                </AlertDialogContent>
              </AlertDialog>
            </>
          )}
        </section>
      </div>
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

export function AdminSecurityAuditPage() {
  return (
    <AdminUsersPage
      active="security-audit"
      breadcrumb="로그인/보안 감사"
      title="로그인/보안 감사"
      icon={LockKeyhole}
      desc="로그인 성공/실패, 실패 사유, IP, 세션 정보를 사용자 단위로 확인하는 보안 감사 화면입니다."
    />
  );
}

export function AdminEmailAuditPage() {
  return (
    <AdminUsersPage
      active="email-audit"
      breadcrumb="이메일 감사"
      title="이메일 인증/재설정 감사"
      icon={MailCheck}
      desc="이메일 인증과 비밀번호 재설정 토큰 발급, 사용, 만료 이력을 사용자 단위로 확인합니다."
    />
  );
}

function Info({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg bg-slate-50 px-3 py-2">
      <div className="text-[11px] font-semibold uppercase text-slate-400">{label}</div>
      <div className="mt-1 break-words text-sm font-semibold text-slate-800">{value}</div>
    </div>
  );
}

function HistoryCard({ title, children }: { title: string; children: ReactNode }) {
  return (
    <Card className="border-slate-200 bg-card">
      <CardHeader className="pb-3">
        <CardTitle className="text-base">{title}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-2">{children}</CardContent>
    </Card>
  );
}

function EmptyText({ text }: { text: string }) {
  return <div className="rounded-lg bg-slate-50 p-4 text-center text-sm text-slate-500">{text}</div>;
}

function LoginHistorySection({ userId, initial }: { userId: number; initial: AdminUserLoginHistoryRow[] }) {
  const [rows, setRows] = useState<AdminUserLoginHistoryRow[]>(initial);
  const [expanded, setExpanded] = useState(false);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setRows(initial);
    setExpanded(false);
  }, [initial, userId]);

  const loadFull = async () => {
    setLoading(true);
    try {
      setRows(await getAdminUserLoginHistory(userId, 100));
      setExpanded(true);
    } finally {
      setLoading(false);
    }
  };

  if (rows.length === 0) {
    return <EmptyText text="로그인 이력이 없습니다." />;
  }

  return (
    <>
      {rows.map((item) => (
        <div key={item.id} className="rounded-lg border border-slate-100 p-3 text-sm">
          <div className="flex items-center justify-between gap-3">
            <span className="font-semibold text-slate-900">{item.eventType} / {item.loginMethod ?? "-"}</span>
            <Badge className={item.success ? "bg-green-100 text-green-700" : "bg-red-100 text-red-700"}>{item.success ? "성공" : "실패"}</Badge>
          </div>
          <div className="mt-1 text-xs text-slate-500">{formatDateTime(item.createdAt)} / {item.ipAddress ?? "-"}</div>
          {item.failReason && <div className="mt-1 text-xs text-red-600">{item.failReason}</div>}
        </div>
      ))}
      {!expanded && (
        <Button variant="outline" size="sm" className="w-full" disabled={loading} onClick={() => void loadFull()}>
          {loading && <RefreshCw className="size-3.5 animate-spin" />}
          전체 로그인 이력 더 보기
        </Button>
      )}
    </>
  );
}
