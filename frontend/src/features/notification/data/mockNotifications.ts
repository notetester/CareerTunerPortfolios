import type { Notification } from "../types/notification";

export const mockNotifications: Notification[] = [
  { id: 1, category: "ai_analysis", type: "PROFILE_ANALYZED", icon: "UserSearch", title: "이력서 분석이 완료됐어요", message: "백엔드 개발자 직무 기준 분석 리포트가 준비되었습니다. 직무 적합도 82점이에요.", createdAt: "2026-06-10T13:50:00", isRead: false, link: "/analysis/profile/1" },
  { id: 2, category: "interview", type: "INTERVIEW_REPORT_READY", icon: "ClipboardList", title: "모의면접 리포트가 생성됐어요", message: "5월 30일 진행한 모의면접의 답변 분석 리포트를 확인해보세요.", createdAt: "2026-06-10T13:40:00", isRead: false, link: "/interview/report/460" },
  { id: 3, category: "correction", type: "CORRECTION_COMPLETE", icon: "SpellCheck", title: "자기소개서 첨삭이 완료됐어요", message: "지원동기 문항에서 보완하면 좋을 제안 3건을 찾았어요.", createdAt: "2026-06-10T13:18:00", isRead: false, link: "/correction/result/55" },
  { id: 4, category: "notice", type: "TICKET_ANSWERED", icon: "MessageSquareReply", title: "1:1 문의에 답변이 등록됐어요", message: "'환불 관련 문의'에 운영팀이 답변을 남겼습니다.", createdAt: "2026-06-10T12:50:00", isRead: false, link: "/support/tickets/24", actorName: "운영팀", actorId: 4 },
  { id: 5, category: "interview", type: "QUESTIONS_GENERATED", icon: "ListChecks", title: "예상 질문이 생성됐어요", message: "프론트엔드 직무 기준 면접 예상 질문 12개가 준비됐어요.", createdAt: "2026-06-10T10:50:00", isRead: false, link: "/interview/questions/88" },
  { id: 6, category: "billing", type: "CREDIT_LOW", icon: "AlertTriangle", title: "무료 분석 1회 남았어요", message: "이번 달 무료 분석이 곧 소진됩니다.", createdAt: "2026-06-10T08:50:00", isRead: true, link: "/billing?tab=credits" },
  { id: 7, category: "notice", type: "NOTICE", icon: "Megaphone", title: "[점검] 서비스 정기 점검 안내", message: "6월 12일(목) 02:00~04:00 서비스 점검이 예정되어 있습니다.", createdAt: "2026-06-09T14:00:00", isRead: true, link: "/support/notices/10" },
  { id: 8, category: "ai_analysis", type: "JOB_ANALYSIS_COMPLETE", icon: "Briefcase", title: "직무 분석이 완료됐어요", message: "프론트엔드 개발자 직무 기준 분석 리포트가 준비되었습니다.", createdAt: "2026-06-09T10:00:00", isRead: true, link: "/analysis/job/903" },
  { id: 9, category: "community", type: "COMMENT", icon: "MessageCircle", title: "내 글에 새 댓글이 달렸어요", message: "'이직 6개월 회고' 글에 새 댓글이 달렸습니다.", createdAt: "2026-06-08T18:00:00", isRead: true, link: "/community/posts/1100", actorName: "김민수", actorId: 88 },
  { id: 10, category: "notice", type: "NOTICE", icon: "Megaphone", title: "새 기능: 음성 답변 분석", message: "이제 모의면접에서 음성 답변을 분석받을 수 있어요.", createdAt: "2026-06-07T09:00:00", isRead: true, link: "/support/notices/9" },
];
