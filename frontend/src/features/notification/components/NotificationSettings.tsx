import { Fragment, useEffect, useState } from "react";
import { Bell, BellRing, Loader2, Save, Smartphone } from "lucide-react";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Checkbox } from "@/app/components/ui/checkbox";
import {
  getNotificationPreferences, updateNotificationPreferences,
  type NotificationPreference,
} from "../api/notificationApi";
import {
  DEFAULT_NOTIFICATION_CHANNELS,
  DEFAULT_NOTIFICATION_SENDERS,
  NOTIFICATION_CHANNELS,
  NOTIFICATION_RULE_GROUPS,
  NOTIFICATION_SENDERS,
  RELATION_AWARE_TYPES,
  normalizeNotificationRules,
  type NotificationChannelKey,
  type NotificationRulePreference,
} from "../types/preferences";
import type { NotificationType, SenderRelation } from "../types/notification";
import { disablePush, enablePush, isPushSupported, pushPermission } from "@/platform/push";

const CATEGORY_LABELS: Record<string, string> = {
  ai_analysis: "AI 분석",
  interview: "면접",
  correction: "첨삭",
  community: "커뮤니티",
  messenger: "메신저",
  recommendation: "추천 공고",
  billing: "결제·크레딧",
  notice: "공지·문의",
  marketing: "광고·혜택",
};
const CATEGORY_ORDER = [
  "ai_analysis",
  "interview",
  "correction",
  "community",
  "messenger",
  "recommendation",
  "billing",
  "notice",
  "marketing",
];

export function NotificationSettings() {
  const [pref, setPref] = useState<NotificationPreference | null>(null);
  const [categories, setCategories] = useState<Record<string, boolean>>({});
  const [rules, setRules] = useState<Record<string, NotificationRulePreference>>({});
  const [keywords, setKeywords] = useState<string[]>([]);
  const [keywordInput, setKeywordInput] = useState("");
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
    setRules(normalizeNotificationRules(p.rules));
    setKeywords(p.keywords ?? []);
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
        toast(result === "subscribed" ? "이 기기로 푸시 알림을 받습니다." : "알림 권한을 허용했습니다.", "ok");
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

  const ensureRule = (type: NotificationType): NotificationRulePreference => (
    rules[type] ?? { enabled: true, channels: { ...DEFAULT_NOTIFICATION_CHANNELS } }
  );

  const toggleRule = (type: NotificationType, next: boolean) => {
    setRules((current) => {
      const rule = current[type] ?? { enabled: true, channels: { ...DEFAULT_NOTIFICATION_CHANNELS } };
      return { ...current, [type]: { ...rule, enabled: next } };
    });
  };

  const toggleChannel = (type: NotificationType, channel: NotificationChannelKey, next: boolean) => {
    setRules((current) => {
      const rule = current[type] ?? { enabled: true, channels: { ...DEFAULT_NOTIFICATION_CHANNELS } };
      return {
        ...current,
        [type]: {
          ...rule,
          channels: { ...DEFAULT_NOTIFICATION_CHANNELS, ...rule.channels, [channel]: next },
        },
      };
    });
  };

  const toggleSender = (type: NotificationType, sender: SenderRelation, next: boolean) => {
    setRules((current) => {
      const rule = current[type] ?? { enabled: true, channels: { ...DEFAULT_NOTIFICATION_CHANNELS } };
      return {
        ...current,
        [type]: {
          ...rule,
          senders: { ...DEFAULT_NOTIFICATION_SENDERS, ...rule.senders, [sender]: next },
        },
      };
    });
  };

  const addKeyword = () => {
    const value = keywordInput.trim();
    if (!value) return;
    if (value.length > 30) { toast("키워드는 30자 이하로 입력해 주세요.", "err"); return; }
    if (keywords.includes(value)) { setKeywordInput(""); return; }
    if (keywords.length >= 20) { toast("키워드는 최대 20개까지 등록할 수 있습니다.", "err"); return; }
    setKeywords((k) => [...k, value]);
    setKeywordInput("");
  };

  const save = async () => {
    setSaving(true);
    try {
      apply(await updateNotificationPreferences({
        categories,
        rules,
        keywords,
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
          <Loader2 className="size-4 animate-spin" /> 알림 설정을 불러오는 중...
        </CardContent>
      </Card>
    );
  }

  return (
    <div className="space-y-4">
      {flash && (
        <div className={`rounded-lg px-4 py-2.5 text-sm border border-border ${flash.tone === "ok" ? "bg-[var(--success-50)] text-[var(--success)]" : "bg-[var(--danger-50)] text-[var(--destructive)]"}`}>
          {flash.msg}
        </div>
      )}

      <Card className="border border-border bg-card">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <Smartphone className="size-4 text-blue-600" />
            푸시 알림
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <label className="flex items-center justify-between rounded-lg border border-border p-4">
            <div className="min-w-0">
              <div className="flex items-center gap-2 text-sm font-semibold text-foreground">
                <BellRing className="size-4 text-blue-600" /> 이 기기에서 푸시 받기
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

      <Card className="border border-border bg-card">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <Bell className="size-4 text-amber-600" />
            카테고리 수신
          </CardTitle>
        </CardHeader>
        <CardContent className="grid gap-3 md:grid-cols-3">
          {CATEGORY_ORDER.map((cat) => (
            <label key={cat} className="flex items-center justify-between rounded-lg border border-border bg-muted p-4">
              <span className="text-sm font-semibold text-foreground">{CATEGORY_LABELS[cat] ?? cat}</span>
              <Checkbox
                checked={categories[cat] ?? true}
                onCheckedChange={(v) => setCategories((c) => ({ ...c, [cat]: v === true }))}
              />
            </label>
          ))}
        </CardContent>
      </Card>

      <Card className="border border-border bg-card">
        <CardHeader>
          <CardTitle className="text-base">세부 알림 채널</CardTitle>
        </CardHeader>
        <CardContent className="space-y-5">
          {NOTIFICATION_RULE_GROUPS.map((group) => (
            <section key={group.key} className="space-y-2">
              <div className="text-sm font-semibold text-foreground">{group.label}</div>
              <div className="overflow-x-auto rounded-lg border border-border">
                <table className="w-full min-w-[900px] border-collapse text-sm">
                  <thead className="bg-muted text-xs text-muted-foreground">
                    <tr>
                      <th className="w-48 px-3 py-2 text-left font-semibold">알림</th>
                      <th className="w-16 px-2 py-2 text-center font-semibold">수신</th>
                      {NOTIFICATION_CHANNELS.map((channel) => (
                        <th key={channel.key} className="px-2 py-2 text-center font-semibold">{channel.label}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {group.types.map((item) => {
                      const rule = ensureRule(item.type);
                      const relationAware = RELATION_AWARE_TYPES.includes(item.type);
                      return (
                        <Fragment key={item.type}>
                          <tr className="border-t border-border">
                            <td className="px-3 py-2 font-medium text-foreground">{item.label}</td>
                            <td className="px-2 py-2 text-center">
                              <Checkbox
                                aria-label={`${item.label} 수신`}
                                checked={rule.enabled}
                                onCheckedChange={(v) => toggleRule(item.type, v === true)}
                              />
                            </td>
                            {NOTIFICATION_CHANNELS.map((channel) => (
                              <td key={channel.key} className="px-2 py-2 text-center">
                                <Checkbox
                                  aria-label={`${item.label} ${channel.label}`}
                                  checked={rule.channels[channel.key]}
                                  disabled={!rule.enabled}
                                  onCheckedChange={(v) => toggleChannel(item.type, channel.key, v === true)}
                                />
                              </td>
                            ))}
                          </tr>
                          {relationAware && (
                            <tr className="border-t border-dashed border-border bg-muted/40">
                              <td className="px-3 py-1.5 pl-6 text-xs text-muted-foreground">보낸 사람별 수신</td>
                              <td colSpan={NOTIFICATION_CHANNELS.length + 1} className="px-2 py-1.5">
                                <div className="flex flex-wrap items-center gap-4">
                                  {NOTIFICATION_SENDERS.map((sender) => (
                                    <label key={sender.key} className="flex items-center gap-1.5 text-xs text-foreground">
                                      <Checkbox
                                        aria-label={`${item.label} - ${sender.label}`}
                                        checked={rule.senders?.[sender.key] !== false}
                                        disabled={!rule.enabled}
                                        onCheckedChange={(v) => toggleSender(item.type, sender.key, v === true)}
                                      />
                                      {sender.label}
                                    </label>
                                  ))}
                                </div>
                              </td>
                            </tr>
                          )}
                        </Fragment>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </section>
          ))}
        </CardContent>
      </Card>

      <Card className="border border-border bg-card">
        <CardHeader>
          <CardTitle className="text-base">언급 키워드</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <p className="text-xs text-muted-foreground">
            알림을 해제한 채팅방이라도 아래 키워드나 내 이름이 언급되면 &lsquo;키워드·이름 언급&rsquo; 알림을 받습니다. (최대 20개)
          </p>
          <div className="flex flex-wrap items-center gap-2">
            {keywords.map((keyword) => (
              <span key={keyword} className="inline-flex items-center gap-1 rounded-full border border-border bg-muted px-3 py-1 text-xs text-foreground">
                {keyword}
                <button
                  type="button"
                  aria-label={`${keyword} 삭제`}
                  className="text-muted-foreground hover:text-foreground"
                  onClick={() => setKeywords((k) => k.filter((v) => v !== keyword))}
                >
                  ×
                </button>
              </span>
            ))}
            {keywords.length === 0 && (
              <span className="text-xs text-muted-foreground">등록된 키워드가 없습니다.</span>
            )}
          </div>
          <div className="flex items-center gap-2">
            <input
              type="text"
              value={keywordInput}
              maxLength={30}
              placeholder="예: 백엔드, 리액트, 내 닉네임"
              onChange={(e) => setKeywordInput(e.target.value)}
              onKeyDown={(e) => { if (e.key === "Enter") { e.preventDefault(); addKeyword(); } }}
              className="h-10 w-64 rounded-lg border border-border px-3 text-sm"
            />
            <Button type="button" variant="outline" onClick={addKeyword}>추가</Button>
          </div>
        </CardContent>
      </Card>

      <Card className="border border-border bg-card">
        <CardHeader>
          <CardTitle className="text-base">방해 금지 시간</CardTitle>
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
