import type { NotificationType, SenderRelation } from "./notification";

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

/** 발신자 관계별 수신 여부 — 댓글·답글·쪽지·채팅 알림에만 노출된다. */
export type NotificationSenderPreference = Record<SenderRelation, boolean>;

export interface NotificationRulePreference {
  enabled: boolean;
  channels: NotificationChannelPreference;
  senders?: NotificationSenderPreference;
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

export const DEFAULT_NOTIFICATION_SENDERS: NotificationSenderPreference = {
  stranger: true,
  friend: true,
  company: true,
  operator: true,
};

export const NOTIFICATION_SENDERS: Array<{ key: SenderRelation; label: string }> = [
  { key: "stranger", label: "모르는 사람" },
  { key: "friend", label: "친구" },
  { key: "company", label: "기업 계정" },
  { key: "operator", label: "운영자" },
];

/** 발신자 관계별 설정을 지원하는 알림 type (백엔드 NotificationCategories.RELATION_AWARE_TYPES 와 동일 기준). */
export const RELATION_AWARE_TYPES: NotificationType[] = [
  "COMMENT",
  "COMMENT_REPLY",
  "ROOM_MESSAGE",
  "NOTE_MESSAGE",
  "ROOM_MENTION",
  "ROOM_INVITE",
  // 커뮤니티 리액션 — 관계별(모르는 사람/친구/기업) 수신 필터 지원
  "LIKE", "LIKE_ANON",
  "POST_DISLIKE", "POST_DISLIKE_ANON",
  "POST_RECOMMEND", "POST_RECOMMEND_ANON",
  "POST_DISRECOMMEND", "POST_DISRECOMMEND_ANON",
  "COMMENT_LIKE", "COMMENT_LIKE_ANON",
  "COMMENT_DISLIKE", "COMMENT_DISLIKE_ANON",
  "COMMENT_RECOMMEND", "COMMENT_RECOMMEND_ANON",
  "COMMENT_DISRECOMMEND", "COMMENT_DISRECOMMEND_ANON",
  "POST_BOOKMARK", "POST_BOOKMARK_ANON",
  "POST_SCRAP", "POST_SCRAP_ANON",
];

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
      { type: "PROFILE_ANALYZED", label: "스펙 분석 완료" },
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
      { type: "INTERVIEW_DISPATCH", label: "세션 폰으로 이어하기" },
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
      { type: "POST_WATCH_COMMENT", label: "구독한 글 새 댓글" },
      { type: "COMMENT_WATCH_REPLY", label: "구독한 댓글 새 답글" },
      { type: "LIKE", label: "게시글 좋아요" },
      { type: "LIKE_ANON", label: "게시글 좋아요 (익명)" },
      { type: "POST_DISLIKE", label: "게시글 싫어요" },
      { type: "POST_DISLIKE_ANON", label: "게시글 싫어요 (익명)" },
      { type: "POST_RECOMMEND", label: "게시글 추천" },
      { type: "POST_RECOMMEND_ANON", label: "게시글 추천 (익명)" },
      { type: "POST_DISRECOMMEND", label: "게시글 비추천" },
      { type: "POST_DISRECOMMEND_ANON", label: "게시글 비추천 (익명)" },
      { type: "COMMENT_LIKE", label: "댓글 좋아요" },
      { type: "COMMENT_LIKE_ANON", label: "댓글 좋아요 (익명)" },
      { type: "COMMENT_DISLIKE", label: "댓글 싫어요" },
      { type: "COMMENT_DISLIKE_ANON", label: "댓글 싫어요 (익명)" },
      { type: "COMMENT_RECOMMEND", label: "댓글 추천" },
      { type: "COMMENT_RECOMMEND_ANON", label: "댓글 추천 (익명)" },
      { type: "COMMENT_DISRECOMMEND", label: "댓글 비추천" },
      { type: "COMMENT_DISRECOMMEND_ANON", label: "댓글 비추천 (익명)" },
      { type: "POST_BOOKMARK", label: "게시글 즐겨찾기" },
      { type: "POST_BOOKMARK_ANON", label: "게시글 즐겨찾기 (익명)" },
      { type: "POST_SCRAP", label: "게시글 스크랩" },
      { type: "POST_SCRAP_ANON", label: "게시글 스크랩 (익명)" },
      { type: "POST_SUMMARY_READY", label: "게시글 요약 완료" },
      { type: "POST_HIDDEN", label: "게시글 숨김(운영)" },
      { type: "POST_RESTORED", label: "게시글 복구(운영)" },
      { type: "POST_REMOVED", label: "게시글 삭제(운영)" },
      { type: "COMMENT_HIDDEN", label: "댓글 숨김(운영)" },
      { type: "COMMENT_RESTORED", label: "댓글 복구(운영)" },
      { type: "COMMENT_REMOVED", label: "댓글 삭제(운영)" },
    ],
  },
  {
    key: "messenger",
    label: "메신저",
    types: [
      { type: "FRIEND_REQUEST", label: "친구 요청" },
      { type: "FRIEND_ACCEPTED", label: "친구 수락" },
      { type: "ROOM_INVITE", label: "채팅방 초대" },
      { type: "ROOM_MESSAGE", label: "새 채팅" },
      { type: "NOTE_MESSAGE", label: "쪽지" },
      { type: "ROOM_MENTION", label: "키워드·이름 언급" },
    ],
  },
  {
    key: "recommendation",
    label: "추천",
    types: [
      { type: "RECOMMENDED_JOB", label: "추천 공고" },
      { type: "RECOMMENDED_POST", label: "추천 취업/면접 후기" },
    ],
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
      { type: "REFUND_RESULT", label: "환불 결과" },
    ],
  },
  {
    key: "notice",
    label: "공지/문의",
    types: [
      { type: "NOTICE", label: "공지" },
      { type: "TICKET_ANSWERED", label: "문의 답변/처리" },
      { type: "ACCOUNT_BLOCKED", label: "계정 제한" },
      { type: "COMPANY_APPLY_RESULT", label: "기업 계정 신청 결과" },
      { type: "JOB_POSTING_REVIEW_RESULT", label: "공고 검토 결과" },
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
      {
        enabled: true,
        channels: { ...DEFAULT_NOTIFICATION_CHANNELS },
        ...(RELATION_AWARE_TYPES.includes(type)
          ? { senders: { ...DEFAULT_NOTIFICATION_SENDERS } }
          : {}),
      },
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
      ...(RELATION_AWARE_TYPES.includes(type as NotificationType)
        ? { senders: { ...DEFAULT_NOTIFICATION_SENDERS, ...(stored.senders ?? {}) } }
        : {}),
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

/**
 * 알림 전역 끄기 여부. 벨 패널의 "알림 끄기"({@link NotificationPreference.pushEnabled})는
 * 라벨 그대로 <b>푸시뿐 아니라 화면 토스트도</b> 끈다.
 *
 * <p>설정을 아직 못 불러왔으면(null/undefined) 기존대로 알림을 띄운다 — 설정 로드 실패로
 * 알림이 조용히 사라지는 편보다 낫다(fail-open).
 */
export function areNotificationsMuted(
  pref: { pushEnabled?: boolean } | null | undefined,
): boolean {
  return pref?.pushEnabled === false;
}

/** "HH:mm" / "HH:mm:ss" → 자정 이후 분(minute). 형식이 어긋나면 null. */
function parseTimeToMinutes(value: string | null | undefined): number | null {
  if (!value) return null;
  const matched = /^(\d{1,2}):(\d{2})(?::\d{2})?$/.exec(value.trim());
  if (!matched) return null;
  const hour = Number(matched[1]);
  const minute = Number(matched[2]);
  if (hour > 23 || minute > 59) return null;
  return hour * 60 + minute;
}

/** 지금 시각을 KST 기준 자정 이후 분으로. KST 는 서머타임이 없어 UTC+9 고정 오프셋으로 충분하다. */
export function kstMinutesOf(now: Date): number {
  const kst = new Date(now.getTime() + 9 * 60 * 60 * 1000);
  return kst.getUTCHours() * 60 + kst.getUTCMinutes();
}

/**
 * 방해금지 구간 판정 — 백엔드 {@code PushDispatcher.isWithinQuietHours} 와 동일 규칙.
 * 시작 포함·끝 제외([start, end)), 자정을 넘기는 구간(start &gt; end)도 처리한다.
 * 미설정·형식 오류·시작==끝은 "방해금지 없음"(false)으로 본다.
 */
export function isWithinQuietHours(
  start: string | null | undefined,
  end: string | null | undefined,
  now: Date = new Date(),
): boolean {
  const s = parseTimeToMinutes(start);
  const e = parseTimeToMinutes(end);
  if (s === null || e === null || s === e) return false;
  const current = kstMinutesOf(now);
  if (s < e) return current >= s && current < e;
  return current >= s || current < e; // 자정 넘김: [s, 24:00) ∪ [00:00, e)
}

/** 카테고리 단위 수신 여부. 설정에 없는 카테고리(관리자 전용 등)는 통과시킨다. */
export function isNotificationCategoryEnabled(
  pref: { categories?: Record<string, boolean> } | null | undefined,
  category: string | null | undefined,
): boolean {
  if (!category) return true;
  return pref?.categories?.[category] !== false;
}

/**
 * 새로 도착한 미읽음 알림 중 실제로 토스트를 띄울 것만 고른다.
 *
 * <p>백엔드 {@code PushDispatcher} 가 푸시를 억제하는 신호를 화면 토스트에도 동일하게 적용한다:
 * 전역 끄기(pushEnabled) → 방해금지 시간대 → 카테고리 → 타입/채널 → 발신자 관계.
 *
 * <p>순수 함수 — 폴링 루프에서 분리해 단독 실행/검증이 가능하게 둔다.
 * urgent 판정은 아이콘 등 런타임 의존이 딸린 typeMeta 를 끌어오지 않도록 주입받는다.
 */
export function selectToastNotifications<
  T extends { type: string; category?: string | null; senderRelation?: string | null },
>(
  fresh: T[],
  pref: {
    pushEnabled?: boolean;
    categories?: Record<string, boolean>;
    quietHoursStart?: string | null;
    quietHoursEnd?: string | null;
    rules?: Record<string, Partial<NotificationRulePreference> | undefined>;
  } | null | undefined,
  isUrgent: (type: T["type"]) => boolean,
  now: Date = new Date(),
): T[] {
  if (areNotificationsMuted(pref)) {
    return [];
  }
  if (isWithinQuietHours(pref?.quietHoursStart, pref?.quietHoursEnd, now)) {
    return [];
  }
  return fresh
    .filter((n) => isUrgent(n.type))
    .filter((n) => isNotificationCategoryEnabled(pref, n.category))
    .filter((n) => isNotificationChannelEnabled(pref, n.type, "webToast"))
    .filter((n) => isNotificationSenderEnabled(pref, n.type, n.senderRelation));
}

/** 발신자 관계 기준 수신 여부 — 관계 미상(undefined)은 통과시킨다. */
export function isNotificationSenderEnabled(
  pref: { rules?: Record<string, Partial<NotificationRulePreference> | undefined> } | null | undefined,
  type: string,
  senderRelation?: string | null,
): boolean {
  const rule = pref?.rules?.[type];
  if (!rule) return true;
  if (rule.enabled === false) return false;
  if (!senderRelation) return true;
  return rule.senders?.[senderRelation as SenderRelation] !== false;
}
