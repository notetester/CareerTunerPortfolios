import { useMemo, useState } from "react";
import { AlertTriangle, RotateCcw, Save, Server } from "lucide-react";
import { useAuth } from "@/app/auth/AuthContext";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Input } from "@/app/components/ui/input";
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from "@/app/components/ui/select";
import { apiBase, apiBaseOverride, setApiBaseOverride } from "@/app/lib/apiBase";
import { isNativeApp } from "@/platform/capacitor";
import {
  initialServerPreset,
  resolveServerOverride,
  SERVER_PRESETS,
  serverOverrideChanged,
} from "../lib/serverAddress";

/**
 * 서버 주소(앱/개발 전용) 카드.
 *
 * 네이티브 앱(APK)은 재빌드 없이, 개발 웹은 재기동 없이 백엔드 환경(로컬/Tailscale/AWS)을
 * 전환할 수 있게 apiBase() 런타임 오버라이드를 기록한다. 프리셋의 정식 호스트 정의는
 * config/environments.json, 전환 방법 전체는 docs/ENVIRONMENTS.md 참고.
 *
 * isNativeApp() 또는 dev 모드에서만 노출된다 — 배포 웹에서는 아무것도 렌더링하지 않는다.
 */

export function ServerAddressSettings() {
  const { isAuthenticated, logout } = useAuth();
  // 배포 웹에서는 노출하지 않는다(앱/개발 전용 기능).
  const visible = isNativeApp() || import.meta.env.DEV;
  const allowPrivateHttp = import.meta.env.DEV
    || import.meta.env.VITE_ALLOW_PRIVATE_HTTP === "true";

  const storedOverride = useMemo(() => apiBaseOverride(), []);
  const [appliedOverride, setAppliedOverride] = useState<string | null>(storedOverride);
  const [preset, setPreset] = useState<string>(() => initialServerPreset(storedOverride));
  const [url, setUrl] = useState<string>(storedOverride ?? "");
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pendingOverride, setPendingOverride] = useState<string | null | undefined>(undefined);
  const [saving, setSaving] = useState(false);

  if (!visible) return null;

  const currentBase = apiBase();
  const overrideActive = appliedOverride != null;

  const onPresetChange = (value: string) => {
    setPreset(value);
    setMessage(null);
    setError(null);
    setPendingOverride(undefined);
    const selected = SERVER_PRESETS.find((item) => item.value === value);
    if (selected?.url) setUrl(selected.url);
    if (selected?.url === null) setUrl("");
  };

  const applyOverride = (nextOverride: string | null) => {
    setApiBaseOverride(nextOverride);
    setAppliedOverride(nextOverride);
    setPendingOverride(undefined);
    setMessage("서버 주소를 저장했습니다. 새로고침한 뒤 새 서버에 로그인해 주세요.");
    setError(null);
  };

  const requestOverride = (nextOverride: string | null) => {
    if (!serverOverrideChanged(appliedOverride, nextOverride)) {
      setMessage("이미 선택한 서버 주소를 사용 중입니다.");
      setError(null);
      setPendingOverride(undefined);
      return;
    }
    if (isAuthenticated) {
      setPendingOverride(nextOverride);
      setMessage(null);
      setError(null);
      return;
    }
    applyOverride(nextOverride);
  };

  const save = () => {
    const resolved = resolveServerOverride(preset, url, allowPrivateHttp);
    if (resolved.error) {
      setError(resolved.error);
      setMessage(null);
      return;
    }
    requestOverride(resolved.override);
  };

  const reset = () => {
    setPreset("default");
    setUrl("");
    requestOverride(null);
  };

  const confirmChange = async () => {
    if (pendingOverride === undefined) return;
    const nextOverride = pendingOverride;
    setSaving(true);
    try {
      // 현재 서버에 로그아웃을 통지하고 로컬 토큰을 지운 뒤에만 새 서버 주소를 기록한다.
      await logout();
      applyOverride(nextOverride);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card className="border border-border bg-card">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <Server className="size-4 text-muted-foreground" />
          서버 주소 (앱/개발 전용)
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
          <span>현재 API 베이스:</span>
          <code className="break-all rounded bg-muted px-2 py-0.5 text-xs text-foreground">{currentBase}</code>
          {overrideActive
            ? <Badge className="bg-amber-100 text-amber-700">오버라이드 사용 중</Badge>
            : <Badge className="bg-muted text-muted-foreground">빌드 설정</Badge>}
        </div>

        <div className="grid gap-3 md:grid-cols-2">
          <Select value={preset} onValueChange={onPresetChange}>
            <SelectTrigger>
              <SelectValue placeholder="환경 프리셋 선택" />
            </SelectTrigger>
            <SelectContent>
              {SERVER_PRESETS.map((item) => (
                <SelectItem key={item.value} value={item.value}>
                  {item.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Input
            value={url}
            onChange={(event) => {
              setUrl(event.target.value);
              setPreset("custom");
              setMessage(null);
              setError(null);
              setPendingOverride(undefined);
            }}
            placeholder="예: http://192.168.0.10:8080/api"
            disabled={preset === "default"}
          />
        </div>

        <div className="rounded-lg border border-border bg-muted p-3 text-xs leading-5 text-muted-foreground">
          APK 재빌드 없이 백엔드 환경(로컬/Tailscale/AWS)을 전환합니다. 운영 빌드는 HTTPS 또는 기기 자체
          loopback만 허용하고, 사설망 HTTP는 명시적인 개발 빌드에서만 허용합니다. 절대 URL 은 백엔드 CORS
          허용이 필요하며, 환경별 정식 주소는 <code>config/environments.json</code> · <code>docs/ENVIRONMENTS.md</code> 기준입니다.
        </div>

        {pendingOverride !== undefined && (
          <div role="alert" className="space-y-3 rounded-lg border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900 dark:border-amber-500/30 dark:bg-amber-500/10 dark:text-amber-200">
            <div className="flex items-start gap-2">
              <AlertTriangle className="mt-0.5 size-4 shrink-0" />
              <div>
                <div className="font-semibold">서버를 바꾸면 현재 계정에서 로그아웃됩니다.</div>
                <div className="mt-1 text-xs leading-5 opacity-80">
                  다른 서버로 인증 토큰이 전송되지 않도록 현재 서버에서 먼저 로그아웃한 뒤 주소를 변경합니다.
                  변경 대상: <code>{pendingOverride ?? "빌드 기본값"}</code>
                </div>
              </div>
            </div>
            <div className="flex flex-wrap gap-2">
              <Button onClick={() => void confirmChange()} disabled={saving} className="bg-amber-600 text-white hover:bg-amber-700">
                {saving ? "로그아웃 중…" : "로그아웃 후 서버 변경"}
              </Button>
              <Button variant="outline" disabled={saving} onClick={() => setPendingOverride(undefined)}>취소</Button>
            </div>
          </div>
        )}

        {message && (
          <div aria-live="polite" className="rounded-lg border border-green-200 bg-green-50 px-3 py-2 text-sm text-green-700 dark:border-green-500/30 dark:bg-green-500/10 dark:text-green-300">{message}</div>
        )}
        {error && <div role="alert" className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700 dark:border-red-500/30 dark:bg-red-500/10 dark:text-red-300">{error}</div>}

        <div className="flex flex-wrap gap-2">
          <Button onClick={save} disabled={saving} className="bg-blue-600 text-white hover:bg-blue-700">
            <Save className="size-4" />
            저장
          </Button>
          <Button variant="outline" onClick={reset} disabled={saving}>
            <RotateCcw className="size-4" />
            기본값으로
          </Button>
          {message && (
            <Button variant="outline" onClick={() => window.location.reload()}>
              지금 새로고침
            </Button>
          )}
        </div>
      </CardContent>
    </Card>
  );
}
