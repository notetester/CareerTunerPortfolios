// 데모/목: 관리자 분석 운영 도메인(분석통계/적합도/공고분석/기업분석/지원건/AI사용량).
// 김데모(id 9001) 외 지원자 + 어드민으로 구성된 플랫폼 전체 운영 뷰. 지원 건 101 카카오 / 102 네이버 / 103 토스 / 104 라인(모두 프론트엔드).
// 모든 응답 타입은 admin/features 의 api 모듈이 기대하는 T(백엔드 응답 shape) 그대로 반환한다(transform 없음).
import type { MockRoute, MockContext } from "../../registry";
import { iso } from "../../registry";
import type {
  AdminAnalyticsSummary,
  AdminAnalysisFailure,
  AdminQualityFlag,
  AdminUserTimeline,
  AdminCareerAnalysisRun,
  AdminCareerRunMemo,
  AdminCareerRunMemoRequest,
} from "@/admin/features/analytics/types/adminAnalytics";
import type {
  AdminFitAnalysisListItem,
  AdminFitAnalysisDetail,
  AdminFitAnalysisMemo,
  AdminFitAnalysisMemoRequest,
} from "@/admin/features/fit-analysis/types/adminFitAnalysis";
import type {
  AdminJobAnalysisRow,
  AdminJobAnalysisSummaryResponse,
  AdminAiUsageLogRow,
  AdminBUsageSummaryResponse,
} from "@/admin/features/job-analysis/types";
import type {
  AdminCompanyAnalysisRow,
  AdminCompanyAnalysisSummaryResponse,
} from "@/admin/features/company-analysis/types";
import type {
  AdminApplicationCaseRow,
  AdminApplicationCaseSummaryResponse,
  AdminApplicationCaseDetail,
  AdminStatusHistoryEntry,
  AdminApplicationJobAnalysis,
  AdminApplicationCompanyAnalysis,
} from "@/admin/features/application-cases/types";
import type { JobPosting } from "@/features/applications/types/jobPosting";

// ──────────────────────────────────────────────────────────────────────────
// 공통 데모 멤버 / 지원 건 식별자 (도메인 전반에서 일관 유지)
// ──────────────────────────────────────────────────────────────────────────
// 멤버: 김데모(9001, 구직자 PRO), 이서연(9002), 박지훈(9003), 정민아(9004), 최우진(9005), 운영자 한관리(9101)
const M = {
  demoId: 9001,
  demoName: "김데모",
  demoEmail: "demo@careertuner.dev",
  seoYeonId: 9002,
  seoYeonName: "이서연",
  seoYeonEmail: "seoyeon@example.com",
  jiHoonId: 9003,
  jiHoonName: "박지훈",
  jiHoonEmail: "jihoon@example.com",
  minAhId: 9004,
  minAhName: "정민아",
  minAhEmail: "minah@example.com",
  wooJinId: 9005,
  wooJinName: "최우진",
  wooJinEmail: "woojin@example.com",
  adminId: 9101,
  adminName: "한관리",
  adminEmail: "admin@careertuner.dev",
};

// 지원 건: 101 카카오 / 102 네이버 / 103 토스 / 104 라인 (모두 프론트엔드)
const CASE = {
  kakao: { id: 101, company: "카카오", title: "프론트엔드 개발자", userId: M.demoId, email: M.demoEmail, name: M.demoName },
  naver: { id: 102, company: "네이버", title: "프론트엔드 개발자", userId: M.demoId, email: M.demoEmail, name: M.demoName },
  toss: { id: 103, company: "토스", title: "프론트엔드 개발자", userId: M.seoYeonId, email: M.seoYeonEmail, name: M.seoYeonName },
  line: { id: 104, company: "라인", title: "프론트엔드 개발자", userId: M.jiHoonId, email: M.jiHoonEmail, name: M.jiHoonName },
};

// ──────────────────────────────────────────────────────────────────────────
// 1) 분석 통계 (/admin/analytics/summary)
// ──────────────────────────────────────────────────────────────────────────
const analyticsSummary: AdminAnalyticsSummary = {
  stats: {
    totalUsers: 142,
    activeUsers: 58,
    totalApplications: 96,
    analyzedApplications: 71,
    totalInterviews: 188,
    averageFitScore: 73.4,
    creditsUsedThisMonth: 4280,
  },
  planDistribution: [
    { label: "FREE", count: 86 },
    { label: "BASIC", count: 31 },
    { label: "PRO", count: 19 },
    { label: "PREMIUM", count: 6 },
  ],
  applicationStatusDistribution: [
    { label: "DRAFT", count: 18 },
    { label: "ANALYZING", count: 9 },
    { label: "READY", count: 34 },
    { label: "APPLIED", count: 27 },
    { label: "CLOSED", count: 8 },
  ],
  skillGaps: [
    { skill: "TypeScript", count: 41, total: 96, percentage: 42.7 },
    { skill: "테스트 코드", count: 38, total: 96, percentage: 39.6 },
    { skill: "성능 최적화", count: 29, total: 96, percentage: 30.2 },
    { skill: "접근성(A11y)", count: 22, total: 96, percentage: 22.9 },
    { skill: "상태관리(Redux/Zustand)", count: 17, total: 96, percentage: 17.7 },
  ],
  fitScoreBands: [
    { label: "90-100", count: 8, percentage: 11.3 },
    { label: "80-89", count: 19, percentage: 26.8 },
    { label: "70-79", count: 24, percentage: 33.8 },
    { label: "60-69", count: 13, percentage: 18.3 },
    { label: "0-59", count: 7, percentage: 9.9 },
  ],
  recentAnalyses: [
    {
      applicationCaseId: CASE.kakao.id,
      fitAnalysisId: 5101,
      userName: CASE.kakao.name,
      userEmail: CASE.kakao.email,
      companyName: CASE.kakao.company,
      jobTitle: CASE.kakao.title,
      fitScore: 82,
      analyzedAt: iso(1),
    },
    {
      applicationCaseId: CASE.naver.id,
      fitAnalysisId: 5102,
      userName: CASE.naver.name,
      userEmail: CASE.naver.email,
      companyName: CASE.naver.company,
      jobTitle: CASE.naver.title,
      fitScore: 76,
      analyzedAt: iso(2),
    },
    {
      applicationCaseId: CASE.toss.id,
      fitAnalysisId: 5103,
      userName: CASE.toss.name,
      userEmail: CASE.toss.email,
      companyName: CASE.toss.company,
      jobTitle: CASE.toss.title,
      fitScore: 58,
      analyzedAt: iso(3),
    },
    {
      applicationCaseId: CASE.line.id,
      fitAnalysisId: 5104,
      userName: CASE.line.name,
      userEmail: CASE.line.email,
      companyName: CASE.line.company,
      jobTitle: CASE.line.title,
      fitScore: null,
      analyzedAt: iso(4),
    },
  ],
  dailyUsage: [
    { date: iso(6).slice(0, 10), tokenUsage: 184320, creditUsed: 620 },
    { date: iso(5).slice(0, 10), tokenUsage: 201540, creditUsed: 705 },
    { date: iso(4).slice(0, 10), tokenUsage: 176200, creditUsed: 588 },
    { date: iso(3).slice(0, 10), tokenUsage: 233100, creditUsed: 812 },
    { date: iso(2).slice(0, 10), tokenUsage: 198760, creditUsed: 690 },
    { date: iso(1).slice(0, 10), tokenUsage: 245880, creditUsed: 855 },
    { date: iso(0).slice(0, 10), tokenUsage: 89540, creditUsed: 310 },
  ],
  promptPerformance: [
    {
      promptKey: "FIT_ANALYSIS",
      promptVersion: "v3.2",
      totalCount: 71,
      successCount: 66,
      fallbackCount: 3,
      failedCount: 2,
      successRate: 92.9,
      averageTokenUsage: 4120,
    },
    {
      promptKey: "JOB_ANALYSIS",
      promptVersion: "v2.8",
      totalCount: 88,
      successCount: 84,
      fallbackCount: 3,
      failedCount: 1,
      successRate: 95.5,
      averageTokenUsage: 2680,
    },
    {
      promptKey: "COMPANY_RESEARCH",
      promptVersion: "v1.9",
      totalCount: 52,
      successCount: 47,
      fallbackCount: 4,
      failedCount: 1,
      successRate: 90.4,
      averageTokenUsage: 5340,
    },
    {
      promptKey: "CAREER_TREND",
      promptVersion: "v2.1",
      totalCount: 34,
      successCount: 31,
      fallbackCount: 2,
      failedCount: 1,
      successRate: 91.2,
      averageTokenUsage: 3760,
    },
  ],
};

// ──────────────────────────────────────────────────────────────────────────
// 2) 분석 실패 큐 (/admin/analytics/failures)
// ──────────────────────────────────────────────────────────────────────────
const analysisFailures: AdminAnalysisFailure[] = [
  {
    source: "FIT_ANALYSIS",
    refId: 5104,
    userName: CASE.line.name,
    userEmail: CASE.line.email,
    companyName: CASE.line.company,
    jobTitle: CASE.line.title,
    status: "FAILED",
    errorMessage: "모델 응답 JSON 파싱 실패 (불완전한 응답)",
    model: "mock-demo",
    promptVersion: "v3.2",
    retryable: true,
    createdAt: iso(4),
  },
  {
    source: "CAREER_TREND",
    refId: 7203,
    userName: M.minAhName,
    userEmail: M.minAhEmail,
    companyName: null,
    jobTitle: null,
    status: "FALLBACK",
    errorMessage: "1차 모델 타임아웃 → 폴백 모델로 재시도 성공",
    model: "mock-demo",
    promptVersion: "v2.1",
    retryable: false,
    createdAt: iso(2),
  },
  {
    source: "DASHBOARD_SUMMARY",
    refId: 7211,
    userName: M.wooJinName,
    userEmail: M.wooJinEmail,
    companyName: null,
    jobTitle: null,
    status: "FAILED",
    errorMessage: "토큰 한도 초과로 요약 생성 중단",
    model: "mock-demo",
    promptVersion: "v2.1",
    retryable: true,
    createdAt: iso(1),
  },
];

// ──────────────────────────────────────────────────────────────────────────
// 3) 품질 검수 큐 (/admin/analytics/quality-flags)
// ──────────────────────────────────────────────────────────────────────────
const qualityFlags: AdminQualityFlag[] = [
  {
    fitAnalysisId: 5103,
    applicationCaseId: CASE.toss.id,
    userName: CASE.toss.name,
    userEmail: CASE.toss.email,
    companyName: CASE.toss.company,
    jobTitle: CASE.toss.title,
    fitScore: 58,
    flagType: "LOW_SCORE_NO_GAPS",
    severity: "HIGH",
    detail: "적합도 58점인데 부족 역량 목록이 비어 있음 (점수·근거 불일치 의심)",
    analyzedAt: iso(3),
  },
  {
    fitAnalysisId: 5102,
    applicationCaseId: CASE.naver.id,
    userName: CASE.naver.name,
    userEmail: CASE.naver.email,
    companyName: CASE.naver.company,
    jobTitle: CASE.naver.title,
    fitScore: 76,
    flagType: "EXCESSIVE_CERTS",
    severity: "MEDIUM",
    detail: "추천 자격증이 7개로 과다 — 핵심 자격증 위주로 정제 필요",
    analyzedAt: iso(2),
  },
  {
    fitAnalysisId: 5101,
    applicationCaseId: CASE.kakao.id,
    userName: CASE.kakao.name,
    userEmail: CASE.kakao.email,
    companyName: CASE.kakao.company,
    jobTitle: CASE.kakao.title,
    fitScore: 82,
    flagType: "LOW_CONFIDENCE",
    severity: "LOW",
    detail: "분석 신뢰도가 'LOW'로 표기 — 원문 정보 부족 가능성",
    analyzedAt: iso(1),
  },
];

// ──────────────────────────────────────────────────────────────────────────
// 4) 사용자 타임라인 (/admin/analytics/users/:id/timeline)
// ──────────────────────────────────────────────────────────────────────────
const userTimelines: Record<number, AdminUserTimeline[]> = {
  [M.demoId]: [
    { eventType: "APPLICATION_CREATED", refId: CASE.kakao.id, summary: "카카오 프론트엔드 지원 건 생성", status: "READY", score: null, createdAt: iso(14) },
    { eventType: "JOB_ANALYSIS", refId: 3101, summary: "카카오 공고 분석 완료", status: "SUCCESS", score: null, createdAt: iso(13) },
    { eventType: "FIT_ANALYSIS", refId: 5101, summary: "카카오 적합도 분석", status: "SUCCESS", score: 82, createdAt: iso(1) },
    { eventType: "INTERVIEW_SESSION", refId: 8101, summary: "카카오 기술 면접 세션", status: "COMPLETED", score: 79, createdAt: iso(1) },
  ],
  [M.seoYeonId]: [
    { eventType: "APPLICATION_CREATED", refId: CASE.toss.id, summary: "토스 프론트엔드 지원 건 생성", status: "ANALYZING", score: null, createdAt: iso(9) },
    { eventType: "FIT_ANALYSIS", refId: 5103, summary: "토스 적합도 분석", status: "SUCCESS", score: 58, createdAt: iso(3) },
  ],
  [M.jiHoonId]: [
    { eventType: "APPLICATION_CREATED", refId: CASE.line.id, summary: "라인 프론트엔드 지원 건 생성", status: "DRAFT", score: null, createdAt: iso(6) },
    { eventType: "FIT_ANALYSIS", refId: 5104, summary: "라인 적합도 분석 실패", status: "FAILED", score: null, createdAt: iso(4) },
  ],
};

// ──────────────────────────────────────────────────────────────────────────
// 5) 취업 분석 실행 이력 (/admin/analytics/runs) + 운영 메모
// ──────────────────────────────────────────────────────────────────────────
const careerRuns: AdminCareerAnalysisRun[] = [
  {
    id: 7201,
    userId: M.demoId,
    userName: M.demoName,
    userEmail: M.demoEmail,
    analysisType: "CAREER_TREND",
    status: "SUCCESS",
    inputSnapshot: '{"targetJob":"프론트엔드 개발자","skills":["React","TypeScript"]}',
    result: '{"trend":"프론트엔드 채용 안정적 증가","recommend":"TypeScript·테스트 역량 강화"}',
    model: "mock-demo",
    promptVersion: "v2.1",
    tokenUsage: 3820,
    errorMessage: null,
    retryable: false,
    createdAt: iso(2),
    memoCount: 2,
    latestMemoAt: iso(1),
  },
  {
    id: 7203,
    userId: M.minAhId,
    userName: M.minAhName,
    userEmail: M.minAhEmail,
    analysisType: "CAREER_TREND",
    status: "FALLBACK",
    inputSnapshot: '{"targetJob":"백엔드 개발자"}',
    result: '{"trend":"폴백 모델 요약","recommend":"Spring·MySQL 심화"}',
    model: "mock-demo",
    promptVersion: "v2.1",
    tokenUsage: 2140,
    errorMessage: "1차 모델 타임아웃 → 폴백 처리",
    retryable: false,
    createdAt: iso(2),
    memoCount: 1,
    latestMemoAt: iso(2),
  },
  {
    id: 7211,
    userId: M.wooJinId,
    userName: M.wooJinName,
    userEmail: M.wooJinEmail,
    analysisType: "DASHBOARD_SUMMARY",
    status: "FAILED",
    inputSnapshot: '{"period":"weekly"}',
    result: null,
    model: "mock-demo",
    promptVersion: "v2.1",
    tokenUsage: 0,
    errorMessage: "토큰 한도 초과로 요약 생성 중단",
    retryable: true,
    createdAt: iso(1),
    memoCount: 0,
    latestMemoAt: null,
  },
];

const careerRunMemos: AdminCareerRunMemo[] = [
  {
    id: 8801,
    careerAnalysisRunId: 7201,
    adminUserId: M.adminId,
    adminName: M.adminName,
    adminEmail: M.adminEmail,
    memoType: "QUALITY",
    content: "트렌드 요약 정확. 추천 역량이 적합도 분석과 일치하여 품질 양호.",
    createdAt: iso(2),
    updatedAt: iso(2),
  },
  {
    id: 8802,
    careerAnalysisRunId: 7201,
    adminUserId: M.adminId,
    adminName: M.adminName,
    adminEmail: M.adminEmail,
    memoType: "GENERAL",
    content: "사용자에게 학습 플랜 연동 안내 발송 예정.",
    createdAt: iso(1),
    updatedAt: iso(1),
  },
  {
    id: 8803,
    careerAnalysisRunId: 7203,
    adminUserId: M.adminId,
    adminName: M.adminName,
    adminEmail: M.adminEmail,
    memoType: "REANALYSIS",
    content: "폴백 결과 — 1차 모델로 재분석 요청 필요.",
    createdAt: iso(2),
    updatedAt: iso(2),
  },
];

let careerRunMemoSeq = 8900;

// ──────────────────────────────────────────────────────────────────────────
// 6) 적합도 분석 운영 (/admin/fit-analyses)
// ──────────────────────────────────────────────────────────────────────────
const fitAnalysisList: AdminFitAnalysisListItem[] = [
  {
    id: 5101,
    applicationCaseId: CASE.kakao.id,
    userName: CASE.kakao.name,
    userEmail: CASE.kakao.email,
    companyName: CASE.kakao.company,
    jobTitle: CASE.kakao.title,
    applicationStatus: "READY",
    favorite: true,
    fitScore: 82,
    matchedSkills: ["React", "JavaScript", "HTML/CSS", "Git"],
    missingSkills: ["TypeScript", "테스트 코드"],
    model: "mock-demo",
    promptVersion: "v3.2",
    status: "SUCCESS",
    errorMessage: null,
    createdAt: iso(1),
    memoCount: 1,
    latestMemoAt: iso(1),
    reanalysisRequested: false,
    gateStatus: "PASSED",
    needsHumanReview: false,
    gateReasonCount: 0,
    gateMaxSeverity: null,
    gateReviewStatus: "PENDING",
  },
  {
    id: 5102,
    applicationCaseId: CASE.naver.id,
    userName: CASE.naver.name,
    userEmail: CASE.naver.email,
    companyName: CASE.naver.company,
    jobTitle: CASE.naver.title,
    applicationStatus: "APPLIED",
    favorite: false,
    fitScore: 76,
    matchedSkills: ["React", "TypeScript", "Redux"],
    missingSkills: ["성능 최적화", "접근성(A11y)"],
    model: "mock-demo",
    promptVersion: "v3.2",
    status: "SUCCESS",
    errorMessage: null,
    createdAt: iso(2),
    memoCount: 0,
    latestMemoAt: null,
    reanalysisRequested: false,
    gateStatus: "PASSED",
    needsHumanReview: false,
    gateReasonCount: 0,
    gateMaxSeverity: null,
    gateReviewStatus: "PENDING",
  },
  {
    id: 5103,
    applicationCaseId: CASE.toss.id,
    userName: CASE.toss.name,
    userEmail: CASE.toss.email,
    companyName: CASE.toss.company,
    jobTitle: CASE.toss.title,
    applicationStatus: "ANALYZING",
    favorite: false,
    fitScore: 58,
    matchedSkills: ["React", "JavaScript"],
    missingSkills: [],
    model: "mock-demo",
    promptVersion: "v3.2",
    status: "SUCCESS",
    errorMessage: null,
    createdAt: iso(3),
    memoCount: 1,
    latestMemoAt: iso(2),
    reanalysisRequested: true,
    gateStatus: "REVIEW_REQUIRED",
    needsHumanReview: true,
    gateReasonCount: 1,
    gateMaxSeverity: "warning",
    gateReviewStatus: "PENDING",
  },
  {
    id: 5104,
    applicationCaseId: CASE.line.id,
    userName: CASE.line.name,
    userEmail: CASE.line.email,
    companyName: CASE.line.company,
    jobTitle: CASE.line.title,
    applicationStatus: "DRAFT",
    favorite: false,
    fitScore: null,
    matchedSkills: [],
    missingSkills: [],
    model: "mock-demo",
    promptVersion: "v3.2",
    status: "FAILED",
    errorMessage: "모델 응답 JSON 파싱 실패 (불완전한 응답)",
    createdAt: iso(4),
    memoCount: 0,
    latestMemoAt: null,
    reanalysisRequested: false,
    gateStatus: "REJECTED",
    needsHumanReview: true,
    gateReasonCount: 1,
    gateMaxSeverity: "critical",
    gateReviewStatus: "PENDING",
  },
];

const fitAnalysisMemos: AdminFitAnalysisMemo[] = [
  {
    id: 9801,
    fitAnalysisId: 5101,
    adminUserId: M.adminId,
    adminName: M.adminName,
    adminEmail: M.adminEmail,
    memoType: "QUALITY",
    content: "매칭/부족 역량 분류 정확. 전략 텍스트도 구체적이라 품질 양호.",
    createdAt: iso(1),
    updatedAt: iso(1),
  },
  {
    id: 9802,
    fitAnalysisId: 5103,
    adminUserId: M.adminId,
    adminName: M.adminName,
    adminEmail: M.adminEmail,
    memoType: "REANALYSIS",
    content: "점수 58인데 부족 역량 비어 있음. 휴리스틱 플래그와 일치 — 재분석 요청.",
    createdAt: iso(2),
    updatedAt: iso(2),
  },
];

let fitMemoSeq = 9900;

function buildFitDetail(id: number): AdminFitAnalysisDetail {
  const item = fitAnalysisList.find((f) => f.id === id) ?? fitAnalysisList[0];
  const memos = fitAnalysisMemos.filter((m) => m.fitAnalysisId === item.id).map((m) => ({ ...m }));
  return {
    id: item.id,
    applicationCaseId: item.applicationCaseId,
    userId:
      item.applicationCaseId === CASE.toss.id
        ? M.seoYeonId
        : item.applicationCaseId === CASE.line.id
          ? M.jiHoonId
          : M.demoId,
    userName: item.userName,
    userEmail: item.userEmail,
    companyName: item.companyName,
    jobTitle: item.jobTitle,
    applicationStatus: item.applicationStatus,
    favorite: item.favorite,
    fitScore: item.fitScore,
    matchedSkills: item.matchedSkills,
    missingSkills: item.missingSkills,
    recommendedStudy: item.missingSkills.length
      ? item.missingSkills.map((s) => `${s} 집중 학습`)
      : ["실무 프로젝트 경험 보강"],
    recommendedCertificates: ["정보처리기사"],
    strategy:
      item.status === "FAILED"
        ? null
        : "지원 직무의 핵심 역량인 React 경험을 강조하고, 부족한 TypeScript·테스트 역량은 학습 계획으로 보완 의지를 보여주세요.",
    sourceSnapshot: '{"resume":"React 3년 / 사이드 프로젝트 5건","posting":"프론트엔드 개발자 채용"}',
    scoreBasis:
      item.status === "FAILED"
        ? []
        : ["핵심 역량 React 보유(+25)", "TypeScript 미보유(-10)", "테스트 경험 부족(-8)"],
    gapRecommendations:
      item.status === "FAILED" ? null : "TypeScript는 2주 집중 학습, 테스트는 Jest/RTL 실습으로 보완 권장.",
    certificateRecommendations: item.status === "FAILED" ? null : "정보처리기사 보유 시 서류 가점 가능.",
    strategyActions:
      item.status === "FAILED"
        ? []
        : ["React 프로젝트 성과 수치화", "TypeScript 마이그레이션 경험 어필", "테스트 커버리지 개선 사례 준비"],
    conditionMatrix:
      item.status === "FAILED"
        ? null
        : '[{"condition":"경력 3년 이상","status":"충족"},{"condition":"TypeScript 필수","status":"미충족"}]',
    analysisConfidence:
      item.fitScore != null && item.fitScore >= 80 ? "LOW" : item.status === "FAILED" ? null : "HIGH",
    applyDecision: item.fitScore == null ? null : item.fitScore >= 70 ? "APPLY" : "RECONSIDER",
    model: item.model,
    promptVersion: item.promptVersion,
    status: item.status,
    errorMessage: item.errorMessage,
    createdAt: item.createdAt,
    gateStatus: item.gateStatus,
    needsHumanReview: item.needsHumanReview,
    gateReasonCount: item.gateReasonCount,
    gateMaxSeverity: item.gateMaxSeverity,
    evidenceGateVersion: item.gateStatus ? "r3-review-first" : null,
    gateReviewStatus: item.gateReviewStatus,
    gateReviewedAt: null,
    gateReviewerName: null,
    gateReasons:
      item.gateStatus === "REVIEW_REQUIRED"
        ? [
            {
              type: "matched_skill_without_user_evidence",
              claim: item.missingSkills[0] ?? "Spark",
              reason: "AI 매칭 역량이 사용자 원본 근거(프로필 스킬/자격)에 없음",
              severity: item.gateMaxSeverity ?? "warning",
            },
          ]
        : item.gateStatus === "REJECTED"
          ? [{ type: "structural", claim: "-", reason: "핵심 계약 필드 누락 또는 점수 범위 위반", severity: "critical" }]
          : [],
    learningTasks:
      item.status === "FAILED"
        ? []
        : [
            {
              id: item.id * 10 + 1,
              fitAnalysisId: item.id,
              skill: "TypeScript",
              title: "TypeScript 타입 시스템 익히기",
              practiceTask: "기존 JS 컴포넌트 3개를 TS로 마이그레이션",
              expectedDuration: "2주",
              priority: "HIGH",
              sortOrder: 1,
              completed: false,
              completedAt: null,
            },
            {
              id: item.id * 10 + 2,
              fitAnalysisId: item.id,
              skill: "테스트 코드",
              title: "React Testing Library 실습",
              practiceTask: "주요 컴포넌트 단위 테스트 작성",
              expectedDuration: "1주",
              priority: "MEDIUM",
              sortOrder: 2,
              completed: true,
              completedAt: iso(1),
            },
          ],
    memos,
  };
}

// ──────────────────────────────────────────────────────────────────────────
// 7) 공고 분석 운영 (/admin/job-analysis)
// ──────────────────────────────────────────────────────────────────────────
const jobAnalysisRows: AdminJobAnalysisRow[] = [
  {
    id: 3101,
    applicationCaseId: CASE.kakao.id,
    jobPostingId: 4101,
    jobPostingRevision: 1,
    latestJobPostingRevision: 1,
    staleAgainstLatestPosting: false,
    userId: CASE.kakao.userId,
    userEmail: CASE.kakao.email,
    companyName: CASE.kakao.company,
    jobTitle: CASE.kakao.title,
    employmentType: "정규직",
    experienceLevel: "경력 3년 이상",
    requiredSkills: "React, JavaScript, HTML/CSS",
    preferredSkills: "TypeScript, Next.js",
    duties: "카카오 서비스 프론트엔드 개발 및 유지보수",
    qualifications: "관련 경력 3년 이상, 협업 능력",
    difficulty: "NORMAL",
    summary: "React 기반 대규모 서비스 프론트엔드 채용. TypeScript 우대.",
    evidence: '[{"field":"필수기술","quote":"React 기반 개발 경험"}]',
    ambiguousConditions: '[{"condition":"경력 3년","assumption":"실무 기준 3년"}]',
    confirmedAt: iso(13),
    adminMemo: "공고 원문과 분석 결과 일치 확인.",
    createdAt: iso(13),
  },
  {
    id: 3102,
    applicationCaseId: CASE.naver.id,
    jobPostingId: 4102,
    jobPostingRevision: 2,
    latestJobPostingRevision: 2,
    staleAgainstLatestPosting: false,
    userId: CASE.naver.userId,
    userEmail: CASE.naver.email,
    companyName: CASE.naver.company,
    jobTitle: CASE.naver.title,
    employmentType: "정규직",
    experienceLevel: "경력 무관",
    requiredSkills: "React, TypeScript, Redux",
    preferredSkills: "성능 최적화 경험",
    duties: "네이버 검색 프론트엔드 개발",
    qualifications: "TypeScript 능숙",
    difficulty: "HARD",
    summary: "TypeScript 필수, 성능 최적화 역량 중시.",
    evidence: '[{"field":"우대사항","quote":"성능 최적화 경험자 우대"}]',
    ambiguousConditions: null,
    confirmedAt: null,
    adminMemo: null,
    createdAt: iso(12),
  },
  {
    id: 3103,
    applicationCaseId: CASE.toss.id,
    jobPostingId: 4103,
    jobPostingRevision: 1,
    latestJobPostingRevision: 2,
    staleAgainstLatestPosting: true,
    userId: CASE.toss.userId,
    userEmail: CASE.toss.email,
    companyName: CASE.toss.company,
    jobTitle: CASE.toss.title,
    employmentType: "정규직",
    experienceLevel: "경력 2년 이상",
    requiredSkills: "React, JavaScript",
    preferredSkills: "금융 도메인 경험",
    duties: "토스 결제 프론트엔드 개발",
    qualifications: "꼼꼼함, 책임감",
    difficulty: "NORMAL",
    summary: "결제 도메인 프론트엔드. 최신 공고 개정본 대비 분석 결과 오래됨.",
    evidence: null,
    ambiguousConditions: '[{"condition":"금융 경험","assumption":"우대 조건"}]',
    confirmedAt: null,
    adminMemo: null,
    createdAt: iso(9),
  },
  {
    id: 3104,
    applicationCaseId: CASE.line.id,
    jobPostingId: 4104,
    jobPostingRevision: 1,
    latestJobPostingRevision: 1,
    staleAgainstLatestPosting: false,
    userId: CASE.line.userId,
    userEmail: CASE.line.email,
    companyName: CASE.line.company,
    jobTitle: CASE.line.title,
    employmentType: "계약직",
    experienceLevel: "신입",
    requiredSkills: "JavaScript, HTML/CSS",
    preferredSkills: "React",
    duties: "라인 메신저 웹 프론트엔드 보조 개발",
    qualifications: "성장 의지",
    difficulty: "EASY",
    summary: "신입 대상 프론트엔드. 난이도 낮음.",
    evidence: null,
    ambiguousConditions: null,
    confirmedAt: null,
    adminMemo: null,
    createdAt: iso(6),
  },
];

const jobAnalysisSummary: AdminJobAnalysisSummaryResponse = {
  totalCount: 4,
  confirmedCount: 1,
  unconfirmedCount: 3,
  easyCount: 1,
  mediumCount: 2,
  hardCount: 1,
  unknownDifficultyCount: 0,
  memoCount: 1,
};

// ──────────────────────────────────────────────────────────────────────────
// 8) 기업 분석 운영 (/admin/company-analysis)
// ──────────────────────────────────────────────────────────────────────────
const companyAnalysisRows: AdminCompanyAnalysisRow[] = [
  {
    id: 6101,
    applicationCaseId: CASE.kakao.id,
    jobPostingId: 4101,
    jobPostingRevision: 1,
    latestJobPostingRevision: 1,
    staleAgainstLatestPosting: false,
    userId: CASE.kakao.userId,
    userEmail: CASE.kakao.email,
    companyName: CASE.kakao.company,
    jobTitle: CASE.kakao.title,
    companySummary: "국내 최대 모바일 플랫폼 기업. 메신저·커머스·콘텐츠 등 다각화.",
    recentIssues: "AI 신사업 확대, 데이터센터 안정화 투자 강화",
    industry: "IT 플랫폼",
    competitors: "네이버, 라인",
    interviewPoints: "대규모 트래픽 처리 경험, 협업 문화 적합도",
    sources: '["채용공고","기업 IR 자료"]',
    verifiedFacts: '[{"fact":"임직원 약 4천명","source":"공식 채용 페이지"}]',
    aiInferences: '[{"inference":"AI 직군 채용 확대 예상","basis":"최근 신사업 발표"}]',
    unknowns: '[{"topic":"매출 규모","reason":"공고문에 관련 정보가 없다","neededSource":"IR 자료"}]',
    sourceType: "OFFICIAL",
    checkedAt: iso(5),
    refreshRecommendedAt: iso(-25),
    confirmedAt: iso(12),
    adminMemo: "출처 검증 완료. 신뢰도 높음.",
    createdAt: iso(13),
  },
  {
    id: 6102,
    applicationCaseId: CASE.naver.id,
    jobPostingId: 4102,
    jobPostingRevision: 2,
    latestJobPostingRevision: 2,
    staleAgainstLatestPosting: false,
    userId: CASE.naver.userId,
    userEmail: CASE.naver.email,
    companyName: CASE.naver.company,
    jobTitle: CASE.naver.title,
    companySummary: "국내 1위 검색 포털 및 클라우드·핀테크 사업자.",
    recentIssues: "하이퍼클로바X 등 자체 LLM 고도화",
    industry: "IT 플랫폼",
    competitors: "카카오, 구글",
    interviewPoints: "검색 품질 개선 경험, 대용량 데이터 다룬 경험",
    sources: '["채용공고"]',
    verifiedFacts: null,
    aiInferences: '[{"inference":"AI 검색 인력 수요 증가","basis":"클로바X 발표"}]',
    unknowns: null,
    sourceType: "AI_RESEARCH",
    checkedAt: null,
    refreshRecommendedAt: iso(3),
    confirmedAt: null,
    adminMemo: null,
    createdAt: iso(12),
  },
  {
    id: 6103,
    applicationCaseId: CASE.toss.id,
    jobPostingId: 4103,
    jobPostingRevision: 1,
    latestJobPostingRevision: 2,
    staleAgainstLatestPosting: true,
    userId: CASE.toss.userId,
    userEmail: CASE.toss.email,
    companyName: CASE.toss.company,
    jobTitle: CASE.toss.title,
    companySummary: "간편송금에서 출발한 종합 금융 플랫폼.",
    recentIssues: "토스뱅크 흑자 전환, 증권 사업 확대",
    industry: "핀테크",
    competitors: "카카오페이, 네이버페이",
    interviewPoints: "금융 규제 이해, 사용자 경험 집착",
    sources: null,
    verifiedFacts: null,
    aiInferences: null,
    unknowns: null,
    sourceType: null,
    checkedAt: null,
    refreshRecommendedAt: iso(8),
    confirmedAt: null,
    adminMemo: null,
    createdAt: iso(9),
  },
];

const companyAnalysisSummary: AdminCompanyAnalysisSummaryResponse = {
  totalCount: 3,
  confirmedCount: 1,
  unconfirmedCount: 2,
  refreshDueCount: 2,
  missingSourceCount: 1,
  checkedCount: 1,
  memoCount: 1,
};

// ──────────────────────────────────────────────────────────────────────────
// 9) AI 사용량 로그 (/admin/ai-usage/b) — B 그룹(공고/기업 분석 계열)
// ──────────────────────────────────────────────────────────────────────────
const aiUsageLogs: AdminAiUsageLogRow[] = [
  {
    id: 21001,
    userId: CASE.kakao.userId,
    userEmail: CASE.kakao.email,
    applicationCaseId: CASE.kakao.id,
    companyName: CASE.kakao.company,
    jobTitle: CASE.kakao.title,
    featureType: "JOB_ANALYSIS",
    status: "SUCCESS",
    model: "mock-demo",
    inputTokens: 1820,
    outputTokens: 860,
    tokenUsage: 2680,
    creditUsed: 2,
    errorMessage: null,
    createdAt: iso(13),
  },
  {
    id: 21002,
    userId: CASE.kakao.userId,
    userEmail: CASE.kakao.email,
    applicationCaseId: CASE.kakao.id,
    companyName: CASE.kakao.company,
    jobTitle: CASE.kakao.title,
    featureType: "COMPANY_RESEARCH",
    status: "SUCCESS",
    model: "mock-demo",
    inputTokens: 2100,
    outputTokens: 3240,
    tokenUsage: 5340,
    creditUsed: 3,
    errorMessage: null,
    createdAt: iso(13),
  },
  {
    id: 21003,
    userId: CASE.naver.userId,
    userEmail: CASE.naver.email,
    applicationCaseId: CASE.naver.id,
    companyName: CASE.naver.company,
    jobTitle: CASE.naver.title,
    featureType: "JOB_POSTING_OCR",
    status: "SUCCESS",
    model: "mock-demo",
    inputTokens: 640,
    outputTokens: 420,
    tokenUsage: 1060,
    creditUsed: 1,
    errorMessage: null,
    createdAt: iso(12),
  },
  {
    id: 21004,
    userId: CASE.naver.userId,
    userEmail: CASE.naver.email,
    applicationCaseId: CASE.naver.id,
    companyName: CASE.naver.company,
    jobTitle: CASE.naver.title,
    featureType: "JOB_POSTING_METADATA",
    status: "SUCCESS",
    model: "mock-demo",
    inputTokens: 380,
    outputTokens: 160,
    tokenUsage: 540,
    creditUsed: 1,
    errorMessage: null,
    createdAt: iso(12),
  },
  {
    id: 21005,
    userId: CASE.line.userId,
    userEmail: CASE.line.email,
    applicationCaseId: CASE.line.id,
    companyName: CASE.line.company,
    jobTitle: CASE.line.title,
    featureType: "JOB_ANALYSIS",
    status: "FAILED",
    model: "mock-demo",
    inputTokens: 1740,
    outputTokens: null,
    tokenUsage: 1740,
    creditUsed: 0,
    errorMessage: "모델 응답 JSON 파싱 실패 (불완전한 응답)",
    createdAt: iso(4),
  },
  {
    id: 21006,
    userId: M.wooJinId,
    userEmail: M.wooJinEmail,
    applicationCaseId: null,
    companyName: null,
    jobTitle: null,
    featureType: "COMPANY_RESEARCH",
    status: "FALLBACK",
    model: "mock-demo",
    inputTokens: 1280,
    outputTokens: 1640,
    tokenUsage: 2920,
    creditUsed: 2,
    errorMessage: "1차 모델 타임아웃 → 폴백 처리",
    createdAt: iso(2),
  },
];

const aiUsageSummary: AdminBUsageSummaryResponse = {
  totalCount: 6,
  successCount: 4,
  failedCount: 1,
  tokenUsage: 14280,
  creditUsed: 9,
  jobAnalysisCount: 2,
  companyResearchCount: 2,
  jobPostingOcrCount: 1,
  jobPostingMetadataCount: 1,
};

// ──────────────────────────────────────────────────────────────────────────
// 10) 지원 건 운영 (/admin/application-cases)
// ──────────────────────────────────────────────────────────────────────────
const applicationCaseRows: AdminApplicationCaseRow[] = [
  {
    id: CASE.kakao.id,
    userId: CASE.kakao.userId,
    userEmail: CASE.kakao.email,
    companyName: CASE.kakao.company,
    jobTitle: CASE.kakao.title,
    postingDate: iso(20),
    deadlineDate: iso(-10),
    sourceType: "URL",
    status: "READY",
    favorite: true,
    archivedAt: null,
    deletedAt: null,
    createdAt: iso(14),
    updatedAt: iso(1),
    latestPostingRevision: 1,
    latestJobAnalysisAt: iso(13),
    latestCompanyAnalysisAt: iso(13),
  },
  {
    id: CASE.naver.id,
    userId: CASE.naver.userId,
    userEmail: CASE.naver.email,
    companyName: CASE.naver.company,
    jobTitle: CASE.naver.title,
    postingDate: iso(18),
    deadlineDate: iso(-5),
    sourceType: "PDF",
    status: "APPLIED",
    favorite: false,
    archivedAt: null,
    deletedAt: null,
    createdAt: iso(12),
    updatedAt: iso(2),
    latestPostingRevision: 2,
    latestJobAnalysisAt: iso(12),
    latestCompanyAnalysisAt: iso(12),
  },
  {
    id: CASE.toss.id,
    userId: CASE.toss.userId,
    userEmail: CASE.toss.email,
    companyName: CASE.toss.company,
    jobTitle: CASE.toss.title,
    postingDate: iso(11),
    deadlineDate: iso(-3),
    sourceType: "TEXT",
    status: "ANALYZING",
    favorite: false,
    archivedAt: null,
    deletedAt: null,
    createdAt: iso(9),
    updatedAt: iso(3),
    latestPostingRevision: 2,
    latestJobAnalysisAt: iso(9),
    latestCompanyAnalysisAt: null,
  },
  {
    id: CASE.line.id,
    userId: CASE.line.userId,
    userEmail: CASE.line.email,
    companyName: CASE.line.company,
    jobTitle: CASE.line.title,
    postingDate: iso(8),
    deadlineDate: iso(-14),
    sourceType: "IMAGE",
    status: "DRAFT",
    favorite: false,
    archivedAt: null,
    deletedAt: null,
    createdAt: iso(6),
    updatedAt: iso(4),
    latestPostingRevision: 1,
    latestJobAnalysisAt: iso(6),
    latestCompanyAnalysisAt: null,
  },
];

let nextStatusHistoryId = 20_000;

function seedStatusHistory(row: AdminApplicationCaseRow): AdminStatusHistoryEntry[] {
  const history: AdminStatusHistoryEntry[] = [
    {
      id: nextStatusHistoryId++,
      applicationCaseId: row.id,
      previousStatus: null,
      newStatus: "DRAFT",
      memo: null,
      changedByName: row.userEmail,
      createdAt: row.createdAt,
    },
  ];
  if (row.status === "DRAFT") return history;

  const firstOperationalStatus = row.status === "APPLIED" || row.status === "CLOSED" ? "READY" : row.status;
  history.unshift({
    id: nextStatusHistoryId++,
    applicationCaseId: row.id,
    previousStatus: "DRAFT",
    newStatus: firstOperationalStatus,
    memo: "공고 등록 후 준비 상태 갱신",
    changedByName: "한관리",
    createdAt: iso(3),
  });
  if (row.status === "APPLIED" || row.status === "CLOSED") {
    history.unshift({
      id: nextStatusHistoryId++,
      applicationCaseId: row.id,
      previousStatus: "READY",
      newStatus: row.status,
      memo: row.status === "APPLIED" ? "서류 접수 확인" : "채용 절차 종료 확인",
      changedByName: "한관리",
      createdAt: iso(1),
    });
  }
  return history;
}

const statusHistoryByCase = new Map<number, AdminStatusHistoryEntry[]>(
  applicationCaseRows.map((row) => [row.id, seedStatusHistory(row)]),
);

const applicationCaseSummary: AdminApplicationCaseSummaryResponse = {
  totalCount: 4,
  draftCount: 1,
  analyzingCount: 1,
  readyCount: 1,
  appliedCount: 1,
  closedCount: 0,
  missingJobAnalysisCount: 0,
  missingCompanyAnalysisCount: 2,
  missingAnyAnalysisCount: 2,
  completeAnalysisCount: 2,
  failedUsageCount: 1,
};

function buildAppCaseDetail(id: number): AdminApplicationCaseDetail {
  const row = applicationCaseRows.find((r) => r.id === id) ?? applicationCaseRows[0];
  const job = jobAnalysisRows.find((j) => j.applicationCaseId === row.id);
  const company = companyAnalysisRows.find((c) => c.applicationCaseId === row.id);
  const usageLogs = aiUsageLogs.filter((u) => u.applicationCaseId === row.id).map((u) => ({ ...u }));

  const jobPostings: JobPosting[] = [
    {
      id: 4000 + row.id,
      applicationCaseId: row.id,
      revision: row.latestPostingRevision ?? 1,
      originalText: `${row.companyName} ${row.jobTitle} 채용 공고 원문`,
      uploadedFileUrl: null,
      extractedText: `${row.companyName}에서 ${row.jobTitle}를 채용합니다. 주요 역량: React, JavaScript.`,
      sourceType: row.sourceType,
      createdAt: row.createdAt,
    },
  ];

  const jobAnalyses: AdminApplicationJobAnalysis[] = job
    ? [
        {
          id: job.id,
          applicationCaseId: job.applicationCaseId,
          jobPostingId: job.jobPostingId,
          jobPostingRevision: job.jobPostingRevision,
          employmentType: job.employmentType,
          experienceLevel: job.experienceLevel,
          requiredSkills: job.requiredSkills,
          preferredSkills: job.preferredSkills,
          duties: job.duties,
          qualifications: job.qualifications,
          difficulty: job.difficulty,
          summary: job.summary,
          evidence: job.evidence,
          ambiguousConditions: job.ambiguousConditions,
          confirmedAt: job.confirmedAt,
          adminMemo: job.adminMemo,
          createdAt: job.createdAt,
        },
      ]
    : [];

  const companyAnalyses: AdminApplicationCompanyAnalysis[] = company
    ? [
        {
          id: company.id,
          applicationCaseId: company.applicationCaseId,
          jobPostingId: company.jobPostingId,
          jobPostingRevision: company.jobPostingRevision,
          companySummary: company.companySummary,
          recentIssues: company.recentIssues,
          industry: company.industry,
          competitors: company.competitors,
          interviewPoints: company.interviewPoints,
          sources: company.sources,
          verifiedFacts: company.verifiedFacts,
          aiInferences: company.aiInferences,
          unknowns: company.unknowns,
          sourceType: company.sourceType,
          checkedAt: company.checkedAt,
          refreshRecommendedAt: company.refreshRecommendedAt,
          confirmedAt: company.confirmedAt,
          adminMemo: company.adminMemo,
          createdAt: company.createdAt,
        },
      ]
    : [];

  return {
    applicationCase: { ...row },
    jobPostings,
    jobAnalyses,
    companyAnalyses,
    usageLogs,
    statusHistory: (statusHistoryByCase.get(row.id) ?? []).map((entry) => ({ ...entry })),
  };
}

// ──────────────────────────────────────────────────────────────────────────
// 라우트 정의 (구체 경로/summary 를 :id 보다 먼저 등록해 우선 매칭)
// ──────────────────────────────────────────────────────────────────────────
export const adminAnalysisOpsRoutes: MockRoute[] = [
  // ===== 분석 통계 / 실패 / 품질 / 타임라인 / 실행 이력 =====
  { method: "GET", pattern: /^\/admin\/analytics\/summary$/, handler: () => ({ ...analyticsSummary }) },
  { method: "GET", pattern: /^\/admin\/analytics\/failures$/, handler: () => [...analysisFailures] },
  { method: "GET", pattern: /^\/admin\/analytics\/quality-flags$/, handler: () => [...qualityFlags] },
  {
    // PATCH /admin/analytics/quality-flags/:fitAnalysisId/:flagType/resolve → api<void>
    method: "PATCH",
    pattern: /^\/admin\/analytics\/quality-flags\/(\d+)\/([^/]+)\/resolve$/,
    handler: ({ params }: MockContext) => {
      const fitId = Number(params[0]);
      const idx = qualityFlags.findIndex((f) => f.fitAnalysisId === fitId);
      if (idx >= 0) qualityFlags.splice(idx, 1);
      return null;
    },
  },
  {
    method: "GET",
    pattern: /^\/admin\/analytics\/users\/(\d+)\/timeline$/,
    handler: ({ params }: MockContext) => [...(userTimelines[Number(params[0])] ?? [])],
  },

  // 실행 이력 운영 메모 (구체 → 목록 순)
  {
    method: "PATCH",
    pattern: /^\/admin\/analytics\/runs\/(\d+)\/memos\/(\d+)$/,
    handler: ({ params, body }: MockContext) => {
      const memoId = Number(params[1]);
      const req = (body ?? {}) as AdminCareerRunMemoRequest;
      const target = careerRunMemos.find((m) => m.id === memoId);
      if (target) {
        target.memoType = req.memoType ?? target.memoType;
        target.content = req.content ?? target.content;
        target.updatedAt = new Date().toISOString();
        return { ...target };
      }
      const created: AdminCareerRunMemo = {
        id: memoId,
        careerAnalysisRunId: Number(params[0]),
        adminUserId: M.adminId,
        adminName: M.adminName,
        adminEmail: M.adminEmail,
        memoType: req.memoType ?? "GENERAL",
        content: req.content ?? "",
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };
      return created;
    },
  },
  {
    method: "DELETE",
    pattern: /^\/admin\/analytics\/runs\/(\d+)\/memos\/(\d+)$/,
    handler: ({ params }: MockContext) => {
      const idx = careerRunMemos.findIndex((m) => m.id === Number(params[1]));
      if (idx >= 0) careerRunMemos.splice(idx, 1);
      return null;
    },
  },
  {
    method: "GET",
    pattern: /^\/admin\/analytics\/runs\/(\d+)\/memos$/,
    handler: ({ params }: MockContext) =>
      careerRunMemos.filter((m) => m.careerAnalysisRunId === Number(params[0])).map((m) => ({ ...m })),
  },
  {
    method: "POST",
    pattern: /^\/admin\/analytics\/runs\/(\d+)\/memos$/,
    handler: ({ params, body }: MockContext) => {
      const runId = Number(params[0]);
      const req = (body ?? {}) as AdminCareerRunMemoRequest;
      const memo: AdminCareerRunMemo = {
        id: ++careerRunMemoSeq,
        careerAnalysisRunId: runId,
        adminUserId: M.adminId,
        adminName: M.adminName,
        adminEmail: M.adminEmail,
        memoType: req.memoType ?? "GENERAL",
        content: req.content ?? "",
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };
      careerRunMemos.unshift(memo);
      const run = careerRuns.find((r) => r.id === runId);
      if (run) {
        run.memoCount += 1;
        run.latestMemoAt = memo.createdAt;
      }
      return { ...memo };
    },
  },
  {
    method: "GET",
    pattern: /^\/admin\/analytics\/runs$/,
    handler: ({ query }: MockContext) => {
      const userId = query.get("userId");
      const list = userId ? careerRuns.filter((r) => r.userId === Number(userId)) : careerRuns;
      return list.map((r) => ({ ...r }));
    },
  },

  // ===== 적합도 분석 운영 =====
  {
    method: "PATCH",
    pattern: /^\/admin\/fit-analyses\/(\d+)\/memos\/(\d+)$/,
    handler: ({ params, body }: MockContext) => {
      const memoId = Number(params[1]);
      const req = (body ?? {}) as AdminFitAnalysisMemoRequest;
      const target = fitAnalysisMemos.find((m) => m.id === memoId);
      if (target) {
        target.memoType = req.memoType ?? target.memoType;
        target.content = req.content ?? target.content;
        target.updatedAt = new Date().toISOString();
        return { ...target };
      }
      const created: AdminFitAnalysisMemo = {
        id: memoId,
        fitAnalysisId: Number(params[0]),
        adminUserId: M.adminId,
        adminName: M.adminName,
        adminEmail: M.adminEmail,
        memoType: req.memoType ?? "GENERAL",
        content: req.content ?? "",
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };
      return created;
    },
  },
  {
    method: "DELETE",
    pattern: /^\/admin\/fit-analyses\/(\d+)\/memos\/(\d+)$/,
    handler: ({ params }: MockContext) => {
      const idx = fitAnalysisMemos.findIndex((m) => m.id === Number(params[1]));
      if (idx >= 0) fitAnalysisMemos.splice(idx, 1);
      return null;
    },
  },
  {
    method: "GET",
    pattern: /^\/admin\/fit-analyses\/(\d+)\/memos$/,
    handler: ({ params }: MockContext) =>
      fitAnalysisMemos.filter((m) => m.fitAnalysisId === Number(params[0])).map((m) => ({ ...m })),
  },
  {
    method: "POST",
    pattern: /^\/admin\/fit-analyses\/(\d+)\/memos$/,
    handler: ({ params, body }: MockContext) => {
      const fitId = Number(params[0]);
      const req = (body ?? {}) as AdminFitAnalysisMemoRequest;
      const memo: AdminFitAnalysisMemo = {
        id: ++fitMemoSeq,
        fitAnalysisId: fitId,
        adminUserId: M.adminId,
        adminName: M.adminName,
        adminEmail: M.adminEmail,
        memoType: req.memoType ?? "GENERAL",
        content: req.content ?? "",
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };
      fitAnalysisMemos.unshift(memo);
      const item = fitAnalysisList.find((f) => f.id === fitId);
      if (item) {
        item.memoCount += 1;
        item.latestMemoAt = memo.createdAt;
        if (memo.memoType === "REANALYSIS") item.reanalysisRequested = true;
      }
      return { ...memo };
    },
  },
  {
    method: "GET",
    pattern: /^\/admin\/fit-analyses\/(\d+)$/,
    handler: ({ params }: MockContext) => buildFitDetail(Number(params[0])),
  },
  { method: "GET", pattern: /^\/admin\/fit-analyses$/, handler: () => fitAnalysisList.map((f) => ({ ...f })) },

  // ===== 공고 분석 운영 =====
  { method: "GET", pattern: /^\/admin\/job-analysis\/summary$/, handler: () => ({ ...jobAnalysisSummary }) },
  {
    method: "PATCH",
    pattern: /^\/admin\/job-analysis\/(\d+)\/memo$/,
    handler: ({ params, body }: MockContext) => {
      const row = jobAnalysisRows.find((r) => r.id === Number(params[0]));
      if (row) row.adminMemo = ((body ?? {}) as { adminMemo?: string }).adminMemo ?? null;
      return null;
    },
  },
  {
    method: "GET",
    pattern: /^\/admin\/job-analysis$/,
    handler: ({ query }: MockContext) => {
      const caseId = query.get("applicationCaseId");
      const userId = query.get("userId");
      let list = [...jobAnalysisRows];
      if (caseId) list = list.filter((r) => r.applicationCaseId === Number(caseId));
      if (userId) list = list.filter((r) => r.userId === Number(userId));
      return list.map((r) => ({ ...r }));
    },
  },

  // ===== 기업 분석 운영 =====
  { method: "GET", pattern: /^\/admin\/company-analysis\/summary$/, handler: () => ({ ...companyAnalysisSummary }) },
  {
    method: "PATCH",
    pattern: /^\/admin\/company-analysis\/(\d+)\/memo$/,
    handler: ({ params, body }: MockContext) => {
      const row = companyAnalysisRows.find((r) => r.id === Number(params[0]));
      if (row) row.adminMemo = ((body ?? {}) as { adminMemo?: string }).adminMemo ?? null;
      return null;
    },
  },
  {
    method: "PATCH",
    pattern: /^\/admin\/company-analysis\/(\d+)\/metadata$/,
    handler: ({ params, body }: MockContext) => {
      const row = companyAnalysisRows.find((r) => r.id === Number(params[0]));
      if (row) {
        const req = (body ?? {}) as {
          sourceType?: string | null;
          checkedAt?: string | null;
          refreshRecommendedAt?: string | null;
          clearCheckedAt?: boolean;
          clearRefreshRecommendedAt?: boolean;
        };
        if (req.sourceType !== undefined) row.sourceType = req.sourceType;
        if (req.clearCheckedAt) row.checkedAt = null;
        else if (req.checkedAt !== undefined) row.checkedAt = req.checkedAt;
        if (req.clearRefreshRecommendedAt) row.refreshRecommendedAt = null;
        else if (req.refreshRecommendedAt !== undefined) row.refreshRecommendedAt = req.refreshRecommendedAt;
      }
      return null;
    },
  },
  {
    method: "GET",
    pattern: /^\/admin\/company-analysis$/,
    handler: ({ query }: MockContext) => {
      const caseId = query.get("applicationCaseId");
      const userId = query.get("userId");
      let list = [...companyAnalysisRows];
      if (caseId) list = list.filter((r) => r.applicationCaseId === Number(caseId));
      if (userId) list = list.filter((r) => r.userId === Number(userId));
      return list.map((r) => ({ ...r }));
    },
  },

  // ===== AI 사용량(B 그룹) =====
  { method: "GET", pattern: /^\/admin\/ai-usage\/b\/summary$/, handler: () => ({ ...aiUsageSummary }) },
  {
    method: "GET",
    pattern: /^\/admin\/ai-usage\/b$/,
    handler: ({ query }: MockContext) => {
      const feature = query.get("featureType");
      const status = query.get("status");
      const caseId = query.get("applicationCaseId");
      let list = [...aiUsageLogs];
      if (feature) list = list.filter((r) => r.featureType === feature);
      if (status) list = list.filter((r) => r.status === status);
      if (caseId) list = list.filter((r) => r.applicationCaseId === Number(caseId));
      return list.map((r) => ({ ...r }));
    },
  },

  // ===== 지원 건 운영 =====
  { method: "GET", pattern: /^\/admin\/application-cases\/summary$/, handler: () => ({ ...applicationCaseSummary }) },
  {
    method: "PATCH",
    pattern: /^\/admin\/application-cases\/(\d+)\/status$/,
    handler: ({ params, body }: MockContext) => {
      const row = applicationCaseRows.find((r) => r.id === Number(params[0])) ?? applicationCaseRows[0];
      const req = (body ?? {}) as { status?: AdminApplicationCaseRow["status"]; memo?: string };
      const previousStatus = row.status;
      if (req.status && req.status !== previousStatus) {
        const history = statusHistoryByCase.get(row.id) ?? [];
        history.unshift({
          id: nextStatusHistoryId++,
          applicationCaseId: row.id,
          previousStatus,
          newStatus: req.status,
          memo: req.memo?.trim() || null,
          changedByName: "한관리",
          createdAt: new Date().toISOString(),
        });
        statusHistoryByCase.set(row.id, history);
        row.status = req.status;
      }
      row.updatedAt = new Date().toISOString();
      return { ...row };
    },
  },
  {
    method: "GET",
    pattern: /^\/admin\/application-cases\/(\d+)$/,
    handler: ({ params }: MockContext) => buildAppCaseDetail(Number(params[0])),
  },
  {
    method: "GET",
    pattern: /^\/admin\/application-cases$/,
    handler: ({ query }: MockContext) => {
      const status = query.get("status");
      const userId = query.get("userId");
      let list = [...applicationCaseRows];
      if (status) list = list.filter((r) => r.status === status);
      if (userId) list = list.filter((r) => r.userId === Number(userId));
      return list.map((r) => ({ ...r }));
    },
  },
];
