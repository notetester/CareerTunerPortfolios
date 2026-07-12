// 데모/목 데이터(C 담당 + 공통 인증). VITE_USE_MOCK=true 일 때 backend 없이 화면을 채운다.
// 일관된 페르소나: 프론트엔드 개발자 지망 "김데모". 타입은 각 feature 의 응답 타입을 그대로 따른다.
import type { MeUser, TokenResponse } from "@/app/auth/AuthContext";
import type {
  DashboardSummary,
  DashboardApplication,
  DashboardActivity,
  DashboardTodo,
} from "@/features/dashboard/types/dashboardSummary";
import type { HomeSummary } from "@/features/home/types/homeSummary";
import type { AnalysisSummary, CareerAnalysisRun } from "@/features/analysis/types/analysisSummary";
import type { CareerRoadmap, FitAnalysisDetail, FitAnalysisHistoryEntry, FitAnalysisLearningTask, FitScoreBreakdown } from "@/features/analysis/types/fitAnalysis";
import type { CareerPlan } from "@/features/analysis/types/careerPlan";
import type { ApplicationCase } from "@/features/applications/types/applicationCase";

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
  { skill: "TypeScript", count: 3, total: 3, percentage: 100 },
  { skill: "AWS", count: 2, total: 3, percentage: 67 },
  { skill: "테스트(Jest)", count: 2, total: 3, percentage: 67 },
  { skill: "CI/CD", count: 1, total: 3, percentage: 33 },
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
  promisingApplication: dashboardApplications[0],
  recentApplications: dashboardApplications,
  todos: [
    { id: null, derivedKey: "interview-practice:102", source: "DERIVED", done: false, task: "네이버 면접 예상 질문 3개 답변 연습", time: "오늘" },
    { id: null, derivedKey: "gap-learning:TypeScript", source: "DERIVED", done: false, task: "TypeScript 리팩토링 프로젝트 포트폴리오 추가", time: "이번 주" },
    { id: 7001, derivedKey: null, source: "USER", done: true, task: "카카오 자기소개서 첨삭 반영", time: "어제" },
  ],
  activities,
  skillGaps,
  recentInterview: {
    sessionId: 8002,
    applicationCaseId: 102,
    companyName: "네이버",
    jobTitle: "프론트엔드 개발자",
    mode: "JOB",
    totalScore: 80,
    previousScore: 74,
    scoreDelta: 6,
    keyImprovement: "답변에 정량 성과가 부족합니다. 성능 개선 경험을 수치(로딩 35% 단축 등)로 설명하세요.",
    occurredAt: iso(1),
  },
  recentNotifications: [
    { id: 9101, type: "ANALYSIS_DONE", title: "네이버 적합도 분석이 완료되었습니다 (84점)", message: null, link: "/applications/102", read: false, createdAt: iso(2) },
    { id: 9102, type: "INTERVIEW_REPORT", title: "모의면접 리포트가 준비되었습니다", message: null, link: "/interview?tab=report", read: false, createdAt: iso(1) },
    { id: 9103, type: "NOTICE", title: "CareerTuner 데모에 오신 것을 환영합니다", message: null, link: null, read: true, createdAt: iso(5) },
  ],
  readiness: {
    overall: 72,
    components: [
      { key: "analysis", label: "적합도 분석 실행률", score: 75, description: "4개 지원 건 중 3건 분석 완료" },
      { key: "fit", label: "평균 적합도", score: 78, description: "최신 분석 기준 평균 점수" },
      { key: "learning", label: "학습 로드맵 완료율", score: 33, description: "9개 과제 중 3개 완료" },
      { key: "interview", label: "모의면접 연습률", score: 50, description: "4개 지원 건 중 2건 연습 진행" },
    ],
  },
  recentChange: {
    reanalyzedApplications: 2,
    improvedApplications: 2,
    declinedApplications: 0,
    averageScoreDelta: 7,
    weeklyFitScoreDelta: 7,
    weeklyGapCountDelta: -2,
    weeklyInterviewScoreDelta: 6,
  },
  statusCounts: [
    { status: "ANALYZING", count: 1 },
    { status: "READY", count: 2 },
    { status: "APPLIED", count: 1 },
  ],
  aiSummary: dashboardAiSummary,
  analysisRun: {
    id: 5001,
    analysisType: "DASHBOARD_SUMMARY",
    status: "FALLBACK",
    model: "mock-demo",
    promptVersion: "v0.2",
    tokenUsage: 0,
    errorMessage: null,
    retryable: false,
    createdAt: iso(0),
  },
  aiHistory: [
    { id: 5001, analysisType: "DASHBOARD_SUMMARY", status: "FALLBACK", model: "mock-demo", promptVersion: "v0.2", tokenUsage: 0, errorMessage: null, retryable: false, createdAt: iso(0) },
    { id: 5000, analysisType: "DASHBOARD_SUMMARY", status: "SUCCESS", model: "mock-demo", promptVersion: "v0.1", tokenUsage: 760, errorMessage: null, retryable: false, createdAt: iso(7) },
  ],
};

export const demoHomeSummary: HomeSummary = {
  user: { name: demoUser.name, plan: demoUser.plan, credit: demoUser.credit },
  focus: demoDashboardSummary.focus,
  aiSummary: dashboardAiSummary,
  recentApplications: dashboardApplications,
  nextActions: demoDashboardSummary.todos,
  recentActivities: activities,
  onboardingSteps: [
    { key: "signup", label: "회원가입", done: true },
    { key: "application", label: "공고(지원 건) 등록", done: true },
    { key: "fit-analysis", label: "적합도 분석 실행", done: true },
    { key: "learning", label: "학습 과제 완료", done: true },
    { key: "interview", label: "모의면접 연습", done: true },
  ],
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
  promptVersion: "v0.2",
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
  strengthTrends: [
    { skill: "React", count: 3, total: 3, percentage: 100 },
    { skill: "REST API", count: 2, total: 3, percentage: 67 },
    { skill: "협업", count: 2, total: 3, percentage: 67 },
    { skill: "성능 최적화", count: 1, total: 3, percentage: 33 },
  ],
  jobDistribution: [
    { jobTitle: "프론트엔드 개발자", count: 3, percentage: 75, averageFitScore: 78 },
    { jobTitle: "웹 프론트엔드 엔지니어", count: 1, percentage: 25, averageFitScore: null },
  ],
  answerThemes: [
    { questionType: "TECH", answerCount: 6, averageScore: 68, sampleFeedback: "기술 선택 이유가 추상적입니다. 비교 대안과 선택 근거를 함께 설명하세요." },
    { questionType: "SITUATION", answerCount: 4, averageScore: 74, sampleFeedback: "상황-행동-결과 구조는 좋으나 결과 수치가 빠져 있습니다." },
    { questionType: "PERSONALITY", answerCount: 4, averageScore: 81, sampleFeedback: null },
  ],
  period: { from: iso(20), to: iso(2), applicationCount: 4, analyzedCount: 3, interviewSessionCount: 3 },
  monthlyFitTrend: [
    { month: "2026-04", averageScore: 66, analysisCount: 1 },
    { month: "2026-05", averageScore: 71, analysisCount: 2 },
    { month: "2026-06", averageScore: 81, analysisCount: 2 },
  ],
  applicationTiers: [
    {
      tier: "SAFE",
      label: "안전 지원",
      description: "적합도 80점 이상. 합격 가능성이 높은 지원 건입니다.",
      items: [{ applicationCaseId: 102, companyName: "네이버", jobTitle: "프론트엔드 개발자", fitScore: 84 }],
    },
    {
      tier: "MATCH",
      label: "적정 지원",
      description: "적합도 60~79점. 현재 스펙과 잘 맞아 보완 1~2개로 경쟁력이 생깁니다.",
      items: [
        { applicationCaseId: 101, companyName: "카카오", jobTitle: "프론트엔드 개발자", fitScore: 78 },
        { applicationCaseId: 104, companyName: "라인", jobTitle: "프론트엔드 개발자", fitScore: 71 },
      ],
    },
    {
      tier: "CHALLENGE",
      label: "상향 지원",
      description: "적합도 60점 미만. 부족 역량 보완이 전제되는 도전 지원 건입니다.",
      items: [],
    },
  ],
  skillFitAverages: [
    { skill: "React", analysisCount: 3, averageScore: 80, mostlyMatched: true },
    { skill: "TypeScript", analysisCount: 3, averageScore: 76, mostlyMatched: false },
    { skill: "REST API", analysisCount: 2, averageScore: 81, mostlyMatched: true },
    { skill: "AWS", analysisCount: 2, averageScore: 74, mostlyMatched: false },
  ],
  fitInterviewBands: [
    { band: "HIGH", label: "적합도 70점 이상", applicationCount: 2, averageFitScore: 81, averageInterviewScore: 78 },
    { band: "MID", label: "적합도 50~69점", applicationCount: 0, averageFitScore: null, averageInterviewScore: null },
    { band: "LOW", label: "적합도 50점 미만", applicationCount: 0, averageFitScore: null, averageInterviewScore: null },
  ],
  applicationPriorities: [
    {
      applicationCaseId: 101,
      companyName: "카카오",
      jobTitle: "프론트엔드 개발자",
      fitScore: 78,
      priorityScore: 86,
      urgency: "NOW",
      reasons: ["현재 적합도 78점", "지원 준비가 완료된 상태"],
    },
    {
      applicationCaseId: 104,
      companyName: "라인",
      jobTitle: "프론트엔드 개발자",
      fitScore: 71,
      priorityScore: 79,
      urgency: "PREPARE",
      reasons: ["현재 적합도 71점", "지원 준비가 완료된 상태"],
    },
  ],
  careerRisks: [
    {
      riskType: "REPEATED_SKILL_GAP",
      severity: "HIGH",
      title: "TypeScript 부족이 반복되고 있습니다.",
      detail: "최근 분석 3건 중 3건에서 같은 부족 역량이 확인됐습니다.",
      action: "이번 주 학습 로드맵에서 TypeScript 과제를 최우선으로 완료하세요.",
    },
  ],
  companyTypeFits: [
    { companyType: "인터넷/SaaS", applicationCount: 2, averageFitScore: 81 },
    { companyType: "핀테크", applicationCount: 1, averageFitScore: 78 },
  ],
  correctionCorrelation: {
    correctedApplications: 2,
    uncorrectedApplications: 1,
    correctedAverageFitScore: 81,
    uncorrectedAverageFitScore: 71,
    scoreDelta: 10,
  },
  weeklyChange: {
    fitScoreDelta: 7,
    gapCountDelta: -2,
    interviewScoreDelta: 6,
    summary: "지난주보다 적합도와 면접 점수가 상승하고 반복 부족 역량은 감소했습니다.",
  },
  avoidJobTypes: ["AWS 운영 경험 필수 공고", "백엔드 실무 3년 이상 필수 공고"],
  next24HourActions: ["TypeScript 리팩토링 결과를 README에 정리", "네이버 지원 건 면접 답변 3개 수치화"],
  toneStrategies: [
    { tone: "DIRECT", label: "냉정한 평가", message: "현재는 프론트엔드 지원에 집중하고 AWS 필수 공고는 후순위로 두세요." },
    { tone: "ENCOURAGING", label: "격려형 평가", message: "강점 직무에서 점수가 꾸준히 오르고 있습니다. 반복 부족 역량 하나씩 해결하세요." },
    { tone: "ACTION", label: "실행 중심 평가", message: "오늘 TypeScript 근거를 보강하고 이번 주 AWS 배포 실습을 완료하세요." },
  ],
  threeLineSummary: [
    "현재 가장 유망한 지원 건은 네이버 프론트엔드입니다.",
    "반복 부족 역량은 TypeScript와 AWS입니다.",
    "이번 주에는 TypeScript 프로젝트 근거 보강을 우선하세요.",
  ],
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

function demoScoreBreakdown(score: number): FitScoreBreakdown[] {
  const definitions = [
    ["REQUIRED", "필수 조건 충족도", 45, "필수 요구조건의 충족·부분 충족 비율"],
    ["PREFERRED", "우대 조건 충족도", 25, "우대 요구조건의 충족·부분 충족 비율"],
    ["PROJECT", "프로젝트 연관성", 15, "매칭 역량과 프로젝트 적용 가능성"],
    ["EXPERIENCE", "경력·경험 신뢰도", 10, "등록된 경험 근거의 활용 가능성"],
    ["PROFILE", "프로필 완성도 보정", 5, "분석 입력의 완성도와 신뢰도"],
  ] as const;
  const earned = definitions.map(([, , maximum]) => Math.floor((score * maximum) / 100));
  let remainder = score - earned.reduce((sum, value) => sum + value, 0);
  for (let index = 0; remainder > 0; index = (index + 1) % earned.length) {
    if (earned[index] < definitions[index][2]) {
      earned[index] += 1;
      remainder -= 1;
    }
  }
  return definitions.map(([key, label, maximum, explanation], index) => ({
    key,
    label,
    earned: earned[index],
    maximum,
    explanation,
  }));
}

const fitAnalyses: FitAnalysisDetail[] = [
  {
    id: 201, applicationCaseId: 101, fitScore: 78,
    matchedSkills: JSON.stringify(["React", "JavaScript", "REST API", "협업"]),
    missingSkills: JSON.stringify(["TypeScript", "테스트"]),
    recommendedStudy: JSON.stringify(["TypeScript 실무", "Jest 단위 테스트"]),
    recommendedCertificates: JSON.stringify(["정보처리기사"]),
    strategy: "React 경험은 충분합니다. TypeScript와 테스트 경험을 보강하면 적합도가 올라갑니다.",
    sourceSnapshot: JSON.stringify({
      jobAnalysisId: 301,
      jobPostingRevision: 2,
      jobAnalysisCreatedAt: iso(4),
      profileVersionId: 90001,
      profileVersionNo: 3,
      profileUpdatedAt: iso(6),
      requiredSkills: ["React", "JavaScript", "TypeScript", "테스트(Jest)"],
      profileSkills: ["React", "JavaScript", "REST API", "Git"],
    }),
    scoreBasis: JSON.stringify(["필수 기술 React 충족", "우대 기술 TypeScript 미충족", "협업 경험 우수"]),
    gapRecommendations: JSON.stringify([
      { skill: "TypeScript", category: "PREFERRED_GAP", priority: "HIGH", reason: "공고 우대 조건이며 최근 지원에서 반복적으로 부족" },
      { skill: "테스트(Jest)", category: "LONG_TERM_GROWTH", priority: "MEDIUM", reason: "유지보수성과 코드 품질을 보여줄 수 있음" },
    ]),
    certificateRecommendations: JSON.stringify([
      { name: "정보처리기사", priority: "MEDIUM", reason: "신입/주니어 지원 시 기본 신뢰도" },
    ]),
    strategyActions: JSON.stringify(["TypeScript 리팩토링 프로젝트 1건 추가", "테스트 커버리지 사례 포트폴리오화"]),
    conditionMatrix: JSON.stringify([
      { condition: "React", conditionType: "REQUIRED", matchStatus: "MET", evidence: "프로필 보유 기술에서 동일 항목이 확인됩니다." },
      { condition: "JavaScript", conditionType: "REQUIRED", matchStatus: "MET", evidence: "프로필 보유 기술에서 동일 항목이 확인됩니다." },
      { condition: "TypeScript", conditionType: "PREFERRED", matchStatus: "UNMET", evidence: "프로필 보유 기술에서 확인되지 않습니다." },
      { condition: "테스트(Jest)", conditionType: "PREFERRED", matchStatus: "UNMET", evidence: "프로필 보유 기술에서 확인되지 않습니다." },
    ]),
    analysisConfidence: JSON.stringify({
      level: "MEDIUM",
      score: 72,
      reasons: ["보유 자격증 정보가 없어 자격증 추천이 일반 기준으로 제공됩니다."],
    }),
    applyDecision: JSON.stringify({
      decision: "COMPLEMENT",
      reasons: ["적합도 78점이며 우대 조건 TypeScript 가 미충족이라 보완 후 지원을 권장합니다."],
      actions: ["TypeScript 보완 결과물(작은 프로젝트/실습 기록)을 먼저 준비합니다.", "학습 로드맵의 상위 과제를 완료한 뒤 적합도 재분석을 실행합니다."],
    }),
    model: "mock-demo", status: "FALLBACK", errorMessage: null, createdAt: iso(3),
    application: { id: 101, companyName: "카카오", jobTitle: "프론트엔드 개발자", postingDate: iso(5), status: "READY", favorite: false, updatedAt: iso(2) },
    learningTasks: learningTasks(201),
    // 자격증 근거 snapshot — 실제 백엔드(CertificateEvidenceService) 출력과 동일한 형태·문구(공식 일정 확인 해피패스).
    certificateEvidence: {
      generatedAt: iso(3),
      strategyStatus: "RECOMMENDED",
      triggeredSignals: ["GAP_CERTIFIABLE"],
      items: [
        {
          certName: "정보처리기사",
          kind: "NATIONAL_TECHNICAL",
          scheduleStatus: "VERIFIED_CURRENT",
          registrationStatus: null,
          message: "Q-Net 공식 확인 기준 시험일정입니다. 시험 일정은 변경될 수 있으니 접수 전 공식 페이지 재확인이 필요합니다.",
          sourceName: "한국산업인력공단 큐넷(Q-Net) 국가기술자격 시험정보",
          sourceUrl: "https://www.q-net.or.kr/",
          scheduleRounds: [
            { round: "기사(2026년도 제2회)", docRegStart: "20260413", docRegEnd: "20260416", docExam: "20260517", docPass: "20260611", pracExamStart: "20260719", pracExamEnd: "20260801", pracPass: "20260828" },
          ],
        },
      ],
    },
  },
  {
    id: 202, applicationCaseId: 102, fitScore: 84,
    matchedSkills: JSON.stringify(["React", "TypeScript", "REST API", "성능 최적화"]),
    missingSkills: JSON.stringify(["AWS"]),
    recommendedStudy: JSON.stringify(["AWS 배포 기초"]),
    recommendedCertificates: JSON.stringify(["AWS Cloud Practitioner"]),
    strategy: "적합도가 높습니다. 면접에서 성능 최적화 경험을 구체적 수치로 설명하세요.",
    sourceSnapshot: JSON.stringify({
      jobAnalysisId: 302,
      jobPostingRevision: 1,
      jobAnalysisCreatedAt: iso(3),
      profileVersionId: 90001,
      profileVersionNo: 3,
      profileUpdatedAt: iso(6),
      requiredSkills: ["React", "TypeScript", "AWS"],
      profileSkills: ["React", "TypeScript", "REST API", "성능 최적화"],
    }),
    scoreBasis: JSON.stringify(["필수 기술 대부분 충족", "TypeScript 실무 경험 보유", "클라우드 배포 경험 부족"]),
    gapRecommendations: JSON.stringify([
      { skill: "AWS", category: "PREFERRED_GAP", priority: "MEDIUM", reason: "배포/운영 경험을 보여주면 가산점" },
    ]),
    certificateRecommendations: JSON.stringify([
      { name: "AWS Cloud Practitioner", priority: "LOW", reason: "기초 클라우드 이해 증빙" },
    ]),
    strategyActions: JSON.stringify(["성능 개선 정량 성과 정리", "AWS 배포 과정 문서화"]),
    conditionMatrix: JSON.stringify([
      { condition: "React", conditionType: "REQUIRED", matchStatus: "MET", evidence: "프로필 보유 기술에서 동일 항목이 확인됩니다." },
      { condition: "TypeScript", conditionType: "REQUIRED", matchStatus: "MET", evidence: "프로필 보유 기술에서 동일 항목이 확인됩니다." },
      { condition: "AWS", conditionType: "PREFERRED", matchStatus: "PARTIAL", evidence: "프로필에 유사/연관 기술이 있어 부분 충족으로 판정합니다." },
    ]),
    analysisConfidence: JSON.stringify({ level: "HIGH", score: 100, reasons: [] }),
    applyDecision: JSON.stringify({
      decision: "APPLY",
      reasons: ["적합도 84점으로 현재 스펙 기준 지원 가능성이 높습니다.", "핵심 요구 역량 React, TypeScript 가 프로필과 매칭됩니다."],
      actions: ["지원서에 매칭 역량 경험을 수치·역할 중심으로 정리합니다.", "마감 전 지원을 진행하고 면접 답변 준비를 병행합니다."],
    }),
    model: "mock-demo", status: "FALLBACK", errorMessage: null, createdAt: iso(2),
    application: { id: 102, companyName: "네이버", jobTitle: "프론트엔드 개발자", postingDate: iso(8), status: "APPLIED", favorite: true, updatedAt: iso(1) },
    learningTasks: learningTasks(202),
    // 벤더 자격(AWS)은 국내 공공데이터 밖 — '후순위 + 주관기관 확인 필요'의 솔직한 degrade 상태를 보여준다.
    certificateEvidence: {
      generatedAt: iso(2),
      strategyStatus: "OPTIONAL_LOW_PRIORITY",
      triggeredSignals: ["USER_REQUESTED"],
      items: [
        {
          certName: "AWS Cloud Practitioner",
          kind: "PRIVATE_OR_OTHER",
          scheduleStatus: "MANUAL_REQUIRED",
          registrationStatus: "NOT_FOUND",
          message: "공식 민간자격 등록정보에서 확인되지 않았습니다 — 자격명을 재확인하세요. 시험일정은 주관기관(AWS) 공식 페이지 확인이 필요합니다.",
          sourceName: "한국직업능력연구원 민간자격등록정보(민간자격정보서비스)",
          sourceUrl: "https://www.pqi.or.kr/",
          scheduleRounds: [],
        },
      ],
    },
  },
].map((analysis) => ({
  ...analysis,
  promptVersion: "v0.2",
  scoreBreakdown: demoScoreBreakdown(analysis.fitScore ?? 0),
  actionBoard: {
    todo: ["지원서에 부족 역량 보완 계획 추가"],
    inProgress: ["TypeScript 리팩토링 프로젝트 정리"],
    done: ["React 프로젝트 경험 정리"],
  },
  adverseStrategies: ["TypeScript 경험 부족은 기존 JavaScript 프로젝트의 점진적 리팩토링 결과로 대응하세요."],
  next24HourActions: ["README에 문제 해결 과정과 검증 결과 추가", "면접에서 설명할 보완 계획 1분 답변 준비"],
  toneStrategies: [
    { tone: "DIRECT", label: "냉정한 평가", message: "필수 조건 미충족이 있다면 지원 전 근거를 먼저 보강하세요." },
    { tone: "ENCOURAGING", label: "격려형 평가", message: "현재 강점을 유지하며 부족 역량을 작은 결과물로 증명하면 됩니다." },
    { tone: "ACTION", label: "실행 중심 평가", message: "오늘 24시간 액션을 끝낸 뒤 적합도를 재분석하세요." },
  ],
}));

export const demoFitAnalyses = fitAnalyses;

// 장기 커리어 자격증 전략(사용자 단위, 데모 프로필 '프론트엔드 개발자' 기준) — 백엔드 careerCertificateStrategy 와 동일 형태.
export const demoCareerCertificateStrategy = {
  desiredJob: "프론트엔드 개발자",
  heldStrengths: [],
  longTermCandidates: [
    { name: "정보처리기사", reason: "프론트엔드 개발자 직군에서 장기적으로 취득 가치가 있는 후보입니다. 이번 지원 건과는 별개로, 학습 여유가 있을 때 준비하세요." },
    { name: "SQLD", reason: "프론트엔드 개발자 직군에서 장기적으로 취득 가치가 있는 후보입니다. 이번 지원 건과는 별개로, 학습 여유가 있을 때 준비하세요." },
  ],
  note: "자격증은 보조 전략입니다. 실무 프로젝트·배포 경험 보완이 우선이며, 시험 일정은 공식 출처(Q-Net 등) 확인 후 계획하세요.",
};

/** 장애 독립 데모에서도 로드맵→플래너 흐름을 끝까지 시연할 수 있는 C 결정론 fixture. */
export const demoCareerRoadmap: CareerRoadmap = {
  desiredJob: "프론트엔드 개발자",
  horizonMonths: 12,
  generatedAt: "2026-07-12T09:00:00+09:00",
  items: [
    {
      type: "APPLICATION_DEADLINE",
      title: "네이버 프론트엔드 지원 마감",
      startDate: "2026-08-20",
      endDate: null,
      certName: null,
      detail: "지원서와 TypeScript 프로젝트 근거를 최종 점검합니다.",
      sourceName: "데모 지원 건",
      planningBlock: false,
    },
    {
      type: "SKILL_LEARNING",
      title: "TypeScript 실무 근거 만들기",
      startDate: "2026-08-01",
      endDate: "2026-09-30",
      certName: null,
      detail: "기존 React 프로젝트를 strict 모드로 전환하고 테스트 결과를 기록합니다.",
      sourceName: "반복 부족 역량 집계",
      planningBlock: true,
    },
    {
      type: "CERT_REGISTRATION",
      title: "정보처리기사 제3회 원서접수",
      startDate: "2026-07-20",
      endDate: "2026-07-23",
      certName: "정보처리기사",
      detail: "시연용 공식 일정 스냅샷입니다. 실제 접수 전 Q-Net 공고를 다시 확인하세요.",
      sourceName: "Q-Net 데모 스냅샷",
      planningBlock: false,
    },
  ],
  basisNotes: [
    "지원 마감은 현재 지원 건, 학습 블록은 최근 적합도 분석의 반복 부족 역량을 기준으로 배치했습니다.",
    "자격증 일정은 데모 스냅샷이며 실제 신청 전 공식 공고 확인이 필요합니다.",
  ],
};

const demoNationalCertificates = [
  { name: "정보처리기사", kind: "NATIONAL_TECHNICAL", scheduleQueryable: true },
  { name: "정보처리산업기사", kind: "NATIONAL_TECHNICAL", scheduleQueryable: true },
  { name: "공인노무사", kind: "NATIONAL_PROFESSIONAL", scheduleQueryable: true },
];

const demoPrivateCertificates = [
  { name: "SQL 개발자(SQLD)", currentStatus: "등록", institution: "한국데이터산업진흥원", registrationNo: "2008-000000" },
  { name: "데이터분석 준전문가(ADsP)", currentStatus: "등록", institution: "한국데이터산업진흥원", registrationNo: "2014-000000" },
];

/** 외부 공공데이터 장애와 무관하게 검색 결과·약어 안내·빈 상태를 검증하는 데모 검색. */
export function searchDemoCertificates(rawQuery: string) {
  const query = rawQuery.trim();
  const lower = query.toLocaleLowerCase("ko-KR");
  const alias = lower === "sqld" ? "SQL" : lower === "adsp" ? "데이터분석" : null;
  const needles = [query, alias].filter((value): value is string => Boolean(value));
  const matches = (name: string) => needles.some((needle) => name.toLocaleLowerCase("ko-KR").includes(needle.toLocaleLowerCase("ko-KR")));

  return {
    query,
    resolvedAlias: alias,
    national: demoNationalCertificates.filter((item) => matches(item.name)),
    nationalUnavailable: false,
    privateMatches: demoPrivateCertificates.filter((item) => matches(item.name)),
    privateLookupFailed: false,
  };
}

export function findFitByApplicationCase(applicationCaseId: number): FitAnalysisDetail | undefined {
  return fitAnalyses.find((f) => f.applicationCaseId === applicationCaseId);
}

export const demoAnalysisHistory: CareerAnalysisRun[] = [
  analysisRun,
  { ...analysisRun, id: 6000, createdAt: iso(7) },
];

export const demoApplicationCases: ApplicationCase[] = dashboardApplications.map((application) => ({
  id: application.id,
  companyName: application.companyName,
  jobTitle: application.jobTitle,
  postingDate: application.postingDate,
  deadlineDate: null,
  sourceType: "TEXT",
  status: application.status as ApplicationCase["status"],
  favorite: application.favorite,
  archived: false,
  archivedAt: null,
  deletedAt: null,
  createdAt: application.updatedAt,
  updatedAt: application.updatedAt,
}));

export const demoCareerPlan: CareerPlan = {
  goal: {
    id: 9501,
    targetJob: "프론트엔드 개발자",
    targetPeriod: "2026년 하반기",
    prioritySkill: "TypeScript",
    preferredCompanyType: "SaaS/플랫폼",
    updatedAt: iso(1),
  },
  learningPlans: [
    {
      id: 9601,
      title: "TypeScript 실무 근거 만들기",
      targetSkill: "TypeScript",
      startDate: null,
      endDate: null,
      status: "ACTIVE",
      completionRate: 50,
      tasks: [
        { id: 9701, learningPlanId: 9601, task: "React 컴포넌트 3개 타입 적용", done: true, sortOrder: 1, completedAt: iso(1) },
        { id: 9702, learningPlanId: 9601, task: "API 응답 타입과 오류 처리 정리", done: false, sortOrder: 2, completedAt: null },
      ],
    },
  ],
};

// 재분석 히스토리(지원 건별, 최신순). 점수·역량 변화 추적 데모.
const fitHistoryByCase: Record<number, FitAnalysisHistoryEntry[]> = {
  101: [
    { id: 201, fitScore: 78, previousScore: 70, scoreDelta: 8, gainedSkills: ["REST API"], resolvedGaps: ["협업"], newGaps: [], model: "mock-demo", status: "FALLBACK", createdAt: iso(3) },
    { id: 200, fitScore: 70, previousScore: null, scoreDelta: null, gainedSkills: [], resolvedGaps: [], newGaps: [], model: "mock-demo", status: "FALLBACK", createdAt: iso(12) },
  ],
  102: [
    { id: 202, fitScore: 84, previousScore: 76, scoreDelta: 8, gainedSkills: ["TypeScript"], resolvedGaps: ["TypeScript"], newGaps: ["AWS"], model: "mock-demo", status: "FALLBACK", createdAt: iso(2) },
    { id: 199, fitScore: 76, previousScore: null, scoreDelta: null, gainedSkills: [], resolvedGaps: [], newGaps: [], model: "mock-demo", status: "FALLBACK", createdAt: iso(14) },
  ],
};

export function findFitHistoryByApplicationCase(applicationCaseId: number): FitAnalysisHistoryEntry[] {
  return fitHistoryByCase[applicationCaseId] ?? [];
}
