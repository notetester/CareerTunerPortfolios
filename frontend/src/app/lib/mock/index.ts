// 데모/목 API 레지스트리. VITE_USE_MOCK=true 일 때 api() 가 네트워크 대신 이 핸들러로 응답한다.
// 사용자 앱 전 도메인 + 관리자 콘솔 전 도메인을 채운다(백엔드 없이 모든 화면이 데이터가 있는 듯 동작).
//   - 핵심(여기 coreRoutes): 인증 + C(home/dashboard/analysis/fit/plan) + B 지원건 상세 + D 가상 면접.
//   - 도메인별 모듈(./domains/*): billing/community/notification/support/profile + applications·interview 잔여,
//     그리고 ./domains/admin/* (운영 대시보드·회원·콘텐츠·결제·분석·면접·프롬프트).
// 공통 계약(MockRoute/ok/iso 등)은 ./registry.ts. 새 엔드포인트는 해당 도메인 모듈의 routes 배열에
//   { method, pattern, handler } 한 줄을 추가한다(공통 인프라, additive). 미등록 엔드포인트만 "데모 미제공".
import {
  demoTokenResponse,
  demoUser,
  demoHomeSummary,
  demoDashboardSummary,
  demoAnalysisSummary,
  demoAnalysisHistory,
  demoCareerPlan,
  demoApplicationCases,
  demoCareerCertificateStrategy,
  demoCareerRoadmap,
  demoFitAnalyses,
  findFitByApplicationCase,
  findFitHistoryByApplicationCase,
  searchDemoCertificates,
} from "./data";
import { findJobPosting, findJobAnalysis, findCompanyAnalysis } from "./domains/applications";
import {
  demoInterviewSessions, findSessionQuestions, findReport, progress, agentSteps,
  createSession, generateQuestions, submitAnswer as submitInterviewAnswer, followUps,
  realtimeSession, fileAsset, deleteFileAsset,
} from "./domains/interview";
import {
  communityPostPage, demoHotPosts, findCommunityPost, communityCommentsFor, demoPublishedGuideline,
  demoFaqs, demoNotices,
  demoAdminReports,
  demoAdminNotices, demoAdminFaqs, demoAdminGuidelines, demoAdminTickets, adminTicketDetail,
  demoAdminNotifications,
} from "./domains/f-area";

import type { FitAnalysisDetail, FitAnalysisHistoryEntry } from "@/features/analysis/types/fitAnalysis";
import { ADMIN_PERMISSION_CODES, canAccessMockApi } from "@/admin/auth/adminAccess";
import { getOutageFallbackSnapshot } from "../outageFallback";

import type { MockRoute } from "./registry";
import { ok } from "./registry";
// 도메인별 mock 라우트(공통 인프라, additive). 새 도메인은 ./domains/<name>.ts 에 작성 후 여기에 spread 한다.
import { billingRoutes } from "./domains/billing";
import { chatbotRoutes } from "./domains/chatbot";
import { communityRoutes } from "./domains/community";
import { notificationRoutes } from "./domains/notification";
import { supportRoutes } from "./domains/support";
import { profileRoutes } from "./domains/profile";
import { correctionRoutes } from "./domains/correction";
import { applicationsExtraRoutes } from "./domains/applicationsExtra";
import { interviewExtraRoutes } from "./domains/interviewExtra";
import { collaborationRoutes } from "./domains/collaboration";
import { privacyRoutes } from "./domains/privacy";
import { mfaRoutes } from "./domains/mfa";
import { companyRoutes } from "./domains/company";
import { catalogRoutes } from "./domains/catalog";
import { legalRoutes } from "./domains/legal";
import { adsRoutes } from "./domains/ads";
import { nicknameProfileRoutes } from "./domains/nicknameProfile";
import { plannerRoutes, rewardRoutes } from "./domains/planner";
import { autoprepRoutes } from "./domains/autoprep";
import { adminRoutes } from "./domains/admin";

/** 등록된 핸들러가 없을 때 반환하는 sentinel. */
export const MOCK_UNHANDLED = Symbol("mock-unhandled");
/** 현재 mock 세션으로 접근할 수 없는 관리자 API sentinel. */
export const MOCK_FORBIDDEN = Symbol("mock-forbidden");

let demoJobPostingFallbackSetting = {
  enabled: false,
  allowedStages: [] as string[],
  availableStages: ["JOB_POSTING_PDF_OCR", "JOB_POSTING_IMAGE_OCR"],
  source: "DEFAULT",
};

// 공고 업로드 한도 — 관리자 GET/PATCH 와 사용자 GET(/application-cases/upload-limit)이 이 상태를 공유한다.
const UPLOAD_LIMIT_MB = 1024 * 1024;
let demoUploadLimitBytes = 10 * UPLOAD_LIMIT_MB; // 백엔드 기본 10MB
let demoUploadLimitSource = "PROPERTIES";
function demoUploadLimitSetting() {
  return {
    maxBytes: demoUploadLimitBytes,
    minBytes: 1 * UPLOAD_LIMIT_MB,
    maxAllowedBytes: 20 * UPLOAD_LIMIT_MB,
    source: demoUploadLimitSource,
  };
}

const demoAdminUser = {
  ...demoUser,
  id: 9101,
  email: "admin@careertuner.dev",
  name: "한관리",
  role: "ADMIN",
  permissions: ADMIN_PERMISSION_CODES.filter((code) => !code.startsWith("ADMIN_PERMISSION_")),
};
let mockSessionUser = demoUser;
const MOCK_ROLE_KEY = "careertuner.mock.role";

function setMockSession(user: typeof demoUser) {
  // 장애 fallback은 운영자 콘솔 대체 수단이 아니다. static-demo에서만 명시적 관리자 persona를 허용한다.
  const nextUser = user.role === "ADMIN" && getOutageFallbackSnapshot().mode !== "static-demo"
    ? demoUser
    : user;
  mockSessionUser = nextUser;
  if (typeof localStorage !== "undefined") {
    if (nextUser.role === "ADMIN") localStorage.setItem(MOCK_ROLE_KEY, "ADMIN");
    else localStorage.removeItem(MOCK_ROLE_KEY);
  }
}

function getMockSession() {
  if (
    getOutageFallbackSnapshot().mode === "static-demo"
    && typeof localStorage !== "undefined"
    && localStorage.getItem(MOCK_ROLE_KEY) === "ADMIN"
  ) {
    return demoAdminUser;
  }
  return getOutageFallbackSnapshot().mode === "static-demo" ? mockSessionUser : demoUser;
}

export function canResolveMockRequest(rawPath: string, method = "GET", body?: unknown): boolean {
  const session = getMockSession();
  const permissions = "permissions" in session && Array.isArray(session.permissions)
    ? new Set<string>(session.permissions)
    : new Set<string>();
  return canAccessMockApi(rawPath, session.role, permissions, method, body);
}

// ── C: 적합도 분석 mock 세션 상태 ──
// 데모 데이터(101 카카오·102 네이버)에 없는 지원 건에 demoFitAnalyses[0](카카오)를 fallback 으로 반환하면
// 새 지원 건에 남의 회사 분석이 표시된다. POST(생성)는 demoFitAnalyses[0] 을 해당 지원 건 정보로 치환한
// 사본을 만들어 이 Map 에 보관하고(세션 내 유지), GET 미존재는 null(레지스트리의 기존 not-found 패턴 —
// 상태코드/에러 메커니즘이 없고 useApplicationFitAnalysis 가 null 을 '분석 미실행 안내'로 처리한다).
const generatedFitAnalyses = new Map<number, FitAnalysisDetail>();
const generatedFitHistory = new Map<number, FitAnalysisHistoryEntry[]>();
let nextGeneratedFitAnalysisId = 251; // 정적 201·202(학습 과제 id 2011~/2021~ 대역)와 충돌하지 않는 대역

function findAnyFitByApplicationCase(applicationCaseId: number): FitAnalysisDetail | undefined {
  return generatedFitAnalyses.get(applicationCaseId) ?? findFitByApplicationCase(applicationCaseId);
}

function buildGeneratedFitAnalysis(applicationCaseId: number, requestedModel: string): FitAnalysisDetail {
  const template = findAnyFitByApplicationCase(applicationCaseId) ?? demoFitAnalyses[0];
  const applicationCase = demoApplicationCases.find((item) => item.id === applicationCaseId);
  const id = nextGeneratedFitAnalysisId++;
  return {
    ...template,
    id,
    applicationCaseId,
    model: requestedModel === "AUTO" ? "mock-demo" : `mock-demo:${requestedModel}`,
    createdAt: new Date().toISOString(),
    application: {
      id: applicationCaseId,
      companyName: applicationCase?.companyName ?? "새 지원 기업",
      jobTitle: applicationCase?.jobTitle ?? "프론트엔드 개발자",
      postingDate: applicationCase?.postingDate ?? null,
      status: applicationCase?.status ?? "READY",
      favorite: applicationCase?.favorite ?? false,
      updatedAt: applicationCase?.updatedAt ?? new Date().toISOString(),
    },
    // 학습 과제는 새 id/fitAnalysisId 로 복제(토글 상태가 템플릿 원본과 섞이지 않게), 미완료로 시작한다.
    learningTasks: template.learningTasks.map((task, index) => ({
      ...task,
      id: id * 10 + index + 1,
      fitAnalysisId: id,
      completed: false,
      completedAt: null,
    })),
  };
}

const coreRoutes: MockRoute[] = [
  // ── 인증(공통 게이트) ──
  {
    method: "POST",
    pattern: /^\/auth\/login$/,
    handler: ({ body }) => {
      const email = String((body as { email?: unknown })?.email ?? "").toLowerCase();
      setMockSession(email === demoAdminUser.email ? demoAdminUser : demoUser);
      // MFA 도입 이후 LoginResponse 는 token 을 중첩으로 담는다 — flat 반환 시 로그인이 조용히 실패(토큰 미저장).
      return {
        mfaRequired: false,
        mfaSetupRecommended: false,
        challengeToken: null,
        challengeMethod: null,
        expiresIn: demoTokenResponse.expiresIn,
        token: { ...demoTokenResponse, user: getMockSession() },
      };
    },
  },
  { method: "POST", pattern: /^\/auth\/register$/, handler: ok(demoTokenResponse) },
  { method: "GET", pattern: /^\/auth\/me$/, handler: () => getMockSession() },
  { method: "POST", pattern: /^\/auth\/logout$/, handler: () => { setMockSession(demoUser); return null; } },
  {
    method: "GET",
    pattern: /^\/auth\/oauth\/providers$/,
    handler: () => ({ google: true, kakao: true, naver: true }),
  },

  {
    method: "GET",
    pattern: /^\/admin\/ai-settings\/job-posting-fallback$/,
    handler: () => demoJobPostingFallbackSetting,
  },
  {
    method: "PATCH",
    pattern: /^\/admin\/ai-settings\/job-posting-fallback$/,
    handler: ({ body }) => {
      const request = body as { enabled?: boolean; allowedStages?: string[] };
      demoJobPostingFallbackSetting = {
        ...demoJobPostingFallbackSetting,
        enabled: !!request?.enabled,
        allowedStages: request?.enabled ? request.allowedStages ?? [] : [],
        source: "DATABASE",
      };
      return demoJobPostingFallbackSetting;
    },
  },
  // 공고 업로드 한도 — 사용자 GET + 관리자 GET/PATCH 가 같은 상태(demoUploadLimitBytes)를 공유한다.
  {
    method: "GET",
    pattern: /^\/application-cases\/upload-limit$/,
    handler: () => ({ maxBytes: demoUploadLimitBytes }),
  },
  {
    method: "GET",
    pattern: /^\/admin\/ai-settings\/upload-size$/,
    handler: () => demoUploadLimitSetting(),
  },
  {
    method: "PATCH",
    pattern: /^\/admin\/ai-settings\/upload-size$/,
    handler: ({ body }) => {
      const request = body as { maxBytes?: number };
      if (typeof request?.maxBytes === "number") {
        demoUploadLimitBytes = Math.max(1 * UPLOAD_LIMIT_MB, Math.min(20 * UPLOAD_LIMIT_MB, request.maxBytes));
        demoUploadLimitSource = "DATABASE";
      }
      return demoUploadLimitSetting();
    },
  },

  // ── C: 홈 / 대시보드 / 취업 분석 ──
  { method: "GET", pattern: /^\/home\/summary$/, handler: ok(demoHomeSummary) },
  { method: "GET", pattern: /^\/dashboard\/summary$/, handler: ok(demoDashboardSummary) },
  { method: "POST", pattern: /^\/dashboard\/summary\/refresh$/, handler: ok(demoDashboardSummary) },

  // ── C: 오늘의 할 일 완료 처리/추가/삭제 (세션 내 in-memory 상태 유지) ──
  { method: "GET", pattern: /^\/dashboard\/todos$/, handler: () => [...demoDashboardSummary.todos] },
  {
    method: "POST",
    pattern: /^\/dashboard\/todos$/,
    handler: ({ body }) => {
      const request = body as { task?: string; time?: string };
      demoDashboardSummary.todos.push({
        id: 7000 + demoDashboardSummary.todos.length + 1,
        derivedKey: null,
        source: "USER",
        done: false,
        task: request?.task ?? "",
        time: request?.time ?? "오늘",
      });
      return [...demoDashboardSummary.todos];
    },
  },
  {
    method: "PATCH",
    pattern: /^\/dashboard\/todos\/derived$/,
    handler: ({ body }) => {
      const request = body as { derivedKey?: string; done?: boolean };
      const target = demoDashboardSummary.todos.find((todo) => todo.derivedKey === request?.derivedKey);
      if (target) target.done = !!request?.done;
      return [...demoDashboardSummary.todos];
    },
  },
  {
    method: "PATCH",
    pattern: /^\/dashboard\/todos\/(\d+)$/,
    handler: ({ params, body }) => {
      const target = demoDashboardSummary.todos.find((todo) => todo.id === Number(params[0]));
      if (target) target.done = !!(body as { done?: boolean })?.done;
      return [...demoDashboardSummary.todos];
    },
  },
  {
    method: "DELETE",
    pattern: /^\/dashboard\/todos\/(\d+)$/,
    handler: ({ params }) => {
      const index = demoDashboardSummary.todos.findIndex((todo) => todo.id === Number(params[0]));
      if (index >= 0) demoDashboardSummary.todos.splice(index, 1);
      return [...demoDashboardSummary.todos];
    },
  },
  { method: "GET", pattern: /^\/analysis\/summary$/, handler: ok(demoAnalysisSummary) },
  { method: "POST", pattern: /^\/analysis\/summary\/refresh$/, handler: ok(demoAnalysisSummary) },
  { method: "GET", pattern: /^\/analysis\/history$/, handler: ok(demoAnalysisHistory) },
  { method: "GET", pattern: /^\/analysis\/plan$/, handler: ok(demoCareerPlan) },
  {
    method: "PUT",
    pattern: /^\/analysis\/plan\/goal$/,
    handler: ({ body }) => {
      const request = body as {
        targetJob?: string | null;
        targetPeriod?: string | null;
        prioritySkill?: string | null;
        preferredCompanyType?: string | null;
      };
      demoCareerPlan.goal = {
        id: demoCareerPlan.goal?.id ?? 9501,
        targetJob: request.targetJob ?? null,
        targetPeriod: request.targetPeriod ?? null,
        prioritySkill: request.prioritySkill ?? null,
        preferredCompanyType: request.preferredCompanyType ?? null,
        updatedAt: new Date().toISOString(),
      };
      return demoCareerPlan.goal;
    },
  },
  {
    method: "POST",
    pattern: /^\/analysis\/plan\/learning-plans$/,
    handler: ({ body }) => {
      const request = body as { title?: string; targetSkill?: string; startDate?: string | null; endDate?: string | null };
      const plan = {
        id: 9600 + demoCareerPlan.learningPlans.length + 1,
        title: request.title ?? "",
        targetSkill: request.targetSkill ?? "",
        startDate: request.startDate ?? null,
        endDate: request.endDate ?? null,
        status: "ACTIVE",
        completionRate: 0,
        tasks: [],
      };
      demoCareerPlan.learningPlans.unshift(plan);
      return plan;
    },
  },
  {
    method: "POST",
    pattern: /^\/analysis\/plan\/learning-plans\/(\d+)\/tasks$/,
    handler: ({ params, body }) => {
      const plan = demoCareerPlan.learningPlans.find((item) => item.id === Number(params[0]));
      if (!plan) return null;
      const task = {
        id: 9700 + plan.tasks.length + 1,
        learningPlanId: plan.id,
        task: (body as { task?: string })?.task ?? "",
        done: false,
        sortOrder: plan.tasks.length + 1,
        completedAt: null,
      };
      plan.tasks.push(task);
      plan.completionRate = Math.round((plan.tasks.filter((item) => item.done).length * 100) / plan.tasks.length);
      return task;
    },
  },
  {
    method: "PATCH",
    pattern: /^\/analysis\/plan\/learning-plans\/(\d+)\/tasks\/(\d+)$/,
    handler: ({ params, body }) => {
      const plan = demoCareerPlan.learningPlans.find((item) => item.id === Number(params[0]));
      const task = plan?.tasks.find((item) => item.id === Number(params[1]));
      if (!plan || !task) return null;
      task.done = !!(body as { done?: boolean })?.done;
      task.completedAt = task.done ? new Date().toISOString() : null;
      plan.completionRate = Math.round((plan.tasks.filter((item) => item.done).length * 100) / plan.tasks.length);
      return task;
    },
  },

  // ── 지원 건 상세 셸용 읽기 응답. C 패널 데모 진입에 필요한 최소 B 원본 요약만 제공한다. ──
  { method: "GET", pattern: /^\/application-cases$/, handler: ok(demoApplicationCases) },
  {
    method: "GET",
    pattern: /^\/application-cases\/(\d+)$/,
    handler: ({ params }) => demoApplicationCases.find((item) => item.id === Number(params[0])) ?? demoApplicationCases[0],
  },

  // ── B: 지원 건 상세 서브탭(공고문/공고분석/기업분석). 생성(mock POST)은 저장본을 그대로 반환. ──
  { method: "GET", pattern: /^\/application-cases\/(\d+)\/job-posting$/, handler: ({ params }) => findJobPosting(Number(params[0])) },
  { method: "GET", pattern: /^\/application-cases\/(\d+)\/job-posting\/revisions$/, handler: ({ params }) => { const p = findJobPosting(Number(params[0])); return p ? [p] : []; } },
  { method: "GET", pattern: /^\/application-cases\/(\d+)\/job-analysis$/, handler: ({ params }) => findJobAnalysis(Number(params[0])) },
  { method: "GET", pattern: /^\/application-cases\/(\d+)\/job-analysis\/history$/, handler: ({ params }) => { const a = findJobAnalysis(Number(params[0])); return a ? [a] : []; } },
  { method: "POST", pattern: /^\/application-cases\/(\d+)\/job-analysis(?:\/mock)?$/, handler: ({ params }) => findJobAnalysis(Number(params[0])) },
  { method: "GET", pattern: /^\/application-cases\/(\d+)\/company-analysis$/, handler: ({ params }) => findCompanyAnalysis(Number(params[0])) },
  { method: "GET", pattern: /^\/application-cases\/(\d+)\/company-analysis\/history$/, handler: ({ params }) => { const a = findCompanyAnalysis(Number(params[0])); return a ? [a] : []; } },
  { method: "POST", pattern: /^\/application-cases\/(\d+)\/company-analysis(?:\/mock)?$/, handler: ({ params }) => findCompanyAnalysis(Number(params[0])) },
  { method: "GET", pattern: /^\/application-cases\/(\d+)\/ai-usage\/b\/failures$/, handler: () => [] },

  // ── D: 가상 면접 (세션 목록/생성, 질문 생성·조회, 답변·꼬리질문, 진행·리포트·에이전트) ──
  {
    // 목록 응답은 페이지 envelope( SessionPageResponse: { sessions, total, page, size, hasNext } ).
    // demoInterviewSessions 는 createSession 이 unshift 하는 원본 배열이라 그대로 두고 여기서 감싼다.
    method: "GET",
    pattern: /^\/interview\/sessions$/,
    handler: ({ query }) => {
      const size = Number(query.get("size") ?? 10) || 10;
      return { sessions: demoInterviewSessions, total: demoInterviewSessions.length, page: 0, size, hasNext: false };
    },
  },
  { method: "POST", pattern: /^\/interview\/sessions$/, handler: ({ body }) => createSession(body as { applicationCaseId: number; mode: "BASIC" | "JOB" | "PERSONALITY" | "PRESSURE" | "RESUME" | "PORTFOLIO" | "REAL" | "COMPANY" }) },
  { method: "GET", pattern: /^\/interview\/sessions\/(\d+)\/questions$/, handler: ({ params }) => findSessionQuestions(Number(params[0])) },
  { method: "POST", pattern: /^\/interview\/sessions\/(\d+)\/generate-questions$/, handler: ({ params }) => generateQuestions(Number(params[0])) },
  { method: "GET", pattern: /^\/interview\/sessions\/(\d+)\/progress$/, handler: ({ params }) => progress(Number(params[0])) },
  { method: "GET", pattern: /^\/interview\/sessions\/(\d+)\/report$/, handler: ({ params }) => findReport(Number(params[0])) },
  { method: "GET", pattern: /^\/interview\/sessions\/(\d+)\/agent-steps$/, handler: ({ params }) => agentSteps(Number(params[0])) },
  { method: "POST", pattern: /^\/interview\/sessions\/(\d+)\/realtime$/, handler: () => realtimeSession() },
  { method: "POST", pattern: /^\/interview\/questions\/(\d+)\/answers$/, handler: ({ params, body }) => submitInterviewAnswer(Number(params[0]), body as { answerText: string }) },
  { method: "POST", pattern: /^\/interview\/questions\/(\d+)\/follow-ups$/, handler: ({ params }) => followUps(Number(params[0])) },
  { method: "POST", pattern: /^\/file\/upload$/, handler: ({ body }) => fileAsset(body) },
  { method: "DELETE", pattern: /^\/file\/(\d+)$/, handler: ({ params }) => { deleteFileAsset(Number(params[0])); return null; } },

  // ── C: 적합도 분석 ──
  {
    method: "GET",
    pattern: /^\/fit-analyses$/,
    handler: () => [
      ...generatedFitAnalyses.values(),
      ...demoFitAnalyses.filter((analysis) => !generatedFitAnalyses.has(analysis.applicationCaseId)),
    ],
  },
  // 장기 커리어 자격증 전략(사용자 단위, 현재 지원 건과 분리)
  { method: "GET", pattern: /^\/fit-analyses\/career-certificate-strategy$/, handler: ok(demoCareerCertificateStrategy) },
  // 외부 일정/공공데이터 장애 시에도 로드맵·자격증 검색 시연은 mock fixture로 독립 동작한다.
  {
    method: "GET",
    pattern: /^\/fit-analyses\/career-roadmap$/,
    handler: ({ query }) => ({
      ...demoCareerRoadmap,
      horizonMonths: Number(query.get("months") ?? demoCareerRoadmap.horizonMonths),
    }),
  },
  {
    method: "GET",
    pattern: /^\/certificates\/search$/,
    handler: ({ query }) => searchDemoCertificates(query.get("q") ?? ""),
  },
  {
    // 미존재 지원 건은 null(분석 미실행) — 다른 회사 분석을 fallback 으로 보여주지 않는다.
    method: "GET",
    pattern: /^\/fit-analyses\/application-cases\/(\d+)$/,
    handler: ({ params }) => findAnyFitByApplicationCase(Number(params[0])) ?? null,
  },
  {
    // 재분석 히스토리(점수·역량 변화 추적)
    method: "GET",
    pattern: /^\/fit-analyses\/application-cases\/(\d+)\/history$/,
    handler: ({ params }) => [
      ...(generatedFitHistory.get(Number(params[0])) ?? []),
      ...findFitHistoryByApplicationCase(Number(params[0])),
    ],
  },
  {
    // 생성/재분석. 선택 모델·새 분석 ID·히스토리를 세션 내 유지해 실제 재분석 UX와 같은 계약으로 보인다.
    method: "POST",
    pattern: /^\/fit-analyses\/application-cases\/(\d+)$/,
    handler: ({ params, query }) => {
      const applicationCaseId = Number(params[0]);
      const previous = findAnyFitByApplicationCase(applicationCaseId);
      const requestedModel = query.get("model")?.toUpperCase() || "AUTO";
      const generated = buildGeneratedFitAnalysis(applicationCaseId, requestedModel);
      generatedFitAnalyses.set(applicationCaseId, generated);
      generatedFitHistory.set(applicationCaseId, [{
        id: generated.id,
        fitScore: generated.fitScore,
        previousScore: previous?.fitScore ?? null,
        scoreDelta: generated.fitScore != null && previous?.fitScore != null ? generated.fitScore - previous.fitScore : null,
        gainedSkills: [],
        resolvedGaps: [],
        newGaps: [],
        model: generated.model,
        status: generated.status,
        createdAt: generated.createdAt,
      }, ...(generatedFitHistory.get(applicationCaseId) ?? [])]);
      return generated;
    },
  },
  {
    // 학습 과제 완료 토글: 원본 과제 객체를 직접 갱신해 세션 내(새로고침 전까지) 완료 상태·준비율이 유지되게 한다.
    // (GET /fit-analyses/application-cases/:id 가 같은 객체를 반환하므로 탭 이동·재조회 후에도 반영된다.)
    method: "PATCH",
    pattern: /^\/fit-analyses\/(\d+)\/learning-tasks\/(\d+)$/,
    handler: ({ params, body }) => {
      const fitAnalysisId = Number(params[0]);
      const taskId = Number(params[1]);
      const completed = !!(body as { completed?: boolean })?.completed;
      const task = [...demoFitAnalyses, ...generatedFitAnalyses.values()]
        .flatMap((analysis) => analysis.learningTasks)
        .find((item) => item.id === taskId);
      if (task) {
        task.completed = completed;
        task.completedAt = completed ? new Date().toISOString() : null;
        return { ...task };
      }
      // 데모 데이터에 없는 과제 id 는 기존처럼 요청 값을 echo 한다(영속 대상 없음).
      return {
        id: taskId,
        fitAnalysisId,
        skill: "",
        title: "",
        practiceTask: "",
        expectedDuration: "",
        priority: "MEDIUM",
        sortOrder: 0,
        completed,
        completedAt: completed ? new Date().toISOString() : null,
      };
    },
  },

  // ── F: 커뮤니티 (게시글/인기글/상세/댓글/AI태그/반응/신고/가이드라인) ──
  { method: "GET", pattern: /^\/community\/posts$/, handler: () => communityPostPage() },
  { method: "GET", pattern: /^\/community\/posts\/hot$/, handler: ok(demoHotPosts) },
  { method: "GET", pattern: /^\/community\/posts\/(\d+)$/, handler: ({ params }) => findCommunityPost(Number(params[0])) },
  { method: "GET", pattern: /^\/community\/posts\/(\d+)\/comments$/, handler: ({ params }) => communityCommentsFor(Number(params[0])) },
  { method: "GET", pattern: /^\/community\/posts\/(\d+)\/ai-tags$/, handler: ({ params }) => ({ postId: Number(params[0]), taskType: "태그추천", status: "DONE", resultJson: JSON.stringify({ tags: ["면접", "백엔드", "시스템설계"], confidence: 0.86, applied: true }) }) },
  { method: "POST", pattern: /^\/community\/posts$/, handler: () => ({ postId: 999 }) },
  { method: "POST", pattern: /^\/community\/reactions$/, handler: () => ({ active: true }) },
  { method: "POST", pattern: /^\/community\/reports$/, handler: ok(null) },
  { method: "GET", pattern: /^\/community\/guidelines\/published$/, handler: ok(demoPublishedGuideline) },

  // ── F: 고객센터 (FAQ / 공지 / 내 문의) ──
  { method: "GET", pattern: /^\/support\/faq$/, handler: ok(demoFaqs) },
  { method: "GET", pattern: /^\/support\/notices$/, handler: ok(demoNotices) },
  { method: "GET", pattern: /^\/support\/notices\/(\d+)$/, handler: () => ({ ...demoNotices[0], content: "공지 본문 데모 콘텐츠입니다." }) },
  { method: "GET", pattern: /^\/support\/tickets$/, handler: () => [] },
  { method: "POST", pattern: /^\/support\/tickets$/, handler: ({ body }) => ({ id: 7099, status: "RECEIVED", priority: "NORMAL", createdAt: new Date().toISOString(), ...(body as object) }) },

  // ── F(관리자): 신고 / AI 검열 ──
  { method: "GET", pattern: /^\/admin\/community\/reports$/, handler: ok(demoAdminReports) },
  { method: "GET", pattern: /^\/admin\/community\/reports\/(\d+)$/, handler: ({ params }) => ({ ...(demoAdminReports.find((r) => r.id === Number(params[0])) ?? demoAdminReports[0]), reasons: ["욕설/비방", "허위사실"], aiOpinion: { label: "ABUSE", confidence: 0.91 } }) },
  // ── F(관리자): 공지 / FAQ / 가이드라인 ──
  { method: "GET", pattern: /^\/admin\/notices$/, handler: ok(demoAdminNotices) },
  { method: "GET", pattern: /^\/admin\/faq$/, handler: ok(demoAdminFaqs) },
  { method: "GET", pattern: /^\/admin\/guidelines$/, handler: ok(demoAdminGuidelines) },
  { method: "GET", pattern: /^\/admin\/guidelines\/published$/, handler: ok(demoAdminGuidelines[0]) },

  // ── F(관리자): 문의(티켓) / 알림 발송 ──
  { method: "GET", pattern: /^\/admin\/tickets$/, handler: ok(demoAdminTickets) },
  { method: "GET", pattern: /^\/admin\/tickets\/(\d+)$/, handler: ({ params }) => adminTicketDetail(Number(params[0])) },
  { method: "GET", pattern: /^\/admin\/notifications$/, handler: ok(demoAdminNotifications) },
];

// 핵심(인증·C·B·D) + 도메인별 라우트를 모두 합친 최종 레지스트리. 앞에 오는 핸들러가 우선 매칭된다.
const routes: MockRoute[] = [
  ...coreRoutes,
  ...autoprepRoutes,
  ...applicationsExtraRoutes,
  ...interviewExtraRoutes,
  ...billingRoutes,
  ...chatbotRoutes,
  ...communityRoutes,
  ...notificationRoutes,
  ...supportRoutes,
  ...profileRoutes,
  ...correctionRoutes,
  ...collaborationRoutes,
  ...privacyRoutes,
  ...mfaRoutes,
  ...companyRoutes,
  ...catalogRoutes,
  ...legalRoutes,
  ...adsRoutes,
  ...nicknameProfileRoutes,
  ...plannerRoutes,
  ...rewardRoutes,
  ...adminRoutes,
];

/**
 * mock 응답을 해석한다. 등록된 핸들러가 있으면 그 `data` 페이로드를, 없으면 MOCK_UNHANDLED 를 반환.
 * 약간의 지연을 줘 실제 네트워크처럼 로딩 상태가 자연스럽게 보이도록 한다.
 */
export async function resolveMock(
  rawPath: string,
  options: RequestInit,
): Promise<unknown | typeof MOCK_UNHANDLED | typeof MOCK_FORBIDDEN> {
  const method = (options.method ?? "GET").toUpperCase();
  let body: unknown = options.body;
  if (typeof options.body === "string") {
    try {
      body = JSON.parse(options.body);
    } catch {
      body = options.body;
    }
  }
  if (!canResolveMockRequest(rawPath, method, body)) return MOCK_FORBIDDEN;
  const [path, queryString = ""] = rawPath.split("?");
  const query = new URLSearchParams(queryString);
  const route = routes.find((r) => r.method === method && r.pattern.test(path));
  if (!route) return MOCK_UNHANDLED;

  const match = route.pattern.exec(path);
  const params = match ? match.slice(1) : [];
  await new Promise((resolve) => setTimeout(resolve, 220));
  return route.handler({ method, path, query, params, body });
}
