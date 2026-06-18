// 데모/목: 관리자 프롬프트 운영(Prompt Ops) 도메인.
// 공고/기업 분석(B 프롬프트), 프로필 AI 프롬프트, 적합도 분석 프롬프트, 장기 분석(analytics) 프롬프트의
// 템플릿·버전·품질 기준을 운영자 관점에서 채운다. 모든 응답 타입은 admin/prompts 기능 모듈에서 그대로
// 검증한다(api() 가 envelope 를 풀어 data 만 반환하므로 핸들러는 백엔드 data 페이로드를 그대로 돌려준다).
import type { MockRoute, MockContext } from "../../registry";
import { iso } from "../../registry";
import type {
  AdminPromptView,
  AdminJobAnalysisPromptView,
  AdminCompanyAnalysisPromptView,
  AdminBPromptView,
} from "@/admin/features/prompts/types";
import type { FitAnalysisPromptTemplate } from "@/admin/features/prompts/fit-analysis/types/fitAnalysisPrompt";
import type { AnalyticsPromptTemplate } from "@/admin/features/prompts/analytics/types/analyticsPrompt";

// ── B 프롬프트: 공고 분석 ──
const jobAnalysisPrompt: AdminJobAnalysisPromptView = {
  feature: "job-analysis",
  name: "공고 분석 프롬프트",
  version: "b-v3.2",
  purpose:
    "채용공고 원문에서 직무 요건·필수/우대 역량·키워드를 구조화 추출해 지원 건의 분석 기준을 만든다.",
  systemPrompt: [
    "당신은 채용공고를 분석하는 CareerTuner 의 직무 분석 엔진입니다.",
    "입력으로 받은 공고 원문에서 다음을 추출하세요:",
    "1) 고용형태(정규직/계약직/인턴), 경력 수준(신입/주니어/시니어)",
    "2) 필수 역량(requiredSkills)과 우대 역량(preferredSkills) — 공고에 명시된 표현 우선",
    "3) 핵심 키워드와 직무 책임(responsibilities)",
    "4) 추론한 항목은 반드시 inferred=true 로 표시하고 근거 문장을 함께 남기세요.",
    "공고에 없는 정보를 단정하지 말고, 모호하면 보수적으로 표기합니다.",
    "출력은 지정된 JSON 스키마만 반환하고 그 외 설명은 붙이지 않습니다.",
  ].join("\n"),
  schemaSummary: [
    "{",
    '  "employmentType": "FULL_TIME | CONTRACT | INTERN",',
    '  "careerLevel": "NEW | JUNIOR | SENIOR",',
    '  "requiredSkills": string[],',
    '  "preferredSkills": string[],',
    '  "responsibilities": string[],',
    '  "keywords": string[],',
    '  "inferences": { "field": string, "value": string, "evidence": string }[]',
    "}",
  ].join("\n"),
};

// ── B 프롬프트: 기업 분석 ──
const companyAnalysisPrompt: AdminCompanyAnalysisPromptView = {
  feature: "company-analysis",
  name: "기업 분석 프롬프트",
  version: "b-v2.8",
  purpose:
    "지원 기업의 사업·인재상·최근 이슈를 면접 준비 관점에서 요약하고, 검증된 사실과 AI 추론을 분리한다.",
  systemPrompt: [
    "당신은 CareerTuner 의 기업 리서치 엔진입니다.",
    "지원자가 면접에서 활용할 수 있도록 기업 정보를 요약하세요:",
    "1) 한 줄 요약(companySummary)과 산업군(industry)",
    "2) 검증 가능한 사실(verifiedFacts) — 공식 출처가 있는 항목만",
    "3) AI 추론(aiInferences) — 추론임을 명확히 구분하고 단정하지 마세요",
    "4) 인재상·조직문화 키워드, 최근 이슈(있을 때만)",
    "근거가 약한 내용은 verifiedFacts 가 아니라 aiInferences 로 분류합니다.",
    "출력은 지정된 JSON 스키마만 반환합니다.",
  ].join("\n"),
  schemaSummary: [
    "{",
    '  "companySummary": string,',
    '  "industry": string,',
    '  "verifiedFacts": string[],',
    '  "aiInferences": string[],',
    '  "cultureKeywords": string[],',
    '  "recentIssues": string[]',
    "}",
  ].join("\n"),
};

const bPromptViews: AdminBPromptView[] = [jobAnalysisPrompt, companyAnalysisPrompt];

// ── 프로필 AI 프롬프트: 평가 기준 + 직무군별 가중치 + 스키마 ──
const profilePrompt: AdminPromptView = {
  feature: "profile",
  name: "프로필 AI 프롬프트",
  version: "p-v1.6",
  purpose:
    "이력서·프로필 입력에서 핵심 역량을 추출하고 완성도를 진단해, 직무군별 가중치로 강점/보완점을 산출한다.",
  systemPrompt: [
    "당신은 CareerTuner 의 프로필 분석 엔진입니다.",
    "사용자의 프로필(경력·프로젝트·스킬·자격)을 입력으로 받아:",
    "1) 직무 역량 키워드를 추출하고 근거를 남깁니다.",
    "2) 평가 기준(전문성·경험·임팩트·커뮤니케이션·성장성)별 점수를 0~100 으로 산출합니다.",
    "3) 직무군별 가중치를 적용해 종합 완성도(completeness)를 계산합니다.",
    "4) 강점 3개·보완점 3개와 다음 액션을 제안합니다.",
    "비어 있는 항목은 0 점이 아니라 'missing' 으로 표기해 입력 유도 카피를 생성합니다.",
    "출력은 지정된 JSON 스키마만 반환합니다.",
  ].join("\n"),
  schemaSummary: [
    "{",
    '  "completeness": number,',
    '  "scores": { "expertise": number, "experience": number, "impact": number, "communication": number, "growth": number },',
    '  "strengths": string[],',
    '  "improvements": string[],',
    '  "extractedSkills": string[],',
    '  "missingFields": string[]',
    "}",
  ].join("\n"),
  evaluationCriteria: [
    { criterion: "expertise", label: "전문성", description: "직무 핵심 기술·도구의 깊이와 최신성" },
    { criterion: "experience", label: "경험", description: "관련 직무 경력·프로젝트 규모와 지속성" },
    { criterion: "impact", label: "임팩트", description: "성과의 정량 지표와 기여도 명확성" },
    { criterion: "communication", label: "커뮤니케이션", description: "협업·문서화·전달력 근거" },
    { criterion: "growth", label: "성장성", description: "학습 이력·신규 역량 확장 추세" },
  ],
  weightProfiles: [
    {
      jobFamily: "frontend",
      label: "프론트엔드",
      description: "UI/UX 구현과 협업 비중이 큰 직무군",
      weights: { expertise: 30, experience: 25, impact: 20, communication: 15, growth: 10 },
    },
    {
      jobFamily: "backend",
      label: "백엔드",
      description: "시스템 설계·안정성 중심 직무군",
      weights: { expertise: 35, experience: 25, impact: 20, communication: 10, growth: 10 },
    },
    {
      jobFamily: "data",
      label: "데이터",
      description: "분석·모델링과 성과 임팩트 비중이 큰 직무군",
      weights: { expertise: 30, experience: 20, impact: 25, communication: 10, growth: 15 },
    },
  ],
};

// ── 적합도 분석 프롬프트 템플릿(공고-스펙 비교 / 부족 역량 / 지원 전략) ──
const fitAnalysisPrompts: FitAnalysisPromptTemplate[] = [
  {
    key: "fit-gap-scoring",
    name: "공고-스펙 적합도 채점",
    version: "f-v2.1",
    status: "운영중",
    purpose: "공고 요건과 지원자 스펙을 항목별로 매칭해 적합도 점수와 충족/부족 역량을 산출한다.",
    inputFields: ["공고 분석 결과(requiredSkills)", "지원자 프로필 스킬", "경력 요약", "지원 직무"],
    outputFields: ["fitScore(0~100)", "matchedSkills[]", "missingSkills[]", "항목별 근거"],
    qualityChecklist: [
      "공고에 없는 역량으로 감점하지 않는다",
      "동의어/표기 차이(React.js≈React)를 매칭으로 인정한다",
      "점수와 매칭 근거가 일관되는지 확인한다",
    ],
    riskNotes: ["과도한 추론으로 충족 역량을 부풀리지 말 것", "민감 정보(나이·성별)는 채점에 반영 금지"],
    lastReviewedAt: iso(6),
  },
  {
    key: "fit-skill-recommend",
    name: "부족 역량 추천",
    version: "f-v1.9",
    status: "운영중",
    purpose: "부족 역량을 우선순위화하고 학습 과제와 예상 소요를 추천한다.",
    inputFields: ["missingSkills[]", "목표 직무", "지원 마감까지 남은 기간"],
    outputFields: ["우선순위 역량[]", "학습 과제[]", "예상 소요 기간", "추천 근거"],
    qualityChecklist: [
      "마감 기간 대비 현실적인 과제만 추천한다",
      "이미 보유한 역량을 중복 추천하지 않는다",
      "우선순위 기준(임팩트/난이도)을 명시한다",
    ],
    riskNotes: ["특정 유료 강의·브랜드를 단정적으로 추천하지 말 것"],
    lastReviewedAt: iso(11),
  },
  {
    key: "fit-strategy",
    name: "지원 전략 생성",
    version: "f-v1.4",
    status: "검토중",
    purpose: "적합도와 강·약점을 바탕으로 자소서 강조점과 면접 대비 포인트를 제안한다.",
    inputFields: ["fitScore", "matchedSkills[]", "missingSkills[]", "기업 분석 요약"],
    outputFields: ["강조 포인트[]", "보완 서술 가이드[]", "예상 약점 방어 멘트[]"],
    qualityChecklist: [
      "거짓 경력·과장을 유도하는 문구를 생성하지 않는다",
      "기업 인재상과 연결된 근거를 제시한다",
    ],
    riskNotes: ["사실과 다른 자기소개를 권유하지 말 것", "검토중 버전이므로 운영 반영 전 QA 필요"],
    lastReviewedAt: iso(2),
  },
];

// ── 장기 분석(analytics) 프롬프트 템플릿(취업 경향 / 대시보드 액션 / 직무 준비도) ──
const analyticsPrompts: AnalyticsPromptTemplate[] = [
  {
    key: "career-trend",
    name: "장기 취업 경향 분석",
    version: "a-v2.3",
    status: "운영중",
    purpose: "누적 지원·분석·면접 데이터를 종합해 사용자의 취업 준비 경향과 변화 추세를 요약한다.",
    inputFields: ["지원 건 이력", "적합도 점수 추이", "면접 리포트 요약", "학습 완료율"],
    outputFields: ["핵심 경향 요약", "강점 추세[]", "정체 구간[]", "다음 분기 제언"],
    qualityChecklist: [
      "데이터가 부족한 구간을 추세로 단정하지 않는다",
      "점수 변화의 원인을 데이터 근거와 함께 설명한다",
    ],
    riskNotes: ["표본이 적을 때 과도한 일반화 금지"],
    lastReviewedAt: iso(9),
  },
  {
    key: "dashboard-next-action",
    name: "대시보드 다음 액션",
    version: "a-v1.7",
    status: "운영중",
    purpose: "현재 진행 중인 지원 건과 부족 역량을 기준으로 오늘/이번 주 권장 액션을 생성한다.",
    inputFields: ["진행 중 지원 건", "미완료 학습 과제", "임박한 마감", "최근 면접 점수"],
    outputFields: ["오늘의 액션[]", "이번 주 액션[]", "우선순위 사유"],
    qualityChecklist: [
      "마감 임박 항목을 우선 노출한다",
      "이미 완료한 액션을 다시 권하지 않는다",
      "하루 처리 가능한 분량으로 제한한다",
    ],
    riskNotes: ["사용자에게 부담을 주는 과도한 일일 과제 수 지양"],
    lastReviewedAt: iso(4),
  },
  {
    key: "job-readiness",
    name: "직무별 준비도",
    version: "a-v1.2",
    status: "검토중",
    purpose: "직무군별로 보유 역량과 시장 요구를 비교해 준비도 레벨과 보완 로드맵을 제시한다.",
    inputFields: ["직무군", "보유 역량", "최근 공고 요구 역량 빈도", "학습 이력"],
    outputFields: ["준비도 레벨", "역량 갭[]", "단계별 로드맵"],
    qualityChecklist: [
      "최신 공고 데이터 기준으로 요구 역량을 산정한다",
      "로드맵 단계 간 의존성을 고려한다",
    ],
    riskNotes: ["시장 데이터 갱신 주기를 명시하지 않으면 오해 소지", "검토중 버전 — 운영 반영 전 QA 필요"],
    lastReviewedAt: iso(1),
  },
];

export const adminPromptsRoutes: MockRoute[] = [
  // ── B 프롬프트(공고/기업) — 페이지가 각각 호출하는 키 엔드포인트 ──
  { method: "GET", pattern: /^\/admin\/prompts\/job-analysis$/, handler: () => ({ ...jobAnalysisPrompt }) },
  { method: "GET", pattern: /^\/admin\/prompts\/company-analysis$/, handler: () => ({ ...companyAnalysisPrompt }) },

  // ── B 프롬프트 묶음 조회(보조). 두 뷰를 배열로 반환해 목록 정규화기와도 호환. ──
  { method: "GET", pattern: /^\/admin\/prompts$/, handler: () => [...bPromptViews] },

  // ── 프로필 AI 프롬프트 ──
  { method: "GET", pattern: /^\/admin\/prompts\/profile$/, handler: () => ({ ...profilePrompt }) },

  // ── 적합도 분석 프롬프트: 목록 + 키 단건 ──
  { method: "GET", pattern: /^\/admin\/prompts\/fit-analysis$/, handler: () => [...fitAnalysisPrompts] },
  {
    method: "GET",
    pattern: /^\/admin\/prompts\/fit-analysis\/([^/]+)$/,
    handler: ({ params }: MockContext) =>
      fitAnalysisPrompts.find((item) => item.key === params[0]) ?? fitAnalysisPrompts[0],
  },

  // ── 장기 분석(analytics) 프롬프트: 목록 + 키 단건 ──
  { method: "GET", pattern: /^\/admin\/prompts\/analytics$/, handler: () => [...analyticsPrompts] },
  {
    method: "GET",
    pattern: /^\/admin\/prompts\/analytics\/([^/]+)$/,
    handler: ({ params }: MockContext) =>
      analyticsPrompts.find((item) => item.key === params[0]) ?? analyticsPrompts[0],
  },
];
