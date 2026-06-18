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
  demoFitAnalyses,
  findFitByApplicationCase,
  findFitHistoryByApplicationCase,
} from "./data";
import { findJobPosting, findJobAnalysis, findCompanyAnalysis } from "./domains/applications";
import {
  demoInterviewSessions, findSessionQuestions, findReport, progress, agentSteps,
  createSession, generateQuestions, submitAnswer as submitInterviewAnswer, followUps,
  realtimeSession, fileAsset,
} from "./domains/interview";

import type { MockRoute } from "./registry";
import { ok } from "./registry";
// 도메인별 mock 라우트(공통 인프라, additive). 새 도메인은 ./domains/<name>.ts 에 작성 후 여기에 spread 한다.
import { billingRoutes } from "./domains/billing";
import { communityRoutes } from "./domains/community";
import { notificationRoutes } from "./domains/notification";
import { supportRoutes } from "./domains/support";
import { profileRoutes } from "./domains/profile";
import { correctionRoutes } from "./domains/correction";
import { applicationsExtraRoutes } from "./domains/applicationsExtra";
import { interviewExtraRoutes } from "./domains/interviewExtra";
import { adminRoutes } from "./domains/admin";

/** 등록된 핸들러가 없을 때 반환하는 sentinel. */
export const MOCK_UNHANDLED = Symbol("mock-unhandled");

const coreRoutes: MockRoute[] = [
  // ── 인증(공통 게이트) ──
  { method: "POST", pattern: /^\/auth\/login$/, handler: ok(demoTokenResponse) },
  { method: "POST", pattern: /^\/auth\/register$/, handler: ok(demoTokenResponse) },
  { method: "GET", pattern: /^\/auth\/me$/, handler: ok(demoUser) },
  { method: "POST", pattern: /^\/auth\/logout$/, handler: ok(null) },

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
  { method: "POST", pattern: /^\/interview\/sessions$/, handler: ({ body }) => createSession(body as { applicationCaseId: number; mode: "BASIC" | "JOB" | "PERSONALITY" | "PRESSURE" | "RESUME" | "COMPANY" }) },
  { method: "GET", pattern: /^\/interview\/sessions\/(\d+)\/questions$/, handler: ({ params }) => findSessionQuestions(Number(params[0])) },
  { method: "POST", pattern: /^\/interview\/sessions\/(\d+)\/generate-questions$/, handler: ({ params }) => generateQuestions(Number(params[0])) },
  { method: "GET", pattern: /^\/interview\/sessions\/(\d+)\/progress$/, handler: ({ params }) => progress(Number(params[0])) },
  { method: "GET", pattern: /^\/interview\/sessions\/(\d+)\/report$/, handler: ({ params }) => findReport(Number(params[0])) },
  { method: "GET", pattern: /^\/interview\/sessions\/(\d+)\/agent-steps$/, handler: ({ params }) => agentSteps(Number(params[0])) },
  { method: "POST", pattern: /^\/interview\/sessions\/(\d+)\/realtime$/, handler: () => realtimeSession() },
  { method: "POST", pattern: /^\/interview\/questions\/(\d+)\/answers$/, handler: ({ params, body }) => submitInterviewAnswer(Number(params[0]), body as { answerText: string }) },
  { method: "POST", pattern: /^\/interview\/questions\/(\d+)\/follow-ups$/, handler: ({ params }) => followUps(Number(params[0])) },
  { method: "POST", pattern: /^\/file\/upload$/, handler: () => fileAsset() },

  // ── C: 적합도 분석 ──
  { method: "GET", pattern: /^\/fit-analyses$/, handler: ok(demoFitAnalyses) },
  {
    method: "GET",
    pattern: /^\/fit-analyses\/application-cases\/(\d+)$/,
    handler: ({ params }) => findFitByApplicationCase(Number(params[0])) ?? demoFitAnalyses[0],
  },
  {
    // 재분석 히스토리(점수·역량 변화 추적)
    method: "GET",
    pattern: /^\/fit-analyses\/application-cases\/(\d+)\/history$/,
    handler: ({ params }) => findFitHistoryByApplicationCase(Number(params[0])),
  },
  {
    method: "POST",
    pattern: /^\/fit-analyses\/application-cases\/(\d+)$/,
    handler: ({ params }) => findFitByApplicationCase(Number(params[0])) ?? demoFitAnalyses[0],
  },
  {
    // 학습 과제 완료 토글: 요청 body 의 completed 를 그대로 반영해 echo (목이라 영속화는 없음)
    method: "PATCH",
    pattern: /^\/fit-analyses\/(\d+)\/learning-tasks\/(\d+)$/,
    handler: ({ params, body }) => {
      const fitAnalysisId = Number(params[0]);
      const taskId = Number(params[1]);
      const completed = !!(body as { completed?: boolean })?.completed;
      const source = demoFitAnalyses
        .flatMap((f) => f.learningTasks)
        .find((t) => t.id === taskId);
      return {
        id: taskId,
        fitAnalysisId,
        skill: source?.skill ?? "",
        title: source?.title ?? "",
        practiceTask: source?.practiceTask ?? "",
        expectedDuration: source?.expectedDuration ?? "",
        priority: source?.priority ?? "MEDIUM",
        sortOrder: source?.sortOrder ?? 0,
        completed,
        completedAt: completed ? new Date().toISOString() : null,
      };
    },
  },
];

// 핵심(인증·C·B·D) + 도메인별 라우트를 모두 합친 최종 레지스트리. 앞에 오는 핸들러가 우선 매칭된다.
const routes: MockRoute[] = [
  ...coreRoutes,
  ...applicationsExtraRoutes,
  ...interviewExtraRoutes,
  ...billingRoutes,
  ...communityRoutes,
  ...notificationRoutes,
  ...supportRoutes,
  ...profileRoutes,
  ...correctionRoutes,
  ...adminRoutes,
];

/**
 * mock 응답을 해석한다. 등록된 핸들러가 있으면 그 `data` 페이로드를, 없으면 MOCK_UNHANDLED 를 반환.
 * 약간의 지연을 줘 실제 네트워크처럼 로딩 상태가 자연스럽게 보이도록 한다.
 */
export async function resolveMock(
  rawPath: string,
  options: RequestInit,
): Promise<unknown | typeof MOCK_UNHANDLED> {
  const method = (options.method ?? "GET").toUpperCase();
  const [path, queryString = ""] = rawPath.split("?");
  const query = new URLSearchParams(queryString);
  const route = routes.find((r) => r.method === method && r.pattern.test(path));
  if (!route) return MOCK_UNHANDLED;

  const match = route.pattern.exec(path);
  const params = match ? match.slice(1) : [];
  let body: unknown;
  if (typeof options.body === "string") {
    try {
      body = JSON.parse(options.body);
    } catch {
      body = options.body;
    }
  }
  await new Promise((resolve) => setTimeout(resolve, 220));
  return route.handler({ method, path, query, params, body });
}
