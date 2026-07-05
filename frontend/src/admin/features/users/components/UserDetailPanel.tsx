import { useEffect, useState } from "react";
import { CheckCircle2, RefreshCw, ShieldAlert } from "lucide-react";
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
import { getAdminUserLoginHistory, updateAdminUserStatus } from "../api";
import type { AdminUserDetail, AdminUserLoginHistoryRow, AdminUserRow, AdminUserStatus } from "../types";
import {
  EmptyText,
  HistoryCard,
  Info,
  STATUS_OPTIONS,
  formatDateTime,
  statusLabel,
  statusTone,
  summarizeJson,
  toIsoOrNull,
  toLocalInputValue,
} from "./userDisplay";

/**
 * 회원 상세 패널 — 상세 컨텍스트(로그인/보안/동의/프로필/세션) + 상태 변경 폼.
 * 목록형 화면(레거시 마스터-디테일)과 그리드의 상세 모달이 함께 재사용한다.
 */
interface UserDetailPanelProps {
  detail: AdminUserDetail;
  /** 상태 저장 성공 시 갱신된 행 전달(목록 갱신 + 상세 재조회는 부모 책임). */
  onUpdated: (updated: AdminUserRow) => void | Promise<void>;
}

export function UserDetailPanel({ detail, onUpdated }: UserDetailPanelProps) {
  const [nextStatus, setNextStatus] = useState<AdminUserStatus>(detail.user.status);
  const [reason, setReason] = useState(detail.user.blockedReason ?? "");
  const [memo, setMemo] = useState("");
  const [blockedUntil, setBlockedUntil] = useState(toLocalInputValue(detail.user.blockedUntil));
  const [saving, setSaving] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  useEffect(() => {
    setNextStatus(detail.user.status);
    setReason(detail.user.blockedReason ?? "");
    setMemo("");
    setBlockedUntil(toLocalInputValue(detail.user.blockedUntil));
    setError(null);
    setSuccess(null);
  }, [detail.user.id, detail.user.status, detail.user.blockedReason, detail.user.blockedUntil]);

  const handleRequestStatusUpdate = () => {
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
      setSuccess(`${updated.email} 회원 상태를 ${statusLabel(updated.status)} 상태로 저장했습니다.`);
      setConfirmOpen(false);
      await onUpdated(updated);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "회원 상태를 변경하지 못했습니다.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-4">
      {error && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}
      {success && (
        <div className="flex items-center gap-2 rounded-lg border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-700">
          <CheckCircle2 className="size-4" />
          {success}
        </div>
      )}

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
              <select
                value={nextStatus}
                onChange={(event) => setNextStatus(event.target.value as AdminUserStatus)}
                className="h-10 rounded-md border border-slate-200 px-3 text-sm"
              >
                {STATUS_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
              <Input
                type="datetime-local"
                value={blockedUntil}
                onChange={(event) => setBlockedUntil(event.target.value)}
                disabled={nextStatus !== "BLOCKED"}
              />
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
    </div>
  );
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
