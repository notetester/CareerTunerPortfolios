// 데모/목: 프로필·설정·계정·동의 도메인 라우트.
// 페르소나는 프론트엔드 개발자 지망 "김데모"(userId 9001, demo@careertuner.dev, PRO/50크레딧)로
// data.ts 의 demoUser 와 일치시킨다. 지원 건 id(101 카카오·102 네이버·103 토스·104 라인)도 동일하게 맞춘다.
//
// 커버 엔드포인트(이 도메인이 미커버였던 것만):
//   GET  /profile                    -> UserProfile (백엔드 원본 shape; education/career/projects 등 unknown 필드)
//   PUT  /profile                    -> UserProfile (요청 body 를 저장본에 반영 후 echo)
//   POST /profile/ai/summary         -> ProfileAiResponse
//   POST /profile/ai/skills          -> ProfileAiResponse
//   POST /profile/ai/completeness    -> ProfileCompleteness
//   GET  /consents/me                -> ConsentStatus
//   POST /consents/me                -> ConsentStatus (요청 동의값 반영)
//   POST /consents/ai/revoke         -> ConsentStatus (AI_DATA 철회)
//   (챗봇 /chatbot/* 는 ./chatbot.ts 담당)
//
// /auth/me 는 코어 레지스트리에서 이미 처리한다(중복 등록하지 않음).
// 알림 링크의 /analysis/profile, /analysis/job 는 프런트 라우트 경로일 뿐 api() 호출 엔드포인트가 아니므로 제외.
import type { MockRoute } from "../registry";
import { iso } from "../registry";
import type {
  UserProfile,
  ProfileAiResponse,
  ProfileCompleteness,
  ProfilePortfolioFile,
  UserProfileVersion,
} from "@/app/profile/profileApi";
import type { ConsentStatus, ConsentView } from "@/app/auth/consentApi";
import { ApiError } from "@/app/lib/api";

const USER_ID = 9001;
let portfolioFileSeq = 7900;
const portfolioContents = new Map<number, Blob>();
const demoPortfolioFiles: ProfilePortfolioFile[] = [
  {
    id: 7899,
    kind: "PORTFOLIO",
    refType: "USER_PROFILE_PORTFOLIO",
    refId: 5001,
    originalName: "kimdemo-portfolio.pdf",
    contentType: "application/pdf",
    sizeBytes: 482_100,
    contentUrl: "/api/file/7899/content",
    createdAt: iso(3),
  },
];
portfolioContents.set(
  7899,
  new Blob(["CareerTuner demo portfolio\nReact and TypeScript project evidence"], {
    type: "application/pdf",
  }),
);

function uploadMockPortfolio(body: unknown): ProfilePortfolioFile {
  const form = body instanceof FormData ? body : null;
  const file = form?.get("file");
  const id = ++portfolioFileSeq;
  const uploaded: ProfilePortfolioFile = {
    id,
    kind: "PORTFOLIO",
    refType: "USER_PROFILE_PORTFOLIO",
    refId: demoProfile.id ?? 5001,
    originalName: file instanceof File ? file.name : `portfolio-${id}.pdf`,
    contentType: file instanceof File ? file.type : "application/pdf",
    sizeBytes: file instanceof File ? file.size : 0,
    contentUrl: `/api/file/${id}/content`,
    createdAt: new Date().toISOString(),
  };
  demoPortfolioFiles.unshift(uploaded);
  portfolioContents.set(
    id,
    file instanceof File
      ? file.slice(0, file.size, file.type || "application/octet-stream")
      : new Blob([`Demo portfolio ${id}`], { type: "application/pdf" }),
  );
  return uploaded;
}

function deleteMockPortfolio(rawId: string | undefined): null {
  const id = Number(rawId);
  const index = demoPortfolioFiles.findIndex((file) => file.id === id);
  if (index >= 0) demoPortfolioFiles.splice(index, 1);
  portfolioContents.delete(id);
  return null;
}

function mockPortfolioContent(rawId: string | undefined): Blob {
  const content = portfolioContents.get(Number(rawId));
  if (!content) throw new Error("포트폴리오 파일을 찾을 수 없습니다.");
  return content;
}

// ── 김데모 프로필(프론트엔드 개발자). education/career/projects 는 백엔드 JSON 배열, skills 류는 문자열 배열. ──
const demoProfile: UserProfile = {
  id: 5001,
  userId: USER_ID,
  desiredJob: "프론트엔드 개발자",
  desiredIndustry: "IT 플랫폼",
  skills: ["React", "JavaScript", "TypeScript", "REST API", "Git", "HTML/CSS", "Zustand"],
  certificates: ["정보처리기사", "SQLD", "TOEIC 870"],
  languages: ["영어(비즈니스 회화 가능)", "일본어(기초)"],
  portfolioLinks: [
    "https://github.com/kimdemo",
    "https://kimdemo.dev",
    "https://velog.io/@kimdemo",
  ],
  education: [
    {
      school: "한국대학교",
      major: "컴퓨터공학과",
      startDate: "2016-03",
      endDate: "2022-02",
      status: "졸업",
      period: "2016-03 - 2022-02",
    },
  ],
  career: [
    {
      company: "스타트업 디버그",
      role: "프론트엔드 개발 인턴",
      startDate: "2022-07",
      endDate: "2023-01",
      tasks: "React 기반 어드민 대시보드 화면 개발, REST API 연동, 공통 컴포넌트 정리",
      achievements: "반복 코드 30% 감소, 페이지 로딩 체감 속도 개선",
      period: "2022-07 - 2023-01",
    },
  ],
  projects: [
    {
      title: "취업 일정 관리 웹앱",
      type: "개인 프로젝트",
      role: "프론트엔드 전담",
      startDate: "2023-03",
      endDate: "2023-06",
      description: "React + TypeScript 로 지원 일정·서류 상태를 관리하는 SPA 제작, REST API 연동",
      result: "GitHub 스타 40개, 채용 면접에서 포트폴리오로 활용",
      period: "2023-03 - 2023-06",
    },
    {
      title: "프론트엔드 스터디 운영",
      type: "동아리",
      role: "스터디장",
      startDate: "2023-09",
      endDate: "현재",
      description: "주 1회 React·TypeScript 심화 스터디 운영 및 코드 리뷰 진행",
      result: "12주 완주, 참여자 6명 전원 프로젝트 1건 이상 완성",
      period: "2023-09 - 현재",
    },
  ],
  preferences: {
    region: "서울",
    workType: "재택 병행",
    salary: "3,400만원 이상",
    employmentType: "정규직",
  },
  resumeText:
    "프론트엔드 개발자 김데모입니다. React와 TypeScript를 중심으로 사용자 화면을 만들고, " +
    "REST API 연동과 상태 관리에 익숙합니다. 인턴 기간 동안 어드민 대시보드를 개발하며 협업 경험을 쌓았고, " +
    "개인 프로젝트로 취업 일정 관리 웹앱을 만들었습니다.",
  selfIntro:
    "사용자가 막힘 없이 흐르는 화면을 만드는 데 집중합니다. 작은 컴포넌트부터 재사용성을 고려해 설계하고, " +
    "코드 리뷰와 스터디 운영을 통해 함께 성장하는 협업을 중요하게 생각합니다.",
  versionNo: 1,
  updatedAt: iso(2),
};

let profileVersionSeq = 8100;
const demoProfileVersions: UserProfileVersion[] = [{
  ...structuredClone(demoProfile),
  id: profileVersionSeq,
  userId: USER_ID,
  versionNo: 1,
  source: "MIGRATION",
  createdAt: demoProfile.updatedAt ?? iso(2),
}];

// PUT /profile 요청을 반영한다(목이므로 세션 내 메모리에만). 요청에 없는 필드는 기존 값 유지.
function applyProfileUpdate(body: unknown): UserProfile {
  const { baseVersionNo, ...patch } = (body ?? {}) as Partial<UserProfile> & {
    baseVersionNo?: number | null;
  };
  if (baseVersionNo == null || baseVersionNo !== demoProfile.versionNo) {
    throw new ApiError(
      "프로필이 다른 화면에서 변경되었습니다. 최신 내용을 다시 불러온 뒤 저장해 주세요.",
      "CONFLICT",
      409,
    );
  }
  const versionNo = (demoProfile.versionNo ?? 0) + 1;
  const createdAt = new Date().toISOString();
  Object.assign(demoProfile, patch, { versionNo, updatedAt: createdAt });
  demoProfileVersions.unshift({
    ...structuredClone(demoProfile),
    id: ++profileVersionSeq,
    userId: USER_ID,
    versionNo,
    source: "MANUAL_SAVE",
    createdAt,
  });
  return demoProfile;
}

// ── 프로필 AI 요약/역량 추출(둘 다 ProfileAiResponse). ──
const demoAiSummary: ProfileAiResponse = {
  featureType: "PROFILE_SUMMARY",
  summary:
    "React·TypeScript 중심의 프론트엔드 개발자로, REST API 연동과 컴포넌트 재사용 설계에 강점이 있습니다. " +
    "인턴 실무와 개인 프로젝트를 통해 협업과 완성 경험을 모두 갖췄습니다.",
  extractedSkills: ["React", "TypeScript", "JavaScript", "REST API", "Git", "상태 관리"],
  strengths: ["컴포넌트 재사용 설계", "API 연동 경험", "꾸준한 학습·스터디 운영"],
  gaps: ["대규모 트래픽 성능 최적화 경험", "테스트 코드(Jest) 작성", "CI/CD 구축 경험"],
  recommendations: [
    "Jest/React Testing Library로 테스트 코드를 1개 프로젝트에 적용해 보세요.",
    "성능 최적화(코드 스플리팅, 메모이제이션) 사례를 포트폴리오에 추가하세요.",
    "GitHub Actions로 간단한 CI 파이프라인을 구성해 경험을 쌓으세요.",
  ],
  completenessScore: 82,
  jobFamily: "FRONTEND_DEVELOPER",
  jobFamilyLabel: "프론트엔드 개발",
  criteria: [
    {
      criterion: "TECH_STACK",
      label: "기술 스택 적합도",
      rawScore: 88,
      weight: 30,
      weightedScore: 26.4,
      evidence: "React, TypeScript, REST API 등 직무 필수 스택을 보유.",
      improvement: "성능 최적화·테스트 관련 스택을 보강하면 좋습니다.",
    },
    {
      criterion: "PROJECT",
      label: "프로젝트 경험",
      rawScore: 80,
      weight: 30,
      weightedScore: 24,
      evidence: "개인 프로젝트와 인턴 실무 경험을 보유.",
      improvement: "팀 단위 협업 프로젝트를 1건 더 확보하면 강력합니다.",
    },
    {
      criterion: "EXPERIENCE",
      label: "경력",
      rawScore: 70,
      weight: 25,
      weightedScore: 17.5,
      evidence: "프론트엔드 인턴 6개월 경험.",
      improvement: "정규직 또는 장기 실무 경험을 확보하세요.",
    },
    {
      criterion: "BASIC_INFO",
      label: "기본 정보 충실도",
      rawScore: 90,
      weight: 15,
      weightedScore: 13.5,
      evidence: "희망 직무·산업·포트폴리오 링크가 충실히 작성됨.",
      improvement: "자기소개에 구체적 수치를 더하면 좋습니다.",
    },
  ],
  model: "mock-demo",
  status: "SUCCESS",
  profileVersionId: profileVersionSeq,
  profileVersionNo: 1,
};

const demoAiSkills: ProfileAiResponse = {
  ...demoAiSummary,
  featureType: "PROFILE_SKILLS",
  summary:
    "프로필에서 프론트엔드 직무 역량 키워드를 추출했습니다. 핵심 프레임워크와 API 연동 역량이 두드러집니다.",
  extractedSkills: [
    "React",
    "TypeScript",
    "JavaScript",
    "REST API",
    "Git",
    "HTML/CSS",
    "상태 관리(Zustand)",
    "컴포넌트 설계",
  ],
};

// ── 프로필 완성도 진단(ProfileCompleteness). ──
const demoCompleteness: ProfileCompleteness = {
  score: 82,
  completed: ["희망 직무", "기술 스택", "학력", "경력", "프로젝트", "자기소개"],
  missing: ["자격증 상세(취득일)", "테스트 경험", "수상/외부 활동"],
  recommendations: [
    "프로젝트에 사용 기술과 성과 수치를 더 구체적으로 적어주세요.",
    "테스트 코드 작성 경험을 추가하면 직무 적합도가 올라갑니다.",
    "포트폴리오 링크에 대표 작업물 설명을 붙여주세요.",
  ],
  jobFamily: "FRONTEND_DEVELOPER",
  jobFamilyLabel: "프론트엔드 개발",
  criteria: demoAiSummary.criteria,
  model: "mock-demo",
  status: "SUCCESS",
  profileVersionId: profileVersionSeq,
  profileVersionNo: 1,
};

// ── 동의(약관/개인정보/AI데이터/이력서분석/마케팅) 상태와 변경 이력. ──
const consentHistory: ConsentView[] = [
  { id: 7001, userId: USER_ID, userEmail: "demo@careertuner.dev", consentType: "TERMS", consentVersion: "v2026.07", agreed: true, agreedAt: iso(30), revokedAt: null, source: "SIGNUP", createdAt: iso(30) },
  { id: 7002, userId: USER_ID, userEmail: "demo@careertuner.dev", consentType: "PRIVACY", consentVersion: "v2026.07", agreed: true, agreedAt: iso(30), revokedAt: null, source: "SIGNUP", createdAt: iso(30) },
  { id: 7003, userId: USER_ID, userEmail: "demo@careertuner.dev", consentType: "AI_DATA", consentVersion: "v2026.07", agreed: true, agreedAt: iso(30), revokedAt: null, source: "SIGNUP", createdAt: iso(30) },
  { id: 7004, userId: USER_ID, userEmail: "demo@careertuner.dev", consentType: "RESUME_ANALYSIS", consentVersion: "v2026.07", agreed: true, agreedAt: iso(30), revokedAt: null, source: "SIGNUP", createdAt: iso(30) },
  { id: 7005, userId: USER_ID, userEmail: "demo@careertuner.dev", consentType: "MARKETING", consentVersion: "v2026.07", agreed: false, agreedAt: null, revokedAt: iso(10), source: "SETTINGS", createdAt: iso(10) },
];
let consentHistorySeq = 7005;

const consentStatus: ConsentStatus = {
  termsAgreed: true,
  privacyAgreed: true,
  aiDataAgreed: true,
  resumeAnalysisAgreed: true,
  marketingAgreed: false,
  requiredConsentsMissing: false,
  history: consentHistory,
};

function recomputeRequiredMissing(): void {
  consentStatus.requiredConsentsMissing = !(consentStatus.termsAgreed && consentStatus.privacyAgreed);
}

function applyConsents(body: unknown): ConsentStatus {
  const request = (body ?? {}) as {
    termsAgreed?: boolean;
    privacyAgreed?: boolean;
    aiDataAgreed?: boolean;
    resumeAnalysisAgreed?: boolean;
    marketingAgreed?: boolean;
  };
  recordMockConsent("TERMS", "termsAgreed", request.termsAgreed);
  recordMockConsent("PRIVACY", "privacyAgreed", request.privacyAgreed);
  recordMockConsent("AI_DATA", "aiDataAgreed", request.aiDataAgreed);
  recordMockConsent("RESUME_ANALYSIS", "resumeAnalysisAgreed", request.resumeAnalysisAgreed);
  recordMockConsent("MARKETING", "marketingAgreed", request.marketingAgreed);
  recomputeRequiredMissing();
  return consentStatus;
}

type ConsentStatusKey = "termsAgreed" | "privacyAgreed" | "aiDataAgreed" | "resumeAnalysisAgreed" | "marketingAgreed";

function recordMockConsent(consentType: string, key: ConsentStatusKey, agreed: boolean | undefined, source = "SETTINGS"): void {
  if (typeof agreed !== "boolean" || consentStatus[key] === agreed) return;
  consentStatus[key] = agreed;
  const now = new Date().toISOString();
  consentStatus.history.unshift({
    id: ++consentHistorySeq,
    userId: USER_ID,
    userEmail: "demo@careertuner.dev",
    consentType,
    consentVersion: "v2026.07",
    agreed,
    agreedAt: agreed ? now : null,
    revokedAt: agreed ? null : now,
    source,
    createdAt: now,
  });
}

function revokeMockConsent(consentType: string): ConsentStatus {
  const keys: Record<string, ConsentStatusKey> = {
    terms: "termsAgreed",
    privacy: "privacyAgreed",
    "ai-data": "aiDataAgreed",
    "resume-analysis": "resumeAnalysisAgreed",
    marketing: "marketingAgreed",
  };
  const key = keys[consentType];
  if (key) recordMockConsent(consentType.replace(/-/g, "_").toUpperCase(), key, false, "REVOKE");
  recomputeRequiredMissing();
  return consentStatus;
}

// (챗봇 mock 은 ./chatbot.ts 로 이관 — 구 shape(answer/matchedFaqIds)가 현 useChatbot 계약과 어긋나 있었다.)

export const profileRoutes: MockRoute[] = [
  // 프로필 조회/저장
  { method: "GET", pattern: /^\/profile$/, handler: () => demoProfile },
  { method: "PUT", pattern: /^\/profile$/, handler: ({ body }) => applyProfileUpdate(body) },
  { method: "GET", pattern: /^\/profile\/versions$/, handler: ({ query }) => demoProfileVersions.slice(0, Number(query.get("limit") ?? 20) || 20) },
  { method: "GET", pattern: /^\/profile\/versions\/(\d+)$/, handler: ({ params }) => demoProfileVersions.find((version) => version.id === Number(params[0])) ?? null },
  { method: "GET", pattern: /^\/profile\/portfolio-files$/, handler: () => demoPortfolioFiles },
  { method: "POST", pattern: /^\/profile\/portfolio-files\/upload$/, handler: ({ body }) => uploadMockPortfolio(body) },
  { method: "POST", pattern: /^\/profile\/portfolio-files\/link$/, handler: () => demoPortfolioFiles },
  { method: "DELETE", pattern: /^\/profile\/portfolio-files\/(\d+)$/, handler: ({ params }) => deleteMockPortfolio(params[0]) },
  { method: "GET", pattern: /^\/file\/(\d+)\/content$/, handler: ({ params }) => mockPortfolioContent(params[0]) },

  // 프로필 AI 도구
  {
    method: "POST",
    pattern: /^\/profile\/ai\/summary$/,
    handler: () => ({
      ...demoAiSummary,
      profileVersionId: demoProfileVersions[0]?.id ?? null,
      profileVersionNo: demoProfileVersions[0]?.versionNo ?? null,
    }),
  },
  {
    method: "POST",
    pattern: /^\/profile\/ai\/skills$/,
    handler: () => ({
      ...demoAiSkills,
      profileVersionId: demoProfileVersions[0]?.id ?? null,
      profileVersionNo: demoProfileVersions[0]?.versionNo ?? null,
    }),
  },
  {
    method: "POST",
    pattern: /^\/profile\/ai\/completeness$/,
    handler: () => ({
      ...demoCompleteness,
      profileVersionId: demoProfileVersions[0]?.id ?? null,
      profileVersionNo: demoProfileVersions[0]?.versionNo ?? null,
    }),
  },
  {
    method: "GET",
    pattern: /^\/profile\/ai-analysis$/,
    handler: () => ({
      hasAnalysis: true,
      summary: demoAiSummary.summary,
      strengths: demoAiSummary.strengths,
      gaps: demoAiSummary.gaps,
      recommendations: demoAiSummary.recommendations,
      extractedSkills: demoAiSkills.extractedSkills,
      jobFamily: demoAiSummary.jobFamily ?? null,
      jobFamilyLabel: demoAiSummary.jobFamilyLabel ?? null,
      completenessScore: demoCompleteness.score,
      aiScore: demoAiSummary.aiScore ?? null,
      criteria: demoAiSummary.criteria ?? [],
      qualityWarnings: demoAiSummary.qualityWarnings ?? [],
      profileVersionId: demoProfileVersions[0]?.id ?? null,
      profileVersionNo: demoProfileVersions[0]?.versionNo ?? null,
      analyzedAt: new Date().toISOString(),
    }),
  },

  // 동의(설정 > AI 데이터/개인정보 탭)
  { method: "GET", pattern: /^\/consents\/me$/, handler: () => consentStatus },
  { method: "POST", pattern: /^\/consents\/me$/, handler: ({ body }) => applyConsents(body) },
  { method: "POST", pattern: /^\/consents\/ai\/revoke$/, handler: () => revokeMockConsent("ai-data") },
  { method: "POST", pattern: /^\/consents\/([^/]+)\/revoke$/, handler: ({ params }) => revokeMockConsent(params[0] ?? "") },
];
