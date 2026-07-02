import type { NotificationType } from "./notification";

export type NotificationChannelKey =
  | "webToast"
  | "webPush"
  | "mobilePush"
  | "mobileSound"
  | "mobileVibration"
  | "desktopToast"
  | "desktopTaskbar";

export interface NotificationChannelPreference {
  webToast: boolean;
  webPush: boolean;
  mobilePush: boolean;
  mobileSound: boolean;
  mobileVibration: boolean;
  desktopToast: boolean;
  desktopTaskbar: boolean;
}

export interface NotificationRulePreference {
  enabled: boolean;
  channels: NotificationChannelPreference;
}

export const DEFAULT_NOTIFICATION_CHANNELS: NotificationChannelPreference = {
  webToast: true,
  webPush: true,
  mobilePush: true,
  mobileSound: true,
  mobileVibration: true,
  desktopToast: true,
  desktopTaskbar: true,
};

export const NOTIFICATION_CHANNELS: Array<{ key: NotificationChannelKey; label: string }> = [
  { key: "webToast", label: "웹 팝업" },
  { key: "webPush", label: "웹 푸시" },
  { key: "mobilePush", label: "모바일 푸시" },
  { key: "mobileSound", label: "소리" },
  { key: "mobileVibration", label: "진동" },
  { key: "desktopToast", label: "윈도우 알림" },
  { key: "desktopTaskbar", label: "작업표시줄" },
];

export const NOTIFICATION_RULE_GROUPS: Array<{
  key: string;
  label: string;
  types: Array<{ type: NotificationType; label: string }>;
}> = [
  {
    key: "ai_analysis",
    label: "AI 분석",
    types: [
      { type: "JOB_ANALYSIS_COMPLETE", label: "공고 분석 완료" },
      { type: "COMPANY_ANALYSIS_COMPLETE", label: "기업 분석 완료" },
      { type: "FIT_ANALYSIS_COMPLETE", label: "적합도 분석 완료" },
      { type: "CAREER_TREND_COMPLETE", label: "커리어 트렌드 완료" },
      { type: "JOB_POSTING_EXTRACTION_SUCCEEDED", label: "공고문 추출 완료" },
      { type: "JOB_POSTING_EXTRACTION_REVIEW_REQUIRED", label: "공고문 검수 필요" },
      { type: "JOB_POSTING_EXTRACTION_FAILED", label: "공고문 추출 실패" },
    ],
  },
  {
    key: "interview",
    label: "면접",
    types: [
      { type: "QUESTIONS_GENERATED", label: "예상 질문 생성" },
      { type: "INTERVIEW_REPORT_READY", label: "면접 리포트 완료" },
    ],
  },
  {
    key: "correction",
    label: "첨삭",
    types: [{ type: "CORRECTION_COMPLETE", label: "첨삭 완료" }],
  },
  {
    key: "community",
    label: "커뮤니티",
    types: [
      { type: "COMMENT", label: "댓글" },
      { type: "COMMENT_REPLY", label: "답글" },
      { type: "LIKE", label: "좋아요" },
      { type: "POST_SUMMARY_READY", label: "게시글 요약 완료" },
    ],
  },
  {
    key: "messenger",
    label: "메신저",
    types: [
      { type: "FRIEND_REQUEST", label: "친구 요청" },
      { type: "FRIEND_ACCEPTED", label: "친구 수락" },
      { type: "ROOM_INVITE", label: "채팅방 초대" },
      { type: "ROOM_MESSAGE", label: "새 채팅/쪽지" },
      { type: "ROOM_MENTION", label: "키워드·이름 언급" },
    ],
  },
  {
    key: "recommendation",
    label: "추천",
    types: [{ type: "RECOMMENDED_JOB", label: "추천 공고" }],
  },
  {
    key: "billing",
    label: "결제",
    types: [
      { type: "CREDIT_LOW", label: "크레딧 부족" },
      { type: "PAYMENT_COMPLETE", label: "결제 완료" },
      { type: "PAYMENT_SCHEDULED", label: "결제 예정" },
      { type: "SUBSCRIPTION_CANCELED", label: "구독 해지" },
      { type: "CREDIT_RECHARGED", label: "크레딧 충전" },
    ],
  },
  {
    key: "notice",
    label: "공지/문의",
    types: [
      { type: "NOTICE", label: "공지" },
      { type: "TICKET_ANSWERED", label: "문의 답변/처리" },
      { type: "ACCOUNT_BLOCKED", label: "계정 제한" },
    ],
  },
  {
    key: "marketing",
    label: "광고/혜택",
    types: [{ type: "MARKETING_AD", label: "유용한 광고/혜택" }],
  },
];

export function notificationRuleTypes(): NotificationType[] {
  return NOTIFICATION_RULE_GROUPS.flatMap((group) => group.types.map((item) => item.type));
}

export function defaultNotificationRules(): Record<string, NotificationRulePreference> {
  return Object.fromEntries(
    notificationRuleTypes().map((type) => [
      type,
      { enabled: true, channels: { ...DEFAULT_NOTIFICATION_CHANNELS } },
    ]),
  );
}

export function normalizeNotificationRules(
  rules?: Record<string, Partial<NotificationRulePreference> | undefined> | null,
): Record<string, NotificationRulePreference> {
  const defaults = defaultNotificationRules();
  for (const type of Object.keys(defaults)) {
    const stored = rules?.[type];
    if (!stored) continue;
    defaults[type] = {
      enabled: stored.enabled ?? true,
      channels: {
        ...DEFAULT_NOTIFICATION_CHANNELS,
        ...(stored.channels ?? {}),
      },
    };
  }
  return defaults;
}

export function isNotificationChannelEnabled(
  pref: { rules?: Record<string, Partial<NotificationRulePreference> | undefined> } | null | undefined,
  type: string,
  channel: NotificationChannelKey,
): boolean {
  const rule = pref?.rules?.[type];
  if (!rule) return true;
  if (rule.enabled === false) return false;
  return rule.channels?.[channel] !== false;
}
