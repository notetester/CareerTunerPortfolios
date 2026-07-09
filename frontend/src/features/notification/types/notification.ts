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
  | "JOB_POSTING_EXTRACTION_REVIEW_REQUIRED"
  | "JOB_POSTING_EXTRACTION_FAILED"
  | "LOW_CONFIDENCE_REPORT"
  | "TICKET_DRAFT_READY";

/* ── 비-AI 알림 — 사용자 ── */
export type UserNotificationType =
  | "COMMENT"
  | "COMMENT_REPLY"
  | "COMMENT_HIDDEN"
  | "COMMENT_RESTORED"
  | "COMMENT_REMOVED"
  | "LIKE"
  | "LIKE_ANON"
  | "POST_DISLIKE"
  | "POST_DISLIKE_ANON"
  | "POST_RECOMMEND"
  | "POST_RECOMMEND_ANON"
  | "POST_DISRECOMMEND"
  | "POST_DISRECOMMEND_ANON"
  | "COMMENT_LIKE"
  | "COMMENT_LIKE_ANON"
  | "COMMENT_DISLIKE"
  | "COMMENT_DISLIKE_ANON"
  | "COMMENT_RECOMMEND"
  | "COMMENT_RECOMMEND_ANON"
  | "COMMENT_DISRECOMMEND"
  | "COMMENT_DISRECOMMEND_ANON"
  | "POST_BOOKMARK"
  | "POST_BOOKMARK_ANON"
  | "POST_SCRAP"
  | "POST_SCRAP_ANON"
  | "POST_WATCH_COMMENT"
  | "COMMENT_WATCH_REPLY"
  | "COMPANY_APPLY_RESULT"
  | "JOB_POSTING_REVIEW_RESULT"
  | "POST_HIDDEN"
  | "POST_IMAGE_BLURRED"
  | "COMMUNITY_STRIKE_WARNING"
  | "POST_REMOVED"
  | "POST_RESTORED"
  | "NOTICE"
  | "TICKET_ANSWERED"
  | "ACCOUNT_BLOCKED"
  | "MFA_LOGIN_APPROVAL"
  | "FRIEND_REQUEST"
  | "FRIEND_ACCEPTED"
  | "ROOM_INVITE"
  | "ROOM_MESSAGE"
  | "NOTE_MESSAGE"
  | "ROOM_MENTION"
  | "INTERVIEW_DISPATCH"
  | "RECOMMENDED_JOB"
  | "RECOMMENDED_POST"
  | "MARKETING_AD"
  | "CREDIT_LOW"
  // 결제/크레딧 (E 결제 흐름에서 발생, billing 카테고리)
  | "PAYMENT_COMPLETE"
  | "PAYMENT_SCHEDULED"
  | "SUBSCRIPTION_CANCELED"
  | "CREDIT_RECHARGED"
  | "REFUND_RESULT"
  | "SCHEDULE_REMINDER";

/* ── 발신자 관계 (댓글·답글·쪽지·채팅 알림의 세부 필터 차원) ── */
export type SenderRelation = "stranger" | "friend" | "company" | "operator";

/* ── 비-AI 알림 — 관리자 (3개) ── */
export type AdminNotificationType =
  | "NEW_REPORT"
  | "NEW_TICKET"
  | "NEW_USER"
  | "NEW_COMPANY_APPLICATION"
  | "NEW_JOB_POSTING_REVIEW";

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
  | "messenger"
  | "recommendation"
  | "billing"
  | "notice"
  | "marketing"
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
  senderRelation?: SenderRelation;
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
  urgent?: boolean;      // false → 몰아보기(토스트 안 띄우고 뱃지 카운트만). 미지정 = 즉시(true)로 간주.
}

export const TYPE_META: Record<NotificationType, TypeMeta> = {
  /* AI 분석 */
  PROFILE_ANALYZED:          { cat: "ai_analysis", icon: "UserSearch",         variant: "success", cta: "프로필 분석 보기" },
  JOB_ANALYSIS_COMPLETE:     { cat: "ai_analysis", icon: "Briefcase",          variant: "success", cta: "직무 분석 보기" },
  COMPANY_ANALYSIS_COMPLETE: { cat: "ai_analysis", icon: "Building2",          variant: "success", cta: "기업 분석 보기" },
  FIT_ANALYSIS_COMPLETE:     { cat: "ai_analysis", icon: "Target",             variant: "success", cta: "적합도 분석 보기" },
  CAREER_TREND_COMPLETE:     { cat: "ai_analysis", icon: "TrendingUp",         variant: "info",    cta: "트렌드 리포트 보기" },
  JOB_POSTING_EXTRACTION_SUCCEEDED: { cat: "ai_analysis", icon: "FileSearch",  variant: "success", cta: "지원 건 보기" },
  JOB_POSTING_EXTRACTION_REVIEW_REQUIRED: { cat: "ai_analysis", icon: "FileSearch",  variant: "warning", cta: "지원 건 보기" },
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
  LIKE_ANON:                 { cat: "community",   icon: "Heart",              variant: "info",    cta: "게시글 보기" },
  POST_DISLIKE:              { cat: "community",   icon: "HeartOff",           variant: "info",    cta: "게시글 보기",    actor: true, urgent: false },
  POST_DISLIKE_ANON:         { cat: "community",   icon: "HeartOff",           variant: "info",    cta: "게시글 보기",    urgent: false },
  POST_RECOMMEND:            { cat: "community",   icon: "ThumbsUp",           variant: "info",    cta: "게시글 보기",    actor: true },
  POST_RECOMMEND_ANON:       { cat: "community",   icon: "ThumbsUp",           variant: "info",    cta: "게시글 보기" },
  POST_DISRECOMMEND:         { cat: "community",   icon: "ThumbsDown",         variant: "info",    cta: "게시글 보기",    actor: true, urgent: false },
  POST_DISRECOMMEND_ANON:    { cat: "community",   icon: "ThumbsDown",         variant: "info",    cta: "게시글 보기",    urgent: false },
  COMMENT_LIKE:              { cat: "community",   icon: "Heart",              variant: "info",    cta: "댓글 보기",      actor: true },
  COMMENT_LIKE_ANON:         { cat: "community",   icon: "Heart",              variant: "info",    cta: "댓글 보기" },
  COMMENT_DISLIKE:           { cat: "community",   icon: "HeartOff",           variant: "info",    cta: "댓글 보기",      actor: true, urgent: false },
  COMMENT_DISLIKE_ANON:      { cat: "community",   icon: "HeartOff",           variant: "info",    cta: "댓글 보기",      urgent: false },
  COMMENT_RECOMMEND:         { cat: "community",   icon: "ThumbsUp",           variant: "info",    cta: "댓글 보기",      actor: true },
  COMMENT_RECOMMEND_ANON:    { cat: "community",   icon: "ThumbsUp",           variant: "info",    cta: "댓글 보기" },
  COMMENT_DISRECOMMEND:      { cat: "community",   icon: "ThumbsDown",         variant: "info",    cta: "댓글 보기",      actor: true, urgent: false },
  COMMENT_DISRECOMMEND_ANON: { cat: "community",   icon: "ThumbsDown",         variant: "info",    cta: "댓글 보기",      urgent: false },
  POST_BOOKMARK:             { cat: "community",   icon: "Star",               variant: "info",    cta: "게시글 보기",    actor: true },
  POST_BOOKMARK_ANON:        { cat: "community",   icon: "Star",               variant: "info",    cta: "게시글 보기" },
  POST_SCRAP:                { cat: "community",   icon: "Clipboard",          variant: "info",    cta: "게시글 보기",    actor: true },
  POST_SCRAP_ANON:           { cat: "community",   icon: "Clipboard",          variant: "info",    cta: "게시글 보기" },
  POST_WATCH_COMMENT:        { cat: "community",   icon: "BellRing",           variant: "info",    cta: "새 댓글 보기" },
  COMMENT_WATCH_REPLY:       { cat: "community",   icon: "BellRing",           variant: "info",    cta: "새 답글 보기" },
  COMPANY_APPLY_RESULT:      { cat: "notice",      icon: "Building2",          variant: "info",    cta: "신청 결과 보기" },
  JOB_POSTING_REVIEW_RESULT: { cat: "notice",      icon: "Briefcase",          variant: "info",    cta: "공고 검토 결과" },
  POST_HIDDEN:               { cat: "community",   icon: "EyeOff",             variant: "warning", cta: "가이드라인 보기" },
  POST_IMAGE_BLURRED:        { cat: "community",   icon: "EyeOff",             variant: "warning", cta: "게시글 보기" },
  COMMUNITY_STRIKE_WARNING:  { cat: "community",   icon: "AlertTriangle",      variant: "warning", cta: "가이드라인 보기" },
  POST_REMOVED:              { cat: "community",   icon: "Trash2",             variant: "danger",  cta: "가이드라인 보기" },
  POST_RESTORED:             { cat: "community",   icon: "RotateCcw",          variant: "success", cta: "게시글 보기" },
  POST_SUMMARY_READY:        { cat: "community",   icon: "Sparkles",           variant: "info",    cta: "요약 보기" },
  /* 메신저 */
  FRIEND_REQUEST:            { cat: "messenger",   icon: "UserPlus",           variant: "info",    cta: "친구 요청 보기", actor: true },
  FRIEND_ACCEPTED:           { cat: "messenger",   icon: "UserPlus",           variant: "success", cta: "친구 목록 보기", actor: true },
  ROOM_INVITE:               { cat: "messenger",   icon: "MessageCircle",      variant: "info",    cta: "채팅방 보기", actor: true },
  ROOM_MESSAGE:              { cat: "messenger",   icon: "MessageCircle",      variant: "info",    cta: "메신저 열기", actor: true },
  NOTE_MESSAGE:              { cat: "messenger",   icon: "Mail",               variant: "info",    cta: "쪽지 보기", actor: true },
  ROOM_MENTION:              { cat: "messenger",   icon: "MessageSquareReply", variant: "info",    cta: "언급 보기", actor: true },
  /* 면접 세션 전달(데스크톱→폰 이어하기) */
  INTERVIEW_DISPATCH:        { cat: "interview",   icon: "Smartphone",         variant: "info",    cta: "세션 이어하기" },
  /* 추천/마케팅 */
  RECOMMENDED_JOB:           { cat: "recommendation", icon: "Briefcase",       variant: "info",    cta: "추천 공고 보기" },
  RECOMMENDED_POST:          { cat: "recommendation", icon: "FileText",        variant: "info",    cta: "추천 글 보기" },
  MARKETING_AD:              { cat: "marketing",   icon: "Megaphone",          variant: "info",    cta: "혜택 보기", urgent: false },
  /* 결제 */
  CREDIT_LOW:                { cat: "billing",     icon: "AlertTriangle",      variant: "warning", cta: "크레딧 충전" },
  PAYMENT_COMPLETE:          { cat: "billing",     icon: "CreditCard",         variant: "success", cta: "결제 내역 보기" },
  PAYMENT_SCHEDULED:         { cat: "billing",     icon: "CreditCard",         variant: "info",    cta: "결제 예정 보기" },
  SUBSCRIPTION_CANCELED:     { cat: "billing",     icon: "CalendarX",          variant: "info",    cta: "구독 상태 보기" },
  CREDIT_RECHARGED:          { cat: "billing",     icon: "CreditCard",         variant: "success", cta: "크레딧 보기" },
  REFUND_RESULT:             { cat: "billing",     icon: "CreditCard",         variant: "info",    cta: "환불 결과 보기" },
  SCHEDULE_REMINDER:         { cat: "notice",      icon: "CalendarClock",      variant: "warning", cta: "일정 보기" },
  /* 공지 */
  NOTICE:                    { cat: "notice",      icon: "Megaphone",          variant: "warning", cta: "공지 보기" },
  TICKET_ANSWERED:           { cat: "notice",      icon: "MessageSquareReply", variant: "info",    cta: "문의 답변 보기", actor: true },
  ACCOUNT_BLOCKED:           { cat: "notice",      icon: "ShieldAlert",        variant: "danger",  cta: "문의하기" },
  MFA_LOGIN_APPROVAL:         { cat: "notice",      icon: "ShieldCheck",        variant: "warning", cta: "로그인 승인하기" },
  /* 관리자 */
  NEW_REPORT:                { cat: "admin",       icon: "Flag",               variant: "danger",  cta: "신고 확인" },
  NEW_TICKET:                { cat: "admin",       icon: "Ticket",             variant: "info",    cta: "문의 확인",      urgent: false },
  NEW_COMPANY_APPLICATION:   { cat: "admin",       icon: "Building2",          variant: "info",    cta: "기업 신청 확인" },
  NEW_JOB_POSTING_REVIEW:    { cat: "admin",       icon: "Briefcase",          variant: "info",    cta: "공고 검토" },
  NEW_USER:                  { cat: "admin",       icon: "UserPlus",           variant: "info",    cta: "회원 보기",      actor: true, urgent: false },
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
  { value: "messenger", label: "메신저" },
  { value: "recommendation", label: "추천 공고" },
  { value: "billing", label: "결제" },
  { value: "notice", label: "공지" },
  { value: "marketing", label: "광고/혜택" },
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
  JOB_POSTING_EXTRACTION_REVIEW_REQUIRED: "ai_analysis",
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
  LIKE_ANON: "community",
  POST_DISLIKE: "community",
  POST_DISLIKE_ANON: "community",
  POST_RECOMMEND: "community",
  POST_RECOMMEND_ANON: "community",
  POST_DISRECOMMEND: "community",
  POST_DISRECOMMEND_ANON: "community",
  COMMENT_LIKE: "community",
  COMMENT_LIKE_ANON: "community",
  COMMENT_DISLIKE: "community",
  COMMENT_DISLIKE_ANON: "community",
  COMMENT_RECOMMEND: "community",
  COMMENT_RECOMMEND_ANON: "community",
  COMMENT_DISRECOMMEND: "community",
  COMMENT_DISRECOMMEND_ANON: "community",
  POST_BOOKMARK: "community",
  POST_BOOKMARK_ANON: "community",
  POST_SCRAP: "community",
  POST_SCRAP_ANON: "community",
  POST_WATCH_COMMENT: "community",
  COMMENT_WATCH_REPLY: "community",
  COMPANY_APPLY_RESULT: "notice",
  JOB_POSTING_REVIEW_RESULT: "notice",
  POST_HIDDEN: "community",
  POST_IMAGE_BLURRED: "community",
  COMMUNITY_STRIKE_WARNING: "community",
  POST_REMOVED: "community",
  POST_RESTORED: "community",
  POST_SUMMARY_READY: "community",
  FRIEND_REQUEST: "messenger",
  FRIEND_ACCEPTED: "messenger",
  ROOM_INVITE: "messenger",
  ROOM_MESSAGE: "messenger",
  NOTE_MESSAGE: "messenger",
  ROOM_MENTION: "messenger",
  INTERVIEW_DISPATCH: "interview",
  RECOMMENDED_JOB: "recommendation",
  RECOMMENDED_POST: "recommendation",
  MARKETING_AD: "marketing",
  /* 결제 */
  CREDIT_LOW: "billing",
  PAYMENT_COMPLETE: "billing",
  PAYMENT_SCHEDULED: "billing",
  SUBSCRIPTION_CANCELED: "billing",
  CREDIT_RECHARGED: "billing",
  REFUND_RESULT: "billing",
  SCHEDULE_REMINDER: "notice",
  /* 공지/문의 */
  NOTICE: "notice",
  TICKET_ANSWERED: "notice",
  ACCOUNT_BLOCKED: "notice",
  MFA_LOGIN_APPROVAL: "notice",
  /* 관리자 */
  NEW_REPORT: "admin",
  NEW_TICKET: "admin",
  NEW_USER: "admin",
  NEW_COMPANY_APPLICATION: "admin",
  NEW_JOB_POSTING_REVIEW: "admin",
  LOW_CONFIDENCE_REPORT: "admin",
  TICKET_DRAFT_READY: "admin",
};
