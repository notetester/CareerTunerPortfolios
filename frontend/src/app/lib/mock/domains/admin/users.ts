// 데모/목: 관리자 — 회원 / 프로필 / 동의 도메인.
// 백엔드 없이 /admin/users, /admin/profiles, /admin/consents 화면이 운영 데이터처럼 채워지도록 한다.
// 회원 6명(김데모 9001 포함 + 관리자 1명)을 기준으로 상태·로그인 이력·상태 변경 이력·동의·프로필을 일관되게 구성한다.
// 응답 타입은 admin/features/* 의 api 모듈이 기대하는 RAW payload 그대로다(api() 가 envelope 를 이미 벗긴다).
import type { MockRoute, MockContext } from "../../registry";
import { iso } from "../../registry";
import type {
  AdminUserRow,
  AdminUserCreateRequest,
  AdminUserStatus,
  AdminUserDetail,
  AdminUserLoginHistoryRow,
  AdminUserStatusHistoryRow,
  AdminUserConsentRow,
} from "@/admin/features/users/types";
import type { AdminUserProfile } from "@/admin/features/profiles/types";
import type { AdminConsentView } from "@/admin/features/consents/types";

// ── 회원 목록(세션 내 in-memory). 상태 변경 시 갱신해 목록·상세에 즉시 반영한다. ──
const users: AdminUserRow[] = [
  {
    id: 9001,
    email: "demo@careertuner.dev",
    name: "김데모",
    passwordEnabled: true,
    emailVerified: true,
    userType: "JOB_SEEKER",
    role: "USER",
    status: "ACTIVE",
    plan: "PRO",
    credit: 222,
    lastLoginAt: iso(0),
    dormantAt: null,
    blockedReason: null,
    blockedUntil: null,
    deletedAt: null,
    statusChangedAt: iso(120),
    statusChangedBy: null,
    failedLoginCount: 0,
    lastFailedLoginAt: iso(14),
    createdAt: iso(120),
    updatedAt: iso(0),
    loginSuccessCount: 184,
    loginFailCount: 3,
  },
  {
    id: 1,
    email: "admin@careertuner.dev",
    name: "운영자",
    passwordEnabled: true,
    emailVerified: true,
    userType: "STAFF",
    role: "ADMIN",
    status: "ACTIVE",
    plan: "FREE",
    credit: 0,
    lastLoginAt: iso(0),
    dormantAt: null,
    blockedReason: null,
    blockedUntil: null,
    deletedAt: null,
    statusChangedAt: iso(300),
    statusChangedBy: null,
    failedLoginCount: 0,
    lastFailedLoginAt: null,
    createdAt: iso(300),
    updatedAt: iso(1),
    loginSuccessCount: 642,
    loginFailCount: 1,
  },
  {
    id: 9002,
    email: "jiwon.park@example.com",
    name: "박지원",
    passwordEnabled: true,
    emailVerified: true,
    userType: "JOB_SEEKER",
    role: "USER",
    status: "ACTIVE",
    plan: "BASIC",
    credit: 41,
    lastLoginAt: iso(2),
    dormantAt: null,
    blockedReason: null,
    blockedUntil: null,
    deletedAt: null,
    statusChangedAt: iso(64),
    statusChangedBy: null,
    failedLoginCount: 0,
    lastFailedLoginAt: iso(9),
    createdAt: iso(64),
    updatedAt: iso(2),
    loginSuccessCount: 57,
    loginFailCount: 2,
  },
  {
    id: 9003,
    email: "minseo.lee@example.com",
    name: "이민서",
    passwordEnabled: false,
    emailVerified: true,
    userType: "JOB_SEEKER",
    role: "USER",
    status: "DORMANT",
    plan: "FREE",
    credit: 5,
    lastLoginAt: iso(98),
    dormantAt: iso(8),
    blockedReason: null,
    blockedUntil: null,
    deletedAt: null,
    statusChangedAt: iso(8),
    statusChangedBy: 1,
    failedLoginCount: 0,
    lastFailedLoginAt: null,
    createdAt: iso(210),
    updatedAt: iso(8),
    loginSuccessCount: 22,
    loginFailCount: 0,
  },
  {
    id: 9004,
    email: "junho.choi@example.com",
    name: "최준호",
    passwordEnabled: true,
    emailVerified: false,
    userType: "JOB_SEEKER",
    role: "USER",
    status: "BLOCKED",
    plan: "FREE",
    credit: 0,
    lastLoginAt: iso(11),
    dormantAt: null,
    blockedReason: "로그인 5회 연속 실패로 자동 차단",
    blockedUntil: iso(-3),
    deletedAt: null,
    statusChangedAt: iso(5),
    statusChangedBy: 1,
    failedLoginCount: 5,
    lastFailedLoginAt: iso(5),
    createdAt: iso(47),
    updatedAt: iso(5),
    loginSuccessCount: 13,
    loginFailCount: 9,
  },
  {
    id: 9005,
    email: "former.user@example.com",
    name: "정해린",
    passwordEnabled: true,
    emailVerified: true,
    userType: "JOB_SEEKER",
    role: "USER",
    status: "DELETED",
    plan: "FREE",
    credit: 0,
    lastLoginAt: iso(75),
    dormantAt: null,
    blockedReason: null,
    blockedUntil: null,
    deletedAt: iso(30),
    statusChangedAt: iso(30),
    statusChangedBy: 1,
    failedLoginCount: 0,
    lastFailedLoginAt: null,
    createdAt: iso(180),
    updatedAt: iso(30),
    loginSuccessCount: 41,
    loginFailCount: 1,
  },
];

// ── 회원별 로그인 이력(상세 요약 + 전체 조회 공용). 최신순. ──
const loginHistory: Record<number, AdminUserLoginHistoryRow[]> = {
  9001: [
    {
      id: 70001,
      userId: 9001,
      eventType: "LOGIN_SUCCESS",
      authProvider: "LOCAL",
      loginMethod: "PASSWORD",
      loginIdentifier: "demo@careertuner.dev",
      success: true,
      failReason: null,
      ipAddress: "211.234.12.45",
      userAgent: "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Chrome/124.0",
      requestUri: "/api/auth/login",
      createdAt: iso(0),
    },
    {
      id: 70002,
      userId: 9001,
      eventType: "LOGIN_SUCCESS",
      authProvider: "GOOGLE",
      loginMethod: "OAUTH",
      loginIdentifier: "demo@careertuner.dev",
      success: true,
      failReason: null,
      ipAddress: "211.234.12.45",
      userAgent: "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4) Safari/605.1",
      requestUri: "/api/auth/oauth/google",
      createdAt: iso(2),
    },
    {
      id: 70003,
      userId: 9001,
      eventType: "LOGIN_FAIL",
      authProvider: "LOCAL",
      loginMethod: "PASSWORD",
      loginIdentifier: "demo@careertuner.dev",
      success: false,
      failReason: "비밀번호 불일치",
      ipAddress: "118.45.201.7",
      userAgent: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Edge/124.0",
      requestUri: "/api/auth/login",
      createdAt: iso(14),
    },
  ],
  1: [
    {
      id: 71001,
      userId: 1,
      eventType: "LOGIN_SUCCESS",
      authProvider: "LOCAL",
      loginMethod: "PASSWORD",
      loginIdentifier: "admin@careertuner.dev",
      success: true,
      failReason: null,
      ipAddress: "10.0.3.21",
      userAgent: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/124.0",
      requestUri: "/api/auth/login",
      createdAt: iso(0),
    },
    {
      id: 71002,
      userId: 1,
      eventType: "LOGIN_SUCCESS",
      authProvider: "LOCAL",
      loginMethod: "PASSWORD",
      loginIdentifier: "admin@careertuner.dev",
      success: true,
      failReason: null,
      ipAddress: "10.0.3.21",
      userAgent: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/124.0",
      requestUri: "/api/auth/login",
      createdAt: iso(1),
    },
  ],
  9002: [
    {
      id: 72001,
      userId: 9002,
      eventType: "LOGIN_SUCCESS",
      authProvider: "KAKAO",
      loginMethod: "OAUTH",
      loginIdentifier: "jiwon.park@example.com",
      success: true,
      failReason: null,
      ipAddress: "175.223.8.190",
      userAgent: "Mozilla/5.0 (Linux; Android 14) Chrome/124.0 Mobile",
      requestUri: "/api/auth/oauth/kakao",
      createdAt: iso(2),
    },
    {
      id: 72002,
      userId: 9002,
      eventType: "LOGIN_FAIL",
      authProvider: "LOCAL",
      loginMethod: "PASSWORD",
      loginIdentifier: "jiwon.park@example.com",
      success: false,
      failReason: "비밀번호 불일치",
      ipAddress: "175.223.8.190",
      userAgent: "Mozilla/5.0 (Linux; Android 14) Chrome/124.0 Mobile",
      requestUri: "/api/auth/login",
      createdAt: iso(9),
    },
  ],
  9003: [
    {
      id: 73001,
      userId: 9003,
      eventType: "LOGIN_SUCCESS",
      authProvider: "GOOGLE",
      loginMethod: "OAUTH",
      loginIdentifier: "minseo.lee@example.com",
      success: true,
      failReason: null,
      ipAddress: "203.241.55.12",
      userAgent: "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Safari/605.1",
      requestUri: "/api/auth/oauth/google",
      createdAt: iso(98),
    },
  ],
  9004: [
    {
      id: 74001,
      userId: 9004,
      eventType: "LOGIN_FAIL",
      authProvider: "LOCAL",
      loginMethod: "PASSWORD",
      loginIdentifier: "junho.choi@example.com",
      success: false,
      failReason: "비밀번호 5회 연속 실패 — 계정 차단",
      ipAddress: "45.83.201.11",
      userAgent: "python-requests/2.31",
      requestUri: "/api/auth/login",
      createdAt: iso(5),
    },
    {
      id: 74002,
      userId: 9004,
      eventType: "LOGIN_FAIL",
      authProvider: "LOCAL",
      loginMethod: "PASSWORD",
      loginIdentifier: "junho.choi@example.com",
      success: false,
      failReason: "비밀번호 불일치",
      ipAddress: "45.83.201.11",
      userAgent: "python-requests/2.31",
      requestUri: "/api/auth/login",
      createdAt: iso(5),
    },
    {
      id: 74003,
      userId: 9004,
      eventType: "LOGIN_SUCCESS",
      authProvider: "LOCAL",
      loginMethod: "PASSWORD",
      loginIdentifier: "junho.choi@example.com",
      success: true,
      failReason: null,
      ipAddress: "121.88.10.4",
      userAgent: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/123.0",
      requestUri: "/api/auth/login",
      createdAt: iso(11),
    },
  ],
  9005: [
    {
      id: 75001,
      userId: 9005,
      eventType: "LOGIN_SUCCESS",
      authProvider: "LOCAL",
      loginMethod: "PASSWORD",
      loginIdentifier: "former.user@example.com",
      success: true,
      failReason: null,
      ipAddress: "112.187.44.9",
      userAgent: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Firefox/121.0",
      requestUri: "/api/auth/login",
      createdAt: iso(75),
    },
  ],
};

// ── 회원별 상태 변경 이력. 최신순. ──
const statusHistory: Record<number, AdminUserStatusHistoryRow[]> = {
  9001: [
    {
      id: 80001,
      userId: 9001,
      actorUserId: null,
      previousStatus: null,
      newStatus: "ACTIVE",
      reason: "회원 가입",
      memo: "데모 기본 계정",
      blockedUntil: null,
      createdAt: iso(120),
    },
  ],
  1: [
    {
      id: 81001,
      userId: 1,
      actorUserId: null,
      previousStatus: null,
      newStatus: "ACTIVE",
      reason: "운영자 계정 생성",
      memo: null,
      blockedUntil: null,
      createdAt: iso(300),
    },
  ],
  9002: [
    {
      id: 82001,
      userId: 9002,
      actorUserId: null,
      previousStatus: null,
      newStatus: "ACTIVE",
      reason: "회원 가입",
      memo: null,
      blockedUntil: null,
      createdAt: iso(64),
    },
  ],
  9003: [
    {
      id: 83001,
      userId: 9003,
      actorUserId: 1,
      previousStatus: "ACTIVE",
      newStatus: "DORMANT",
      reason: "90일 이상 미접속 — 휴면 전환",
      memo: "자동 휴면 정책 적용",
      blockedUntil: null,
      createdAt: iso(8),
    },
    {
      id: 83002,
      userId: 9003,
      actorUserId: null,
      previousStatus: null,
      newStatus: "ACTIVE",
      reason: "회원 가입",
      memo: null,
      blockedUntil: null,
      createdAt: iso(210),
    },
  ],
  9004: [
    {
      id: 84001,
      userId: 9004,
      actorUserId: 1,
      previousStatus: "ACTIVE",
      newStatus: "BLOCKED",
      reason: "로그인 5회 연속 실패로 자동 차단",
      memo: "비정상 자동화 트래픽 의심(IP 45.83.201.11)",
      blockedUntil: iso(-3),
      createdAt: iso(5),
    },
    {
      id: 84002,
      userId: 9004,
      actorUserId: null,
      previousStatus: null,
      newStatus: "ACTIVE",
      reason: "회원 가입",
      memo: null,
      blockedUntil: null,
      createdAt: iso(47),
    },
  ],
  9005: [
    {
      id: 85001,
      userId: 9005,
      actorUserId: 1,
      previousStatus: "ACTIVE",
      newStatus: "DELETED",
      reason: "본인 요청에 의한 회원 탈퇴",
      memo: "개인정보 파기 스케줄 등록",
      blockedUntil: null,
      createdAt: iso(30),
    },
    {
      id: 85002,
      userId: 9005,
      actorUserId: null,
      previousStatus: null,
      newStatus: "ACTIVE",
      reason: "회원 가입",
      memo: null,
      blockedUntil: null,
      createdAt: iso(180),
    },
  ],
};

// ── 회원별 동의 이력(상세 카드용). ──
const consentsByUser: Record<number, AdminUserConsentRow[]> = {
  9001: [
    { id: 90001, userId: 9001, consentType: "TERMS", agreed: true, agreedAt: iso(120), revokedAt: null, source: "SIGNUP", createdAt: iso(120) },
    { id: 90002, userId: 9001, consentType: "PRIVACY", agreed: true, agreedAt: iso(120), revokedAt: null, source: "SIGNUP", createdAt: iso(120) },
    { id: 90004, userId: 9001, consentType: "AI_DATA", agreed: true, agreedAt: iso(120), revokedAt: null, source: "SIGNUP", createdAt: iso(120) },
    { id: 90005, userId: 9001, consentType: "RESUME_ANALYSIS", agreed: true, agreedAt: iso(120), revokedAt: null, source: "SIGNUP", createdAt: iso(120) },
    { id: 90003, userId: 9001, consentType: "MARKETING", agreed: true, agreedAt: iso(40), revokedAt: null, source: "SETTINGS", createdAt: iso(120) },
  ],
  1: [
    { id: 91001, userId: 1, consentType: "TERMS", agreed: true, agreedAt: iso(300), revokedAt: null, source: "SIGNUP", createdAt: iso(300) },
    { id: 91002, userId: 1, consentType: "PRIVACY", agreed: true, agreedAt: iso(300), revokedAt: null, source: "SIGNUP", createdAt: iso(300) },
  ],
  9002: [
    { id: 92001, userId: 9002, consentType: "TERMS", agreed: true, agreedAt: iso(64), revokedAt: null, source: "SIGNUP", createdAt: iso(64) },
    { id: 92002, userId: 9002, consentType: "PRIVACY", agreed: true, agreedAt: iso(64), revokedAt: null, source: "SIGNUP", createdAt: iso(64) },
    { id: 92003, userId: 9002, consentType: "MARKETING", agreed: false, agreedAt: iso(64), revokedAt: iso(20), source: "SETTINGS", createdAt: iso(64) },
  ],
  9003: [
    { id: 93001, userId: 9003, consentType: "TERMS", agreed: true, agreedAt: iso(210), revokedAt: null, source: "SIGNUP", createdAt: iso(210) },
    { id: 93002, userId: 9003, consentType: "PRIVACY", agreed: true, agreedAt: iso(210), revokedAt: null, source: "SIGNUP", createdAt: iso(210) },
  ],
  9004: [
    { id: 94001, userId: 9004, consentType: "TERMS", agreed: true, agreedAt: iso(47), revokedAt: null, source: "SIGNUP", createdAt: iso(47) },
    { id: 94002, userId: 9004, consentType: "PRIVACY", agreed: true, agreedAt: iso(47), revokedAt: null, source: "SIGNUP", createdAt: iso(47) },
    { id: 94003, userId: 9004, consentType: "MARKETING", agreed: false, agreedAt: null, revokedAt: null, source: "SIGNUP", createdAt: iso(47) },
  ],
  9005: [
    { id: 95001, userId: 9005, consentType: "TERMS", agreed: true, agreedAt: iso(180), revokedAt: null, source: "SIGNUP", createdAt: iso(180) },
    { id: 95002, userId: 9005, consentType: "PRIVACY", agreed: false, agreedAt: iso(180), revokedAt: iso(30), source: "WITHDRAWAL", createdAt: iso(180) },
  ],
};

// ── 회원별 프로필. 김데모는 프론트엔드 지망 + 풍부한 스킬/경력. ──
const profiles: AdminUserProfile[] = [
  {
    id: 1,
    userId: 9001,
    desiredJob: "프론트엔드 개발자",
    desiredIndustry: "IT/플랫폼",
    education: [{ school: "한국대학교", major: "컴퓨터공학", degree: "학사", graduatedAt: "2024-02" }],
    career: [{ company: "스타트업A", role: "프론트엔드 인턴", period: "2023.06 ~ 2023.12" }],
    projects: [
      { name: "CareerTuner 클론", stack: ["React", "TypeScript", "Vite"], desc: "취업 전략 SPA 개인 프로젝트" },
      { name: "사내 대시보드", stack: ["React", "Recharts"], desc: "운영 지표 시각화" },
    ],
    skills: ["React", "TypeScript", "Tailwind CSS", "Vite", "Zustand", "Jest"],
    certificates: ["정보처리기사", "SQLD"],
    languages: [{ name: "영어", level: "TOEIC 870" }],
    portfolioLinks: ["https://github.com/kimdemo", "https://kimdemo.dev"],
    resumeText: "프론트엔드 개발자 김데모입니다. React/TypeScript 기반 SPA 개발 경험이 있으며 사용자 중심 UI 구현에 강점이 있습니다.",
    selfIntro: "사용자 경험을 데이터로 검증하며 개선하는 것을 좋아합니다. 작은 인터랙션도 끝까지 다듬습니다.",
    preferences: { workType: "정규직", location: "서울", remote: "주 2회 재택 선호", salary: "4000만원 이상" },
    updatedAt: iso(0),
  },
  {
    id: 2,
    userId: 9002,
    desiredJob: "프론트엔드 개발자",
    desiredIndustry: "핀테크",
    education: [{ school: "지방대학교", major: "소프트웨어학", degree: "학사", graduatedAt: "2023-08" }],
    career: [],
    projects: [{ name: "가계부 앱", stack: ["Vue", "Firebase"], desc: "개인 자산 관리 앱" }],
    skills: ["Vue", "JavaScript", "CSS", "Firebase"],
    certificates: ["정보처리기사"],
    languages: [{ name: "영어", level: "OPIc IM2" }],
    portfolioLinks: ["https://github.com/jiwonp"],
    resumeText: "신입 프론트엔드 지원자 박지원입니다. Vue 기반 개인 프로젝트 경험이 있습니다.",
    selfIntro: "꾸준히 학습하며 성장하는 개발자가 되고 싶습니다.",
    preferences: { workType: "정규직", location: "판교", remote: "출근 선호" },
    updatedAt: iso(2),
  },
  {
    id: 3,
    userId: 9003,
    desiredJob: "백엔드 개발자",
    desiredIndustry: "이커머스",
    education: [{ school: "한국대학교", major: "정보통신공학", degree: "학사", graduatedAt: "2022-02" }],
    career: [{ company: "쇼핑몰B", role: "백엔드 개발자", period: "2022.03 ~ 2024.01" }],
    projects: [],
    skills: ["Java", "Spring Boot", "MySQL"],
    certificates: [],
    languages: [],
    portfolioLinks: [],
    resumeText: null,
    selfIntro: null,
    preferences: { workType: "정규직", location: "서울" },
    updatedAt: iso(98),
  },
  {
    id: 4,
    userId: 9004,
    desiredJob: null,
    desiredIndustry: null,
    education: [],
    career: [],
    projects: [],
    skills: [],
    certificates: [],
    languages: [],
    portfolioLinks: [],
    resumeText: null,
    selfIntro: null,
    preferences: null,
    updatedAt: iso(40),
  },
];

const profileByUser = new Map(profiles.map((profile) => [profile.userId, profile]));

// ── 동의 플랫폼 전체 뷰(/admin/consents). 회원별 동의를 평탄화 + 이메일 결합. ──
function buildConsentViews(): AdminConsentView[] {
  const emailOf = new Map(users.map((user) => [user.id, user.email]));
  const views: AdminConsentView[] = [];
  for (const userId of Object.keys(consentsByUser)) {
    const id = Number(userId);
    for (const consent of consentsByUser[id]) {
      views.push({
        id: consent.id,
        userId: consent.userId,
        userEmail: emailOf.get(consent.userId) ?? null,
        consentType: consent.consentType,
        consentVersion: "v2026.07",
        agreed: consent.agreed,
        agreedAt: consent.agreedAt,
        revokedAt: consent.revokedAt,
        source: consent.source,
        createdAt: consent.createdAt,
      });
    }
  }
  // 최신 생성순 정렬
  return views.sort((a, b) => String(b.createdAt ?? "").localeCompare(String(a.createdAt ?? "")));
}

// ── 목록 필터링 헬퍼 ──
function filterUsers(ctx: MockContext): AdminUserRow[] {
  const keyword = (ctx.query.get("keyword") ?? "").trim().toLowerCase();
  const status = (ctx.query.get("status") ?? "").trim();
  const role = (ctx.query.get("role") ?? "").trim();
  const limit = Number(ctx.query.get("limit") ?? 50) || 50;
  let rows = users.slice();
  if (keyword) {
    rows = rows.filter(
      (row) => row.name.toLowerCase().includes(keyword) || row.email.toLowerCase().includes(keyword),
    );
  }
  if (status) rows = rows.filter((row) => row.status === status);
  if (role) rows = rows.filter((row) => row.role === role);
  return rows.slice(0, limit);
}

function findUser(id: number): AdminUserRow | undefined {
  return users.find((user) => user.id === id);
}

interface StatusUpdateBody {
  status?: AdminUserStatus;
  reason?: string;
  memo?: string;
  blockedUntil?: string | null;
}

function softDeleteUser(user: AdminUserRow, reason?: string): AdminUserRow {
  const previousStatus = user.status;
  const now = new Date().toISOString();
  user.status = "DELETED";
  user.deletedAt = now;
  user.dormantAt = null;
  user.blockedReason = null;
  user.blockedUntil = null;
  user.statusChangedAt = now;
  user.statusChangedBy = 1;
  user.updatedAt = now;
  const history = statusHistory[user.id] ?? (statusHistory[user.id] = []);
  history.unshift({
    id: 800000 + history.length + 1,
    userId: user.id,
    actorUserId: 1,
    previousStatus,
    newStatus: "DELETED",
    reason: reason?.trim() || "관리자 소프트 삭제",
    memo: null,
    blockedUntil: null,
    createdAt: now,
  });
  return { ...user };
}

export const adminUsersRoutes: MockRoute[] = [
  // ── 회원 목록(keyword/status/role/limit 필터) ──
  {
    method: "GET",
    pattern: /^\/admin\/users$/,
    handler: (ctx: MockContext) => filterUsers(ctx),
  },

  // ── 회원 생성: 서버와 동일하게 USER / ACTIVE / FREE로만 생성한다. ──
  {
    method: "POST",
    pattern: /^\/admin\/users$/,
    handler: (ctx: MockContext): AdminUserRow => {
      const body = (ctx.body ?? {}) as Partial<AdminUserCreateRequest>;
      const now = new Date().toISOString();
      const created: AdminUserRow = {
        id: Math.max(...users.map((user) => user.id), 9000) + 1,
        email: String(body.email ?? "").trim().toLowerCase(),
        name: String(body.name ?? "").trim(),
        passwordEnabled: true,
        emailVerified: false,
        userType: "JOB_SEEKER",
        role: "USER",
        status: "ACTIVE",
        plan: "FREE",
        credit: 0,
        lastLoginAt: null,
        dormantAt: null,
        blockedReason: null,
        blockedUntil: null,
        deletedAt: null,
        statusChangedAt: now,
        statusChangedBy: 1,
        failedLoginCount: 0,
        lastFailedLoginAt: null,
        createdAt: now,
        updatedAt: now,
        loginSuccessCount: 0,
        loginFailCount: 0,
      };
      users.unshift(created);
      loginHistory[created.id] = [];
      statusHistory[created.id] = [];
      consentsByUser[created.id] = [];
      return { ...created };
    },
  },

  // ── 회원 일괄 소프트 삭제 ──
  {
    method: "POST",
    pattern: /^\/admin\/users\/bulk-delete$/,
    handler: (ctx: MockContext) => {
      const body = (ctx.body ?? {}) as { ids?: number[]; params?: { reason?: string } };
      const ids = Array.isArray(body.ids) ? body.ids : [];
      let updated = 0;
      ids.forEach((id) => {
        const user = findUser(Number(id));
        if (!user || user.status === "DELETED") return;
        softDeleteUser(user, body.params?.reason);
        updated += 1;
      });
      return { requested: ids.length, updated, skipped: ids.length - updated };
    },
  },

  // ── 회원 단건 소프트 삭제 ──
  {
    method: "DELETE",
    pattern: /^\/admin\/users\/(\d+)$/,
    handler: (ctx: MockContext): AdminUserRow | null => {
      const user = findUser(Number(ctx.params[0]));
      return user ? softDeleteUser(user, ctx.query.get("reason") ?? undefined) : null;
    },
  },

  // ── 회원 전체 로그인 이력(상세 요약보다 많은 건수) ──
  {
    method: "GET",
    pattern: /^\/admin\/users\/(\d+)\/login-history$/,
    handler: (ctx: MockContext) => {
      const id = Number(ctx.params[0]);
      const limit = Number(ctx.query.get("limit") ?? 100) || 100;
      return (loginHistory[id] ?? []).slice(0, limit);
    },
  },

  // ── 회원 상태 변경(PATCH). 메모리 갱신 후 갱신된 row 반환 ──
  {
    method: "PATCH",
    pattern: /^\/admin\/users\/(\d+)\/status$/,
    handler: (ctx: MockContext) => {
      const id = Number(ctx.params[0]);
      const user = findUser(id);
      const body = (ctx.body ?? {}) as StatusUpdateBody;
      if (!user) return null;
      const previousStatus = user.status;
      const nextStatus = body.status ?? user.status;
      const now = new Date().toISOString();
      user.status = nextStatus;
      user.statusChangedAt = now;
      user.statusChangedBy = 1;
      user.updatedAt = now;
      user.blockedReason = nextStatus === "BLOCKED" ? body.reason ?? user.blockedReason : null;
      user.blockedUntil = nextStatus === "BLOCKED" ? body.blockedUntil ?? null : null;
      user.dormantAt = nextStatus === "DORMANT" ? now : null;
      user.deletedAt = nextStatus === "DELETED" ? now : null;
      if (nextStatus === "ACTIVE") user.failedLoginCount = 0;
      const history = statusHistory[id] ?? (statusHistory[id] = []);
      history.unshift({
        id: 800000 + history.length + 1,
        userId: id,
        actorUserId: 1,
        previousStatus,
        newStatus: nextStatus,
        reason: body.reason ?? null,
        memo: body.memo ?? null,
        blockedUntil: nextStatus === "BLOCKED" ? body.blockedUntil ?? null : null,
        createdAt: now,
      });
      return { ...user };
    },
  },

  // ── 회원 상세(user + 로그인/상태/동의 이력) ──
  {
    method: "GET",
    pattern: /^\/admin\/users\/(\d+)$/,
    handler: (ctx: MockContext): AdminUserDetail | null => {
      const id = Number(ctx.params[0]);
      const user = findUser(id);
      if (!user) return null;
      return {
        user: { ...user },
        loginHistory: (loginHistory[id] ?? []).slice(0, 5),
        statusHistory: statusHistory[id] ?? [],
        consents: consentsByUser[id] ?? [],
        emailVerifications: [],
        refreshTokens: [],
        aiUsage: [],
        profile: null,
      };
    },
  },

  // ── 프로필 상세(userId) ──
  {
    method: "GET",
    pattern: /^\/admin\/profiles\/(\d+)$/,
    handler: (ctx: MockContext): AdminUserProfile => {
      const userId = Number(ctx.params[0]);
      return profileByUser.get(userId) ?? { userId, skills: [], updatedAt: null };
    },
  },

  // ── 프로필 목록(keyword 필터: 직무/산업/스킬 텍스트 매칭) ──
  {
    method: "GET",
    pattern: /^\/admin\/profiles$/,
    handler: (ctx: MockContext): AdminUserProfile[] => {
      const keyword = (ctx.query.get("keyword") ?? "").trim().toLowerCase();
      const limit = Number(ctx.query.get("limit") ?? 100) || 100;
      let rows = profiles.slice();
      if (keyword) {
        rows = rows.filter((row) => {
          const haystack = [
            row.desiredJob ?? "",
            row.desiredIndustry ?? "",
            JSON.stringify(row.skills ?? ""),
          ]
            .join(" ")
            .toLowerCase();
          return haystack.includes(keyword);
        });
      }
      return rows.slice(0, limit);
    },
  },

  // ── 플랫폼 전체 동의 목록(keyword/consentType/status/source/from/to 필터) ──
  {
    method: "GET",
    pattern: /^\/admin\/consents$/,
    handler: (ctx: MockContext): AdminConsentView[] => {
      const keyword = (ctx.query.get("keyword") ?? "").trim().toLowerCase();
      const consentType = (ctx.query.get("consentType") ?? "").trim();
      const status = (ctx.query.get("status") ?? "").trim();
      const source = (ctx.query.get("source") ?? "").trim();
      const from = (ctx.query.get("from") ?? "").trim();
      const to = (ctx.query.get("to") ?? "").trim();
      const limit = Number(ctx.query.get("limit") ?? 100) || 100;
      let rows = buildConsentViews();
      if (keyword) {
        rows = rows.filter((row) => (row.userEmail ?? "").toLowerCase().includes(keyword));
      }
      if (consentType) rows = rows.filter((row) => row.consentType === consentType);
      if (status === "AGREED") rows = rows.filter((row) => row.agreed);
      else if (status === "REVOKED") rows = rows.filter((row) => !row.agreed || !!row.revokedAt);
      if (source) rows = rows.filter((row) => row.source === source);
      if (from) rows = rows.filter((row) => (row.createdAt ?? "").slice(0, 10) >= from);
      if (to) rows = rows.filter((row) => (row.createdAt ?? "").slice(0, 10) <= to);
      return rows.slice(0, limit);
    },
  },
];
