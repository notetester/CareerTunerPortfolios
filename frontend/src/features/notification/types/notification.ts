/* ── AI 알림 (13개) ── */
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
  | "JOB_POSTING_EXTRACTION_SUCCEEDED"
  | "JOB_POSTING_EXTRACTION_FAILED"
  | "LOW_CONFIDENCE_REPORT"
  | "TICKET_DRAFT_READY";

/* ── 비-AI 알림 — 사용자 (10개) ── */
export type UserNotificationType =
  | "COMMENT"
  | "COMMENT_REPLY"
  | "COMMENT_HIDDEN"
  | "COMMENT_RESTORED"
  | "COMMENT_REMOVED"
  | "LIKE"
  | "POST_HIDDEN"
  | "POST_REMOVED"
  | "POST_RESTORED"
  | "NOTICE"
  | "TICKET_ANSWERED"
  | "ACCOUNT_BLOCKED"
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
  icon?: string;
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

/* ── TYPE_META: type(22종) → UI 표현 단일 소스 (디자인 시스템 Notify.jsx 기반) ── */

export type ToastVariant = "success" | "info" | "warning" | "danger";

export interface TypeMeta {
  cat: NotificationCategory;
  icon: string;          // lucide 아이콘 이름
  variant: ToastVariant;
  cta: string;           // CTA 라벨 ("분석 보기" 등)
  actor?: boolean;       // true → 주체 아바타 + 타입 배지 렌더
}

export const TYPE_META: Record<NotificationType, TypeMeta> = {
  /* AI 분석 */
  PROFILE_ANALYZED:          { cat: "ai_analysis", icon: "UserSearch",         variant: "success", cta: "프로필 분석 보기" },
  JOB_ANALYSIS_COMPLETE:     { cat: "ai_analysis", icon: "Briefcase",          variant: "success", cta: "직무 분석 보기" },
  COMPANY_ANALYSIS_COMPLETE: { cat: "ai_analysis", icon: "Building2",          variant: "success", cta: "기업 분석 보기" },
  FIT_ANALYSIS_COMPLETE:     { cat: "ai_analysis", icon: "Target",             variant: "success", cta: "적합도 분석 보기" },
  CAREER_TREND_COMPLETE:     { cat: "ai_analysis", icon: "TrendingUp",         variant: "info",    cta: "트렌드 리포트 보기" },
  JOB_POSTING_EXTRACTION_SUCCEEDED: { cat: "ai_analysis", icon: "FileSearch",  variant: "success", cta: "지원 건 보기" },
  JOB_POSTING_EXTRACTION_FAILED:    { cat: "ai_analysis", icon: "AlertTriangle", variant: "danger", cta: "지원 건 보기" },
  /* 면접 */
  QUESTIONS_GENERATED:       { cat: "interview",   icon: "ListChecks",         variant: "info",    cta: "예상 질문 보기" },
  INTERVIEW_REPORT_READY:    { cat: "interview",   icon: "ClipboardList",      variant: "info",    cta: "면접 리포트 보기" },
  /* 첨삭 */
  CORRECTION_COMPLETE:       { cat: "correction",  icon: "SpellCheck",         variant: "success", cta: "첨삭 결과 보기" },
  /* 커뮤니티 */
  COMMENT:                   { cat: "community",   icon: "MessageCircle",      variant: "info",    cta: "댓글 보기",      actor: true },
  COMMENT_REPLY:             { cat: "community",   icon: "CornerDownRight",    variant: "info",    cta: "답글 보기",      actor: true },
  COMMENT_HIDDEN:            { cat: "community",   icon: "EyeOff",             variant: "warning", cta: "댓글 보기" },
  COMMENT_RESTORED:          { cat: "community",   icon: "RotateCcw",          variant: "success", cta: "댓글 보기" },
  COMMENT_REMOVED:           { cat: "community",   icon: "Trash2",             variant: "danger",  cta: "댓글 보기" },
  LIKE:                      { cat: "community",   icon: "Heart",              variant: "info",    cta: "게시글 보기",    actor: true },
  POST_HIDDEN:               { cat: "community",   icon: "EyeOff",             variant: "warning", cta: "가이드라인 보기" },
  POST_REMOVED:              { cat: "community",   icon: "Trash2",             variant: "danger",  cta: "가이드라인 보기" },
  POST_RESTORED:             { cat: "community",   icon: "RotateCcw",          variant: "success", cta: "게시글 보기" },
  POST_SUMMARY_READY:        { cat: "community",   icon: "Sparkles",           variant: "info",    cta: "요약 보기" },
  /* 결제 */
  CREDIT_LOW:                { cat: "billing",     icon: "AlertTriangle",      variant: "warning", cta: "크레딧 충전" },
  PAYMENT_COMPLETE:          { cat: "billing",     icon: "CreditCard",         variant: "success", cta: "결제 내역 보기" },
  PAYMENT_SCHEDULED:         { cat: "billing",     icon: "CreditCard",         variant: "info",    cta: "결제 예정 보기" },
  CREDIT_RECHARGED:          { cat: "billing",     icon: "CreditCard",         variant: "success", cta: "크레딧 보기" },
  /* 공지 */
  NOTICE:                    { cat: "notice",      icon: "Megaphone",          variant: "warning", cta: "공지 보기" },
  TICKET_ANSWERED:           { cat: "notice",      icon: "MessageSquareReply", variant: "info",    cta: "문의 답변 보기", actor: true },
  ACCOUNT_BLOCKED:           { cat: "notice",      icon: "ShieldAlert",        variant: "danger",  cta: "문의하기" },
  /* 관리자 */
  NEW_REPORT:                { cat: "admin",       icon: "Flag",               variant: "danger",  cta: "신고 확인" },
  NEW_TICKET:                { cat: "admin",       icon: "Ticket",             variant: "info",    cta: "문의 확인" },
  NEW_USER:                  { cat: "admin",       icon: "UserPlus",           variant: "info",    cta: "회원 보기",      actor: true },
  LOW_CONFIDENCE_REPORT:     { cat: "admin",       icon: "ShieldAlert",        variant: "warning", cta: "리포트 점검" },
  TICKET_DRAFT_READY:        { cat: "admin",       icon: "FilePen",            variant: "info",    cta: "답변 초안 보기" },
};

const TYPE_FALLBACK: TypeMeta = { cat: "notice", icon: "Bell", variant: "info", cta: "자세히 보기" };

/** type → UI 메타 (알 수 없는 type은 폴백) */
export function typeMeta(type: NotificationType): TypeMeta {
  return TYPE_META[type] || TYPE_FALLBACK;
}

/** ISO 8601 → 상대시간 문자열 */
export function relTime(ts: string): string {
  const t = new Date(ts).getTime();
  if (isNaN(t)) return typeof ts === "string" ? ts : "";
  const m = Math.floor(Math.max(0, Date.now() - t) / 60000);
  if (m < 1) return "방금";
  if (m < 60) return m + "분 전";
  const h = Math.floor(m / 60);
  if (h < 24) return h + "시간 전";
  const d = Math.floor(h / 24);
  if (d === 1) return "어제";
  if (d < 7) return d + "일 전";
  return new Date(t).toLocaleDateString("ko-KR", { month: "long", day: "numeric" });
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
  JOB_POSTING_EXTRACTION_SUCCEEDED: "ai_analysis",
  JOB_POSTING_EXTRACTION_FAILED: "ai_analysis",
  /* 면접 */
  QUESTIONS_GENERATED: "interview",
  INTERVIEW_REPORT_READY: "interview",
  /* 첨삭 */
  CORRECTION_COMPLETE: "correction",
  /* 커뮤니티 */
  COMMENT: "community",
  COMMENT_REPLY: "community",
  COMMENT_HIDDEN: "community",
  COMMENT_RESTORED: "community",
  COMMENT_REMOVED: "community",
  LIKE: "community",
  POST_HIDDEN: "community",
  POST_REMOVED: "community",
  POST_RESTORED: "community",
  POST_SUMMARY_READY: "community",
  /* 결제 */
  CREDIT_LOW: "billing",
  PAYMENT_COMPLETE: "billing",
  PAYMENT_SCHEDULED: "billing",
  CREDIT_RECHARGED: "billing",
  /* 공지/문의 */
  NOTICE: "notice",
  TICKET_ANSWERED: "notice",
  ACCOUNT_BLOCKED: "notice",
  /* 관리자 */
  NEW_REPORT: "admin",
  NEW_TICKET: "admin",
  NEW_USER: "admin",
  LOW_CONFIDENCE_REPORT: "admin",
  TICKET_DRAFT_READY: "admin",
};
