// 데모/목: 프로필·설정·계정·동의·챗봇 도메인 라우트.
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
//   POST /chatbot/ask                -> { answer, links, matchedFaqIds, topSimilarity }  (useChatbot 내부 응답 shape)
//
// /auth/me 는 코어 레지스트리에서 이미 처리한다(중복 등록하지 않음).
// 알림 링크의 /analysis/profile, /analysis/job 는 프런트 라우트 경로일 뿐 api() 호출 엔드포인트가 아니므로 제외.
import type { MockRoute } from "../registry";
import { iso } from "../registry";
import type {
  UserProfile,
  ProfileAiResponse,
  ProfileCompleteness,
} from "@/app/profile/profileApi";
import type { ConsentStatus, ConsentView } from "@/app/auth/consentApi";
import type { SiteLink } from "@/features/support/types/chatbot";

const USER_ID = 9001;

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
  updatedAt: iso(2),
};

// PUT /profile 요청을 반영한다(목이므로 세션 내 메모리에만). 요청에 없는 필드는 기존 값 유지.
function applyProfileUpdate(body: unknown): UserProfile {
  const patch = (body ?? {}) as Partial<UserProfile>;
  Object.assign(demoProfile, patch, { updatedAt: new Date().toISOString() });
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
};

// ── 동의(약관/개인정보/AI데이터/마케팅) 상태. AI_DATA 는 토글 가능. ──
const consentHistory: ConsentView[] = [
  { id: 7001, userId: USER_ID, userEmail: "demo@careertuner.dev", consentType: "TERMS", agreed: true, agreedAt: iso(30), revokedAt: null, source: "SIGNUP", createdAt: iso(30) },
  { id: 7002, userId: USER_ID, userEmail: "demo@careertuner.dev", consentType: "PRIVACY", agreed: true, agreedAt: iso(30), revokedAt: null, source: "SIGNUP", createdAt: iso(30) },
  { id: 7003, userId: USER_ID, userEmail: "demo@careertuner.dev", consentType: "AI_DATA", agreed: true, agreedAt: iso(30), revokedAt: null, source: "SIGNUP", createdAt: iso(30) },
  { id: 7004, userId: USER_ID, userEmail: "demo@careertuner.dev", consentType: "MARKETING", agreed: false, agreedAt: null, revokedAt: iso(10), source: "SETTINGS", createdAt: iso(30) },
];

const consentStatus: ConsentStatus = {
  termsAgreed: true,
  privacyAgreed: true,
  aiDataAgreed: true,
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
    marketingAgreed?: boolean;
  };
  if (typeof request.termsAgreed === "boolean") consentStatus.termsAgreed = request.termsAgreed;
  if (typeof request.privacyAgreed === "boolean") consentStatus.privacyAgreed = request.privacyAgreed;
  if (typeof request.aiDataAgreed === "boolean") consentStatus.aiDataAgreed = request.aiDataAgreed;
  if (typeof request.marketingAgreed === "boolean") consentStatus.marketingAgreed = request.marketingAgreed;
  recomputeRequiredMissing();
  return consentStatus;
}

function revokeAiConsent(): ConsentStatus {
  consentStatus.aiDataAgreed = false;
  const aiRow = consentStatus.history.find((row) => row.consentType === "AI_DATA");
  if (aiRow) {
    aiRow.agreed = false;
    aiRow.revokedAt = new Date().toISOString();
  }
  recomputeRequiredMissing();
  return consentStatus;
}

// ── 챗봇 응답. useChatbot 내부의 ChatbotApiResponse 형태를 그대로 맞춘다(비공개 인터페이스라 로컬 미러). ──
interface ChatbotAskResponse {
  answer: string;
  links: SiteLink[];
  matchedFaqIds: number[];
  topSimilarity: number;
}

// 질문 키워드별 데모 답변. 매칭 실패는 빈 결과(useChatbot 가 not_found 처리).
function answerChatbot(body: unknown): ChatbotAskResponse {
  const question = String((body as { question?: string } | null)?.question ?? "");
  const q = question.toLowerCase();

  const has = (...keys: string[]) => keys.some((k) => question.includes(k) || q.includes(k.toLowerCase()));

  if (has("환불", "돈", "결제 취소", "취소")) {
    return {
      answer:
        "결제 후 7일 이내, 크레딧을 사용하지 않은 경우 전액 환불이 가능합니다. " +
        "마이페이지 > 결제/구독에서 환불 신청을 하시거나 고객센터로 문의해 주세요.",
      links: [
        { url: "/billing", label: "결제/구독 관리" },
        { url: "/support/faq", label: "환불 정책 FAQ" },
      ],
      matchedFaqIds: [12, 14],
      topSimilarity: 0.91,
    };
  }
  if (has("면접", "모의면접", "인터뷰")) {
    return {
      answer:
        "모의면접은 지원 건을 선택한 뒤 면접 모드(기본/직무/인성/압박 등)를 고르면 시작됩니다. " +
        "AI가 질문을 생성하고, 답변을 입력하면 꼬리질문과 평가 리포트를 제공합니다.",
      links: [
        { url: "/interview", label: "모의면접 시작하기" },
        { url: "/support/faq", label: "모의면접 가이드" },
      ],
      matchedFaqIds: [21, 22],
      topSimilarity: 0.88,
    };
  }
  if (has("탈퇴", "회원탈퇴", "계정 삭제")) {
    return {
      answer:
        "회원 탈퇴는 설정 > 계정 설정에서 진행할 수 있습니다. 탈퇴 시 프로필·분석 기록이 모두 삭제되며 복구되지 않습니다. " +
        "남은 크레딧이 있다면 탈퇴 전에 환불 여부를 먼저 확인해 주세요.",
      links: [
        { url: "/settings?tab=account", label: "계정 설정" },
        { url: "/support/faq", label: "탈퇴 안내 FAQ" },
      ],
      matchedFaqIds: [31],
      topSimilarity: 0.86,
    };
  }
  if (has("무료", "공짜", "어디까지")) {
    return {
      answer:
        "무료 플랜에서도 지원 건 등록, 기본 공고 분석, 일부 적합도 분석을 체험할 수 있습니다. " +
        "심화 분석과 모의면접 횟수는 PRO 플랜에서 크레딧으로 이용할 수 있습니다.",
      links: [
        { url: "/pricing", label: "요금제 보기" },
        { url: "/support/faq", label: "플랜 비교 FAQ" },
      ],
      matchedFaqIds: [5, 6],
      topSimilarity: 0.84,
    };
  }
  if (has("시작", "처음", "어떻게")) {
    return {
      answer:
        "처음이시라면 먼저 프로필/이력서를 작성한 뒤, 지원할 채용공고를 등록해 지원 건을 만드세요. " +
        "그다음 공고 분석 → 적합도 분석 → 모의면접 순서로 진행하면 됩니다.",
      links: [
        { url: "/profile", label: "프로필 작성" },
        { url: "/dashboard", label: "대시보드로 시작하기" },
      ],
      matchedFaqIds: [1, 2],
      topSimilarity: 0.9,
    };
  }

  // 매칭 실패: matchedFaqIds 비움 + topSimilarity 0 → not_found 상태.
  return { answer: "", links: [], matchedFaqIds: [], topSimilarity: 0 };
}

export const profileRoutes: MockRoute[] = [
  // 프로필 조회/저장
  { method: "GET", pattern: /^\/profile$/, handler: () => demoProfile },
  { method: "PUT", pattern: /^\/profile$/, handler: ({ body }) => applyProfileUpdate(body) },

  // 프로필 AI 도구
  { method: "POST", pattern: /^\/profile\/ai\/summary$/, handler: () => demoAiSummary },
  { method: "POST", pattern: /^\/profile\/ai\/skills$/, handler: () => demoAiSkills },
  { method: "POST", pattern: /^\/profile\/ai\/completeness$/, handler: () => demoCompleteness },

  // 동의(설정 > AI 데이터/개인정보 탭)
  { method: "GET", pattern: /^\/consents\/me$/, handler: () => consentStatus },
  { method: "POST", pattern: /^\/consents\/me$/, handler: ({ body }) => applyConsents(body) },
  { method: "POST", pattern: /^\/consents\/ai\/revoke$/, handler: () => revokeAiConsent() },

  // 챗봇 질의응답
  { method: "POST", pattern: /^\/chatbot\/ask$/, handler: ({ body }) => answerChatbot(body) },
];
