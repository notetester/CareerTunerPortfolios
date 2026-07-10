// 데모/목: B 도메인 지원 건 상세의 서브리소스(공고문/공고분석/기업분석).
// 지원 건 목록·상세 자체는 data.ts(demoApplicationCases)에서 이미 제공하므로 여기선 서브탭만 채운다.
// applicationCaseId(101·102)는 data.ts·fit 데모와 동일하게 맞춘다.
import type { JobPosting } from "@/features/applications/types/jobPosting";
import type { JobAnalysis, CompanyAnalysis } from "@/features/applications/types/analysis";
import { NULL_ANALYSIS_PROVENANCE } from "@/features/applications/types/analysis";

const now = Date.now();
const iso = (daysAgo: number) => new Date(now - daysAgo * 86_400_000).toISOString();

const jobPostings: Record<number, JobPosting> = {
  101: { id: 1101, applicationCaseId: 101, revision: 1, sourceType: "TEXT", uploadedFileUrl: null, createdAt: iso(5),
    originalText: "카카오 프론트엔드 개발자 채용\n- 필수: React, JavaScript, REST API 연동 경험\n- 우대: TypeScript, 테스트 코드 작성\n- 담당: 카카오 서비스 웹 프론트엔드 개발 및 유지보수",
    extractedText: "필수: React, JavaScript, REST API / 우대: TypeScript, 테스트 / 담당: 웹 프론트엔드 개발·유지보수" },
  102: { id: 1102, applicationCaseId: 102, revision: 1, sourceType: "TEXT", uploadedFileUrl: null, createdAt: iso(8),
    originalText: "네이버 프론트엔드 개발자\n- 필수: React, TypeScript, REST API\n- 우대: AWS, 성능 최적화 경험\n- 담당: 대규모 트래픽 서비스 프론트엔드 개발",
    extractedText: "필수: React, TypeScript, REST API / 우대: AWS, 성능 최적화 / 담당: 대규모 트래픽 프론트엔드" },
};

export function findJobPosting(applicationCaseId: number): JobPosting | null {
  return jobPostings[applicationCaseId] ?? null;
}

const jobAnalyses: Record<number, JobAnalysis> = {
  101: { id: 3101, applicationCaseId: 101, jobPostingId: 1101, jobPostingRevision: 1, employmentType: "정규직", experienceLevel: "신입~3년", difficulty: "NORMAL",
    requiredSkills: JSON.stringify(["React", "JavaScript", "REST API"]),
    preferredSkills: JSON.stringify(["TypeScript", "테스트 코드"]),
    duties: JSON.stringify(["카카오 서비스 웹 프론트엔드 개발", "기존 화면 유지보수"]),
    qualifications: JSON.stringify(["관련 전공 또는 동등 경험"]),
    summary: "React 기반 웹 프론트엔드 개발 직무. REST API 연동 경험이 핵심이며 TypeScript·테스트는 가산 요소.",
    evidence: JSON.stringify([{ field: "필수 React", quote: "필수: React, JavaScript, REST API 연동 경험" }]),
    ambiguousConditions: JSON.stringify([]), confirmedAt: iso(2), adminMemo: null, ...NULL_ANALYSIS_PROVENANCE, createdAt: iso(3) },
  102: { id: 3102, applicationCaseId: 102, jobPostingId: 1102, jobPostingRevision: 1, employmentType: "정규직", experienceLevel: "2~5년", difficulty: "HARD",
    requiredSkills: JSON.stringify(["React", "TypeScript", "REST API"]),
    preferredSkills: JSON.stringify(["AWS", "성능 최적화"]),
    duties: JSON.stringify(["대규모 트래픽 서비스 프론트엔드 개발"]),
    qualifications: JSON.stringify(["대용량 서비스 경험 우대"]),
    summary: "대규모 트래픽 환경의 프론트엔드 개발. TypeScript 실무와 성능 최적화 경험이 중요.",
    evidence: JSON.stringify([{ field: "필수 TypeScript", quote: "필수: React, TypeScript, REST API" }]),
    ambiguousConditions: JSON.stringify([]), confirmedAt: iso(2), adminMemo: null, ...NULL_ANALYSIS_PROVENANCE, createdAt: iso(2) },
};

export function findJobAnalysis(applicationCaseId: number): JobAnalysis | null {
  return jobAnalyses[applicationCaseId] ?? null;
}

const companyAnalyses: Record<number, CompanyAnalysis> = {
  101: { id: 4101, applicationCaseId: 101, jobPostingId: 1101, jobPostingRevision: 1,
    companySummary: "카카오는 메신저·콘텐츠·결제 등 다양한 서비스를 운영하는 대형 IT 기업입니다.",
    recentIssues: JSON.stringify(["AI 서비스 확장", "광고 사업 강화"]),
    industry: "IT 플랫폼", competitors: JSON.stringify(["네이버", "라인"]),
    interviewPoints: JSON.stringify(["대규모 서비스 협업 경험", "React 상태 관리 설계"]),
    sources: JSON.stringify(["채용 공고", "기업 홈페이지"]),
    verifiedFacts: JSON.stringify([{ fact: "메신저 서비스 운영", source: "기업 홈페이지" }]),
    aiInferences: JSON.stringify([{ inference: "프론트엔드 인력 수요 증가", basis: "AI 서비스 확장" }]),
    unknowns: JSON.stringify([{ topic: "매출 규모", reason: "공고문에 관련 정보가 없다", neededSource: "IR 자료" }]),
    sourceType: "TEXT", checkedAt: iso(3), refreshRecommendedAt: iso(-27), confirmedAt: iso(2), adminMemo: null, ...NULL_ANALYSIS_PROVENANCE, createdAt: iso(3) },
  102: { id: 4102, applicationCaseId: 102, jobPostingId: 1102, jobPostingRevision: 1,
    companySummary: "네이버는 검색·커머스·클라우드를 운영하는 국내 최대 IT 기업입니다.",
    recentIssues: JSON.stringify(["하이퍼클로바X 확장", "글로벌 커머스 진출"]),
    industry: "IT 플랫폼", competitors: JSON.stringify(["카카오", "쿠팡"]),
    interviewPoints: JSON.stringify(["대용량 트래픽 성능 최적화", "TypeScript 실무 경험"]),
    sources: JSON.stringify(["채용 공고", "기업 뉴스룸"]),
    verifiedFacts: JSON.stringify([{ fact: "검색 서비스 운영", source: "기업 뉴스룸" }]),
    aiInferences: JSON.stringify([{ inference: "성능 중심 프론트엔드 역량 중시", basis: "대용량 트래픽 서비스" }]),
    unknowns: null,
    sourceType: "TEXT", checkedAt: iso(2), refreshRecommendedAt: iso(-28), confirmedAt: iso(1), adminMemo: null, ...NULL_ANALYSIS_PROVENANCE, createdAt: iso(2) },
};

export function findCompanyAnalysis(applicationCaseId: number): CompanyAnalysis | null {
  return companyAnalyses[applicationCaseId] ?? null;
}
