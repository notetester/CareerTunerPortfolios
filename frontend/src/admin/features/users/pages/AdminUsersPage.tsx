import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { CheckCircle2, RefreshCw, Search, ShieldAlert, Users } from "lucide-react";
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
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Input } from "@/app/components/ui/input";
import { Textarea } from "@/app/components/ui/textarea";
import { getAdminUserDetail, getAdminUsers, updateAdminUserStatus } from "../api";
import type { AdminUserDetail, AdminUserRow, AdminUserStatus } from "../types";

const STATUS_OPTIONS: Array<{ value: AdminUserStatus; label: string }> = [
  { value: "ACTIVE", label: "활성" },
  { value: "DORMANT", label: "휴면" },
  { value: "BLOCKED", label: "차단" },
  { value: "DELETED", label: "삭제" },
];

const statusTone: Record<AdminUserStatus, string> = {
  ACTIVE: "bg-green-100 text-green-700",
  DORMANT: "bg-amber-100 text-amber-700",
  BLOCKED: "bg-red-100 text-red-700",
  DELETED: "bg-slate-200 text-slate-700",
};

const statusLabel = (status: AdminUserStatus) =>
  STATUS_OPTIONS.find((option) => option.value === status)?.label ?? status;

function formatDateTime(value: string | null | undefined): string {
  if (!value) return "-";
  return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

function toLocalInputValue(value: string | null): string {
  if (!value) return "";
  const date = new Date(value);
  const offset = date.getTimezoneOffset() * 60000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 16);
}

function toIsoOrNull(value: string): string | null {
  return value ? `${value}:00` : null;
}

export function AdminUsersPage() {
  const [rows, setRows] = useState<AdminUserRow[]>([]);
  const [detail, setDetail] = useState<AdminUserDetail | null>(null);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [keyword, setKeyword] = useState("");
  const [status, setStatus] = useState("");
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
        setError(requestError instanceof Error ? requestError.message : "회원 상세를 불러오지 못했습니다.");
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
      setError("상태가 변경되지 않았습니다. 상태를 바꾸거나 관리자 메모를 입력해 주세요.");
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
      active="members"
      breadcrumb="회원 관리"
      title="회원 관리"
      icon={Users}
      desc="회원 상태, 로그인 실패, 로그인 감사 로그와 동의 이력을 확인하고 운영 상태를 변경합니다."
      actions={(
        <Button variant="outline" onClick={() => void loadRows()} disabled={loading}>
          <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
          새로고침
        </Button>
      )}
    >
      <div className="grid gap-5 lg:grid-cols-[380px_minmax(0,1fr)]">
        <section className="space-y-4">
          <Card className="border-slate-200 bg-white">
            <CardContent className="space-y-3 p-4">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
                <Input value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="이름 또는 이메일 검색" className="pl-9" />
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

          <div className="space-y-2">
            {rows.map((row) => (
              <button
                key={row.id}
                type="button"
                className={`w-full rounded-lg border bg-white p-3 text-left transition-colors ${
                  selected?.id === row.id ? "border-blue-300 ring-2 ring-blue-100" : "border-slate-200 hover:border-blue-200"
                }`}
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
            ))}
          </div>
        </section>

        <section className="min-w-0 space-y-4">
          {!detail ? (
            <Card className="border-slate-200 bg-white">
              <CardContent className="p-8 text-center text-sm text-slate-500">회원을 선택하세요.</CardContent>
            </Card>
          ) : (
            <>
              <Card className="border-slate-200 bg-white">
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
                  <AlertDialog open={confirmOpen} onOpenChange={setConfirmOpen}>
                    <AlertDialogContent>
                      <AlertDialogHeader>
                        <AlertDialogTitle>회원 상태를 변경할까요?</AlertDialogTitle>
                        <AlertDialogDescription>
                          {detail.user.email} 회원을 {statusLabel(detail.user.status)} 상태에서 {statusLabel(nextStatus)} 상태로 변경합니다.
                          ACTIVE가 아닌 상태로 변경하면 기존 로그인 세션이 만료될 수 있습니다.
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
                </CardContent>
              </Card>

              <div className="grid gap-4 xl:grid-cols-2">
                <HistoryCard title="로그인 이력">
                  {detail.loginHistory.length ? detail.loginHistory.map((item) => (
                    <div key={item.id} className="rounded-lg border border-slate-100 p-3 text-sm">
                      <div className="flex items-center justify-between gap-3">
                        <span className="font-semibold text-slate-900">{item.eventType} / {item.loginMethod ?? "-"}</span>
                        <Badge className={item.success ? "bg-green-100 text-green-700" : "bg-red-100 text-red-700"}>{item.success ? "성공" : "실패"}</Badge>
                      </div>
                      <div className="mt-1 text-xs text-slate-500">{formatDateTime(item.createdAt)} · {item.ipAddress ?? "-"}</div>
                      {item.failReason && <div className="mt-1 text-xs text-red-600">{item.failReason}</div>}
                    </div>
                  )) : <EmptyText text="로그인 이력이 없습니다." />}
                </HistoryCard>

                <HistoryCard title="상태 변경 이력">
                  {detail.statusHistory.length ? detail.statusHistory.map((item) => (
                    <div key={item.id} className="rounded-lg border border-slate-100 p-3 text-sm">
                      <div className="font-semibold text-slate-900">{item.previousStatus ?? "-"} → {item.newStatus}</div>
                      <div className="mt-1 text-xs text-slate-500">{formatDateTime(item.createdAt)} · 관리자 #{item.actorUserId ?? "SYSTEM"}</div>
                      {(item.reason || item.memo) && <div className="mt-1 text-xs text-slate-600">{item.reason ?? item.memo}</div>}
                    </div>
                  )) : <EmptyText text="상태 변경 이력이 없습니다." />}
                </HistoryCard>
              </div>

              <HistoryCard title="동의 이력">
                {detail.consents.length ? detail.consents.map((item) => (
                  <div key={item.id} className="rounded-lg border border-slate-100 p-3 text-sm">
                    <div className="flex items-center justify-between gap-3">
                      <span className="font-semibold text-slate-900">{item.consentType}</span>
                      <Badge className={item.agreed ? "bg-green-100 text-green-700" : "bg-slate-200 text-slate-700"}>{item.agreed ? "동의" : "미동의/철회"}</Badge>
                    </div>
                    <div className="mt-1 text-xs text-slate-500">{formatDateTime(item.createdAt)} · {item.source ?? "-"}</div>
                  </div>
                )) : <EmptyText text="동의 이력이 없습니다." />}
              </HistoryCard>
            </>
          )}
        </section>
      </div>
    </AdminShell>
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
    <Card className="border-slate-200 bg-white">
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
