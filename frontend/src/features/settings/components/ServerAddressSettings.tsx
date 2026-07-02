import { useMemo, useState } from "react";
import { RotateCcw, Save, Server } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Input } from "@/app/components/ui/input";
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from "@/app/components/ui/select";
import { apiBase, apiBaseOverride, setApiBaseOverride } from "@/app/lib/apiBase";
import { isNativeApp } from "@/platform/capacitor";

/**
 * 서버 주소(앱/개발 전용) 카드.
 *
 * 네이티브 앱(APK)은 재빌드 없이, 개발 웹은 재기동 없이 백엔드 환경(로컬/Tailscale/AWS/도메인)을
 * 전환할 수 있게 apiBase() 런타임 오버라이드를 기록한다. 프리셋의 정식 호스트 정의는
 * config/environments.json, 전환 방법 전체는 docs/ENVIRONMENTS.md 참고.
 *
 * isNativeApp() 또는 dev 모드에서만 노출된다 — 배포 웹에서는 아무것도 렌더링하지 않는다.
 */

interface ServerPreset {
  value: string;
  label: string;
  /** null = 오버라이드 해제(빌드 설정 사용), undefined = 직접 입력(입력값 유지). */
  url: string | null | undefined;
}

const PRESETS: ServerPreset[] = [
  { value: "default", label: "기본값 (빌드 설정 사용)", url: null },
  { value: "local", label: "로컬 백엔드", url: "http://localhost:8080/api" },
  { value: "tailscale", label: "Tailscale (개발 PC 원격)", url: "https://careertuner-dev.example.invalid/api" },
  { value: "aws", label: "AWS (주소 미확정 — 플레이스홀더)", url: "http://CHANGEME-aws-api:8080/api" },
  { value: "domain", label: "도메인 (미확정 — 플레이스홀더)", url: "https://api.careertuner.kr/api" },
  { value: "custom", label: "직접 입력", url: undefined },
];

/** 저장된 오버라이드로부터 초기 프리셋을 판정한다(일치하는 프리셋 없으면 직접 입력). */
function initialPreset(override: string | null): string {
  if (!override) return "default";
  const matched = PRESETS.find((preset) => preset.url === override);
  return matched ? matched.value : "custom";
}

export function ServerAddressSettings() {
  // 배포 웹에서는 노출하지 않는다(앱/개발 전용 기능).
  const visible = isNativeApp() || import.meta.env.DEV;

  const storedOverride = useMemo(() => apiBaseOverride(), []);
  const [preset, setPreset] = useState<string>(() => initialPreset(storedOverride));
  const [url, setUrl] = useState<string>(storedOverride ?? "");
  const [message, setMessage] = useState<string | null>(null);

  if (!visible) return null;

  const currentBase = apiBase();
  const overrideActive = apiBaseOverride() != null;

  const onPresetChange = (value: string) => {
    setPreset(value);
    setMessage(null);
    const selected = PRESETS.find((item) => item.value === value);
    if (selected?.url) setUrl(selected.url);
    if (selected?.url === null) setUrl("");
  };

  const save = () => {
    if (preset === "default" || !url.trim()) {
      setApiBaseOverride(null);
    } else {
      setApiBaseOverride(url);
    }
    setMessage("저장했습니다 — 새로고침하면 모든 요청에 적용됩니다.");
  };

  const reset = () => {
    setApiBaseOverride(null);
    setPreset("default");
    setUrl("");
    setMessage("기본값(빌드 설정)으로 되돌렸습니다 — 새로고침하면 적용됩니다.");
  };

  return (
    <Card className="border border-slate-200 bg-card">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <Server className="size-4 text-slate-600" />
          서버 주소 (앱/개발 전용)
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="flex flex-wrap items-center gap-2 text-sm text-slate-600">
          <span>현재 API 베이스:</span>
          <code className="rounded bg-slate-100 px-2 py-0.5 text-xs text-slate-800">{currentBase}</code>
          {overrideActive
            ? <Badge className="bg-amber-100 text-amber-700">오버라이드 사용 중</Badge>
            : <Badge className="bg-slate-200 text-slate-700">빌드 설정</Badge>}
        </div>

        <div className="grid gap-3 md:grid-cols-2">
          <Select value={preset} onValueChange={onPresetChange}>
            <SelectTrigger>
              <SelectValue placeholder="환경 프리셋 선택" />
            </SelectTrigger>
            <SelectContent>
              {PRESETS.map((item) => (
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
            }}
            placeholder="예: http://192.168.0.10:8080/api"
            disabled={preset === "default"}
          />
        </div>

        <div className="rounded-lg border border-slate-200 bg-slate-50 p-3 text-xs leading-5 text-slate-500">
          APK 재빌드 없이 백엔드 환경(로컬/Tailscale/AWS/도메인)을 전환합니다. 절대 URL 은 백엔드 CORS
          허용이 필요하며, 환경별 정식 주소는 <code>config/environments.json</code> · <code>docs/ENVIRONMENTS.md</code> 기준입니다.
        </div>

        {message && (
          <div className="rounded-lg border border-green-200 bg-green-50 px-3 py-2 text-sm text-green-700">{message}</div>
        )}

        <div className="flex flex-wrap gap-2">
          <Button onClick={save} className="bg-blue-600 text-white hover:bg-blue-700">
            <Save className="size-4" />
            저장
          </Button>
          <Button variant="outline" onClick={reset}>
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
