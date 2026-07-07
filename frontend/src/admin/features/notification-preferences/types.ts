/** 관리자 알림 수신 설정(개인 opt-out) 타입. */

/** 관리자 알림 type → 수신 여부. 키가 없으면 수신(true)이 기본. */
export type AdminNotificationCategories = Record<string, boolean>;

export interface AdminNotificationCategoryMeta {
  type: string;
  label: string;
  desc: string;
}

/** 백엔드 AdminNotificationOptOutService.ADMIN_OPT_OUT_TYPES 와 1:1. */
export const ADMIN_NOTIFICATION_CATEGORY_METAS: AdminNotificationCategoryMeta[] = [
  { type: "NEW_REPORT", label: "새 신고 접수", desc: "게시글·댓글 신고가 접수되면 알림을 받습니다. (콘텐츠 운영 권한 보유자 대상)" },
  { type: "NEW_TICKET", label: "새 문의 접수", desc: "새 고객 문의(티켓)가 접수되면 알림을 받습니다. (콘텐츠 운영 권한 보유자 대상)" },
  { type: "NEW_USER", label: "새 회원 가입", desc: "새 회원이 가입하면 알림을 받습니다. (회원 운영 권한 보유자 대상)" },
  { type: "NEW_COMPANY_APPLICATION", label: "기업 계정 신청", desc: "기업 계정 전환 신청이 접수되면 알림을 받습니다. (회원 운영 권한 보유자 대상)" },
  { type: "NEW_JOB_POSTING_REVIEW", label: "공고 검수 요청", desc: "기업 공고가 검수 대기 상태가 되면 알림을 받습니다. (콘텐츠 운영 권한 보유자 대상)" },
];
