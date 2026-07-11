// 데모/목: ADMIN CORE 도메인(운영 종합 대시보드 / 관리자 홈 / 시스템 로그).
// VITE_USE_MOCK=true 일 때 /admin/dashboard/overview · /admin/home/summary · /admin/logs/ai-usage
// 가 백엔드 없이도 일관된 운영 데이터로 렌더되도록 채운다. 김데모(9001)·카카오/네이버/토스/라인
// 지원 건 세트와 숫자를 맞춰 다른 admin 그룹과 충돌 없이 단독 동작한다(transform 없음).
import type { MockRoute, MockContext } from "../../registry";
import { iso } from "../../registry";
import type { AdminDashboardOverview } from "@/admin/features/dashboard/types/adminDashboard";
import type { AdminHomeSummary } from "@/admin/features/home/types/adminHome";
import type { AdminAiUsageLogRow } from "@/admin/features/system-logs/api";
import type { EmailAuditRow } from "@/admin/features/email-audit/api";
import type { AdminLoginAuditRow } from "@/admin/features/audit/types";

// ── 운영 종합 대시보드: 도메인 횡단 운영 현황 카운트 ──
// 회원 7명(활성 5) · 지원 건 4건(카카오/네이버/토스/라인) · 누적 적합도 분석/면접 세션/이번 달 AI 호출.
const dashboardOverview: AdminDashboardOverview = {
  totalUsers: 7,
  activeUsers: 5,
  totalApplications: 4,
  totalFitAnalyses: 18,
  totalInterviewSessions: 23,
  aiCallsThisMonth: 412,
  reviewRequiredAnalyses: 2,
};

// ── 관리자 홈: 지금 처리할 적합도 분석 작업 대기 큐 + 운영 바로가기 ──
const homeSummary: AdminHomeSummary = {
  fitAnalysisFailures: 2,
  unanalyzedApplications: 1,
  newAnalysesLast7Days: 9,
  degradedLatestAnalyses: 1,
  reanalysisRequests: 3,
  careerRunFailures: 1,
  reviewRequiredAnalyses: 2,
  shortcuts: [
    { label: "적합도 분석 관리", path: "/admin/fit-analysis", description: "전체 지원 건의 적합도 분석 결과를 점검하고 재분석합니다." },
    { label: "운영 종합 대시보드", path: "/admin/dashboard", description: "회원·지원·분석·면접·AI 호출 현황 카운트를 봅니다." },
    { label: "분석 통계 상세", path: "/admin/analytics", description: "플랜 분포·적합도 점수 밴드·반복 부족 역량을 분석합니다." },
    { label: "시스템 로그", path: "/admin/logs", description: "전체 사용자의 AI 호출 이력과 실패를 시간순으로 조회합니다." },
    { label: "적합도 프롬프트", path: "/admin/prompts/fit-analysis", description: "적합도 분석 프롬프트 템플릿을 수정합니다." },
    { label: "장기 분석 프롬프트", path: "/admin/prompts/analytics", description: "장기 경향·대시보드 요약 프롬프트를 수정합니다." },
  ],
};

// ── 시스템 로그: 전체 사용자 AI 호출 이력(최신순). 성공/실패 혼합으로 필터 데모가 동작한다. ──
// 회원: 김데모(demo@careertuner.dev) · 이서연(seoyeon@careertuner.dev) · 박지훈(jihoon@careertuner.dev) 등.
const aiUsageLogs: AdminAiUsageLogRow[] = [
  {
    id: 9101,
    userId: 9001,
    userEmail: "demo@careertuner.dev",
    featureType: "FIT_ANALYSIS",
    status: "SUCCESS",
    model: "mock-demo",
    tokenUsage: 3120,
    creditUsed: 2,
    errorMessage: null,
    createdAt: iso(0),
  },
  {
    id: 9102,
    userId: 9001,
    userEmail: "demo@careertuner.dev",
    featureType: "INTERVIEW_QUESTION",
    status: "SUCCESS",
    model: "mock-demo",
    tokenUsage: 1860,
    creditUsed: 2,
    errorMessage: null,
    createdAt: iso(0),
  },
  {
    id: 9103,
    userId: 9002,
    userEmail: "seoyeon@careertuner.dev",
    featureType: "JOB_ANALYSIS",
    status: "SUCCESS",
    model: "mock-demo",
    tokenUsage: 2440,
    creditUsed: 1,
    errorMessage: null,
    createdAt: iso(1),
  },
  {
    id: 9104,
    userId: 9003,
    userEmail: "jihoon@careertuner.dev",
    featureType: "FIT_ANALYSIS",
    status: "FAILED",
    model: "mock-demo",
    tokenUsage: null,
    creditUsed: null,
    errorMessage: "AI 응답 파싱 실패 (timeout 30s 초과) — 재시도 대기",
    createdAt: iso(1),
  },
  {
    id: 9105,
    userId: 9002,
    userEmail: "seoyeon@careertuner.dev",
    featureType: "COMPANY_ANALYSIS",
    status: "SUCCESS",
    model: "mock-demo",
    tokenUsage: 2980,
    creditUsed: 1,
    errorMessage: null,
    createdAt: iso(2),
  },
  {
    id: 9106,
    userId: 9001,
    userEmail: "demo@careertuner.dev",
    featureType: "INTERVIEW_ANSWER_EVAL",
    status: "SUCCESS",
    model: "mock-demo",
    tokenUsage: 1520,
    creditUsed: 1,
    errorMessage: null,
    createdAt: iso(2),
  },
  {
    id: 9107,
    userId: 9004,
    userEmail: "minjun@careertuner.dev",
    featureType: "CAREER_TREND",
    status: "FALLBACK",
    model: "mock-demo",
    tokenUsage: 980,
    creditUsed: 3,
    errorMessage: "1차 모델 오류로 폴백 응답 사용",
    createdAt: iso(3),
  },
  {
    id: 9108,
    userId: 9003,
    userEmail: "jihoon@careertuner.dev",
    featureType: "INTERVIEW_REPORT",
    status: "SUCCESS",
    model: "mock-demo",
    tokenUsage: 4210,
    creditUsed: 3,
    errorMessage: null,
    createdAt: iso(3),
  },
  {
    id: 9109,
    userId: null,
    userEmail: null,
    featureType: "JOB_ANALYSIS",
    status: "FAILED",
    model: null,
    tokenUsage: null,
    creditUsed: null,
    errorMessage: "사용자 토큰 만료 — 인증 컨텍스트 없음",
    createdAt: iso(4),
  },
  {
    id: 9110,
    userId: 9005,
    userEmail: "hayoon@careertuner.dev",
    featureType: "FIT_ANALYSIS",
    status: "SUCCESS",
    model: "mock-demo",
    tokenUsage: 3340,
    creditUsed: 2,
    errorMessage: null,
    createdAt: iso(5),
  },
];

const emailAuditRows: EmailAuditRow[] = [
  {
    id: 9301,
    userId: 9001,
    email: "demo@careertuner.dev",
    purpose: "VERIFY",
    status: "USED",
    used: true,
    createdAt: iso(12),
    expiredAt: iso(11),
    usedAt: iso(12),
  },
  {
    id: 9302,
    userId: 9002,
    email: "jiwon.park@example.com",
    purpose: "RESET_PW",
    status: "PENDING",
    used: false,
    createdAt: iso(1),
    expiredAt: iso(-1),
    usedAt: null,
  },
  {
    id: 9303,
    userId: null,
    email: "unknown@example.com",
    purpose: "RESET_PW",
    status: "EXPIRED",
    used: false,
    createdAt: iso(48),
    expiredAt: iso(47),
    usedAt: null,
  },
];

const loginAuditRows: AdminLoginAuditRow[] = [
  {
    id: 9401,
    userId: 9001,
    userEmail: "demo@careertuner.dev",
    userName: "김데모",
    eventType: "LOGIN",
    authProvider: "LOCAL",
    loginMethod: "PASSWORD",
    loginIdentifier: "demo@careertuner.dev",
    success: true,
    failReason: null,
    ipAddress: "211.234.12.45",
    userAgent: "CareerTuner static demo",
    requestUri: "/api/auth/login",
    createdAt: iso(1),
  },
  {
    id: 9402,
    userId: 9004,
    userEmail: "junho.choi@example.com",
    userName: "최준호",
    eventType: "LOGIN",
    authProvider: "LOCAL",
    loginMethod: "PASSWORD",
    loginIdentifier: "junho.choi@example.com",
    success: false,
    failReason: "비밀번호 불일치",
    ipAddress: "118.45.201.7",
    userAgent: "CareerTuner static demo",
    requestUri: "/api/auth/login",
    createdAt: iso(5),
  },
];

export const adminCoreRoutes: MockRoute[] = [
  // ── 운영 종합 대시보드 현황 카운트 ──
  { method: "GET", pattern: /^\/admin\/dashboard\/overview$/, handler: () => ({ ...dashboardOverview }) },

  // ── 관리자 홈 작업 대기 큐 + 바로가기 ──
  { method: "GET", pattern: /^\/admin\/home\/summary$/, handler: () => ({ ...homeSummary, shortcuts: [...homeSummary.shortcuts] }) },

  // ── 시스템 로그(AI 사용 이력). status 쿼리로 필터, limit 으로 상한 적용. ──
  {
    method: "GET",
    pattern: /^\/admin\/logs\/ai-usage$/,
    handler: ({ query }: MockContext) => {
      const status = query.get("status");
      const limit = Number(query.get("limit") ?? 100) || 100;
      const filtered = status ? aiUsageLogs.filter((row) => row.status === status) : aiUsageLogs;
      return filtered.slice(0, limit).map((row) => ({ ...row }));
    },
  },

  // ── 이메일 인증/비밀번호 재설정 발급 감사 ──
  {
    method: "GET",
    pattern: /^\/admin\/email-audit$/,
    handler: ({ query }: MockContext) => {
      const email = (query.get("email") ?? "").trim().toLowerCase();
      const purpose = (query.get("purpose") ?? "").trim();
      const status = (query.get("status") ?? "").trim();
      const limit = Number(query.get("limit") ?? 200) || 200;
      return emailAuditRows
        .filter((row) => !email || row.email.toLowerCase().includes(email))
        .filter((row) => !purpose || row.purpose === purpose)
        .filter((row) => !status || row.status === status)
        .slice(0, limit)
        .map((row) => ({ ...row }));
    },
  },

  // ── 로그인 감사 공통 그리드 계약 ──
  {
    method: "GET",
    pattern: /^\/admin\/audit\/logins$/,
    handler: ({ query }: MockContext) => {
      const keyword = (query.get("keyword") ?? "").trim().toLowerCase();
      const eventType = query.get("filters[eventType]") ?? "";
      const provider = query.get("filters[authProvider]") ?? "";
      const result = query.get("filters[result]") ?? "";
      const page = Math.max(1, Number(query.get("page") ?? 1) || 1);
      const size = Math.max(1, Number(query.get("size") ?? 20) || 20);
      const filtered = loginAuditRows
        .filter((row) => !keyword || [row.userEmail, row.loginIdentifier, row.ipAddress]
          .some((value) => value?.toLowerCase().includes(keyword)))
        .filter((row) => !eventType || row.eventType === eventType)
        .filter((row) => !provider || row.authProvider === provider)
        .filter((row) => result !== "SUCCESS" || row.success)
        .filter((row) => result !== "FAIL" || !row.success);
      const totalPages = Math.max(1, Math.ceil(filtered.length / size));
      const safePage = Math.min(page, totalPages);
      return {
        items: filtered.slice((safePage - 1) * size, safePage * size).map((row) => ({ ...row })),
        total: filtered.length,
        page: safePage,
        size,
        totalPages,
      };
    },
  },
];
