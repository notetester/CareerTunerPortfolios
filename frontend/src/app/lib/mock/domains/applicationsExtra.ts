// 데모/목: B 지원 건(Application Case) 도메인 중 core 레지스트리에 없는 나머지 엔드포인트.
// core(index.ts coreRoutes)에서 이미 처리: GET /application-cases, GET /application-cases/:id,
//   job-posting(GET·revisions), job-analysis(GET·history·POST mock), company-analysis(GET·history·POST mock),
//   ai-usage/b/failures.
// 여기서는 생성/수정/삭제/복원, from-job-posting(텍스트·파일 업로드), 추출 잡(active·latest·단건·retry),
//   공고문 저장/업로드(POST), 분석 종합 조회, 공고/기업 분석 리뷰(PATCH) 를 채운다.
// 페르소나·식별자는 data.ts 와 동일: 김데모, 지원 건 101=카카오·102=네이버·103=토스·104=라인(모두 프론트엔드 개발자).
import type { MockRoute, MockContext } from "../registry";
import { iso } from "../registry";
import type {
  ApplicationCase,
  ApplicationCaseExtraction,
  ApplicationSourceType,
} from "@/features/applications/types/applicationCase";
import type { JobPosting, JobPostingMetadata } from "@/features/applications/types/jobPosting";
import type { JobAnalysis, CompanyAnalysis } from "@/features/applications/types/analysis";
import { NULL_ANALYSIS_PROVENANCE } from "@/features/applications/types/analysis";

// from-job-posting 응답 타입(applicationCasesApi 에서 export). 타입 전용 import.
import type { CreateApplicationCaseFromJobPostingResponse } from "@/features/applications/api/applicationCasesApi";
import type { ModelOptions, ProviderOption, StageOptions } from "@/features/applications/api/modelOptionsApi";
// 분석 종합 응답 타입(analysisApi 에서 export). jobAnalysis/fitAnalysis 존재 여부만 사용한다.
import type { ApplicationCaseAnalysisOverview } from "@/features/applications/api/analysisApi";

// ── 기준 지원 건(data.ts 와 동일 식별자·회사명). 생성·수정·삭제 echo 의 기본값으로 재사용한다. ──
const baseCases: Record<number, { companyName: string; jobTitle: string }> = {
  101: { companyName: "카카오", jobTitle: "프론트엔드 개발자" },
  102: { companyName: "네이버", jobTitle: "프론트엔드 개발자" },
  103: { companyName: "토스", jobTitle: "웹 프론트엔드 엔지니어" },
  104: { companyName: "라인", jobTitle: "프론트엔드 개발자" },
};

function buildApplicationCase(overrides: Partial<ApplicationCase> & { id: number }): ApplicationCase {
  const base = baseCases[overrides.id];
  return {
    id: overrides.id,
    companyName: overrides.companyName ?? base?.companyName ?? "새 지원 기업",
    jobTitle: overrides.jobTitle ?? base?.jobTitle ?? "프론트엔드 개발자",
    postingDate: overrides.postingDate ?? null,
    deadlineDate: overrides.deadlineDate ?? null,
    sourceType: overrides.sourceType ?? "TEXT",
    status: overrides.status ?? "DRAFT",
    favorite: overrides.favorite ?? false,
    archived: overrides.archived ?? false,
    archivedAt: overrides.archivedAt ?? null,
    deletedAt: overrides.deletedAt ?? null,
    createdAt: overrides.createdAt ?? iso(0),
    updatedAt: overrides.updatedAt ?? iso(0),
  };
}

function buildExtraction(
  overrides: Partial<ApplicationCaseExtraction> & { id: number; applicationCaseId: number },
): ApplicationCaseExtraction {
  return {
    id: overrides.id,
    applicationCaseId: overrides.applicationCaseId,
    jobPostingId: overrides.jobPostingId ?? null,
    sourceType: overrides.sourceType ?? "TEXT",
    status: overrides.status ?? "SUCCEEDED",
    errorMessage: overrides.errorMessage ?? null,
    extractionStrategy: overrides.extractionStrategy ?? null,
    qualityScore: overrides.qualityScore ?? null,
    qualityStatus: overrides.qualityStatus ?? null,
    qualityReportJson: overrides.qualityReportJson ?? null,
    modelVersionsJson: overrides.modelVersionsJson ?? null,
    fallbackEligible: overrides.fallbackEligible ?? false,
    fallbackReason: overrides.fallbackReason ?? null,
    reviewedAt: overrides.reviewedAt ?? null,
    startedAt: overrides.startedAt ?? iso(0),
    finishedAt: overrides.finishedAt ?? iso(0),
    createdAt: overrides.createdAt ?? iso(0),
    updatedAt: overrides.updatedAt ?? iso(0),
  };
}

// 지원 건별 최신 추출 잡(공고문 OCR/파싱 상태). 데모에선 대부분 성공으로 둔다.
const latestExtractions: Record<number, ApplicationCaseExtraction> = {
  101: buildExtraction({ id: 7101, applicationCaseId: 101, jobPostingId: 1101, sourceType: "TEXT", status: "SUCCEEDED", createdAt: iso(5), startedAt: iso(5), finishedAt: iso(5) }),
  102: buildExtraction({ id: 7102, applicationCaseId: 102, jobPostingId: 1102, sourceType: "TEXT", status: "SUCCEEDED", createdAt: iso(8), startedAt: iso(8), finishedAt: iso(8) }),
  103: buildExtraction({
    id: 7103,
    applicationCaseId: 103,
    jobPostingId: 1103,
    sourceType: "PDF",
    status: "SUCCEEDED",
    qualityScore: 0.72,
    qualityStatus: "REVIEW_REQUIRED",
    startedAt: iso(1),
    finishedAt: iso(1),
    createdAt: iso(1),
    updatedAt: iso(1),
  }),
  104: buildExtraction({ id: 7104, applicationCaseId: 104, jobPostingId: 1104, sourceType: "IMAGE", status: "SUCCEEDED", createdAt: iso(12), startedAt: iso(12), finishedAt: iso(12) }),
};

function buildJobPosting(
  applicationCaseId: number,
  overrides: Partial<JobPosting> = {},
): JobPosting {
  const base = baseCases[applicationCaseId];
  const company = base?.companyName ?? "기업";
  return {
    id: overrides.id ?? 1100 + applicationCaseId,
    applicationCaseId,
    revision: overrides.revision ?? 1,
    originalText:
      overrides.originalText ??
      `${company} 프론트엔드 개발자 채용\n- 필수: React, JavaScript, REST API 연동 경험\n- 우대: TypeScript, 테스트 코드 작성\n- 담당: ${company} 서비스 웹 프론트엔드 개발 및 유지보수`,
    uploadedFileUrl: overrides.uploadedFileUrl ?? null,
    extractedText:
      overrides.extractedText ??
      "필수: React, JavaScript, REST API / 우대: TypeScript, 테스트 / 담당: 웹 프론트엔드 개발·유지보수",
    sourceType: overrides.sourceType ?? "TEXT",
    createdAt: overrides.createdAt ?? iso(1),
  };
}

// 분석 종합(공고 분석 + 적합도 분석) 존재 여부. data.ts 기준 101·102 는 분석 완료, 103·104 는 미완료.
const analysisOverviews: Record<number, ApplicationCaseAnalysisOverview> = {
  101: { jobAnalysis: { id: 3101, applicationCaseId: 101 }, fitAnalysis: { id: 201, applicationCaseId: 101, fitScore: 78 } },
  102: { jobAnalysis: { id: 3102, applicationCaseId: 102 }, fitAnalysis: { id: 202, applicationCaseId: 102, fitScore: 84 } },
  103: { jobAnalysis: null, fitAnalysis: null },
  104: { jobAnalysis: { id: 3104, applicationCaseId: 104 }, fitAnalysis: null },
};

// ── 신규 생성 카운터(세션 내 단조 증가 id). 새 지원 건/공고문/추출 잡 id 충돌 방지. ──
let nextCaseId = 901;
let nextJobPostingId = 1901;
let nextExtractionId = 7901;
const extractionPollsRemaining = new Map<number, number>();

function rememberExtraction(job: ApplicationCaseExtraction, activePolls = 0) {
  latestExtractions[job.applicationCaseId] = job;
  if (job.status === "QUEUED" || job.status === "RUNNING") {
    extractionPollsRemaining.set(job.id, activePolls);
  }
}

function pollActiveExtractions(): ApplicationCaseExtraction[] {
  const active: ApplicationCaseExtraction[] = [];
  for (const job of Object.values(latestExtractions)) {
    if (job.status !== "QUEUED" && job.status !== "RUNNING") continue;
    const remaining = extractionPollsRemaining.get(job.id) ?? 1;
    if (remaining > 0) {
      extractionPollsRemaining.set(job.id, remaining - 1);
      active.push(job);
      continue;
    }
    latestExtractions[job.applicationCaseId] = {
      ...job,
      status: "SUCCEEDED",
      qualityScore: 0.96,
      qualityStatus: "PASS",
      finishedAt: iso(0),
      updatedAt: iso(0),
    };
    extractionPollsRemaining.delete(job.id);
  }
  return active;
}

// 리뷰(PATCH) echo 용 기준 분석본. core 의 findJobAnalysis/findCompanyAnalysis 와 같은 값 모양.
function buildJobAnalysis(applicationCaseId: number, analysisId: number): JobAnalysis {
  return {
    id: analysisId,
    applicationCaseId,
    jobPostingId: 1100 + applicationCaseId,
    jobPostingRevision: 1,
    employmentType: "FULL_TIME",
    experienceLevel: "JUNIOR",
    requiredSkills: JSON.stringify(["React", "JavaScript", "REST API"]),
    preferredSkills: JSON.stringify(["TypeScript", "테스트 코드"]),
    duties: JSON.stringify(["서비스 웹 프론트엔드 개발", "기존 화면 유지보수"]),
    qualifications: JSON.stringify(["관련 전공 또는 동등 경험"]),
    difficulty: "NORMAL",
    summary: "React 기반 웹 프론트엔드 개발 직무. REST API 연동 경험이 핵심이며 TypeScript·테스트는 가산 요소.",
    evidence: JSON.stringify([{ field: "필수 React", quote: "필수: React, JavaScript, REST API 연동 경험" }]),
    ambiguousConditions: JSON.stringify([]),
    confirmedAt: iso(1),
    adminMemo: null,
    ...NULL_ANALYSIS_PROVENANCE,
    createdAt: iso(3),
  };
}

function buildCompanyAnalysis(applicationCaseId: number, analysisId: number): CompanyAnalysis {
  const company = baseCases[applicationCaseId]?.companyName ?? "기업";
  return {
    id: analysisId,
    applicationCaseId,
    jobPostingId: 1100 + applicationCaseId,
    jobPostingRevision: 1,
    companySummary: `${company}는 다양한 서비스를 운영하는 대형 IT 기업입니다.`,
    recentIssues: JSON.stringify(["AI 서비스 확장", "글로벌 사업 강화"]),
    industry: "IT 플랫폼",
    competitors: JSON.stringify(["네이버", "라인"]),
    interviewPoints: JSON.stringify(["대규모 서비스 협업 경험", "React 상태 관리 설계"]),
    sources: JSON.stringify(["채용 공고", "기업 홈페이지"]),
    verifiedFacts: JSON.stringify([{ fact: "메신저 서비스 운영", source: "기업 홈페이지" }]),
    aiInferences: JSON.stringify([{ inference: "프론트엔드 인력 수요 증가", basis: "AI 서비스 확장" }]),
    unknowns: null,
    sourceType: "WEB",
    checkedAt: iso(3),
    refreshRecommendedAt: iso(-27),
    confirmedAt: iso(1),
    adminMemo: null,
    ...NULL_ANALYSIS_PROVENANCE,
    createdAt: iso(3),
  };
}

// 리뷰 요청 body 중 부분 수정 + confirmed 토글을 echo 에 반영하기 위한 헬퍼.
function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" ? (value as Record<string, unknown>) : {};
}

function demoProvider(
  provider: string,
  displayName: string,
  actualModel: string,
  autoFallbackIncluded: boolean | null = null,
): ProviderOption {
  return {
    provider,
    displayName,
    selectable: true,
    reason: null,
    actualModel,
    autoFallbackIncluded,
  };
}

const demoJobAnalysisOptions: StageOptions = {
  recommendedDefault: "LOCAL",
  options: [
    demoProvider("LOCAL", "자체 모델(R1)", "careertuner-b-jobposting-r1"),
    demoProvider("CLAUDE", "Claude", "claude-haiku-4-5-20251001"),
    demoProvider("OPENAI", "OpenAI", "gpt-5"),
  ],
};

const demoCompanyAnalysisOptions: StageOptions = {
  recommendedDefault: "OPENAI",
  options: [
    demoProvider("OPENAI", "OpenAI", "gpt-5.4-mini"),
    demoProvider("CLAUDE", "Claude", "claude-haiku-4-5-20251001"),
    demoProvider("LOCAL", "자체 모델(R1)", "careertuner-b-jobposting-r1"),
  ],
};

function demoModelOptions(sourceType: string | null): ModelOptions {
  const normalizedSourceType = sourceType?.trim().toUpperCase();
  const requiresOcr = normalizedSourceType === "PDF" || normalizedSourceType === "IMAGE";
  return {
    ocr: requiresOcr
      ? {
          recommendedDefault: "CLAUDE",
          options: [
            demoProvider("CLAUDE", "Claude", "claude-haiku-4-5-20251001"),
            demoProvider("OPENAI", "OpenAI", "gpt-5", true),
            demoProvider("SELF_OCR", "자체 OCR 워커(PaddleOCR)", "PaddleOCR"),
          ],
        }
      : null,
    jobAnalysis: demoJobAnalysisOptions,
    companyAnalysis: demoCompanyAnalysisOptions,
  };
}

export const applicationsExtraRoutes: MockRoute[] = [
  // ── 등록·재실행 모델 선택. 운영 응답과 같은 shape을 제공해 데모에서도 picker를 온전히 시연한다. ──
  {
    method: "GET",
    pattern: /^\/application-cases\/model-options$/,
    handler: ({ query }) => demoModelOptions(query.get("sourceType")),
  },

  // ── 지원 건 생성(수동 입력). 요청 body(기업명/직무 등)를 echo 하며 새 id 부여. ──
  {
    method: "POST",
    pattern: /^\/application-cases$/,
    handler: (ctx: MockContext) => {
      const body = asRecord(ctx.body);
      const created: ApplicationCase = buildApplicationCase({
        id: nextCaseId++,
        companyName: typeof body.companyName === "string" ? body.companyName : "새 지원 기업",
        jobTitle: typeof body.jobTitle === "string" ? body.jobTitle : "프론트엔드 개발자",
        postingDate: typeof body.postingDate === "string" ? body.postingDate : null,
        deadlineDate: typeof body.deadlineDate === "string" ? body.deadlineDate : null,
        sourceType: (body.sourceType as ApplicationSourceType) ?? "MANUAL",
        status: (body.status as ApplicationCase["status"]) ?? "DRAFT",
        favorite: Boolean(body.favorite),
        archived: Boolean(body.archived),
      });
      return created;
    },
  },

  // ── 공고문에서 지원 건 생성(텍스트/URL). 새 케이스+공고문+추출 잡(성공)을 함께 반환. ──
  {
    method: "POST",
    pattern: /^\/application-cases\/from-job-posting$/,
    handler: (ctx: MockContext) => {
      const body = asRecord(ctx.body);
      const sourceType = (body.sourceType as ApplicationSourceType) ?? "TEXT";
      const id = nextCaseId++;
      const jobPostingId = nextJobPostingId++;
      const applicationCase: ApplicationCase = buildApplicationCase({
        id,
        companyName: "추출 대기 기업",
        jobTitle: "프론트엔드 개발자",
        status: "ANALYZING",
        sourceType,
      });
      const jobPosting: JobPosting = buildJobPosting(id, {
        id: jobPostingId,
        sourceType,
        originalText: typeof body.originalText === "string" ? body.originalText : undefined,
        uploadedFileUrl: typeof body.uploadedFileUrl === "string" ? body.uploadedFileUrl : null,
        createdAt: iso(0),
      });
      const metadata: JobPostingMetadata = {
        companyName: "추출 대기 기업",
        jobTitle: "프론트엔드 개발자",
        postingDate: null,
        deadlineDate: null,
      };
      const extractionJob: ApplicationCaseExtraction = buildExtraction({
        id: nextExtractionId++,
        applicationCaseId: id,
        jobPostingId,
        sourceType,
        status: "SUCCEEDED",
        startedAt: iso(0),
        finishedAt: iso(0),
        createdAt: iso(0),
      });
      const response: CreateApplicationCaseFromJobPostingResponse = {
        applicationCase,
        jobPosting,
        metadata,
        extractionJob,
      };
      rememberExtraction(extractionJob);
      return response;
    },
  },

  // ── 공고문 파일 업로드로 지원 건 생성(PDF/IMAGE). FormData 라 body 파싱 불가 → 고정 데모. ──
  {
    method: "POST",
    pattern: /^\/application-cases\/from-job-posting\/upload$/,
    handler: () => {
      const id = nextCaseId++;
      const jobPostingId = nextJobPostingId++;
      const applicationCase: ApplicationCase = buildApplicationCase({
        id,
        companyName: "추출 대기 기업",
        jobTitle: "프론트엔드 개발자",
        status: "ANALYZING",
        sourceType: "PDF",
      });
      const jobPosting: JobPosting = buildJobPosting(id, {
        id: jobPostingId,
        sourceType: "PDF",
        uploadedFileUrl: "https://demo.careertuner.dev/uploads/job-posting.pdf",
        createdAt: iso(0),
      });
      const metadata: JobPostingMetadata = {
        companyName: "추출 대기 기업",
        jobTitle: "프론트엔드 개발자",
        postingDate: null,
        deadlineDate: null,
      };
      const extractionJob: ApplicationCaseExtraction = buildExtraction({
        id: nextExtractionId++,
        applicationCaseId: id,
        jobPostingId,
        sourceType: "PDF",
        status: "RUNNING",
        startedAt: iso(0),
        finishedAt: null,
        createdAt: iso(0),
      });
      const response: CreateApplicationCaseFromJobPostingResponse = {
        applicationCase,
        jobPosting,
        metadata,
        extractionJob,
      };
      rememberExtraction(extractionJob, 2);
      return response;
    },
  },

  // ── 진행 중인 추출 잡 목록(QUEUED/RUNNING). 새 업로드/재시도에서 만들어진 잡만 노출한다. ──
  {
    method: "GET",
    pattern: /^\/application-cases\/extractions\/active$/,
    handler: (): ApplicationCaseExtraction[] => pollActiveExtractions(),
  },

  // ── 여러 지원 건의 최신 추출 잡(목록 화면 배지용). query applicationCaseIds 필터. ──
  {
    method: "GET",
    pattern: /^\/application-cases\/job-posting\/extractions\/latest$/,
    handler: (ctx: MockContext): ApplicationCaseExtraction[] => {
      const ids = ctx.query.getAll("applicationCaseIds").map(Number).filter((id) => Number.isFinite(id));
      const targets = ids.length > 0 ? ids : Object.keys(latestExtractions).map(Number);
      return targets
        .map((id) => latestExtractions[id])
        .filter((item): item is ApplicationCaseExtraction => Boolean(item));
    },
  },

  // ── 단건 최신 추출 잡(상세/마법사 폴링용). 없으면 null. ──
  {
    method: "GET",
    pattern: /^\/application-cases\/(\d+)\/job-posting\/extraction$/,
    handler: (ctx: MockContext): ApplicationCaseExtraction | null =>
      latestExtractions[Number(ctx.params[0])] ?? null,
  },

  // ── 추출 재시도. 새 잡 id 로 RUNNING 상태를 반환(폴링이 이어서 진행되는 것처럼). ──
  {
    method: "POST",
    pattern: /^\/application-cases\/(\d+)\/job-posting\/extraction\/retry$/,
    handler: (ctx: MockContext): ApplicationCaseExtraction => {
      const applicationCaseId = Number(ctx.params[0]);
      const previous = latestExtractions[applicationCaseId];
      const retried = buildExtraction({
        id: nextExtractionId++,
        applicationCaseId,
        jobPostingId: previous?.jobPostingId ?? 1100 + applicationCaseId,
        sourceType: previous?.sourceType ?? "TEXT",
        status: "RUNNING",
        startedAt: iso(0),
        finishedAt: null,
        createdAt: iso(0),
      });
      rememberExtraction(retried, 2);
      return retried;
    },
  },

  // ── 공고문 저장(새 리비전). 요청 body 를 반영해 revision +1 로 echo. ──
  {
    method: "POST",
    pattern: /^\/application-cases\/(\d+)\/job-posting$/,
    handler: (ctx: MockContext): JobPosting => {
      const applicationCaseId = Number(ctx.params[0]);
      const body = asRecord(ctx.body);
      return buildJobPosting(applicationCaseId, {
        id: nextJobPostingId++,
        revision: 2,
        sourceType: (body.sourceType as ApplicationSourceType) ?? "TEXT",
        originalText: typeof body.originalText === "string" ? body.originalText : null,
        extractedText: typeof body.extractedText === "string" ? body.extractedText : null,
        uploadedFileUrl: typeof body.uploadedFileUrl === "string" ? body.uploadedFileUrl : null,
        createdAt: iso(0),
      });
    },
  },

  // ── 공고문 파일 업로드(PDF/IMAGE). FormData 라 고정 데모 응답. ──
  {
    method: "POST",
    pattern: /^\/application-cases\/(\d+)\/job-posting\/upload$/,
    handler: (ctx: MockContext): JobPosting => {
      const applicationCaseId = Number(ctx.params[0]);
      return buildJobPosting(applicationCaseId, {
        id: nextJobPostingId++,
        revision: 2,
        sourceType: "PDF",
        originalText: null,
        uploadedFileUrl: "https://demo.careertuner.dev/uploads/job-posting.pdf",
        extractedText: "필수: React, TypeScript / 우대: 테스트 / 담당: 웹 프론트엔드 개발",
        createdAt: iso(0),
      });
    },
  },

  // ── 분석 종합 조회(공고 분석 + 적합도 분석 존재 여부). ──
  {
    method: "GET",
    pattern: /^\/application-cases\/(\d+)\/analysis$/,
    handler: (ctx: MockContext): ApplicationCaseAnalysisOverview =>
      analysisOverviews[Number(ctx.params[0])] ?? { jobAnalysis: null, fitAnalysis: null },
  },

  // ── 공고 분석 리뷰(사용자 확정/수정). 요청 body 부분 반영 + confirmed → confirmedAt echo. ──
  {
    method: "PATCH",
    pattern: /^\/application-cases\/(\d+)\/job-analysis\/(\d+)\/review$/,
    handler: (ctx: MockContext): JobAnalysis => {
      const applicationCaseId = Number(ctx.params[0]);
      const analysisId = Number(ctx.params[1]);
      const body = asRecord(ctx.body);
      const base = buildJobAnalysis(applicationCaseId, analysisId);
      const stringKeys: (keyof JobAnalysis)[] = [
        "employmentType",
        "experienceLevel",
        "requiredSkills",
        "preferredSkills",
        "duties",
        "qualifications",
        "difficulty",
        "summary",
        "evidence",
        "ambiguousConditions",
      ];
      stringKeys.forEach((key) => {
        if (typeof body[key] === "string") {
          (base[key] as string | null) = body[key] as string;
        }
      });
      const confirmed = body.confirmed === undefined ? true : Boolean(body.confirmed);
      base.confirmedAt = confirmed ? iso(0) : null;
      return base;
    },
  },

  // ── 기업 분석 리뷰(사용자 확정/수정). ──
  {
    method: "PATCH",
    pattern: /^\/application-cases\/(\d+)\/company-analysis\/(\d+)\/review$/,
    handler: (ctx: MockContext): CompanyAnalysis => {
      const applicationCaseId = Number(ctx.params[0]);
      const analysisId = Number(ctx.params[1]);
      const body = asRecord(ctx.body);
      const base = buildCompanyAnalysis(applicationCaseId, analysisId);
      const stringKeys: (keyof CompanyAnalysis)[] = [
        "companySummary",
        "recentIssues",
        "industry",
        "competitors",
        "interviewPoints",
        "sources",
        "verifiedFacts",
        "aiInferences",
      ];
      stringKeys.forEach((key) => {
        if (typeof body[key] === "string") {
          (base[key] as string | null) = body[key] as string;
        }
      });
      const confirmed = body.confirmed === undefined ? true : Boolean(body.confirmed);
      base.confirmedAt = confirmed ? iso(0) : null;
      return base;
    },
  },

  // ── 지원 건 수정(PATCH). 요청 body 를 반영한 ApplicationCase echo. ──
  {
    method: "PATCH",
    pattern: /^\/application-cases\/(\d+)$/,
    handler: (ctx: MockContext): ApplicationCase => {
      const id = Number(ctx.params[0]);
      const body = asRecord(ctx.body);
      const clearPostingDate = Boolean(body.clearPostingDate);
      const clearDeadlineDate = Boolean(body.clearDeadlineDate);
      return buildApplicationCase({
        id,
        companyName: typeof body.companyName === "string" ? body.companyName : undefined,
        jobTitle: typeof body.jobTitle === "string" ? body.jobTitle : undefined,
        postingDate: clearPostingDate
          ? null
          : typeof body.postingDate === "string"
            ? body.postingDate
            : null,
        deadlineDate: clearDeadlineDate
          ? null
          : typeof body.deadlineDate === "string"
            ? body.deadlineDate
            : null,
        sourceType: (body.sourceType as ApplicationSourceType) ?? "TEXT",
        status: (body.status as ApplicationCase["status"]) ?? "READY",
        favorite: Boolean(body.favorite),
        archived: Boolean(body.archived),
        archivedAt: body.archived ? iso(0) : null,
        updatedAt: iso(0),
      });
    },
  },

  // ── 지원 건 복원(휴지통 → 활성). api<void> → null. ──
  {
    method: "PATCH",
    pattern: /^\/application-cases\/(\d+)\/restore$/,
    handler: () => null,
  },

  // ── 지원 건 삭제(휴지통 이동). api<void> → null. ──
  {
    method: "DELETE",
    pattern: /^\/application-cases\/(\d+)$/,
    handler: () => null,
  },
];
