import { useEffect, useState } from "react";
import { Bell, BellRing, Loader2, Save, Smartphone } from "lucide-react";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Checkbox } from "@/app/components/ui/checkbox";
import {
  getNotificationPreferences, updateNotificationPreferences,
  type NotificationPreference,
} from "../api/notificationApi";
import { disablePush, enablePush, isPushSupported, pushPermission } from "@/platform/push";

const CATEGORY_LABELS: Record<string, string> = {
  ai_analysis: "AI 분석 (공고·기업·적합도·장기분석)",
  interview: "면접 (질문·리포트)",
  correction: "첨삭",
  community: "커뮤니티 (댓글·좋아요·검열)",
  billing: "결제·크레딧",
  notice: "공지·문의 답변",
};
const CATEGORY_ORDER = ["ai_analysis", "interview", "correction", "community", "billing", "notice"];

export function NotificationSettings() {
  const [pref, setPref] = useState<NotificationPreference | null>(null);
  const [categories, setCategories] = useState<Record<string, boolean>>({});
  const [quietStart, setQuietStart] = useState("");
  const [quietEnd, setQuietEnd] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [pushBusy, setPushBusy] = useState(false);
  const [flash, setFlash] = useState<{ msg: string; tone: "ok" | "err" } | null>(null);

  const toast = (msg: string, tone: "ok" | "err") => {
    setFlash({ msg, tone });
    setTimeout(() => setFlash(null), 3000);
  };

  const apply = (p: NotificationPreference) => {
    setPref(p);
    setCategories(p.categories);
    setQuietStart(p.quietHoursStart ?? "");
    setQuietEnd(p.quietHoursEnd ?? "");
  };

  useEffect(() => {
    getNotificationPreferences()
      .then(apply)
      .catch(() => toast("알림 설정을 불러오지 못했습니다.", "err"))
      .finally(() => setLoading(false));
  }, []);

  const togglePush = async (next: boolean) => {
    setPushBusy(true);
    try {
      if (next) {
        const result = await enablePush();
        if (result === "denied") { toast("브라우저/기기에서 알림 권한이 거부되어 있습니다.", "err"); return; }
        if (result === "unsupported") { toast("이 환경은 푸시를 지원하지 않습니다.", "err"); return; }
        apply(await updateNotificationPreferences({ pushEnabled: true }));
        toast(result === "subscribed" ? "이 기기로 푸시 알림을 받습니다." : "알림 권한을 허용했습니다. (서버 발송키 설정 시 단말 푸시가 활성화됩니다)", "ok");
      } else {
        await disablePush();
        apply(await updateNotificationPreferences({ pushEnabled: false }));
        toast("푸시 알림을 껐습니다.", "ok");
      }
    } catch {
      toast("푸시 설정 변경에 실패했습니다.", "err");
    } finally {
      setPushBusy(false);
    }
  };

  const save = async () => {
    setSaving(true);
    try {
      apply(await updateNotificationPreferences({
        categories,
        quietHoursStart: quietStart || null,
        quietHoursEnd: quietEnd || null,
      }));
      toast("알림 설정을 저장했습니다.", "ok");
    } catch {
      toast("저장에 실패했습니다.", "err");
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <Card className="border border-border bg-card">
        <CardContent className="flex items-center gap-2 p-6 text-sm text-muted-foreground">
          <Loader2 className="size-4 animate-spin" /> 알림 설정을 불러오는 중…
        </CardContent>
      </Card>
    );
  }

  return (
    <div className="space-y-4">
      {flash && (
        <div className={`rounded-lg px-4 py-2.5 text-sm ${flash.tone === "ok" ? "border border-green-200 bg-green-50 text-green-700" : "border border-red-200 bg-red-50 text-red-700"}`}>
          {flash.msg}
        </div>
      )}

      {/* 푸시(폰 연동) */}
      <Card className="border border-border bg-card">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <Smartphone className="size-4 text-blue-600" />
            푸시 알림 (이 기기)
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <label className="flex items-center justify-between rounded-xl border border-border p-4">
            <div className="min-w-0">
              <div className="flex items-center gap-2 text-sm font-semibold text-foreground">
                <BellRing className="size-4 text-blue-600" /> 푸시 알림 받기
              </div>
              <div className="mt-0.5 text-xs text-muted-foreground">
                {!isPushSupported() ? "이 환경은 푸시를 지원하지 않습니다." :
                  pref?.pushDeviceRegistered ? "이 기기가 푸시 수신 기기로 등록되어 있습니다." :
                  `알림 권한: ${pushPermission()}`}
              </div>
            </div>
            <Checkbox
              checked={pref?.pushEnabled ?? false}
              disabled={pushBusy || !isPushSupported()}
              onCheckedChange={(v) => void togglePush(v === true)}
            />
          </label>
        </CardContent>
      </Card>

      {/* 종류별 수신 */}
      <Card className="border border-border bg-card">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <Bell className="size-4 text-amber-600" />
            알림 종류별 수신
          </CardTitle>
        </CardHeader>
        <CardContent className="grid gap-3 md:grid-cols-2">
          {CATEGORY_ORDER.map((cat) => (
            <label key={cat} className="flex items-center justify-between rounded-xl border border-border bg-muted p-4">
              <span className="text-sm font-semibold text-foreground">{CATEGORY_LABELS[cat] ?? cat}</span>
              <Checkbox
                checked={categories[cat] ?? true}
                onCheckedChange={(v) => setCategories((c) => ({ ...c, [cat]: v === true }))}
              />
            </label>
          ))}
        </CardContent>
      </Card>

      {/* 방해 금지 시간 */}
      <Card className="border border-border bg-card">
        <CardHeader>
          <CardTitle className="text-base">방해 금지 시간(선택)</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-wrap items-end gap-3">
          <div>
            <div className="mb-1 text-xs font-semibold text-muted-foreground">시작</div>
            <input type="time" value={quietStart} onChange={(e) => setQuietStart(e.target.value)}
              className="h-10 rounded-lg border border-border px-3 text-sm" />
          </div>
          <div>
            <div className="mb-1 text-xs font-semibold text-muted-foreground">종료</div>
            <input type="time" value={quietEnd} onChange={(e) => setQuietEnd(e.target.value)}
              className="h-10 rounded-lg border border-border px-3 text-sm" />
          </div>
          <p className="text-xs text-muted-foreground">설정한 시간대에는 푸시 발송을 자제합니다.</p>
        </CardContent>
      </Card>

      <Button onClick={() => void save()} disabled={saving} className="bg-blue-600 text-white hover:bg-blue-700">
        {saving ? <Loader2 className="size-4 animate-spin" /> : <Save className="size-4" />}
        설정 저장
      </Button>
    </div>
  );
}
