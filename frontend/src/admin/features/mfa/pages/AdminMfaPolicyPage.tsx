import { useEffect, useState } from "react";
import { Loader2, Save, ShieldCheck } from "lucide-react";
import AdminShell from "@/admin/components/AdminShell";
import { getMfaPolicy, updateMfaPolicy, type MfaPolicyResponse } from "@/app/auth/mfaApi";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Checkbox } from "@/app/components/ui/checkbox";

export function AdminMfaPolicyPage() {
  const [policy, setPolicy] = useState<MfaPolicyResponse | null>(null);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void load();
  }, []);

  const load = async () => {
    setError(null);
    try {
      setPolicy(await getMfaPolicy());
    } catch (err) {
      setError(err instanceof Error ? err.message : "MFA 정책을 불러오지 못했습니다.");
    }
  };

  const save = async () => {
    if (!policy) return;
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      setPolicy(await updateMfaPolicy(policy));
      setMessage("MFA 정책이 저장되었습니다.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "MFA 정책 저장에 실패했습니다.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <AdminShell
      active="mfa-policy"
      breadcrumb="보안 / MFA 정책"
      title="MFA 정책"
      icon={ShieldCheck}
      desc="관리자 계정의 2단계 인증 권장 정책과 로그인 보조 수단을 관리합니다."
      actions={
        <Button className="gap-2 bg-blue-600 text-white hover:bg-blue-700" onClick={() => void save()} disabled={saving || !policy}>
          {saving ? <Loader2 className="size-4 animate-spin" /> : <Save className="size-4" />}
          저장
        </Button>
      }
    >
      <Card className="border border-slate-200 bg-white">
        <CardHeader>
          <CardTitle className="text-base">2단계 인증 운영 정책</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          {message && <div className="rounded-lg border border-green-200 bg-green-50 p-3 text-sm text-green-700">{message}</div>}
          {error && <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error}</div>}
          {!policy ? (
            <div className="text-sm text-slate-500">정책을 불러오는 중입니다.</div>
          ) : (
            <div className="space-y-3">
              <PolicyToggle
                checked={policy.requireAdmins}
                onChange={(requireAdmins) => setPolicy({ ...policy, requireAdmins })}
                title="관리자 계정 MFA 설정 권장"
                desc="관리자 계정 설정 화면에 2단계 인증 설정 권장 안내를 표시합니다. 완전 강제 차단은 운영 전환 계획과 함께 별도로 적용하는 것이 안전합니다."
              />
              <PolicyToggle
                checked={policy.allowBackupCode}
                onChange={(allowBackupCode) => setPolicy({ ...policy, allowBackupCode })}
                title="백업 코드 로그인 허용"
                desc="휴대폰 분실이나 인증 앱 접근 불가 상황에서 백업 코드로 로그인할 수 있게 합니다."
              />
              <PolicyToggle
                checked={policy.allowPushApproval}
                onChange={(allowPushApproval) => setPolicy({ ...policy, allowPushApproval })}
                title="모바일 승인형 인증 허용"
                desc="APK/모바일 화면에서 로그인 승인 요청을 승인하거나 거절할 수 있게 합니다."
              />
            </div>
          )}
        </CardContent>
      </Card>
    </AdminShell>
  );
}

function PolicyToggle({
  checked,
  onChange,
  title,
  desc,
}: {
  checked: boolean;
  onChange(next: boolean): void;
  title: string;
  desc: string;
}) {
  return (
    <label className="flex items-start justify-between gap-4 rounded-xl border border-slate-200 p-4">
      <span>
        <span className="block text-sm font-semibold text-slate-900">{title}</span>
        <span className="mt-1 block text-sm leading-6 text-slate-500">{desc}</span>
      </span>
      <Checkbox checked={checked} onCheckedChange={(value) => onChange(value === true)} />
    </label>
  );
}
