import type { Notification } from "../types/notification";

export const mockNotifications: Notification[] = [
  { id: 1, category: "analysis", type: "ANALYSIS_COMPLETED", icon: "FileSearch", title: "이력서 분석이 완료됐어요", message: "백엔드 개발자 직무 기준 분석 리포트가 준비되었습니다. 직무 적합도 82점이에요.", createdAt: "방금", isRead: false, link: "이력서 분석 리포트" },
  { id: 2, category: "report", type: "REPORT_READY", icon: "ClipboardList", title: "모의면접 리포트가 생성됐어요", message: "5월 30일 진행한 모의면접의 답변 분석 리포트를 확인해보세요.", createdAt: "10분 전", isRead: false, link: "모의면접 리포트" },
  { id: 3, category: "analysis", type: "ANALYSIS_COMPLETED", icon: "Sparkles", title: "자기소개서 분석이 완료됐어요", message: "지원동기 문항에서 보완하면 좋을 제안 3건을 찾았어요.", createdAt: "32분 전", isRead: false, link: "자소서 분석 리포트" },
  { id: 4, category: "reply", type: "TICKET_ANSWERED", icon: "MessageSquareReply", title: "1:1 문의에 답변이 등록됐어요", message: "'환불 관련 문의'에 운영팀이 답변을 남겼습니다.", createdAt: "1시간 전", isRead: false, link: "문의 내역" },
  { id: 5, category: "report", type: "REPORT_READY", icon: "UserCheck", title: "면접 코칭 피드백이 도착했어요", message: "박OO 코치님이 모의면접 상세 피드백을 남겼습니다.", createdAt: "3시간 전", isRead: false, link: "코칭 피드백" },
  { id: 6, category: "billing", type: "PAYMENT", icon: "CreditCard", title: "결제가 완료됐어요", message: "프로 플랜(월 9,900원) 결제가 정상 처리되었습니다.", createdAt: "5시간 전", isRead: true, link: "결제 내역" },
  { id: 7, category: "notice", type: "NOTICE", icon: "Megaphone", title: "[점검] 서비스 정기 점검 안내", message: "6월 12일(목) 02:00~04:00 서비스 점검이 예정되어 있습니다.", createdAt: "어제", isRead: true, link: "공지사항" },
  { id: 8, category: "analysis", type: "ANALYSIS_COMPLETED", icon: "RotateCcw", title: "무료 분석 횟수가 충전됐어요", message: "이번 달 무료 이력서 분석 3회가 다시 충전되었습니다.", createdAt: "어제", isRead: true, link: "이력서 분석" },
  { id: 9, category: "billing", type: "PAYMENT", icon: "CalendarClock", title: "결제 예정 안내", message: "프로 플랜이 6월 28일에 자동 갱신될 예정입니다.", createdAt: "2일 전", isRead: true, link: "멤버십 관리" },
  { id: 10, category: "notice", type: "NOTICE", icon: "Rocket", title: "새 기능: 음성 답변 분석", message: "이제 모의면접에서 음성 답변을 분석받을 수 있어요.", createdAt: "3일 전", isRead: true, link: "공지사항" },
];
