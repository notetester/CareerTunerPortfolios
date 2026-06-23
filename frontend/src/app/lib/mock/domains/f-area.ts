// F 영역(커뮤니티/고객센터·FAQ·공지/알림 + 관리자) 데모 데이터.
// 각 핸들러는 해당 api<T>() 가 resolve 하는 "백엔드 응답 shape" 을 그대로 반환한다(프론트 매퍼가 변환).
// dev:mock(VITE_USE_MOCK=true) 에서만 사용 — 실제 백엔드 동작에는 영향 없음(additive).

/* ─────────────────────────── 커뮤니티 ─────────────────────────── */

const A = (id: number, name: string, anon = true) => ({ id, name, isAnonymous: anon });
const S = (v: number, c: number, l: number, b: number) => ({ viewCount: v, commentCount: c, likeCount: l, bookmarkCount: b });

export const demoCommunityPosts = [
  {
    id: 101, category: "INTERVIEW_REVIEW", categoryLabel: "면접후기",
    title: "네이버 백엔드 2차 면접 후기 (시스템 설계 중심)",
    content: "라이브 코딩 1문제 + 시스템 설계 토론으로 진행됐어요. 캐시 무효화 전략을 깊게 물어봤습니다...",
    tags: ["네이버", "백엔드", "시스템설계"], author: A(1, "익명의 라쿤"),
    stats: S(1842, 23, 96, 41), status: "PUBLISHED", createdAt: "2026-06-18T09:20:00",
    companyName: "네이버", jobRole: "백엔드 엔지니어",
    interviewReview: { resultStatus: "PASSED" }, liked: false, bookmarked: true,
  },
  {
    id: 102, category: "JOB_REVIEW", categoryLabel: "취업후기",
    title: "비전공 3년차, 카카오 이직 성공 회고",
    content: "포트폴리오를 직무 기준으로 다시 짜고 면접 스토리를 한 줄로 정리한 게 컸어요...",
    tags: ["카카오", "이직", "비전공"], author: A(2, "이직성공"),
    stats: S(2510, 44, 188, 77), status: "PUBLISHED", createdAt: "2026-06-17T14:05:00",
    companyName: "카카오", jobRole: "프론트엔드", interviewReview: { resultStatus: "PASSED" },
    liked: true, bookmarked: false,
  },
  {
    id: 103, category: "JOB_QUESTION", categoryLabel: "직무질문",
    title: "데이터 직무 지원 시 SQL은 어느 정도까지 준비하나요?",
    content: "윈도우 함수, CTE까지는 보는데 실무에선 더 깊게 물어보는지 궁금합니다.",
    tags: ["데이터분석", "SQL"], author: A(3, "취준생A"),
    stats: S(640, 12, 18, 5), status: "PUBLISHED", createdAt: "2026-06-19T02:30:00",
    liked: false, bookmarked: false,
  },
  {
    id: 104, category: "PASS_STRATEGY", categoryLabel: "합격전략",
    title: "서류 광탈에서 합격률 40%로 — 자소서 구조 공식",
    content: "STAR 변형으로 '상황-내 결정-수치 결과'를 1문단에 압축했더니 통과율이 확 올랐습니다.",
    tags: ["자소서", "서류전형"], author: A(4, "합격공유"),
    stats: S(3120, 51, 240, 132), status: "PUBLISHED", createdAt: "2026-06-16T11:40:00",
    liked: true, bookmarked: true,
  },
  {
    id: 105, category: "PORTFOLIO", categoryLabel: "포트폴리오",
    title: "신입 프론트 포트폴리오 피드백 부탁드려요 (배포 링크 포함)",
    content: "프로젝트 3개 중 무엇을 대표로 올릴지 고민입니다. 성능 개선 사례를 강조했어요.",
    tags: ["포트폴리오", "신입", "프론트엔드"], author: A(5, "포트준비"),
    stats: S(880, 19, 33, 14), status: "PUBLISHED", createdAt: "2026-06-18T20:10:00",
    liked: false, bookmarked: false,
  },
  {
    id: 106, category: "CERT_REVIEW", categoryLabel: "자격증후기",
    title: "정보처리기사 실기 2주 합격 후기 + 교재 추천",
    content: "기출 8회독이 답이었습니다. 알고리즘 파트는 직접 손으로 코드를 써봤어요.",
    tags: ["정보처리기사", "자격증"], author: A(6, "자격왕"),
    stats: S(1430, 27, 71, 38), status: "PUBLISHED", createdAt: "2026-06-15T08:00:00",
    liked: false, bookmarked: true,
  },
  {
    id: 107, category: "FREE", categoryLabel: "자유게시판",
    title: "면접 끝나고 자괴감 드는 거 저만 그런가요…",
    content: "분명 준비했는데 말문이 막혔어요. 다들 어떻게 멘탈 관리하시나요?",
    tags: ["멘탈관리", "면접"], author: A(7, "오늘면접"),
    stats: S(520, 33, 88, 6), status: "PUBLISHED", createdAt: "2026-06-19T01:05:00",
    liked: true, bookmarked: false,
  },
];

export const demoHotPosts = [
  { title: "서류 광탈에서 합격률 40%로 — 자소서 구조 공식", comments: 51, views: 3120 },
  { title: "비전공 3년차, 카카오 이직 성공 회고", comments: 44, views: 2510 },
  { title: "네이버 백엔드 2차 면접 후기 (시스템 설계 중심)", comments: 23, views: 1842 },
  { title: "정보처리기사 실기 2주 합격 후기 + 교재 추천", comments: 27, views: 1430 },
  { title: "신입 프론트 포트폴리오 피드백 부탁드려요", comments: 19, views: 880 },
];

export function communityPostPage() {
  return { posts: demoCommunityPosts, total: demoCommunityPosts.length, page: 0, size: 20 };
}

export function findCommunityPost(id: number) {
  return demoCommunityPosts.find((p) => p.id === id) ?? demoCommunityPosts[0];
}

export const demoComments = [
  { id: 9001, postId: 101, parentId: null, author: { id: 11, name: "현직네카라", isAnonymous: true },
    content: "캐시 무효화는 write-through vs invalidate 트레이드오프를 물어보는 경우가 많아요.", likeCount: 12,
    isAnonymous: true, status: "PUBLISHED", createdAt: "2026-06-18T10:02:00", liked: false },
  { id: 9002, postId: 101, parentId: null, author: { id: 12, name: "곧입사", isAnonymous: true },
    content: "후기 감사합니다! 2차 준비에 큰 도움 됐어요.", likeCount: 4,
    isAnonymous: true, status: "PUBLISHED", createdAt: "2026-06-18T12:40:00", liked: true },
];

export const demoPublishedGuideline = {
  id: 3, versionLabel: "v1.2", publishedAt: "2026-06-10T09:00:00",
  lede: "CareerTuner 커뮤니티는 취업·이직 경험을 안전하게 나누는 공간입니다. 서로의 노력을 존중해 주세요.",
  oksJson: JSON.stringify([
    "면접 질문·분위기 등 본인이 겪은 경험 공유",
    "회사·직무에 대한 사실 기반의 솔직한 후기",
    "출처를 밝힌 정보와 자료 공유",
  ]),
  nosJson: JSON.stringify([
    "특정 면접관·지원자를 식별할 수 있는 비방",
    "확인되지 않은 합격/불합격 정보의 단정",
    "광고·도배·외부 유료 서비스 홍보",
  ]),
  rulesJson: JSON.stringify([
    { t: "개인정보 보호", s: 1, b: "실명·연락처·사번 등 식별 정보는 자동 마스킹됩니다." },
    { t: "허위정보 제재", s: 2, b: "반복 시 게시 제한 및 누적 신고에 따라 이용이 제한될 수 있습니다." },
    { t: "AI 검열", s: 3, b: "욕설·비방은 AI가 1차 감지 후 운영자가 최종 확정합니다." },
  ]),
  paramsJson: JSON.stringify({ blind: 3, sla: 24, expire: 90, s1: 1, s2: 3, appeal: 7 }),
};

/* ─────────────────────────── 고객센터: FAQ / 공지 ─────────────────────────── */
// FAQ·공지 데모 데이터는 프론트 타입과 동일 shape이라 features 의 mock 데이터를 그대로 재사용한다.
export { mockFaqs as demoFaqs, mockNotices as demoNotices } from "@/features/support/data/mockSupport";

/* ─────────────────────────── 알림 ─────────────────────────── */

export const demoNotificationList = [
  { id: 5001, type: "COMMENT", targetType: "POST", targetId: 101, title: "내 글에 새 댓글이 달렸어요",
    message: "‘네이버 백엔드 2차 면접 후기’에 댓글: 캐시 무효화는…", link: "/community?post=101",
    read: false, createdAt: "2026-06-19T03:10:00", actor: { id: 11, name: "현직네카라" } },
  { id: 5002, type: "LIKE", targetType: "POST", targetId: 104, title: "게시글에 좋아요가 추가됐어요",
    message: "‘자소서 구조 공식’ 글이 좋아요 240개를 받았어요.", link: "/community?post=104",
    read: false, createdAt: "2026-06-19T01:55:00" },
  { id: 5003, type: "POST_SUMMARY_READY", targetType: "POST", targetId: 101, title: "AI 면접후기 요약이 완료됐어요",
    message: "작성하신 면접후기의 핵심 질문 요약이 준비됐습니다.", link: "/community?post=101",
    read: false, createdAt: "2026-06-18T09:25:00" },
  { id: 5004, type: "TICKET_ANSWERED", targetType: "TICKET", targetId: 7001, title: "문의에 답변이 등록됐어요",
    message: "‘크레딧 환불 문의’에 운영팀 답변이 도착했습니다.", link: "/support/contact",
    read: true, createdAt: "2026-06-17T16:30:00" },
  { id: 5005, type: "NOTICE", targetType: "NOTICE", targetId: 201, title: "새 공지: 6월 정기 점검 안내",
    message: "6/22(월) 02:00~04:00 서비스 점검이 예정돼 있습니다.", link: "/support/notices/201",
    read: true, createdAt: "2026-06-16T10:00:00" },
];

export function notificationPage(page = 0, size = 20) {
  return { notifications: demoNotificationList, total: demoNotificationList.length, page, size, hasNext: false };
}

export const demoNotificationPreference = {
  pushEnabled: true, emailEnabled: false,
  categories: { community: true, ai: true, support: true, notice: true, payment: false },
  quietHoursStart: "22:00", quietHoursEnd: "08:00", pushDeviceRegistered: true,
};

/* ─────────────────────────── 관리자: 신고 ─────────────────────────── */

export const demoAdminReports = [
  { id: 8001, reason: "ABUSE", type: "게시글", cnt: 3, title: "면접관 실명 거론 비방글",
    excerpt: "○○○ 면접관 진짜 최악이고…", cat: "면접후기", catKey: "interview-review",
    author: "익명의 두더지", time: 12, status: "pending", action: null },
  { id: 8002, reason: "FALSE_INFO", type: "게시글", cnt: 5, title: "확인 안 된 합격컷 단정 게시",
    excerpt: "이 회사 토익 900 안 되면 무조건 탈락…", cat: "취업후기", catKey: "job-review",
    author: "소문전달", time: 47, status: "pending", action: null },
  { id: 8003, reason: "SPAM", type: "댓글", cnt: 2, title: "외부 유료 컨설팅 홍보 댓글",
    excerpt: "DM 주시면 자소서 첨삭 해드려요(유료)…", cat: "자유게시판", catKey: "free",
    author: "광고봇", time: 130, status: "resolved", action: "DELETED" },
  { id: 8004, reason: "PRIVACY", type: "게시글", cnt: 1, title: "지원자 개인정보 노출",
    excerpt: "같이 면접 본 김** 010-…", cat: "면접후기", catKey: "interview-review",
    author: "익명", time: 320, status: "resolved", action: "HIDDEN" },
];

export const demoModerationItems = [
  { postId: 107, title: "면접 끝나고 자괴감 드는 거 저만…", authorName: "오늘면접", category: "자유게시판",
    status: "VISIBLE", toxic: false, aiCategory: null, confidence: 0.12, attemptCount: 1,
    createdAt: "2026-06-19T01:05:00", moderatedAt: "2026-06-19T01:05:30" },
  { postId: 8801, title: "(자동숨김) 욕설 다수 포함 게시글", authorName: "익명의 두더지", category: "자유게시판",
    status: "HIDDEN", toxic: true, aiCategory: "ABUSE", confidence: 0.93, attemptCount: 1,
    createdAt: "2026-06-18T22:14:00", moderatedAt: "2026-06-18T22:14:08" },
  { postId: 8802, title: "(검토대기) 비방 의심 게시글", authorName: "소문전달", category: "취업후기",
    status: "VISIBLE", toxic: true, aiCategory: "FALSE_INFO", confidence: 0.68, attemptCount: 2,
    createdAt: "2026-06-18T19:02:00", moderatedAt: "2026-06-18T19:02:11" },
];

export function moderationPage(page = 1, size = 20) {
  return { items: demoModerationItems, total: demoModerationItems.length, page, size, hasNext: false };
}

export const demoModerationStats = {
  total: 37,
  categories: [
    { category: "ABUSE", count: 18 }, { category: "FALSE_INFO", count: 9 },
    { category: "SPAM", count: 7 }, { category: "PRIVACY", count: 3 },
  ],
};

export const demoModerationSetting = { strictness: "NORMAL", hideThreshold: 0.8, updatedAt: "2026-06-12T09:00:00" };

/* ─────────────────────────── 관리자: 공지 / FAQ / 가이드라인 ─────────────────────────── */

export const demoAdminNotices = [
  { id: 201, title: "6월 정기 점검 안내", content: "6/22(월) 02:00~04:00 서비스 점검이 진행됩니다.",
    status: "PUBLISHED", pinned: true, publishedAt: "2026-06-16T10:00:00", createdAt: "2026-06-15T18:00:00",
    viewCount: 1820, thumbnailUrl: null },
  { id: 202, title: "AI 면접 리포트 기능 업데이트", content: "면접 리포트에 표정·음성 분석 요약이 추가됐습니다.",
    status: "PUBLISHED", pinned: false, publishedAt: "2026-06-12T09:00:00", createdAt: "2026-06-11T20:00:00",
    viewCount: 940, thumbnailUrl: null },
  { id: 203, title: "여름 채용 시즌 이벤트 (작성 중)", content: "초안입니다.",
    status: "DRAFT", pinned: false, publishedAt: null, createdAt: "2026-06-19T02:00:00",
    viewCount: 0, thumbnailUrl: null },
];

export const demoAdminFaqs = [
  { id: 1, category: "ACCOUNT", question: "비밀번호를 잊어버렸어요.", answer: "로그인 화면의 ‘비밀번호 찾기’에서 재설정할 수 있어요.", published: true },
  { id: 2, category: "PAYMENT", question: "크레딧 환불은 어떻게 하나요?", answer: "사용하지 않은 크레딧은 결제 내역에서 환불 요청이 가능합니다.", published: true },
  { id: 3, category: "AI_FEATURE", question: "AI 면접 답변 첨삭은 어떻게 쓰나요?", answer: "AI 첨삭 메뉴에서 답변을 입력하면 문장 단위 피드백을 받습니다.", published: true },
  { id: 4, category: "INTERVIEW", question: "면접 후기 인증은 어떻게 받나요?", answer: "후기 작성 시 증빙 이미지를 첨부하면 AI가 1차 검증합니다.", published: true },
  { id: 5, category: "GENERAL", question: "커뮤니티 글은 실명으로 보이나요?", answer: "기본 익명이며, 작성 시 실명 공개를 선택할 수 있어요.", published: false },
];

export const demoAdminGuidelines = [
  { id: 3, versionLabel: "v1.2", summary: "허위정보 제재 강화 + AI 검열 임계 조정", lede: demoPublishedGuideline.lede,
    oksJson: demoPublishedGuideline.oksJson, nosJson: demoPublishedGuideline.nosJson,
    rulesJson: demoPublishedGuideline.rulesJson, paramsJson: demoPublishedGuideline.paramsJson,
    status: "PUBLISHED", enforceType: "IMMEDIATE", scheduledAt: null,
    publishedAt: "2026-06-10T09:00:00", createdAt: "2026-06-09T15:00:00", updatedAt: "2026-06-10T09:00:00" },
  { id: 4, versionLabel: "v1.3", summary: "신고 누적 차단 정책 신설 (작성 중)", lede: demoPublishedGuideline.lede,
    oksJson: demoPublishedGuideline.oksJson, nosJson: demoPublishedGuideline.nosJson,
    rulesJson: demoPublishedGuideline.rulesJson, paramsJson: demoPublishedGuideline.paramsJson,
    status: "DRAFT", enforceType: "SCHEDULED", scheduledAt: "2026-06-25T00:00:00",
    publishedAt: null, createdAt: "2026-06-18T11:00:00", updatedAt: "2026-06-18T11:00:00" },
];

/* ─────────────────────────── 관리자: 문의(티켓) / 알림 발송 ─────────────────────────── */

export const demoAdminTickets = [
  { id: 7001, category: "PAYMENT", subject: "크레딧 환불 문의", memberName: "김데모", createdAt: "2026-06-17T15:00:00",
    status: "answered", priority: "NORMAL", plan: "프로", joinedAt: "2026-01-12" },
  { id: 7002, category: "BUG", subject: "AI 면접 화면이 멈춰요", memberName: "이지원", createdAt: "2026-06-19T01:20:00",
    status: "pending", priority: "HIGH", plan: "무료", joinedAt: "2026-05-30" },
  { id: 7003, category: "ACCOUNT", subject: "이메일 인증 메일이 안 와요", memberName: "박취준", createdAt: "2026-06-18T22:05:00",
    status: "progress", priority: "NORMAL", plan: "무료", joinedAt: "2026-06-10" },
  { id: 7004, category: "AI", subject: "후기 요약 결과가 이상해요", memberName: "최합격", createdAt: "2026-06-18T13:40:00",
    status: "pending", priority: "LOW", plan: "프로", joinedAt: "2026-03-03" },
];

export function adminTicketDetail(id: number) {
  const t = demoAdminTickets.find((x) => x.id === id) ?? demoAdminTickets[0];
  return {
    ...t, memo: "1차 안내 완료. 환불 정책 재확인 필요.",
    msgs: [
      { who: "user", name: t.memberName, time: t.createdAt, text: "문의 내용입니다. 빠른 확인 부탁드려요.", internal: false },
      { who: "admin", name: "운영팀", time: "2026-06-17T16:30:00", text: "안녕하세요, 확인 후 처리 도와드리겠습니다.", internal: false },
      { who: "admin", name: "운영팀", time: "2026-06-17T16:31:00", text: "(내부) 결제팀 확인 요청함", internal: true },
    ],
  };
}

export const demoAdminNotifications = [
  { id: 6001, userId: 1, recipientName: "김데모", recipientEmail: "demo@careertuner.io", type: "NOTICE",
    title: "6월 정기 점검 안내", message: "6/22 02:00~04:00 점검", read: true, readAt: "2026-06-16T11:00:00", createdAt: "2026-06-16T10:00:00" },
  { id: 6002, userId: 2, recipientName: "이지원", recipientEmail: "jiwon@example.com", type: "COMMENT",
    title: "내 글에 새 댓글", message: "댓글이 달렸어요", read: false, readAt: null, createdAt: "2026-06-19T03:10:00" },
  { id: 6003, userId: 4, recipientName: "최합격", recipientEmail: "choi@example.com", type: "POST_SUMMARY_READY",
    title: "AI 면접후기 요약 완료", message: "요약이 준비됐습니다", read: false, readAt: null, createdAt: "2026-06-18T09:25:00" },
  { id: 6004, userId: 3, recipientName: "박취준", recipientEmail: "park@example.com", type: "TICKET_ANSWERED",
    title: "문의 답변 등록", message: "운영팀 답변 도착", read: true, readAt: "2026-06-17T17:00:00", createdAt: "2026-06-17T16:30:00" },
];
