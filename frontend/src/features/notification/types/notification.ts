export type NotificationType =
  | "ANALYSIS_COMPLETED"
  | "REPORT_READY"
  | "CREDIT_LOW"
  | "PAYMENT"
  | "NOTICE"
  | "TICKET_ANSWERED";

export type NotificationCategory =
  | "all"
  | "analysis"
  | "report"
  | "billing"
  | "notice"
  | "reply";

export interface Notification {
  id: number;
  type: NotificationType;
  category: NotificationCategory;
  icon: string;
  title: string;
  message?: string;
  link?: string;
  isRead: boolean;
  createdAt: string;
}

export const NOTIFICATION_CATEGORIES = [
  { value: "all", label: "전체" },
  { value: "analysis", label: "분석완료" },
  { value: "report", label: "리포트" },
  { value: "billing", label: "결제" },
  { value: "notice", label: "공지" },
  { value: "reply", label: "문의답변" },
] as const;
