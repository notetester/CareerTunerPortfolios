// 데모/목: 기업 계정 파이프라인 + 채용공고 게시판 (/company/**, /job-board/**, /admin/company/**).
// 백엔드 의미론을 미러한다:
//   - 기업 신청: USER 만, PENDING 1건 제한 → 관리자 승인 시 COMPANY 전환(여기서는 profile 생성으로 표현)
//   - 공고: 데모 기업은 BASIC 등급 → 등록/수정 모두 검토 필요(제출 → PENDING_REVIEW, 게시 중 수정 → revision)
//   - 관리자 승인 → PUBLISHED → 공개 게시판에 노출. 데모 사용자(9001)가 전 과정을 한 세션에서 체험 가능.
import type {
  CompanyApplication,
  CompanyJobPosting,
  CompanyProfile,
  JobPostingFields,
  JobPostingUpsertPayload,
} from "@/features/company/types/company";
import type { MockRoute } from "../registry";
import { iso } from "../registry";

/** 데모 로그인 사용자(data.ts demoUser)와 동일 id. */
const DEMO_USER_ID = 9001;

/* ── 기업 신청/프로필 상태 ── */

let demoProfile: CompanyProfile | null = null;
let applicationSeq = 4103;

const applications: CompanyApplication[] = [
  {
    id: 4101,
    userId: 9310,
    companyName: "클라우드나인 주식회사",
    businessNumber: "312-81-04521",
    contact: "박채용 / 02-1544-0001",
    description: "SaaS 협업툴을 만드는 60명 규모 스타트업입니다. 백엔드·프론트 상시 채용 예정입니다.",
    status: "PENDING",
    rejectReason: null,
    reviewedAt: null,
    createdAt: iso(1),
    applicantEmail: "hr@cloudnine.example",
    applicantName: "박채용",
  },
  {
    id: 4102,
    userId: 9311,
    companyName: "데이터포지",
    businessNumber: null,
    contact: "이인사 / 010-9000-1234",
    description: "데이터 파이프라인 컨설팅 기업입니다.",
    status: "PENDING",
    rejectReason: null,
    reviewedAt: null,
    createdAt: iso(0),
    applicantEmail: "recruit@dataforge.example",
    applicantName: "이인사",
  },
  {
    id: 4100,
    userId: 9309,
    companyName: "스텔라소프트",
    businessNumber: "220-88-11223",
    contact: "김운영 / 070-4000-2200",
    description: null,
    status: "APPROVED",
    rejectReason: null,
    reviewedAt: iso(6),
    createdAt: iso(7),
    applicantEmail: "people@stellasoft.example",
    applicantName: "김운영",
  },
];

/* ── 공고 상태 ── */

const emptyFields: JobPostingFields = {
  title: "",
  jobRole: "",
  employmentType: "FULL_TIME",
  careerLevel: "ANY",
  careerYearsMin: null,
  careerYearsMax: null,
  educationLevel: "ANY",
  salaryText: null,
  salaryNegotiable: false,
  workLocation: null,
  workHours: null,
  deadlineDate: null,
  alwaysOpen: false,
  mainTasks: null,
  requirements: null,
  preferred: null,
  benefits: null,
  hiringProcess: null,
  headcount: null,
  tags: [],
};

let postingSeq = 8108;

function posting(
  partial: Partial<CompanyJobPosting> & Pick<CompanyJobPosting, "id" | "title" | "jobRole">,
): CompanyJobPosting {
  return {
    ...emptyFields,
    companyUserId: 9101,
    companyName: null,
    trustGrade: "BASIC",
    status: "PUBLISHED",
    rejectReason: null,
    viewCount: 0,
    hasPendingRevision: false,
    publishedAt: iso(3),
    closedAt: null,
    createdAt: iso(4),
    updatedAt: iso(3),
    ...partial,
  };
}

/** 게시판 seed + 데모 사용자가 만드는 공고가 전부 이 배열에 쌓인다. */
const postings: CompanyJobPosting[] = [
  posting({
    id: 8101, companyUserId: 9101, companyName: "스텔라소프트", trustGrade: "VERIFIED",
    title: "백엔드 개발자 (Java/Spring) 경력 채용", jobRole: "백엔드 개발자",
    employmentType: "FULL_TIME", careerLevel: "EXPERIENCED", careerYearsMin: 3, careerYearsMax: 8,
    educationLevel: "BACHELOR", salaryText: "5,000~7,500만원", salaryNegotiable: true,
    workLocation: "서울 강남구", workHours: "주 5일 10:00~19:00 (유연근무)",
    deadlineDate: iso(-14).slice(0, 10), alwaysOpen: false,
    mainTasks: "- 커머스 주문/정산 도메인 백엔드 설계·개발\n- 대용량 트래픽 처리 및 성능 개선\n- 신규 서비스 API 설계",
    requirements: "- Java/Spring 기반 서버 개발 3년 이상\n- RDBMS 설계·튜닝 경험\n- 협업 툴 기반 개발 프로세스 경험",
    preferred: "- MSA 전환 경험\n- Kafka 등 메시징 시스템 운영 경험\n- 대규모 결제 시스템 경험",
    benefits: "- 점심/저녁 식대 지원\n- 최신 장비 지원\n- 컨퍼런스 참가비 지원",
    hiringProcess: "서류 → 과제 → 기술면접 → 컬처핏 → 처우협의",
    headcount: "2명", tags: ["Java", "Spring", "MySQL", "MSA"],
    viewCount: 412, publishedAt: iso(2), createdAt: iso(3), updatedAt: iso(2),
  }),
  posting({
    id: 8102, companyUserId: 9102, companyName: "클라우드나인 주식회사", trustGrade: "VERIFIED",
    title: "프론트엔드 개발자 (React)", jobRole: "프론트엔드 개발자",
    employmentType: "FULL_TIME", careerLevel: "EXPERIENCED", careerYearsMin: 2, careerYearsMax: null,
    educationLevel: "ANY", salaryText: "4,200~6,000만원", salaryNegotiable: false,
    workLocation: "서울 성수동", workHours: "주 5일 자율출퇴근",
    deadlineDate: iso(-10).slice(0, 10),
    mainTasks: "- 협업툴 웹 클라이언트 개발\n- 디자인 시스템 구축·운영\n- 성능 최적화",
    requirements: "- React/TypeScript 실무 2년 이상\n- 상태관리 라이브러리 사용 경험",
    preferred: "- 실시간 협업 기능(웹소켓) 경험\n- 테스트 자동화 경험",
    benefits: "- 스톡옵션\n- 리모트 근무 주 2일",
    hiringProcess: "서류 → 라이브코딩 → 기술면접 → 최종면접",
    headcount: "1명", tags: ["React", "TypeScript", "디자인시스템"],
    viewCount: 356, publishedAt: iso(4), createdAt: iso(6), updatedAt: iso(1),
    hasPendingRevision: true,
  }),
  posting({
    id: 8103, companyUserId: 9103, companyName: "데이터포지", trustGrade: "BASIC",
    title: "데이터 엔지니어 신입/주니어", jobRole: "데이터 엔지니어",
    employmentType: "FULL_TIME", careerLevel: "NEW",
    educationLevel: "BACHELOR", salaryText: "3,800만원~", salaryNegotiable: true,
    workLocation: "경기 판교", workHours: "주 5일 09:30~18:30",
    alwaysOpen: true, deadlineDate: null,
    mainTasks: "- 데이터 파이프라인 구축·운영\n- 고객사 데이터 웨어하우스 모델링",
    requirements: "- Python/SQL 활용 능력\n- 데이터 처리 프로젝트 경험(학부/부트캠프 포함)",
    preferred: "- Airflow, Spark 사용 경험\n- 클라우드(AWS/GCP) 자격증",
    benefits: "- 자격증 취득 지원\n- 도서 구입비 무제한",
    hiringProcess: "서류 → 코딩테스트 → 기술면접 → 임원면접",
    headcount: "3명", tags: ["Python", "SQL", "Airflow", "AWS"],
    viewCount: 289, publishedAt: iso(5), createdAt: iso(6), updatedAt: iso(5),
  }),
  posting({
    id: 8104, companyUserId: 9101, companyName: "스텔라소프트", trustGrade: "VERIFIED",
    title: "QA 엔지니어 (계약직 6개월)", jobRole: "QA 엔지니어",
    employmentType: "CONTRACT", careerLevel: "ANY",
    educationLevel: "ANY", salaryText: "월 350~420만원", salaryNegotiable: false,
    workLocation: "서울 강남구", workHours: "주 5일 09:00~18:00",
    deadlineDate: iso(-4).slice(0, 10),
    mainTasks: "- 신규 기능 테스트 케이스 설계·수행\n- 자동화 테스트 스크립트 유지보수",
    requirements: "- 웹 서비스 QA 경험 또는 관련 교육 이수",
    preferred: "- Playwright/Cypress 경험",
    benefits: "- 정규직 전환 검토\n- 식대 지원",
    hiringProcess: "서류 → 실무면접",
    headcount: "1명", tags: ["QA", "Playwright", "자동화"],
    viewCount: 133, publishedAt: iso(1), createdAt: iso(2), updatedAt: iso(1),
  }),
  posting({
    id: 8105, companyUserId: 9104, companyName: "핀치페이", trustGrade: "PARTNER",
    title: "안드로이드 개발자 (핀테크)", jobRole: "모바일 개발자",
    employmentType: "FULL_TIME", careerLevel: "EXPERIENCED", careerYearsMin: 4, careerYearsMax: 10,
    educationLevel: "ANY", salaryText: "6,500만원~", salaryNegotiable: true,
    workLocation: "서울 여의도", workHours: "주 5일 코어타임 11:00~16:00",
    alwaysOpen: true, deadlineDate: null,
    mainTasks: "- 간편결제 안드로이드 앱 개발\n- 보안 모듈 연동 및 성능 개선",
    requirements: "- Kotlin 기반 안드로이드 개발 4년 이상\n- 금융/보안 서비스 이해",
    preferred: "- Compose 전환 경험\n- 핀테크 도메인 경험",
    benefits: "- 사이닝 보너스\n- 건강검진 가족 지원",
    hiringProcess: "서류 → 기술면접 2회 → 처우협의",
    headcount: "2명", tags: ["Kotlin", "Android", "Compose", "핀테크"],
    viewCount: 501, publishedAt: iso(6), createdAt: iso(8), updatedAt: iso(6),
  }),
  posting({
    id: 8106, companyUserId: 9103, companyName: "데이터포지", trustGrade: "BASIC",
    title: "프로덕트 디자이너 인턴 (전환형)", jobRole: "프로덕트 디자이너",
    employmentType: "INTERN", careerLevel: "NEW",
    educationLevel: "ANY", salaryText: "월 260만원", salaryNegotiable: false,
    workLocation: "경기 판교", workHours: "주 5일 10:00~19:00",
    deadlineDate: iso(-20).slice(0, 10),
    mainTasks: "- 대시보드 UI/UX 설계 보조\n- 사용자 인터뷰·리서치 참여",
    requirements: "- Figma 활용 능력\n- 포트폴리오 제출 필수",
    preferred: "- 데이터 시각화에 관심",
    benefits: "- 전환 시 연봉 협상\n- 멘토링 프로그램",
    hiringProcess: "서류(포트폴리오) → 인터뷰 → 인턴 3개월 → 전환평가",
    headcount: "1명", tags: ["Figma", "UX", "대시보드"],
    viewCount: 97, publishedAt: iso(0), createdAt: iso(1), updatedAt: iso(0),
  }),
  // 신규 등록 검토 대기(관리자 큐 CREATE 데모)
  posting({
    id: 8107, companyUserId: 9105, companyName: "그로우스랩", trustGrade: "BASIC",
    title: "그로스 마케터 (신입 가능)", jobRole: "마케터",
    employmentType: "FULL_TIME", careerLevel: "ANY",
    educationLevel: "ANY", salaryText: "3,400~4,200만원", salaryNegotiable: true,
    workLocation: "서울 마포구", workHours: "주 5일 10:00~19:00",
    deadlineDate: iso(-7).slice(0, 10),
    mainTasks: "- 퍼포먼스 마케팅 캠페인 운영\n- 지표 분석과 A/B 테스트",
    requirements: "- GA/앰플리튜드 등 분석 도구 사용 경험",
    preferred: "- 콘텐츠 제작 경험",
    benefits: "- 성과급\n- 교육비 지원",
    hiringProcess: "서류 → 실무면접 → 대표면접",
    headcount: "1명", tags: ["마케팅", "GA", "그로스"],
    status: "PENDING_REVIEW", publishedAt: null, viewCount: 0,
    createdAt: iso(0), updatedAt: iso(0),
  }),
];

/** 게시 중 공고의 수정 검토 변경본(postingId → 변경 payload). */
const pendingRevisions = new Map<number, JobPostingFields>([
  [
    8102,
    {
      ...emptyFields,
      title: "프론트엔드 개발자 (React/Next.js)",
      jobRole: "프론트엔드 개발자",
      employmentType: "FULL_TIME",
      careerLevel: "EXPERIENCED",
      careerYearsMin: 2,
      careerYearsMax: null,
      educationLevel: "ANY",
      salaryText: "4,800~6,500만원",
      salaryNegotiable: true,
      workLocation: "서울 성수동",
      workHours: "주 5일 자율출퇴근",
      deadlineDate: iso(-10).slice(0, 10),
      alwaysOpen: false,
      mainTasks: "- 협업툴 웹 클라이언트 개발\n- 디자인 시스템 구축·운영\n- 성능 최적화",
      requirements: "- React/TypeScript 실무 2년 이상\n- 상태관리 라이브러리 사용 경험",
      preferred: "- 실시간 협업 기능(웹소켓) 경험\n- 테스트 자동화 경험\n- Next.js 서비스 운영 경험",
      benefits: "- 스톡옵션\n- 리모트 근무 주 3일",
      hiringProcess: "서류 → 라이브코딩 → 기술면접 → 최종면접",
      headcount: "2명",
      tags: ["React", "TypeScript", "Next.js", "디자인시스템"],
    },
  ],
]);

/* ── 헬퍼 ── */

const nowIso = () => new Date().toISOString();

function myApplication(): CompanyApplication | null {
  const mine = applications.filter((a) => a.userId === DEMO_USER_ID);
  return mine.length === 0 ? null : mine.reduce((latest, a) => (a.id > latest.id ? a : latest));
}

function toFields(body: JobPostingUpsertPayload): JobPostingFields {
  return {
    ...emptyFields,
    ...Object.fromEntries(Object.entries(body).filter(([key]) => key in emptyFields)),
    tags: Array.isArray(body.tags) ? body.tags : [],
  } as JobPostingFields;
}

function findPosting(id: number): CompanyJobPosting | undefined {
  return postings.find((p) => p.id === id);
}

/** 공개 게시판 정렬 — 백엔드 searchPublished 의 latest/deadline/views 미러. */
function sortBoard(items: CompanyJobPosting[], sort: string): CompanyJobPosting[] {
  const sorted = [...items];
  if (sort === "views") {
    sorted.sort((a, b) => b.viewCount - a.viewCount || b.id - a.id);
  } else if (sort === "deadline") {
    sorted.sort((a, b) => {
      const aKey = a.alwaysOpen || !a.deadlineDate ? Number.MAX_SAFE_INTEGER : new Date(a.deadlineDate).getTime();
      const bKey = b.alwaysOpen || !b.deadlineDate ? Number.MAX_SAFE_INTEGER : new Date(b.deadlineDate).getTime();
      return aKey - bKey || b.id - a.id;
    });
  } else {
    sorted.sort((a, b) =>
      new Date(b.publishedAt ?? b.createdAt).getTime() - new Date(a.publishedAt ?? a.createdAt).getTime() || b.id - a.id);
  }
  return sorted;
}

/* ── 라우트 ── */

export const companyRoutes: MockRoute[] = [
  // ── 기업 신청/프로필(사용자) ──
  {
    method: "POST",
    pattern: /^\/company\/applications$/,
    handler: ({ body }) => {
      const request = body as { companyName?: string; businessNumber?: string; contact?: string; description?: string };
      const created: CompanyApplication = {
        id: ++applicationSeq,
        userId: DEMO_USER_ID,
        companyName: request?.companyName ?? "",
        businessNumber: request?.businessNumber ?? null,
        contact: request?.contact ?? "",
        description: request?.description ?? null,
        status: "PENDING",
        rejectReason: null,
        reviewedAt: null,
        createdAt: nowIso(),
        applicantEmail: "demo@careertuner.dev",
        applicantName: "김데모",
      };
      applications.unshift(created);
      return created;
    },
  },
  { method: "GET", pattern: /^\/company\/applications\/me$/, handler: () => myApplication() },
  { method: "GET", pattern: /^\/company\/profile\/me$/, handler: () => demoProfile },

  // ── 내 공고 관리(기업) ──
  {
    method: "GET",
    pattern: /^\/company\/job-postings$/,
    handler: () =>
      postings
        .filter((p) => p.companyUserId === DEMO_USER_ID)
        .map((p) => ({ ...p, hasPendingRevision: pendingRevisions.has(p.id) }))
        .sort((a, b) => b.id - a.id),
  },
  {
    method: "GET",
    pattern: /^\/company\/job-postings\/(\d+)$/,
    handler: ({ params }) => findPosting(Number(params[0])) ?? null,
  },
  {
    method: "POST",
    pattern: /^\/company\/job-postings$/,
    handler: ({ body }) => {
      const request = body as JobPostingUpsertPayload;
      // 데모 기업은 BASIC 등급 → 제출 시 검토 대기(백엔드 기본 정책 미러)
      const created: CompanyJobPosting = {
        ...toFields(request),
        id: ++postingSeq,
        companyUserId: DEMO_USER_ID,
        companyName: demoProfile?.companyName ?? "내 기업",
        trustGrade: demoProfile?.trustGrade ?? "BASIC",
        status: request.submit ? "PENDING_REVIEW" : "DRAFT",
        rejectReason: null,
        viewCount: 0,
        hasPendingRevision: false,
        publishedAt: null,
        closedAt: null,
        createdAt: nowIso(),
        updatedAt: nowIso(),
      };
      postings.unshift(created);
      return created;
    },
  },
  {
    method: "PUT",
    pattern: /^\/company\/job-postings\/(\d+)$/,
    handler: ({ params, body }) => {
      const target = findPosting(Number(params[0]));
      if (!target) return null;
      const request = body as JobPostingUpsertPayload;
      if (target.status === "PUBLISHED") {
        // BASIC 등급 수정 검토 — 변경본만 대기열에 올라가고 게시 내용은 유지된다
        pendingRevisions.set(target.id, toFields(request));
        target.hasPendingRevision = true;
      } else {
        Object.assign(target, toFields(request));
        if (request.submit && (target.status === "DRAFT" || target.status === "REJECTED")) {
          target.status = "PENDING_REVIEW";
          target.rejectReason = null;
        }
      }
      target.updatedAt = nowIso();
      return { ...target, hasPendingRevision: pendingRevisions.has(target.id) };
    },
  },
  {
    method: "POST",
    pattern: /^\/company\/job-postings\/(\d+)\/close$/,
    handler: ({ params }) => {
      const target = findPosting(Number(params[0]));
      if (!target) return null;
      target.status = "CLOSED";
      target.closedAt = nowIso();
      return target;
    },
  },

  // ── 공개 게시판 ──
  {
    method: "GET",
    pattern: /^\/job-board$/,
    handler: ({ query }) => {
      const keyword = (query.get("keyword") ?? "").toLowerCase();
      const jobRole = (query.get("jobRole") ?? "").toLowerCase();
      const location = (query.get("location") ?? "").toLowerCase();
      const employmentType = query.get("employmentType") ?? "";
      const careerLevel = query.get("careerLevel") ?? "";
      const sort = query.get("sort") ?? "latest";
      const page = Number(query.get("page") ?? 0) || 0;
      const size = Number(query.get("size") ?? 20) || 20;

      const filtered = postings.filter((p) => {
        if (p.status !== "PUBLISHED") return false;
        if (keyword && ![p.title, p.jobRole, p.companyName ?? ""].some((v) => v.toLowerCase().includes(keyword))) return false;
        if (jobRole && !p.jobRole.toLowerCase().includes(jobRole)) return false;
        if (location && !(p.workLocation ?? "").toLowerCase().includes(location)) return false;
        if (employmentType && p.employmentType !== employmentType) return false;
        if (careerLevel && p.careerLevel !== careerLevel) return false;
        return true;
      });
      const sorted = sortBoard(filtered, sort);
      return { items: sorted.slice(page * size, page * size + size), total: sorted.length, page, size };
    },
  },
  {
    method: "GET",
    pattern: /^\/job-board\/(\d+)$/,
    handler: ({ params }) => {
      const target = findPosting(Number(params[0]));
      if (!target || target.status !== "PUBLISHED") return null;
      target.viewCount += 1;
      return target;
    },
  },
  {
    // "이 공고로 분석하기" — 데모에서는 기존 지원 건(카카오 101)으로 연결한다
    method: "POST",
    pattern: /^\/job-board\/(\d+)\/analyze$/,
    handler: () => ({ applicationCaseId: 101 }),
  },

  // ── 관리자: 기업 신청 ──
  {
    method: "GET",
    pattern: /^\/admin\/company\/applications$/,
    handler: ({ query }) => {
      const status = query.get("status");
      return applications
        .filter((a) => !status || a.status === status)
        .slice()
        .sort((a, b) => b.id - a.id);
    },
  },
  {
    method: "POST",
    pattern: /^\/admin\/company\/applications\/(\d+)\/approve$/,
    handler: ({ params }) => {
      const target = applications.find((a) => a.id === Number(params[0]));
      if (!target) return null;
      target.status = "APPROVED";
      target.reviewedAt = nowIso();
      if (target.userId === DEMO_USER_ID) {
        demoProfile = {
          userId: DEMO_USER_ID,
          companyName: target.companyName,
          businessNumber: target.businessNumber,
          trustGrade: "BASIC",
        };
      }
      return target;
    },
  },
  {
    method: "POST",
    pattern: /^\/admin\/company\/applications\/(\d+)\/reject$/,
    handler: ({ params, body }) => {
      const target = applications.find((a) => a.id === Number(params[0]));
      if (!target) return null;
      target.status = "REJECTED";
      target.rejectReason = (body as { reason?: string })?.reason ?? "";
      target.reviewedAt = nowIso();
      return target;
    },
  },

  // ── 관리자: 공고 검토 큐 ──
  {
    method: "GET",
    pattern: /^\/admin\/company\/job-postings$/,
    handler: () => {
      const creates = postings
        .filter((p) => p.status === "PENDING_REVIEW")
        .map((p) => ({
          reviewType: "CREATE",
          postingId: p.id,
          revisionId: null,
          title: p.title,
          jobRole: p.jobRole,
          companyName: p.companyName,
          trustGrade: p.trustGrade,
          submittedAt: p.updatedAt,
        }));
      const updates = [...pendingRevisions.keys()]
        .map((postingId) => findPosting(postingId))
        .filter((p): p is CompanyJobPosting => !!p)
        .map((p) => ({
          reviewType: "UPDATE",
          postingId: p.id,
          revisionId: p.id * 10 + 1,
          title: p.title,
          jobRole: p.jobRole,
          companyName: p.companyName,
          trustGrade: p.trustGrade,
          submittedAt: p.updatedAt,
        }));
      return [...creates, ...updates].sort(
        (a, b) => new Date(a.submittedAt).getTime() - new Date(b.submittedAt).getTime(),
      );
    },
  },
  {
    method: "GET",
    pattern: /^\/admin\/company\/job-postings\/(\d+)$/,
    handler: ({ params }) => {
      const target = findPosting(Number(params[0]));
      if (!target) return null;
      const revision = pendingRevisions.get(target.id) ?? null;
      return {
        posting: { ...target, hasPendingRevision: revision != null },
        pendingRevisionId: revision ? target.id * 10 + 1 : null,
        pendingRevision: revision,
      };
    },
  },
  {
    method: "POST",
    pattern: /^\/admin\/company\/job-postings\/(\d+)\/approve$/,
    handler: ({ params }) => {
      const target = findPosting(Number(params[0]));
      if (!target) return null;
      const revision = pendingRevisions.get(target.id);
      if (revision) {
        // 수정 검토 승인 — 변경본을 본문에 반영(게시 유지)
        Object.assign(target, revision);
        pendingRevisions.delete(target.id);
        target.hasPendingRevision = false;
      } else if (target.status === "PENDING_REVIEW") {
        target.status = "PUBLISHED";
        target.publishedAt = target.publishedAt ?? nowIso();
        target.rejectReason = null;
      }
      target.updatedAt = nowIso();
      return null;
    },
  },
  {
    method: "POST",
    pattern: /^\/admin\/company\/job-postings\/(\d+)\/reject$/,
    handler: ({ params, body }) => {
      const target = findPosting(Number(params[0]));
      if (!target) return null;
      const reason = (body as { reason?: string })?.reason ?? "";
      if (pendingRevisions.has(target.id)) {
        // 수정 검토 반려 — 변경본만 폐기, 게시 내용 유지
        pendingRevisions.delete(target.id);
        target.hasPendingRevision = false;
      } else if (target.status === "PENDING_REVIEW") {
        target.status = "REJECTED";
        target.rejectReason = reason;
      }
      target.updatedAt = nowIso();
      return null;
    },
  },
];
