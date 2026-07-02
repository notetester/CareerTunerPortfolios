// 데모/목: 알림 도메인 (목록/미읽음 배지/읽음 처리/설정).
// 클라이언트(notificationApi.ts)는 백엔드 원본(BackendNotification[])을 받아 toNotification 으로 매핑하므로,
// 여기 핸들러는 매핑 전 "백엔드 응답 shape" 를 그대로 반환한다.
//  - GET  /notifications?page&size      -> NotificationPageResponse
//  - GET  /notifications/unread-count   -> number
//  - PATCH /notifications/:id/read      -> void(null)
//  - POST /notifications/read-all       -> void(null)
//  - GET  /notifications/preferences    -> NotificationPreference
//  - PUT  /notifications/preferences    -> NotificationPreference(echo)
// 데모 사용자 김데모의 활동(적합도 분석·모의면접·커뮤니티·공지)과 일관된 알림을 제공한다.
import type { NotificationPreference } from "@/features/notification/api/notificationApi";
import { defaultNotificationRules } from "@/features/notification/types/preferences";
import type { MockRoute, MockContext } from "../registry";
import { iso, pageOf } from "../registry";

// notificationApi.ts 내부(비-export) BackendNotification 와 동일 shape 의 로컬 미러.
interface BackendNotification {
  id: number;
  type: string;
  targetType?: string;
  targetId?: number;
  title: string;
  message?: string;
  link?: string;
  read: boolean;
  createdAt: string;
  actor?: {
    id: number;
    name: string;
    avatarUrl?: string;
  };
}

// notificationApi.ts 내부(비-export) NotificationPageResponse 와 동일 shape 의 로컬 미러.
interface NotificationPageResponse {
  notifications: BackendNotification[];
  total: number;
  page: number;
  size: number;
  hasNext: boolean;
}

// 데모 알림 5건. 일부 미읽음(read:false). 지원 건 id(101 카카오·103 토스)와 교차 일관.
const demoNotifications: BackendNotification[] = [
  {
    id: 9001,
    type: "FIT_ANALYSIS_COMPLETE",
    targetType: "APPLICATION_CASE",
    targetId: 103,
    title: "토스 적합도 분석이 완료됐어요",
    message: "프론트엔드 개발자 적합도 82점. 보완 역량과 학습 과제를 확인해 보세요.",
    link: "/applications/103/fit",
    read: false,
    createdAt: iso(0),
  },
  {
    id: 9002,
    type: "INTERVIEW_REPORT_READY",
    targetType: "INTERVIEW_SESSION",
    targetId: 5001,
    title: "모의면접 리포트가 준비됐어요",
    message: "카카오 직무면접 세션 분석이 끝났어요. 답변별 피드백과 총평을 확인하세요.",
    link: "/interview/sessions/5001/report",
    read: false,
    createdAt: iso(0),
  },
  {
    id: 9003,
    type: "COMMENT_REPLY",
    targetType: "POST",
    targetId: 301,
    title: "내 댓글에 답글이 달렸어요",
    message: "“저도 카카오 면접 후기 공유 부탁드려요!”",
    link: "/community/posts/301",
    read: false,
    createdAt: iso(1),
    actor: { id: 42, name: "면접왕", avatarUrl: undefined },
  },
  {
    id: 9004,
    type: "ROOM_MESSAGE",
    targetType: "CHAT_ROOM",
    targetId: 77,
    title: "새 채팅이 도착했어요",
    message: "스터디 채팅방에 새 메시지가 올라왔습니다.",
    link: "/collaboration",
    read: false,
    createdAt: iso(2),
    actor: { id: 84, name: "스터디장", avatarUrl: undefined },
  },
  {
    id: 9005,
    type: "COMPANY_ANALYSIS_COMPLETE",
    targetType: "APPLICATION_CASE",
    targetId: 101,
    title: "카카오 기업 분석이 완료됐어요",
    message: "최근 이슈와 예상 면접 포인트가 정리됐어요. 지원 건에서 확인하세요.",
    link: "/applications/101",
    read: true,
    createdAt: iso(3),
  },
  {
    id: 9006,
    type: "NOTICE",
    title: "[공지] 6월 정기 점검 안내",
    message: "6월 22일 02:00~04:00 서비스 점검이 예정되어 있어요. 이용에 참고해 주세요.",
    link: "/notice",
    read: true,
    createdAt: iso(5),
  },
];

const unreadCount = (): number => demoNotifications.filter((n) => !n.read).length;

// 알림 설정 데모 상태(세션 내 in-memory). PUT 시 부분 업데이트를 반영해 echo 한다.
const demoPreference: NotificationPreference = {
  pushEnabled: true,
  emailEnabled: true,
  categories: {
    ai_analysis: true,
    interview: true,
    correction: true,
    community: true,
    messenger: true,
    recommendation: true,
    billing: true,
    notice: true,
    marketing: false,
  },
  rules: defaultNotificationRules(),
  quietHoursStart: "23:00",
  quietHoursEnd: "08:00",
  pushDeviceRegistered: true,
};

interface PreferenceUpdateBody {
  pushEnabled?: boolean;
  emailEnabled?: boolean;
  categories?: Record<string, boolean>;
  rules?: NotificationPreference["rules"];
  quietHoursStart?: string | null;
  quietHoursEnd?: string | null;
}

function listNotifications(ctx: MockContext): NotificationPageResponse {
  const { page, size } = pageOf(ctx, 20);
  const start = page * size;
  const slice = demoNotifications.slice(start, start + size);
  return {
    notifications: slice,
    total: demoNotifications.length,
    page,
    size,
    hasNext: start + size < demoNotifications.length,
  };
}

export const notificationRoutes: MockRoute[] = [
  // 알림 목록 (client maps BackendNotification[] -> Notification[])
  { method: "GET", pattern: /^\/notifications$/, handler: (ctx) => listNotifications(ctx) },

  // 미읽음 알림 수 (배지)
  { method: "GET", pattern: /^\/notifications\/unread-count$/, handler: () => unreadCount() },

  // 개별 읽음 처리 -> void
  {
    method: "PATCH",
    pattern: /^\/notifications\/(\d+)\/read$/,
    handler: ({ params }) => {
      const target = demoNotifications.find((n) => n.id === Number(params[0]));
      if (target) target.read = true;
      return null;
    },
  },

  // 전체 읽음 -> void
  {
    method: "POST",
    pattern: /^\/notifications\/read-all$/,
    handler: () => {
      demoNotifications.forEach((n) => {
        n.read = true;
      });
      return null;
    },
  },

  // 알림 설정 조회
  { method: "GET", pattern: /^\/notifications\/preferences$/, handler: () => demoPreference },

  // 알림 설정 수정 (부분 업데이트 echo)
  {
    method: "PUT",
    pattern: /^\/notifications\/preferences$/,
    handler: ({ body }) => {
      const update = (body ?? {}) as PreferenceUpdateBody;
      if (update.pushEnabled !== undefined) demoPreference.pushEnabled = update.pushEnabled;
      if (update.emailEnabled !== undefined) demoPreference.emailEnabled = update.emailEnabled;
      if (update.categories !== undefined) demoPreference.categories = update.categories;
      if (update.rules !== undefined) demoPreference.rules = update.rules;
      if (update.quietHoursStart !== undefined) demoPreference.quietHoursStart = update.quietHoursStart;
      if (update.quietHoursEnd !== undefined) demoPreference.quietHoursEnd = update.quietHoursEnd;
      return demoPreference;
    },
  },
];
