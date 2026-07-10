import { FormEvent, useEffect, useState } from "react";
import { QRCodeSVG } from "qrcode.react";
import { Copy, KeyRound, Loader2, RefreshCw, ShieldCheck, ShieldOff, Smartphone } from "lucide-react";
import {
  disableMfa,
  getMfaStatus,
  regenerateBackupCodes,
  startMfaSetup,
  verifyMfaSetup,
  type MfaSetupStartResponse,
  type MfaStatusResponse,
} from "@/app/auth/mfaApi";
import { generateTotpCode, totpSecondsRemaining } from "@/app/auth/totp";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Input } from "@/app/components/ui/input";
import { enablePush, type PushRegisterResult } from "@/platform/push";

const MOBILE_TOTP_KEY = "careertuner.mobileTotpSecret";

interface StoredTotpSecret {
  secret: string;
  label: string;
}

export function MfaSettingsCard() {
  const [status, setStatus] = useState<MfaStatusResponse | null>(null);
  const [setup, setSetup] = useState<MfaSetupStartResponse | null>(null);
  const [storedSecret, setStoredSecret] = useState<StoredTotpSecret | null>(() => readStoredSecret());
  const [appCode, setAppCode] = useState("");
  const [secondsLeft, setSecondsLeft] = useState(totpSecondsRemaining());
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

  useEffect(() => {
    let alive = true;
    const refresh = async () => {
      setSecondsLeft(totpSecondsRemaining());
      if (!storedSecret?.secret) {
        setAppCode("");
        return;
      }
      try {
        const next = await generateTotpCode(storedSecret.secret);
        if (alive) setAppCode(next);
      } catch {
        if (alive) setAppCode("");
      }
    };
    void refresh();
    const timer = window.setInterval(() => void refresh(), 1000);
    return () => {
      alive = false;
      window.clearInterval(timer);
    };
  }, [storedSecret]);

  const start = async () => {
    setLoading(true);
    setMessage(null);
    setError(null);
    try {
      const next = await startMfaSetup(deviceName);
      setSetup(next);
      setBackupCodes([]);
      saveStoredSecret({ secret: next.secret, label: next.deviceName });
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
      clearStoredSecret();
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

  const saveForMobileDemo = async () => {
    if (!setup?.secret) return;
    saveStoredSecret({ secret: setup.secret, label: setup.deviceName });
    try {
      const nextCode = await generateTotpCode(setup.secret);
      setAppCode(nextCode);
      setCode(nextCode);
      setMessage("현재 CareerTuner 앱에서 6자리 인증 코드를 생성했습니다. 입력칸에 자동으로 채워진 코드를 검증해 주세요.");
    } catch {
      setMessage("현재 브라우저/앱에 인증 코드 생성용 키를 저장했습니다. 아래 수동 입력 키를 외부 인증 앱에 입력해도 됩니다.");
    }
  };

  const registerPushDevice = async () => {
    setLoading(true);
    setError(null);
    setMessage(null);
    try {
      const result = await enablePush();
      setMessage(pushResultMessage(result));
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "이 기기를 푸시 승인 기기로 등록하지 못했습니다.");
    } finally {
      setLoading(false);
    }
  };

  const copyAppCode = async () => {
    if (!appCode) return;
    try {
      await navigator.clipboard.writeText(appCode);
      setMessage("6자리 인증 코드를 복사했습니다.");
    } catch {
      setMessage("코드를 길게 눌러 직접 복사해 주세요.");
    }
  };

  function saveStoredSecret(next: StoredTotpSecret) {
    localStorage.setItem(MOBILE_TOTP_KEY, JSON.stringify(next));
    setStoredSecret(next);
  }

  function clearStoredSecret() {
    localStorage.removeItem(MOBILE_TOTP_KEY);
    setStoredSecret(null);
  }

  // 테마 대응: 강제 흰 배경(dark:bg-white)은 다크에서 제목·버튼(foreground=밝은색)이 백-온-화이트로 불가시가 된다.
  // Card 기본(bg-card/text-card-foreground)에 맡기고, QR 래퍼만 스캔 가독성을 위해 흰색을 유지한다.
  return (
    <Card className="border border-slate-200 shadow-sm">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <ShieldCheck className="size-4 text-blue-600" />
          2단계 인증
          {status?.enabled ? <Badge className="bg-blue-100 text-blue-700">활성화</Badge> : <Badge variant="secondary">비활성화</Badge>}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm leading-6 text-slate-700">
          로그인 시 비밀번호 확인 후 6자리 코드를 입력하거나, CareerTuner 앱으로 전송된 승인 요청을 승인합니다.
          외부 인증 앱을 쓰지 않아도 이 기기에 저장된 키로 CareerTuner 앱 안에서 6자리 코드를 생성할 수 있습니다.
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
            <div className="rounded-xl border border-slate-200 bg-card p-4 shadow-sm">
              {/* QR 래퍼는 스캔 가독성을 위해 테마와 무관하게 흰색 유지 */}
              <div className="rounded-lg bg-white p-2">
                <QRCodeSVG value={setup.otpauthUri} size={148} bgColor="#ffffff" fgColor="#0f172a" />
              </div>
              <p className="mt-3 text-center text-xs font-medium text-slate-600">외부 인증 앱으로 스캔</p>
            </div>
            <div className="space-y-3">
              <div>
                <div className="text-xs font-semibold text-slate-600">수동 입력 키</div>
                <code className="mt-1 block break-all rounded-lg border border-slate-200 bg-card p-3 text-sm font-semibold text-slate-900 shadow-sm">{setup.secret}</code>
              </div>
              {storedSecret?.secret === setup.secret && (
                <div className="rounded-xl border border-blue-200 bg-blue-50 p-3 shadow-sm">
                  <div className="flex items-center justify-between gap-2">
                    <div>
                      <div className="text-sm font-bold text-blue-950">CareerTuner 앱에서 생성된 코드</div>
                      <div className="text-xs text-blue-800">이 코드를 아래 검증 입력칸에 넣으면 2단계 인증이 활성화됩니다.</div>
                    </div>
                    <Badge className="bg-card text-blue-700 ring-1 ring-blue-100">{secondsLeft}초</Badge>
                  </div>
                  <div className="mt-3 flex items-center justify-between gap-3 rounded-lg border border-blue-100 bg-card px-4 py-3 shadow-sm">
                    <code className="text-3xl font-black tracking-[0.25em] text-slate-950">{appCode || "------"}</code>
                    <Button variant="outline" size="sm" type="button" onClick={() => void copyAppCode()} disabled={!appCode}>
                      <Copy className="size-4" />
                    </Button>
                  </div>
                </div>
              )}
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
              <Button variant="outline" type="button" onClick={() => void saveForMobileDemo()}>
                CareerTuner 앱 코드 생성기로 사용
              </Button>
            </div>
          </div>
        )}

        {status?.enabled && (
          <div className="space-y-3">
            <div className="grid gap-3 lg:grid-cols-[1fr_1fr]">
              <div className="rounded-xl border border-blue-200 bg-blue-50 p-4 shadow-sm">
                <div className="flex items-center justify-between gap-3">
                  <div>
                    <div className="text-sm font-bold text-blue-950">CareerTuner 앱 인증 코드</div>
                    <div className="mt-1 text-xs text-blue-800">
                      {storedSecret ? `${storedSecret.label}에 저장된 키로 생성 중` : "이 기기에 저장된 인증 키가 없습니다."}
                    </div>
                  </div>
                  <Badge className="bg-card text-blue-700 ring-1 ring-blue-100">{secondsLeft}초</Badge>
                </div>
                {storedSecret ? (
                  <div className="mt-4 flex items-center justify-between gap-3 rounded-lg border border-blue-100 bg-card px-4 py-3 shadow-sm">
                    <code className="text-3xl font-black tracking-[0.25em] text-slate-950">{appCode || "------"}</code>
                    <Button variant="outline" size="sm" onClick={() => void copyAppCode()} disabled={!appCode}>
                      <Copy className="size-4" />
                    </Button>
                  </div>
                ) : (
                  <div className="mt-4 rounded-lg border border-dashed border-blue-200 bg-card p-3 text-sm leading-6 text-blue-800">
                    이 기능은 설정 시작 시 표시되는 키를 이 기기에 저장해야 사용할 수 있습니다.
                    이미 외부 인증 앱으로만 등록했다면 2단계 인증을 해제 후 다시 설정하면서
                    "CareerTuner 앱 코드 생성기로 사용"을 선택해 주세요.
                  </div>
                )}
              </div>

              <div className="rounded-xl border border-slate-200 bg-card p-4 shadow-sm">
                <div className="text-sm font-bold text-slate-900">푸시 승인형 로그인</div>
                <p className="mt-1 text-sm leading-6 text-slate-600">
                  이 기기를 푸시 수신 기기로 등록하면, PC에서 로그인할 때 앱 알림을 눌러 승인할 수 있습니다.
                  Firebase 서비스계정이 설정된 환경에서는 실제 FCM 푸시가 발송됩니다.
                </p>
                <div className="mt-3 flex flex-col gap-2 sm:flex-row">
                  <Button variant="outline" className="gap-2" onClick={() => void registerPushDevice()} disabled={loading}>
                    <Smartphone className="size-4" />
                    이 기기에서 푸시 승인 받기
                  </Button>
                  <Button variant="outline" className="gap-2" onClick={() => { window.location.href = "/m/mfa-approvals"; }}>
                    승인 요청 보기
                  </Button>
                </div>
              </div>
            </div>
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
                <code key={item} className="rounded bg-card px-3 py-2 text-sm text-slate-700">{item}</code>
              ))}
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function readStoredSecret(): StoredTotpSecret | null {
  try {
    const raw = localStorage.getItem(MOBILE_TOTP_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Partial<StoredTotpSecret>;
    if (!parsed.secret) return null;
    return { secret: parsed.secret, label: parsed.label || "CareerTuner 앱" };
  } catch {
    return null;
  }
}

function pushResultMessage(result: PushRegisterResult): string {
  if (result === "subscribed") {
    return "이 기기를 푸시 승인 기기로 등록했습니다.";
  }
  if (result === "denied") {
    return "알림 권한이 거부되어 푸시 승인을 사용할 수 없습니다. 기기 설정에서 알림 권한을 허용해 주세요.";
  }
  if (result === "unsupported") {
    return "현재 환경에서는 푸시 알림을 지원하지 않습니다.";
  }
  return "알림 권한은 확인됐지만 푸시 토큰 등록은 완료되지 않았습니다. FCM 또는 Web Push 설정을 확인해 주세요.";
}
