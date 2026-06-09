// 데모/목 데이터(C 담당 + 공통 인증). VITE_USE_MOCK=true 일 때 backend 없이 화면을 채운다.
// 일관된 페르소나: 프론트엔드 개발자 지망 "김데모". 타입은 각 feature 의 응답 타입을 그대로 따른다.
import type { MeUser, TokenResponse } from "@/app/auth/AuthContext";
import type {
  DashboardSummary,
  DashboardApplication,
  DashboardActivity,
} from "@/features/dashboard/types/dashboardSummary";
import type { HomeSummary } from "@/features/home/types/homeSummary";
import type { AnalysisSummary, CareerAnalysisRun } from "@/features/analysis/types/analysisSummary";
import type { FitAnalysisDetail, FitAnalysisLearningTask } from "@/features/analysis/types/fitAnalysis";

const now = Date.now();
const iso = (daysAgo: number) => new Date(now - daysAgo * 86_400_000).toISOString();

export const demoUser: MeUser = {
  id: 9001,
  email: "demo@careertuner.dev",
  name: "김데모",
  role: "USER",
  userType: "JOB_SEEKER",
  emailVerified: true,
  plan: "PRO",
  credit: 50,
};

export const demoTokenResponse: TokenResponse = {
  accessToken: "demo-access-token",
  refreshToken: "demo-refresh-token",
  tokenType: "Bearer",
  expiresIn: 1800,
  user: demoUser,
};

const dashboardApplications: DashboardApplication[] = [
  { id: 102, companyName: "네이버", jobTitle: "프론트엔드 개발자", postingDate: iso(8), status: "APPLIED", favorite: true, fitScore: 84, interviewCount: 2, latestInterviewScore: 80, tags: ["React", "TypeScript"], updatedAt: iso(1), analyzedAt: iso(2) },
  { id: 101, companyName: "카카오", jobTitle: "프론트엔드 개발자", postingDate: iso(5), status: "READY", favorite: false, fitScore: 78, interviewCount: 1, latestInterviewScore: 74, tags: ["React", "협업"], updatedAt: iso(2), analyzedAt: iso(3) },
  { id: 104, companyName: "라인", jobTitle: "프론트엔드 개발자", postingDate: iso(12), status: "READY", favorite: false, fitScore: 71, interviewCount: 0, latestInterviewScore: null, tags: ["Vue", "REST API"], updatedAt: iso(4), analyzedAt: iso(4) },
  { id: 103, companyName: "토스", jobTitle: "웹 프론트엔드 엔지니어", postingDate: iso(1), status: "ANALYZING", favorite: true, fitScore: null, interviewCount: 0, latestInterviewScore: null, tags: ["TypeScript"], updatedAt: iso(0), analyzedAt: null },
];

const activities: DashboardActivity[] = [
  { type: "FIT_ANALYSIS", applicationCaseId: 102, content: "네이버 프론트엔드 적합도 분석 완료 (84점)", occurredAt: iso(2), score: 84 },
  { type: "INTERVIEW", applicationCaseId: 102, content: "네이버 모의면접 2회차 진행", occurredAt: iso(1), score: 80 },
  { type: "FIT_ANALYSIS", applicationCaseId: 101, content: "카카오 프론트엔드 적합도 분석 완료 (78점)", occurredAt: iso(3), score: 78 },
  { type: "APPLICATION", applicationCaseId: 103, content: "토스 웹 프론트엔드 지원 건 생성", occurredAt: iso(1), score: null },
];

const skillGaps = [
  { skill: "TypeScript", count: 3, total: 4, percentage: 75 },
  { skill: "AWS", count: 2, total: 4, percentage: 50 },
  { skill: "테스트(Jest)", count: 2, total: 4, percentage: 50 },
  { skill: "CI/CD", count: 1, total: 4, percentage: 25 },
];

const dashboardAiSummary =
  "현재 4건 중 3건을 분석했고 평균 적합도는 78점입니다. 반복 부족 역량은 TypeScript와 AWS입니다. " +
  "네이버 지원 건이 84점으로 가장 높아 우선 준비를 권장합니다. 다음 액션: TypeScript 실무 프로젝트 1건과 AWS 배포 경험을 보강하세요.";

export const demoDashboardSummary: DashboardSummary = {
  user: { name: demoUser.name, plan: demoUser.plan, credit: demoUser.credit },
  stats: {
    activeApplications: 4,
    newApplicationsThisMonth: 2,
    totalInterviews: 3,
    interviewsThisWeek: 1,
    credit: 50,
    creditLimit: 100,
    creditsUsedThisMonth: 12,
    averageFitScore: 78,
  },
  focus: {
    headline: "네이버 프론트엔드 지원 건을 마무리하세요",
    description: "적합도 84점으로 가장 유망합니다. 면접 답변에서 TypeScript 경험을 보강하면 합격 가능성이 높아집니다.",
    readiness: 84,
  },
  recentApplications: dashboardApplications,
  todos: [
    { done: false, task: "네이버 면접 예상 질문 3개 답변 연습", time: "오늘" },
    { done: false, task: "TypeScript 리팩토링 프로젝트 포트폴리오 추가", time: "이번 주" },
    { done: true, task: "카카오 자기소개서 첨삭 반영", time: "어제" },
  ],
  activities,
  skillGaps,
  aiSummary: dashboardAiSummary,
  analysisRun: {
    id: 5001,
    analysisType: "DASHBOARD_SUMMARY",
    status: "FALLBACK",
    model: "mock-demo",
    tokenUsage: 0,
    errorMessage: null,
    retryable: false,
    createdAt: iso(0),
  },
};

export const demoHomeSummary: HomeSummary = {
  user: { name: demoUser.name, plan: demoUser.plan, credit: demoUser.credit },
  focus: demoDashboardSummary.focus,
  aiSummary: dashboardAiSummary,
  recentApplications: dashboardApplications,
  nextActions: demoDashboardSummary.todos,
  recentActivities: activities,
};

const careerTrendSummary =
  "최근 지원에서 TypeScript와 AWS가 반복적으로 부족 역량으로 나타납니다. 프론트엔드 직무 준비도가 78점으로 안정적이며, " +
  "네이버·카카오처럼 React 비중이 큰 공고에서 적합도가 높습니다. 다음 지원은 TypeScript 실무 비중이 높은 공고를 우선하세요.";

const analysisRun: CareerAnalysisRun = {
  id: 6001,
  analysisType: "CAREER_TREND",
  status: "FALLBACK",
  inputSnapshot: null,
  result: JSON.stringify({ trendSummary: careerTrendSummary, recommendedDirections: [] }),
  model: "mock-demo",
  tokenUsage: 0,
  errorMessage: null,
  retryable: false,
  createdAt: iso(0),
};

export const demoAnalysisSummary: AnalysisSummary = {
  stats: { totalApplications: 4, analyzedApplications: 3, averageFitScore: 78, highFitApplications: 2, readyApplications: 3 },
  skillGaps,
  jobReadiness: [
    { jobTitle: "프론트엔드 개발자", readiness: 78, applicationCount: 3, trend: "up" },
    { jobTitle: "웹 프론트엔드 엔지니어", readiness: 65, applicationCount: 1, trend: "neutral" },
  ],
  scoreHistory: [
    { label: "5월 20일", score: 68 },
    { label: "5월 28일", score: 74 },
    { label: "6월 4일", score: 78 },
    { label: "6월 8일", score: 84 },
  ],
  applications: dashboardApplications.map((a) => ({
    applicationCaseId: a.id,
    companyName: a.companyName,
    jobTitle: a.jobTitle,
    postingDate: a.postingDate,
    status: a.status,
    favorite: a.favorite,
    fitScore: a.fitScore,
    analyzedAt: a.analyzedAt,
  })),
  recommendedDirections: [
    "TypeScript 실무 비중이 높은 공고를 우선 지원하세요.",
    "AWS 배포 경험을 포트폴리오에 추가하면 적합도가 오릅니다.",
    "네이버 지원 건은 면접 단계이므로 답변 구체화에 집중하세요.",
  ],
  trendSummary: careerTrendSummary,
  interviewTrend: { totalSessions: 3, averageSessionScore: 78, totalAnswers: 14, averageAnswerScore: 76 },
  analysisRun,
};

// 적합도 분석 상세(지원 건별). applicationCaseId 로 조회한다.
function learningTasks(fitId: number): FitAnalysisLearningTask[] {
  return [
    { id: fitId * 10 + 1, fitAnalysisId: fitId, skill: "TypeScript", title: "TypeScript로 기존 프로젝트 리팩토링", practiceTask: "JS 컴포넌트 3개를 TS로 전환하고 타입 정의 추가", expectedDuration: "2주", priority: "HIGH", sortOrder: 1, completed: false, completedAt: null },
    { id: fitId * 10 + 2, fitAnalysisId: fitId, skill: "AWS", title: "AWS S3 + CloudFront 정적 배포", practiceTask: "포트폴리오를 S3에 배포하고 CDN 연결", expectedDuration: "1주", priority: "MEDIUM", sortOrder: 2, completed: false, completedAt: null },
    { id: fitId * 10 + 3, fitAnalysisId: fitId, skill: "테스트", title: "Jest로 핵심 로직 단위 테스트", practiceTask: "유틸 함수와 훅에 테스트 커버리지 추가", expectedDuration: "3일", priority: "MEDIUM", sortOrder: 3, completed: true, completedAt: iso(1) },
  ];
}

const fitAnalyses: FitAnalysisDetail[] = [
  {
    id: 201, applicationCaseId: 101, fitScore: 78,
    matchedSkills: JSON.stringify(["React", "JavaScript", "REST API", "협업"]),
    missingSkills: JSON.stringify(["TypeScript", "테스트"]),
    recommendedStudy: JSON.stringify(["TypeScript 실무", "Jest 단위 테스트"]),
    recommendedCertificates: JSON.stringify(["정보처리기사"]),
    strategy: "React 경험은 충분합니다. TypeScript와 테스트 경험을 보강하면 적합도가 올라갑니다.",
    sourceSnapshot: null,
    scoreBasis: JSON.stringify(["필수 기술 React 충족", "우대 기술 TypeScript 미충족", "협업 경험 우수"]),
    gapRecommendations: JSON.stringify([
      { skill: "TypeScript", category: "PREFERRED_GAP", priority: "HIGH", reason: "공고 우대 조건이며 최근 지원에서 반복적으로 부족" },
      { skill: "테스트(Jest)", category: "LONG_TERM_GROWTH", priority: "MEDIUM", reason: "유지보수성과 코드 품질을 보여줄 수 있음" },
    ]),
    certificateRecommendations: JSON.stringify([
      { name: "정보처리기사", priority: "MEDIUM", reason: "신입/주니어 지원 시 기본 신뢰도" },
    ]),
    strategyActions: JSON.stringify(["TypeScript 리팩토링 프로젝트 1건 추가", "테스트 커버리지 사례 포트폴리오화"]),
    model: "mock-demo", status: "FALLBACK", errorMessage: null, createdAt: iso(3),
    application: { id: 101, companyName: "카카오", jobTitle: "프론트엔드 개발자", postingDate: iso(5), status: "READY", favorite: false, updatedAt: iso(2) },
    learningTasks: learningTasks(201),
  },
  {
    id: 202, applicationCaseId: 102, fitScore: 84,
    matchedSkills: JSON.stringify(["React", "TypeScript", "REST API", "성능 최적화"]),
    missingSkills: JSON.stringify(["AWS"]),
    recommendedStudy: JSON.stringify(["AWS 배포 기초"]),
    recommendedCertificates: JSON.stringify(["AWS Cloud Practitioner"]),
    strategy: "적합도가 높습니다. 면접에서 성능 최적화 경험을 구체적 수치로 설명하세요.",
    sourceSnapshot: null,
    scoreBasis: JSON.stringify(["필수 기술 대부분 충족", "TypeScript 실무 경험 보유", "클라우드 배포 경험 부족"]),
    gapRecommendations: JSON.stringify([
      { skill: "AWS", category: "PREFERRED_GAP", priority: "MEDIUM", reason: "배포/운영 경험을 보여주면 가산점" },
    ]),
    certificateRecommendations: JSON.stringify([
      { name: "AWS Cloud Practitioner", priority: "LOW", reason: "기초 클라우드 이해 증빙" },
    ]),
    strategyActions: JSON.stringify(["성능 개선 정량 성과 정리", "AWS 배포 과정 문서화"]),
    model: "mock-demo", status: "FALLBACK", errorMessage: null, createdAt: iso(2),
    application: { id: 102, companyName: "네이버", jobTitle: "프론트엔드 개발자", postingDate: iso(8), status: "APPLIED", favorite: true, updatedAt: iso(1) },
    learningTasks: learningTasks(202),
  },
];

export const demoFitAnalyses = fitAnalyses;

export function findFitByApplicationCase(applicationCaseId: number): FitAnalysisDetail | undefined {
  return fitAnalyses.find((f) => f.applicationCaseId === applicationCaseId);
}

export const demoAnalysisHistory: CareerAnalysisRun[] = [
  analysisRun,
  { ...analysisRun, id: 6000, createdAt: iso(7) },
];
