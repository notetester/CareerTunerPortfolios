export type PlannerTimingPrecision = "YEAR" | "MONTH" | "DAY" | "AM_PM" | "HOUR" | "MINUTE" | "SECOND";
export type PlannerScheduleStatus = "PLANNED" | "IN_PROGRESS" | "DONE" | "CANCELED";
export type PlannerScheduleKind = "TASK" | "EVENT" | "DEADLINE" | "STRATEGY" | "REMINDER" | string;
export type PlannerReminderChannel =
  | "WEB_TOAST"
  | "BROWSER"
  | "WEB_PUSH"
  | "EMAIL"
  | "MOBILE_VIBRATE"
  | "MOBILE_SOUND"
  | "MOBILE_SOUND_VIBRATE"
  | "DESKTOP_SOUND"
  | "DESKTOP_TOAST"
  | "DESKTOP_TASKBAR";

export interface PlannerMemo {
  id: number;
  title: string | null;
  content: string | null;
  color: string;
  pinned: boolean;
  overlayVisible: boolean;
  opacity: number;
  applicationCaseId: number | null;
  fitAnalysisId: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface PlannerMemoRequest {
  title: string | null;
  content: string | null;
  color: string;
  pinned: boolean;
  overlayVisible: boolean;
  opacity: number;
  applicationCaseId?: number | null;
  fitAnalysisId?: number | null;
}

export interface PlannerScheduleReminderRequest {
  remindAt: string | null;
  offsetMinutes: number | null;
  channels: PlannerReminderChannel[];
  soundEnabled: boolean;
  vibrationEnabled: boolean;
}

export interface PlannerScheduleReminder extends PlannerScheduleReminderRequest {
  id: number;
  status: "PENDING" | "SENT" | string;
  sentAt: string | null;
}

export interface PlannerScheduleItem {
  id: number;
  title: string;
  description: string | null;
  kind: PlannerScheduleKind;
  status: PlannerScheduleStatus | string;
  allDay: boolean;
  timingPrecision: PlannerTimingPrecision | string;
  startAt: string;
  endAt: string | null;
  timezone: string;
  applicationCaseId: number | null;
  fitAnalysisId: number | null;
  sourceType: string;
  sourceRef: string | null;
  sourceSnapshotJson: string | null;
  overlayVisible: boolean;
  opacity: number;
  pinned: boolean;
  clickThrough: boolean;
  applicationCompanyName: string | null;
  applicationJobTitle: string | null;
  fitScore: number | null;
  applicationDeadlineDate: string | null;
  reminders: PlannerScheduleReminder[];
  createdAt: string;
  updatedAt: string;
}

export interface PlannerScheduleItemRequest {
  title: string;
  description: string | null;
  kind: PlannerScheduleKind;
  status: PlannerScheduleStatus | string;
  allDay: boolean;
  timingPrecision: PlannerTimingPrecision | string;
  startAt: string;
  endAt: string | null;
  timezone: string;
  applicationCaseId?: number | null;
  fitAnalysisId?: number | null;
  sourceType?: string | null;
  sourceRef?: string | null;
  sourceSnapshotJson?: string | null;
  overlayVisible: boolean;
  opacity: number;
  pinned: boolean;
  clickThrough: boolean;
  reminders: PlannerScheduleReminderRequest[];
}

export interface PlannerDashboard {
  memos: PlannerMemo[];
  scheduleItems: PlannerScheduleItem[];
}

export interface PlannerStrategyDraftItem extends Omit<PlannerScheduleItemRequest, "status" | "overlayVisible" | "opacity" | "pinned" | "clickThrough"> {
  overlapCount: number;
}

export interface PlannerStrategyDraft {
  fitAnalysisId: number;
  applicationCaseId: number;
  companyName: string;
  jobTitle: string;
  generatedAt: string;
  staleReasons: string[];
  items: PlannerStrategyDraftItem[];
}

export const REMINDER_CHANNEL_LABELS: Record<PlannerReminderChannel, string> = {
  WEB_TOAST: "화면 알림",
  BROWSER: "브라우저 알림",
  WEB_PUSH: "웹 푸시",
  EMAIL: "이메일",
  MOBILE_VIBRATE: "모바일 진동",
  MOBILE_SOUND: "모바일 소리",
  MOBILE_SOUND_VIBRATE: "모바일 소리+진동",
  DESKTOP_SOUND: "데스크톱 소리",
  DESKTOP_TOAST: "데스크톱 알림",
  DESKTOP_TASKBAR: "작업표시줄",
};
