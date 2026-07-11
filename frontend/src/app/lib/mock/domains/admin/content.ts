// 데모/목: 관리자 콘텐츠 운영 도메인.
// 커뮤니티 신고/모더레이션, 공지, FAQ, 1:1 문의(티켓), 알림 발송, 가이드라인을 채운다.
// 모든 응답 타입은 각 admin api 모듈에서 그대로 export 되므로 그 타입으로 직접 검증한다(transform 없음).
import type { MockRoute, MockContext } from "../../registry";
import { iso } from "../../registry";
import type {
  AdminReportListResponse,
  AdminReportDetailResponse,
} from "@/admin/features/community/types/adminReport";
import type { AdminGuidelineResponse } from "@/admin/features/community/api/adminGuidelineApi";
import type {
  ModerationItem,
  ModerationDetail,
  ModerationPage,
  ModerationStats,
  ModerationReviewAction,
  ModerationReviewQueuePage,
} from "@/admin/features/moderation/types/moderation";
import type {
  ModerationSettingData,
  ModerationTestResult,
} from "@/admin/features/moderation/api/moderationApi";
import type { AdminNoticeResponse } from "@/admin/features/notices/types/adminNotice";
import type { AdminFaqResponse } from "@/admin/features/faqs/types/adminFaq";
import type {
  AdminTicketListResponse,
  AdminTicketDetailResponse,
  AdminTicketMessage,
} from "@/admin/features/support-tickets/types/adminTicket";
import type { AdminNotificationRow } from "@/admin/features/notifications/api/adminNotificationApi";

// ──────────────────────────────────────────────────────────────
// 커뮤니티 신고 (community reports)
//   time 은 백엔드에서 "분 단위 숫자 문자열"로 온다(TIMESTAMPDIFF MINUTE). 화면 api 가 분→상대시간 변환.
// ──────────────────────────────────────────────────────────────
const reportDetails: AdminReportDetailResponse[] = [
  {
    id: 7001,
    reason: "욕설/비방",
    type: "게시글",
    cnt: 4,
    title: "이 회사 면접 진짜 최악이네요",
    excerpt: "면접관 태도가 너무 무례했고, 다른 지원자들 앞에서 대놓고 무시하는…",
    cat: "면접후기",
    catKey: "interview",
    author: "익명_3821",
    time: "18",
    status: "pending",
    action: null,
    reasons: [
      { l: "욕설/비방", n: 3 },
      { l: "허위 정보", n: 1 },
    ],
    aiOpinion: {
      status: "DONE",
      toxic: true,
      category: "비방",
      confidence: 0.82,
      model: "moderation-v2",
      completedAt: iso(0),
      errorMessage: null,
      elapsedMs: 640,
    },
  },
  {
    id: 7002,
    reason: "광고/스팸",
    type: "댓글",
    cnt: 6,
    title: "취업 컨설팅 DM 주세요 100% 합격",
    excerpt: "수강생 전원 대기업 합격! 지금 신청하면 50% 할인, 카톡 아이디는…",
    cat: "자유게시판",
    catKey: "free",
    author: "promo_king",
    time: "95",
    status: "pending",
    action: null,
    reasons: [
      { l: "광고/스팸", n: 5 },
      { l: "도배", n: 1 },
    ],
    aiOpinion: {
      status: "DONE",
      toxic: true,
      category: "스팸",
      confidence: 0.95,
      model: "moderation-v2",
      completedAt: iso(0),
      errorMessage: null,
      elapsedMs: 410,
    },
  },
  {
    id: 7003,
    reason: "개인정보 노출",
    type: "게시글",
    cnt: 2,
    title: "토스 최종 합격 후기 (연봉/처우 공유)",
    excerpt: "담당 인사팀 OO님 연락처랑 제 합격 메일 캡처 같이 올려요. 참고하…",
    cat: "합격후기",
    catKey: "pass",
    author: "김데모",
    time: "320",
    status: "pending",
    action: null,
    reasons: [{ l: "개인정보 노출", n: 2 }],
    aiOpinion: {
      status: "DONE",
      toxic: false,
      category: null,
      confidence: 0.31,
      model: "moderation-v2",
      completedAt: iso(0),
      errorMessage: null,
      elapsedMs: 520,
    },
  },
  {
    id: 7004,
    reason: "허위 정보",
    type: "댓글",
    cnt: 1,
    title: "네이버는 코테 없이 면접만 본대요",
    excerpt: "제가 아는 사람이 그러는데 올해부터 코딩테스트 폐지됐다고…",
    cat: "질문답변",
    catKey: "qna",
    author: "익명_5510",
    time: "1450",
    status: "resolved",
    action: "HIDDEN",
    reasons: [{ l: "허위 정보", n: 1 }],
    aiOpinion: {
      status: "DONE",
      toxic: false,
      category: null,
      confidence: 0.12,
      model: "moderation-v2",
      completedAt: iso(1),
      errorMessage: null,
      elapsedMs: 380,
    },
  },
];

function reportList(r: AdminReportDetailResponse): AdminReportListResponse {
  const { reasons: _reasons, aiOpinion: _ai, ...rest } = r;
  return rest;
}

// ──────────────────────────────────────────────────────────────
// 커뮤니티 가이드라인 (guidelines)
// ──────────────────────────────────────────────────────────────
const guidelines: AdminGuidelineResponse[] = [
  {
    id: 301,
    versionLabel: "v1.4",
    summary: "AI 면접 후기 작성 시 회사 실명 노출 기준 명확화",
    lede: "건강한 취업 정보 공유를 위한 커뮤니티 운영 원칙입니다.",
    oksJson: JSON.stringify([
      "구체적인 면접 경험과 준비 과정 공유",
      "출처가 분명한 채용 정보 인용",
      "서로의 합격을 응원하는 댓글",
    ]),
    nosJson: JSON.stringify([
      "특정 면접관·직원에 대한 인신공격",
      "연락처·이메일 등 개인정보 노출",
      "취업 컨설팅·강의 광고 및 도배",
    ]),
    rulesJson: JSON.stringify([
      { t: "비방 게시물", s: 1, b: "1차 경고 후 블라인드, 누적 시 정지" },
      { t: "광고/스팸", s: 2, b: "즉시 삭제 및 7일 글쓰기 제한" },
      { t: "개인정보 노출", s: 3, b: "즉시 삭제 및 30일 정지" },
    ]),
    paramsJson: JSON.stringify({ blind: 3, sla: 24, expire: 30, s1: 7, s2: 30, appeal: 7 }),
    status: "PUBLISHED",
    enforceType: "IMMEDIATE",
    scheduledAt: null,
    publishedAt: iso(12),
    createdAt: iso(20),
    updatedAt: iso(12),
  },
  {
    id: 302,
    versionLabel: "v1.5",
    summary: "신고 누적 임계값 하향(4→3) 및 자동 블라인드 도입",
    lede: "운영 부담을 줄이기 위해 자동 모더레이션 기준을 강화합니다.",
    oksJson: JSON.stringify(["정상적인 면접 후기", "정중한 피드백"]),
    nosJson: JSON.stringify(["반복 신고된 비방", "AI 판정 유해 게시물"]),
    rulesJson: JSON.stringify([
      { t: "신고 3회 누적", s: 1, b: "자동 블라인드 후 검토 대기" },
    ]),
    paramsJson: JSON.stringify({ blind: 3, sla: 12, expire: 30, s1: 7, s2: 30, appeal: 7 }),
    status: "DRAFT",
    enforceType: "SCHEDULED",
    scheduledAt: iso(-5),
    publishedAt: null,
    createdAt: iso(3),
    updatedAt: iso(1),
  },
];

// ──────────────────────────────────────────────────────────────
// AI 모더레이션 (ai/moderation)
// ──────────────────────────────────────────────────────────────
const moderationDetails: ModerationDetail[] = [
  {
    postId: 8101,
    title: "이 회사 면접 진짜 최악이네요",
    authorName: "익명_3821",
    category: "면접후기",
    status: "HIDDEN",
    toxic: true,
    aiCategory: "비방",
    confidence: 0.82,
    attemptCount: 1,
    createdAt: iso(1),
    moderatedAt: iso(0),
    content:
      "면접관 태도가 너무 무례했고, 다른 지원자들 앞에서 대놓고 무시하는 발언을 했습니다. 솔직히 다시는 지원 안 합니다.",
    model: "moderation-v2",
  },
  {
    postId: 8102,
    title: "취업 컨설팅 DM 주세요 100% 합격",
    authorName: "promo_king",
    category: "자유게시판",
    status: "DELETED",
    toxic: true,
    aiCategory: "스팸",
    confidence: 0.95,
    attemptCount: 2,
    createdAt: iso(2),
    moderatedAt: iso(1),
    content: "수강생 전원 대기업 합격! 지금 신청하면 50% 할인, 카톡 아이디는 프로필 참고.",
    model: "moderation-v2",
  },
  {
    postId: 8103,
    title: "프론트엔드 포트폴리오 피드백 부탁드려요",
    authorName: "김데모",
    category: "질문답변",
    status: "PUBLISHED",
    toxic: false,
    aiCategory: null,
    confidence: 0.08,
    attemptCount: 1,
    createdAt: iso(0),
    moderatedAt: null,
    content: "React + TypeScript로 만든 포트폴리오인데, 구조와 상태관리 부분 피드백 받고 싶습니다.",
    model: "moderation-v2",
  },
  {
    postId: 8104,
    title: "토스 합격 후기 (개인정보 일부 포함)",
    authorName: "익명_5510",
    category: "합격후기",
    status: "PUBLISHED",
    toxic: true,
    aiCategory: "개인정보",
    confidence: 0.64,
    attemptCount: 1,
    createdAt: iso(0),
    moderatedAt: iso(0),
    content: "최종 합격 메일 캡처랑 인사담당자 연락처 같이 올립니다. 참고하세요.",
    model: "moderation-v2",
  },
];

function moderationItem(d: ModerationDetail): ModerationItem {
  const { content: _c, model: _m, ...rest } = d;
  return rest;
}

const moderationSettings: ModerationSettingData = {
  strictness: "NORMAL",
  hideThreshold: 0.8,
  sanctionThreshold: 3,
  blockDays: 7,
  reportBlurThreshold: 3,
  postRateWindowSeconds: 60,
  postRateMax: 10,
  commentRateWindowSeconds: 60,
  commentRateMax: 20,
  inquiryRateWindowSeconds: 600,
  inquiryRateMax: 5,
  updatedAt: iso(4),
};

const moderationReviewActions = new Map<number, ModerationReviewAction>();

// ──────────────────────────────────────────────────────────────
// 공지사항 (notices)
// ──────────────────────────────────────────────────────────────
const notices: AdminNoticeResponse[] = [
  {
    id: 401,
    title: "[중요] 개인정보처리방침 개정 안내 (v2.3 시행)",
    content:
      "개인정보 위탁 업체 변경 사항을 반영하여 개인정보처리방침이 개정되었습니다. 시행일: 2026년 5월 20일.",
    category: "정책",
    status: "PUBLISHED",
    pinned: true,
    thumbnailUrl: null,
    viewCount: 1842,
    publishedAt: iso(29),
    createdAt: iso(33),
    updatedAt: iso(29),
  },
  {
    id: 402,
    title: "AI 음성 면접 기능 정식 오픈",
    content:
      "프로 플랜 이상에서 사용 가능했던 음성 면접 기능이 정식 출시되었습니다. 실시간 음성 평가와 리포트를 확인해 보세요.",
    category: "업데이트",
    status: "PUBLISHED",
    pinned: false,
    thumbnailUrl: "https://picsum.photos/seed/notice402/640/360",
    viewCount: 967,
    publishedAt: iso(9),
    createdAt: iso(10),
    updatedAt: iso(9),
  },
  {
    id: 403,
    title: "정기 서버 점검 안내 (6/22 02:00~04:00)",
    content: "서비스 안정화를 위한 정기 점검이 예정되어 있습니다. 점검 시간 동안 일부 기능 이용이 제한됩니다.",
    category: "점검",
    status: "SCHEDULED",
    pinned: false,
    thumbnailUrl: null,
    viewCount: 0,
    publishedAt: null,
    createdAt: iso(2),
    updatedAt: iso(2),
  },
  {
    id: 404,
    title: "이용약관 v2.4 개정 예고 (작성 중)",
    content: "AI 분석 결과 책임 조항 신설 관련 약관 개정을 준비하고 있습니다.",
    category: "정책",
    status: "DRAFT",
    pinned: false,
    thumbnailUrl: null,
    viewCount: 0,
    publishedAt: null,
    createdAt: iso(1),
    updatedAt: iso(0),
  },
];

// ──────────────────────────────────────────────────────────────
// FAQ
// ──────────────────────────────────────────────────────────────
const faqs: AdminFaqResponse[] = [
  {
    id: 501,
    category: "general",
    question: "커리어튜너는 어떤 서비스인가요?",
    answer: "채용공고에 맞춰 스펙과 면접 답변을 조정해 주는 AI 취업 전략 플랫폼입니다.",
    published: true,
    sortOrder: 1,
    createdAt: iso(40),
    updatedAt: iso(15),
  },
  {
    id: 502,
    category: "payment",
    question: "크레딧은 어떻게 충전하나요?",
    answer: "마이페이지 > 크레딧 메뉴에서 충전 패키지를 선택해 결제하면 즉시 잔액에 반영됩니다.",
    published: true,
    sortOrder: 2,
    createdAt: iso(38),
    updatedAt: iso(12),
  },
  {
    id: 503,
    category: "ai_feature",
    question: "AI 분석 결과는 얼마나 정확한가요?",
    answer: "분석 결과는 취업 준비를 돕기 위한 참고 자료이며, 채용 결과를 보장하지는 않습니다.",
    published: true,
    sortOrder: 3,
    createdAt: iso(30),
    updatedAt: iso(8),
  },
  {
    id: 504,
    category: "interview",
    question: "음성 면접은 어떤 플랜에서 쓸 수 있나요?",
    answer: "프로 플랜 이상에서 음성 면접과 실시간 평가 리포트를 이용할 수 있습니다.",
    published: false,
    sortOrder: 4,
    createdAt: iso(6),
    updatedAt: iso(1),
  },
];

// ──────────────────────────────────────────────────────────────
// 1:1 문의 (tickets)
// ──────────────────────────────────────────────────────────────
const ticketDetails: AdminTicketDetailResponse[] = [
  {
    id: 601,
    category: "결제",
    subject: "프로 결제 직후 AI 크레딧이 반영되지 않아요",
    memberName: "김데모",
    createdAt: iso(0),
    status: "pending",
    priority: true,
    plan: "프로",
    joinedAt: "2026.03.14",
    memo: "결제 로그 정상 확인됨. 크레딧 수동 반영 검토.",
    msgs: [
      {
        id: 60101,
        who: "user",
        name: "김데모",
        time: iso(0),
        text: "방금 프로 플랜 결제했는데 크레딧이 안 보여요. 결제는 카드에서 빠져나갔습니다.",
        internal: false,
      },
      {
        id: 60102,
        who: "admin",
        name: "운영팀",
        time: iso(0),
        text: "결제는 정상 처리되었습니다. 크레딧 반영 지연 여부 확인 중이니 잠시만 기다려 주세요.",
        internal: false,
      },
      {
        id: 60103,
        who: "admin",
        name: "운영팀",
        time: iso(0),
        text: "[내부] PG 결제 로그 정상. 크레딧 배치 지연으로 보임 → 수동 충전 처리.",
        internal: true,
      },
    ],
  },
  {
    id: 602,
    category: "계정",
    subject: "비밀번호 재설정 메일이 오지 않습니다",
    memberName: "이서연",
    createdAt: iso(1),
    status: "progress",
    priority: false,
    plan: "베이직",
    joinedAt: "2026.01.08",
    memo: "",
    msgs: [
      {
        id: 60201,
        who: "user",
        name: "이서연",
        time: iso(1),
        text: "비밀번호 찾기를 눌렀는데 메일이 안 와요. 스팸함도 확인했습니다.",
        internal: false,
      },
    ],
  },
  {
    id: 603,
    category: "기능",
    subject: "면접 리포트 PDF 내보내기가 안 돼요",
    memberName: "박준호",
    createdAt: iso(3),
    status: "answered",
    priority: false,
    plan: "무료",
    joinedAt: "2025.12.20",
    memo: "브라우저 캐시 안내로 해결됨.",
    msgs: [
      {
        id: 60301,
        who: "user",
        name: "박준호",
        time: iso(3),
        text: "리포트 PDF 다운로드 버튼을 눌러도 반응이 없습니다.",
        internal: false,
      },
      {
        id: 60302,
        who: "admin",
        name: "운영팀",
        time: iso(2),
        text: "브라우저 캐시 삭제 후 다시 시도 부탁드립니다. 해결되지 않으면 다시 알려주세요.",
        internal: false,
      },
    ],
  },
];

function ticketList(t: AdminTicketDetailResponse): AdminTicketListResponse {
  return {
    id: t.id,
    category: t.category,
    subject: t.subject,
    memberName: t.memberName,
    createdAt: t.createdAt,
    status: t.status,
    priority: t.priority,
    plan: t.plan,
    joinedAt: t.joinedAt,
  };
}

// ──────────────────────────────────────────────────────────────
// 알림 발송 내역 (notifications)
// ──────────────────────────────────────────────────────────────
const notifications: AdminNotificationRow[] = [
  {
    id: 701,
    userId: 9001,
    recipientName: "김데모",
    recipientEmail: "demo@careertuner.dev",
    type: "PAYMENT",
    title: "프로 플랜 결제가 완료되었습니다",
    message: "프로 플랜 구독이 시작되었습니다. 음성 면접 기능을 이용해 보세요.",
    read: true,
    readAt: iso(11),
    createdAt: iso(12),
  },
  {
    id: 702,
    userId: 9002,
    recipientName: "이서연",
    recipientEmail: "seoyeon@example.com",
    type: "NOTICE",
    title: "개인정보처리방침 개정 안내",
    message: "개정된 개인정보처리방침이 2026년 5월 20일부터 시행됩니다.",
    read: false,
    readAt: null,
    createdAt: iso(29),
  },
  {
    id: 703,
    userId: 9001,
    recipientName: "김데모",
    recipientEmail: "demo@careertuner.dev",
    type: "ANALYSIS",
    title: "토스 적합도 분석이 완료되었습니다",
    message: "지원 건 '토스 - 프론트엔드'의 적합도 분석 결과가 준비되었습니다.",
    read: false,
    readAt: null,
    createdAt: iso(2),
  },
  {
    id: 704,
    userId: 9003,
    recipientName: "박준호",
    recipientEmail: "junho@example.com",
    type: "SUPPORT",
    title: "문의하신 내용에 답변이 등록되었습니다",
    message: "면접 리포트 PDF 내보내기 문의에 대한 답변을 확인해 주세요.",
    read: true,
    readAt: iso(2),
    createdAt: iso(2),
  },
];

// ──────────────────────────────────────────────────────────────
// 요청 본문 타입(부분)
// ──────────────────────────────────────────────────────────────
interface ReportActionBody {
  action?: "HIDDEN" | "DELETED" | "DISMISSED" | "BLOCK_AUTHOR" | "DELETE_AND_BLOCK";
}
interface TicketPatchBody {
  status?: string;
  priority?: string;
}
interface TicketReplyBody {
  content?: string;
  internal?: boolean;
}
interface ModerationSettingBody {
  strictness?: string;
  hideThreshold?: number;
  sanctionThreshold?: number;
  blockDays?: number;
  reportBlurThreshold?: number;
  postRateWindowSeconds?: number;
  postRateMax?: number;
  commentRateWindowSeconds?: number;
  commentRateMax?: number;
  inquiryRateWindowSeconds?: number;
  inquiryRateMax?: number;
}
interface ModerationTestBody {
  title?: string;
  content?: string;
}
interface ModerationReviewDecisionBody {
  action?: ModerationReviewAction;
}

export const adminContentRoutes: MockRoute[] = [
  // ── 커뮤니티 신고 ──
  {
    method: "GET",
    pattern: /^\/admin\/community\/reports$/,
    handler: ({ query }: MockContext) => {
      const status = query.get("status");
      const filtered = status
        ? reportDetails.filter((r) => r.status === status)
        : reportDetails;
      return filtered.map(reportList);
    },
  },
  {
    method: "GET",
    pattern: /^\/admin\/community\/reports\/(\d+)$/,
    handler: ({ params }: MockContext) => {
      const id = Number(params[0]);
      return reportDetails.find((r) => r.id === id) ?? reportDetails[0];
    },
  },
  {
    method: "POST",
    pattern: /^\/admin\/community\/reports\/(\d+)\/action$/,
    handler: ({ params, body }: MockContext) => {
      const id = Number(params[0]);
      const report = reportDetails.find((r) => r.id === id) ?? reportDetails[0];
      const action = (body as ReportActionBody | undefined)?.action;
      report.status = "resolved";
      report.action = action === "DISMISSED" ? "NONE" : action ?? "HIDDEN";
      return report;
    },
  },
  {
    // 종결(기각/취소) 신고 재활성화 — 대기(PENDING) 복원. 백엔드 /reactivate 계약과 동일.
    method: "POST",
    pattern: /^\/admin\/community\/reports\/(\d+)\/reactivate$/,
    handler: ({ params }: MockContext) => {
      const id = Number(params[0]);
      const report = reportDetails.find((r) => r.id === id) ?? reportDetails[0];
      report.status = "pending";
      report.action = null;
      return report;
    },
  },
  {
    method: "POST",
    pattern: /^\/admin\/community\/reports\/(\d+)\/reclassify$/,
    handler: ({ params }: MockContext) => {
      const id = Number(params[0]);
      const report = reportDetails.find((r) => r.id === id) ?? reportDetails[0];
      if (report.aiOpinion) {
        report.aiOpinion = {
          ...report.aiOpinion,
          status: "DONE",
          completedAt: iso(0),
          confidence: Math.min(0.99, (report.aiOpinion.confidence ?? 0.5) + 0.05),
        };
      }
      return report;
    },
  },

  // ── 커뮤니티 가이드라인 (구체 경로 우선) ──
  {
    method: "GET",
    pattern: /^\/admin\/guidelines\/published$/,
    handler: () => guidelines.find((g) => g.status === "PUBLISHED") ?? null,
  },
  { method: "GET", pattern: /^\/admin\/guidelines$/, handler: () => [...guidelines] },
  {
    method: "POST",
    pattern: /^\/admin\/guidelines$/,
    handler: ({ body }: MockContext) => {
      const req = (body ?? {}) as Partial<AdminGuidelineResponse> & {
        oks?: string[];
        nos?: string[];
      };
      const created: AdminGuidelineResponse = {
        id: 300 + guidelines.length + 1,
        versionLabel: req.versionLabel ?? "v1.6",
        summary: req.summary ?? "",
        lede: req.lede ?? "",
        oksJson: JSON.stringify(req.oks ?? []),
        nosJson: JSON.stringify(req.nos ?? []),
        rulesJson: "[]",
        paramsJson: JSON.stringify({ blind: 3, sla: 24, expire: 30, s1: 7, s2: 30, appeal: 7 }),
        status: "DRAFT",
        enforceType: req.enforceType ?? "IMMEDIATE",
        scheduledAt: req.scheduledAt ?? null,
        publishedAt: null,
        createdAt: iso(0),
        updatedAt: iso(0),
      };
      guidelines.unshift(created);
      return created;
    },
  },
  {
    method: "GET",
    pattern: /^\/admin\/guidelines\/(\d+)$/,
    handler: ({ params }: MockContext) => {
      const id = Number(params[0]);
      return guidelines.find((g) => g.id === id) ?? guidelines[0];
    },
  },
  {
    method: "PUT",
    pattern: /^\/admin\/guidelines\/(\d+)$/,
    handler: ({ params, body }: MockContext) => {
      const id = Number(params[0]);
      const target = guidelines.find((g) => g.id === id) ?? guidelines[0];
      const req = (body ?? {}) as Partial<AdminGuidelineResponse> & {
        oks?: string[];
        nos?: string[];
      };
      if (req.versionLabel) target.versionLabel = req.versionLabel;
      if (req.summary !== undefined) target.summary = req.summary;
      if (req.lede !== undefined) target.lede = req.lede;
      if (req.oks) target.oksJson = JSON.stringify(req.oks);
      if (req.nos) target.nosJson = JSON.stringify(req.nos);
      target.updatedAt = iso(0);
      return target;
    },
  },
  {
    method: "POST",
    pattern: /^\/admin\/guidelines\/(\d+)\/publish$/,
    handler: ({ params }: MockContext) => {
      const id = Number(params[0]);
      const target = guidelines.find((g) => g.id === id) ?? guidelines[0];
      guidelines.forEach((g) => {
        if (g.status === "PUBLISHED") g.status = "ARCHIVED";
      });
      target.status = "PUBLISHED";
      target.publishedAt = iso(0);
      target.updatedAt = iso(0);
      return target;
    },
  },
  {
    method: "DELETE",
    pattern: /^\/admin\/guidelines\/(\d+)$/,
    handler: () => null,
  },

  // ── AI 모더레이션 (구체 경로 우선) ──
  {
    method: "GET",
    pattern: /^\/admin\/ai\/moderation\/stats$/,
    handler: (): ModerationStats => ({
      categories: [
        { category: "비방", count: 14 },
        { category: "스팸", count: 9 },
        { category: "개인정보", count: 5 },
        { category: "음란성", count: 2 },
      ],
      total: 30,
    }),
  },
  {
    method: "GET",
    pattern: /^\/admin\/ai\/moderation\/settings$/,
    handler: () => ({ ...moderationSettings }),
  },
  {
    method: "PATCH",
    pattern: /^\/admin\/ai\/moderation\/settings$/,
    handler: ({ body }: MockContext) => {
      const req = (body ?? {}) as ModerationSettingBody;
      if (req.strictness) moderationSettings.strictness = req.strictness;
      if (req.hideThreshold !== undefined) moderationSettings.hideThreshold = req.hideThreshold;
      if (req.sanctionThreshold !== undefined) moderationSettings.sanctionThreshold = req.sanctionThreshold;
      if (req.blockDays !== undefined) moderationSettings.blockDays = req.blockDays;
      if (req.reportBlurThreshold !== undefined) moderationSettings.reportBlurThreshold = req.reportBlurThreshold;
      if (req.postRateWindowSeconds !== undefined) moderationSettings.postRateWindowSeconds = req.postRateWindowSeconds;
      if (req.postRateMax !== undefined) moderationSettings.postRateMax = req.postRateMax;
      if (req.commentRateWindowSeconds !== undefined) moderationSettings.commentRateWindowSeconds = req.commentRateWindowSeconds;
      if (req.commentRateMax !== undefined) moderationSettings.commentRateMax = req.commentRateMax;
      if (req.inquiryRateWindowSeconds !== undefined) moderationSettings.inquiryRateWindowSeconds = req.inquiryRateWindowSeconds;
      if (req.inquiryRateMax !== undefined) moderationSettings.inquiryRateMax = req.inquiryRateMax;
      moderationSettings.updatedAt = iso(0);
      return { ...moderationSettings };
    },
  },
  {
    method: "POST",
    pattern: /^\/admin\/ai\/moderation-test$/,
    handler: ({ body }: MockContext): ModerationTestResult => {
      const text = `${(body as ModerationTestBody | undefined)?.title ?? ""} ${
        (body as ModerationTestBody | undefined)?.content ?? ""
      }`;
      const toxic = /바보|광고|할인|연락처|카톡|욕설/.test(text);
      return {
        toxic,
        category: toxic ? "비방/스팸" : "정상",
        confidence: toxic ? 0.88 : 0.07,
        elapsedMs: 430,
      };
    },
  },
  {
    method: "GET",
    pattern: /^\/admin\/ai\/moderation\/review-queue$/,
    handler: ({ query }: MockContext): ModerationReviewQueuePage => {
      const page = Math.max(1, Number(query.get("page") ?? 1) || 1);
      const size = Math.max(1, Number(query.get("size") ?? 5) || 5);
      const all = moderationDetails
        .filter((item) => item.status === "PUBLISHED"
          && item.toxic
          && item.confidence < moderationSettings.hideThreshold
          && !moderationReviewActions.has(item.postId))
        .map((item) => ({
          postId: item.postId,
          title: item.title,
          contentPreview: item.content.replace(/<[^>]+>/g, "").slice(0, 240),
          authorName: item.authorName,
          category: item.category,
          aiCategory: item.aiCategory,
          confidence: item.confidence,
          createdAt: item.createdAt,
          moderatedAt: item.moderatedAt ?? iso(0),
        }));
      const offset = (page - 1) * size;
      return {
        items: all.slice(offset, offset + size),
        total: all.length,
        page,
        size,
        hasNext: offset + size < all.length,
      };
    },
  },
  {
    method: "PATCH",
    pattern: /^\/admin\/ai\/moderation\/review-queue\/(\d+)$/,
    handler: ({ params, body }: MockContext) => {
      const postId = Number(params[0]);
      const action = (body as ModerationReviewDecisionBody | undefined)?.action;
      if (action !== "HIDE" && action !== "KEEP") throw new Error("action은 HIDE 또는 KEEP이어야 합니다.");
      const existing = moderationReviewActions.get(postId);
      if (existing === action) return null;
      if (existing) throw new Error("이미 다른 검토 결정이 완료됐습니다.");
      const target = moderationDetails.find((item) => item.postId === postId);
      if (!target || target.status !== "PUBLISHED" || !target.toxic
        || target.confidence >= moderationSettings.hideThreshold) {
        throw new Error("더 이상 검토 대기 조건을 만족하지 않는 게시글입니다.");
      }
      moderationReviewActions.set(postId, action);
      if (action === "HIDE") target.status = "HIDDEN";
      return null;
    },
  },
  {
    method: "GET",
    pattern: /^\/admin\/ai\/moderation$/,
    handler: ({ query }: MockContext): ModerationPage => {
      const status = query.get("status");
      const toxicRaw = query.get("toxic");
      const page = Number(query.get("page") ?? 1) || 1;
      const size = Number(query.get("size") ?? 20) || 20;
      let list = moderationDetails.map(moderationItem);
      if (status) list = list.filter((m) => m.status === status);
      if (toxicRaw !== null) list = list.filter((m) => m.toxic === (toxicRaw === "true"));
      return {
        items: list,
        total: list.length,
        page,
        size,
        hasNext: false,
      };
    },
  },
  {
    method: "GET",
    pattern: /^\/admin\/ai\/moderation\/(\d+)$/,
    handler: ({ params }: MockContext) => {
      const id = Number(params[0]);
      return moderationDetails.find((m) => m.postId === id) ?? moderationDetails[0];
    },
  },
  {
    method: "POST",
    pattern: /^\/admin\/ai\/moderation\/(\d+)\/restore$/,
    handler: ({ params }: MockContext) => {
      const id = Number(params[0]);
      const target = moderationDetails.find((m) => m.postId === id);
      if (target) {
        target.status = "PUBLISHED";
        target.moderatedAt = iso(0);
      }
      return null;
    },
  },
  {
    method: "POST",
    pattern: /^\/admin\/ai\/moderation\/(\d+)\/delete$/,
    handler: ({ params }: MockContext) => {
      const id = Number(params[0]);
      const target = moderationDetails.find((m) => m.postId === id);
      if (target) {
        target.status = "DELETED";
        target.moderatedAt = iso(0);
      }
      return null;
    },
  },

  // ── 공지사항 ──
  { method: "GET", pattern: /^\/admin\/notices$/, handler: () => [...notices] },
  {
    method: "POST",
    pattern: /^\/admin\/notices$/,
    handler: ({ body }: MockContext) => {
      const req = (body ?? {}) as {
        title?: string;
        content?: string;
        category?: string | null;
        status?: string;
        isPinned?: boolean;
        thumbnailUrl?: string | null;
      };
      const status = req.status ?? "DRAFT";
      const created: AdminNoticeResponse = {
        id: 400 + notices.length + 1,
        title: req.title ?? "(제목 없음)",
        content: req.content ?? "",
        category: req.category ?? null,
        status,
        pinned: req.isPinned ?? false,
        thumbnailUrl: req.thumbnailUrl ?? null,
        viewCount: 0,
        publishedAt: status === "PUBLISHED" ? iso(0) : null,
        createdAt: iso(0),
        updatedAt: iso(0),
      };
      notices.unshift(created);
      return created;
    },
  },
  {
    method: "PUT",
    pattern: /^\/admin\/notices\/(\d+)$/,
    handler: ({ params, body }: MockContext) => {
      const id = Number(params[0]);
      const target = notices.find((n) => n.id === id) ?? notices[0];
      const req = (body ?? {}) as {
        title?: string;
        content?: string;
        status?: string;
        isPinned?: boolean;
        thumbnailUrl?: string | null;
      };
      if (req.title !== undefined) target.title = req.title;
      if (req.content !== undefined) target.content = req.content;
      if (req.status !== undefined) {
        target.status = req.status;
        if (req.status === "PUBLISHED" && !target.publishedAt) target.publishedAt = iso(0);
      }
      if (req.isPinned !== undefined) target.pinned = req.isPinned;
      if (req.thumbnailUrl !== undefined) target.thumbnailUrl = req.thumbnailUrl;
      target.updatedAt = iso(0);
      return target;
    },
  },
  {
    method: "DELETE",
    pattern: /^\/admin\/notices\/(\d+)$/,
    handler: () => null,
  },

  // ── FAQ ──
  {
    method: "POST",
    pattern: /^\/admin\/faq\/embed-all$/,
    handler: () => ({ embeddedCount: faqs.filter((f) => f.published).length }),
  },
  { method: "GET", pattern: /^\/admin\/faq$/, handler: () => [...faqs] },
  {
    method: "POST",
    pattern: /^\/admin\/faq$/,
    handler: ({ body }: MockContext) => {
      const req = (body ?? {}) as {
        category?: string;
        question?: string;
        answer?: string;
        isPublished?: boolean;
        sortOrder?: number;
      };
      const created: AdminFaqResponse = {
        id: 500 + faqs.length + 1,
        category: req.category ?? "general",
        question: req.question ?? "",
        answer: req.answer ?? "",
        published: req.isPublished ?? false,
        sortOrder: req.sortOrder ?? 0,
        createdAt: iso(0),
        updatedAt: iso(0),
      };
      faqs.unshift(created);
      return created;
    },
  },
  {
    method: "PUT",
    pattern: /^\/admin\/faq\/(\d+)$/,
    handler: ({ params, body }: MockContext) => {
      const id = Number(params[0]);
      const target = faqs.find((f) => f.id === id) ?? faqs[0];
      const req = (body ?? {}) as {
        category?: string;
        question?: string;
        answer?: string;
        isPublished?: boolean;
        sortOrder?: number;
      };
      if (req.category !== undefined) target.category = req.category;
      if (req.question !== undefined) target.question = req.question;
      if (req.answer !== undefined) target.answer = req.answer;
      if (req.isPublished !== undefined) target.published = req.isPublished;
      if (req.sortOrder !== undefined) target.sortOrder = req.sortOrder;
      target.updatedAt = iso(0);
      return target;
    },
  },
  {
    method: "DELETE",
    pattern: /^\/admin\/faq\/(\d+)$/,
    handler: () => null,
  },

  // ── 1:1 문의 (tickets) ──
  {
    method: "GET",
    pattern: /^\/admin\/tickets$/,
    handler: ({ query }: MockContext) => {
      const status = query.get("status");
      const filtered = status
        ? ticketDetails.filter((t) => t.status === status)
        : ticketDetails;
      return filtered.map(ticketList);
    },
  },
  {
    method: "GET",
    pattern: /^\/admin\/tickets\/(\d+)$/,
    handler: ({ params }: MockContext) => {
      const id = Number(params[0]);
      return ticketDetails.find((t) => t.id === id) ?? ticketDetails[0];
    },
  },
  {
    method: "PATCH",
    pattern: /^\/admin\/tickets\/(\d+)$/,
    handler: ({ params, body }: MockContext) => {
      const id = Number(params[0]);
      const target = ticketDetails.find((t) => t.id === id) ?? ticketDetails[0];
      const req = (body ?? {}) as TicketPatchBody;
      if (req.status !== undefined) target.status = req.status;
      if (req.priority !== undefined) target.priority = req.priority === "true" || req.priority === "high";
      return target;
    },
  },
  {
    method: "POST",
    pattern: /^\/admin\/tickets\/(\d+)\/reply$/,
    handler: ({ params, body }: MockContext) => {
      const id = Number(params[0]);
      const target = ticketDetails.find((t) => t.id === id) ?? ticketDetails[0];
      const req = (body ?? {}) as TicketReplyBody;
      const msg: AdminTicketMessage = {
        id: target.id * 100 + target.msgs.length + 1,
        who: "admin",
        name: "운영팀",
        time: iso(0),
        text: req.content ?? "",
        internal: req.internal ?? false,
      };
      target.msgs.push(msg);
      if (!msg.internal) target.status = "answered";
      return target;
    },
  },

  // ── 알림 발송 내역 ──
  {
    method: "GET",
    pattern: /^\/admin\/notifications$/,
    handler: ({ query }: MockContext) => {
      const size = Number(query.get("size") ?? 100) || 100;
      return notifications.slice(0, size);
    },
  },
];
