/* ── AI 알림 (11개) ── */
export type AINotificationType =
  | "PROFILE_ANALYZED"
  | "JOB_ANALYSIS_COMPLETE"
  | "COMPANY_ANALYSIS_COMPLETE"
  | "FIT_ANALYSIS_COMPLETE"
  | "CAREER_TREND_COMPLETE"
  | "QUESTIONS_GENERATED"
  | "INTERVIEW_REPORT_READY"
  | "CORRECTION_COMPLETE"
  | "POST_SUMMARY_READY"
  | "LOW_CONFIDENCE_REPORT"
  | "TICKET_DRAFT_READY";

/* ── 비-AI 알림 — 사용자 ── */
export type UserNotificationType =
  | "COMMENT"
  | "COMMENT_REPLY"
  | "LIKE"
  | "NOTICE"
  | "TICKET_ANSWERED"
  | "CREDIT_LOW"
  // 결제/크레딧 (E 결제 흐름에서 발생, billing 카테고리)
  | "PAYMENT_COMPLETE"
  | "PAYMENT_SCHEDULED"
  | "CREDIT_RECHARGED";

/* ── 비-AI 알림 — 관리자 (3개) ── */
export type AdminNotificationType =
  | "NEW_REPORT"
  | "NEW_TICKET"
  | "NEW_USER";

export type NotificationType =
  | AINotificationType
  | UserNotificationType
  | AdminNotificationType;

export type NotificationCategory =
  | "all"
  | "ai_analysis"
  | "interview"
  | "correction"
  | "community"
  | "billing"
  | "notice"
  | "admin";

export interface Notification {
  id: number;
  type: NotificationType;
  category: NotificationCategory;
  icon: string;
  title: string;
  message?: string;
  link?: string;
  targetType?: string;
  targetId?: number;
  actorName?: string;
  actorId?: number;
  isRead: boolean;
  createdAt: string;
}

export const NOTIFICATION_CATEGORIES = [
  { value: "all", label: "전체" },
  { value: "ai_analysis", label: "AI 분석" },
  { value: "interview", label: "면접" },
  { value: "correction", label: "첨삭" },
  { value: "community", label: "커뮤니티" },
  { value: "billing", label: "결제" },
  { value: "notice", label: "공지" },
  { value: "admin", label: "관리자" },
] as const;

/** type → category 매핑 */
export const TYPE_TO_CATEGORY: Record<NotificationType, NotificationCategory> = {
  /* AI 분석 */
  PROFILE_ANALYZED: "ai_analysis",
  JOB_ANALYSIS_COMPLETE: "ai_analysis",
  COMPANY_ANALYSIS_COMPLETE: "ai_analysis",
  FIT_ANALYSIS_COMPLETE: "ai_analysis",
  CAREER_TREND_COMPLETE: "ai_analysis",
  /* 면접 */
  QUESTIONS_GENERATED: "interview",
  INTERVIEW_REPORT_READY: "interview",
  /* 첨삭 */
  CORRECTION_COMPLETE: "correction",
  /* 커뮤니티 */
  COMMENT: "community",
  COMMENT_REPLY: "community",
  LIKE: "community",
  POST_SUMMARY_READY: "community",
  /* 결제 */
  CREDIT_LOW: "billing",
  PAYMENT_COMPLETE: "billing",
  PAYMENT_SCHEDULED: "billing",
  CREDIT_RECHARGED: "billing",
  /* 공지/문의 */
  NOTICE: "notice",
  TICKET_ANSWERED: "notice",
  /* 관리자 */
  NEW_REPORT: "admin",
  NEW_TICKET: "admin",
  NEW_USER: "admin",
  LOW_CONFIDENCE_REPORT: "admin",
  TICKET_DRAFT_READY: "admin",
};
