import {
  parseVerifiedFactViews,
  type VerifiedFactView,
} from "@/features/applications/types/analysis";
import type {
  AdminCompanyAnalysisQueryParams,
  AdminCompanyAnalysisRow,
  AdminCompanyAnalysisSummaryResponse,
} from "./types";

const companyAnalysisQueryParams: AdminCompanyAnalysisQueryParams = {
  keyword: "Example",
  sourceType: "WEB",
  industry: "SaaS",
  confirmed: true,
  hasMemo: false,
  checked: true,
  refreshDue: false,
  applicationCaseId: 1,
  userId: 10,
  createdFrom: "2026-06-01",
  createdTo: "2026-06-30",
  sort: "createdAt_desc",
  limit: 30,
  offset: 0,
};

const companyAnalysisRow: AdminCompanyAnalysisRow = {
  id: 12,
  applicationCaseId: 1,
  jobPostingId: 21,
  jobPostingRevision: 2,
  latestJobPostingRevision: 3,
  staleAgainstLatestPosting: true,
  userId: 10,
  userEmail: "user@example.com",
  companyName: "Example Co",
  jobTitle: "Frontend Engineer",
  companySummary: "Company summary",
  recentIssues: null,
  industry: "SaaS",
  competitors: null,
  interviewPoints: null,
  sources: null,
  verifiedFacts: "Verified facts",
  aiInferences: "AI inferences",
  unknowns: "[{\"topic\":\"매출 규모\",\"reason\":\"공고문에 관련 정보가 없다\"}]",
  sourceType: "WEB",
  checkedAt: "2026-06-02T00:00:00Z",
  refreshRecommendedAt: "2026-06-09T00:00:00Z",
  confirmedAt: null,
  adminMemo: null,
  createdAt: "2026-06-02T00:00:00Z",
};

const companyAnalysisSummary: AdminCompanyAnalysisSummaryResponse = {
  totalCount: 8,
  confirmedCount: 3,
  unconfirmedCount: 5,
  refreshDueCount: 4,
  missingSourceCount: 1,
  checkedCount: 6,
  memoCount: 2,
};

// D-4d: verifiedFacts read-only 파서가 WEB fact 의 sourceKind/sourceRef(URL)를 타입 있는 형태로
// 보존하는지 typecheck 로 고정한다. (record 의 sourceType:"WEB" 은 검색 파라미터/레코드 타입 확인일 뿐
// D-4d 검증 근거가 아니다 — 클릭 가능한 URL 은 verifiedFacts[].sourceRef 에서만 나온다.)
const webVerifiedFactsJson =
  '[{"fact":"클라우드 매니지드 서비스를 출시했다","source":"웹검색","evidence":"...",' +
  '"sourceKind":"WEB","sourceRef":"https://news.example.com/1"},' +
  '{"fact":"React 경험 필수","source":"채용공고","sourceKind":"JOB_POSTING","sourceRef":"jobPosting:12#rev2"}]';

const parsedVerifiedFacts: VerifiedFactView[] = parseVerifiedFactViews(webVerifiedFactsJson);
const webFact: VerifiedFactView | undefined = parsedVerifiedFacts.find((fact) => fact.sourceKind === "WEB");
const webFactSourceKind: string | null = webFact ? webFact.sourceKind : null;
const webFactSourceRef: string | null = webFact ? webFact.sourceRef : null;

void companyAnalysisQueryParams;
void companyAnalysisRow;
void companyAnalysisSummary;
void parsedVerifiedFacts;
void webFactSourceKind;
void webFactSourceRef;
