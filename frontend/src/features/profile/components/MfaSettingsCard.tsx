import { FormEvent, useEffect, useState } from "react";
import { QRCodeSVG } from "qrcode.react";
import { KeyRound, Loader2, RefreshCw, ShieldCheck, ShieldOff } from "lucide-react";
import {
  disableMfa,
  getMfaStatus,
  regenerateBackupCodes,
  startMfaSetup,
  verifyMfaSetup,
  type MfaSetupStartResponse,
  type MfaStatusResponse,
} from "@/app/auth/mfaApi";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Input } from "@/app/components/ui/input";

const MOBILE_TOTP_KEY = "careertuner.mobileTotpSecret";

export function MfaSettingsCard() {
  const [status, setStatus] = useState<MfaStatusResponse | null>(null);
  const [setup, setSetup] = useState<MfaSetupStartResponse | null>(null);
  const [deviceName, setDeviceName] = useState("내 휴대폰");
  const [code, setCode] = useState("");
  const [disableCode, setDisableCode] = useState("");
  const [backupCodes, setBackupCodes] = useState<string[]>([]);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const load = async () => {
    setError(null);
    try {
      setStatus(await getMfaStatus());
    } catch (err) {
      setError(err instanceof Error ? err.message : "2단계 인증 상태를 불러오지 못했습니다.");
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const start = async () => {
    setLoading(true);
    setMessage(null);
    setError(null);
    try {
      const next = await startMfaSetup(deviceName);
      setSetup(next);
      setBackupCodes([]);
      setMessage("QR 코드를 인증 앱으로 스캔한 뒤 6자리 코드를 입력해 주세요.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "2단계 인증 설정을 시작하지 못했습니다.");
    } finally {
      setLoading(false);
    }
  };

  const verify = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setLoading(true);
    setError(null);
    try {
      const result = await verifyMfaSetup(code);
      setBackupCodes(result.codes);
      setSetup(null);
      setCode("");
      setMessage("2단계 인증이 활성화되었습니다. 백업 코드는 지금 한 번만 표시됩니다.");
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "인증 코드가 올바르지 않습니다.");
    } finally {
      setLoading(false);
    }
  };

  const disable = async () => {
    setLoading(true);
    setError(null);
    try {
      await disableMfa(disableCode, disableCode);
      setDisableCode("");
      setBackupCodes([]);
      setMessage("2단계 인증이 비활성화되었습니다.");
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "2단계 인증을 해제하지 못했습니다.");
    } finally {
      setLoading(false);
    }
  };

  const regenerate = async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await regenerateBackupCodes();
      setBackupCodes(result.codes);
      setMessage("백업 코드가 새로 발급되었습니다. 이전 백업 코드는 사용할 수 없습니다.");
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "백업 코드를 재발급하지 못했습니다.");
    } finally {
      setLoading(false);
    }
  };

  const saveForMobileDemo = () => {
    if (!setup?.secret) return;
    localStorage.setItem(MOBILE_TOTP_KEY, JSON.stringify({ secret: setup.secret, label: setup.deviceName }));
    setMessage("현재 브라우저/앱에 인증 코드 생성용 키를 저장했습니다. 실제 운영 앱은 Android Keystore 같은 보안 저장소로 교체해야 합니다.");
  };

  return (
    <Card className="border border-slate-200 bg-card">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <ShieldCheck className="size-4 text-blue-600" />
          2단계 인증
          {status?.enabled ? <Badge className="bg-blue-100 text-blue-700">활성화</Badge> : <Badge variant="secondary">비활성화</Badge>}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm leading-6 text-slate-600">
          로그인 시 비밀번호 확인 후 인증 앱의 6자리 코드를 한 번 더 입력합니다. 모바일 앱에서는 로그인 승인 요청을 승인하는 방식도 사용할 수 있습니다.
        </div>
        {status?.adminSetupRecommended && !status.enabled && (
          <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">
            관리자 정책상 관리자 계정은 2단계 인증 설정이 권장됩니다.
          </div>
        )}
        {message && <div className="rounded-lg border border-green-200 bg-green-50 p-3 text-sm text-green-700">{message}</div>}
        {error && <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error}</div>}

        {!status?.enabled && !setup && (
          <div className="grid gap-3 sm:grid-cols-[1fr_auto]">
            <Input value={deviceName} onChange={(event) => setDeviceName(event.target.value)} placeholder="기기 이름" />
            <Button onClick={() => void start()} disabled={loading} className="gap-2 bg-blue-600 text-white hover:bg-blue-700">
              {loading ? <Loader2 className="size-4 animate-spin" /> : <KeyRound className="size-4" />}
              설정 시작
            </Button>
          </div>
        )}

        {setup && (
          <div className="grid gap-4 lg:grid-cols-[180px_1fr]">
            <div className="rounded-xl border border-slate-200 bg-white p-4">
              <QRCodeSVG value={setup.otpauthUri} size={148} />
            </div>
            <div className="space-y-3">
              <div>
                <div className="text-xs font-semibold text-slate-500">수동 입력 키</div>
                <code className="mt-1 block break-all rounded bg-slate-100 p-2 text-sm text-slate-700">{setup.secret}</code>
              </div>
              <form className="flex flex-col gap-2 sm:flex-row" onSubmit={verify}>
                <Input
                  inputMode="numeric"
                  maxLength={6}
                  value={code}
                  onChange={(event) => setCode(event.target.value.replace(/\D/g, "").slice(0, 6))}
                  placeholder="6자리 코드"
                />
                <Button disabled={loading || code.length !== 6} className="bg-blue-600 text-white hover:bg-blue-700">검증</Button>
              </form>
              <Button variant="outline" type="button" onClick={saveForMobileDemo}>
                이 기기에 인증 코드 생성 키 저장
              </Button>
            </div>
          </div>
        )}

        {status?.enabled && (
          <div className="space-y-3">
            <div className="grid gap-2 text-sm text-slate-600 sm:grid-cols-3">
              <div className="rounded-lg border border-slate-200 p-3">기기: {status.deviceName ?? "미지정"}</div>
              <div className="rounded-lg border border-slate-200 p-3">방식: {status.mfaType}</div>
              <div className="rounded-lg border border-slate-200 p-3">남은 백업 코드: {status.backupCodeRemaining}개</div>
            </div>
            <div className="flex flex-col gap-2 sm:flex-row">
              <Input value={disableCode} onChange={(event) => setDisableCode(event.target.value)} placeholder="6자리 코드 또는 백업 코드" />
              <Button variant="outline" className="gap-2 text-red-600" onClick={() => void disable()} disabled={loading || !disableCode}>
                <ShieldOff className="size-4" />
                비활성화
              </Button>
              <Button variant="outline" className="gap-2" onClick={() => void regenerate()} disabled={loading}>
                <RefreshCw className="size-4" />
                백업 코드 재발급
              </Button>
            </div>
          </div>
        )}

        {backupCodes.length > 0 && (
          <div className="rounded-xl border border-amber-200 bg-amber-50 p-4">
            <div className="mb-2 text-sm font-bold text-amber-900">백업 코드</div>
            <div className="grid gap-2 sm:grid-cols-2">
              {backupCodes.map((item) => (
                <code key={item} className="rounded bg-white px-3 py-2 text-sm text-slate-700">{item}</code>
              ))}
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
