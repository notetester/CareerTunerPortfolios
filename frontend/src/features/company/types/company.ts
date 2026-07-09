// 기업 계정/채용공고 도메인 타입 + 라벨 사전.
// jobboard(공개 게시판)·admin/features/company 에서도 이 파일을 공유한다.

export type CompanyApplicationStatus = "PENDING" | "APPROVED" | "REJECTED";
export type TrustGrade = "BASIC" | "VERIFIED" | "PARTNER";
export type JobPostingStatus = "DRAFT" | "PENDING_REVIEW" | "PUBLISHED" | "REJECTED" | "CLOSED";

export interface CompanyApplication {
  id: number;
  userId: number;
  companyName: string;
  businessNumber: string | null;
  contact: string;
  description: string | null;
  status: CompanyApplicationStatus;
  rejectReason: string | null;
  reviewedAt: string | null;
  createdAt: string;
  applicantEmail?: string | null;
  applicantName?: string | null;
}

export interface CompanyProfile {
  userId: number;
  companyName: string;
  businessNumber: string | null;
  trustGrade: TrustGrade;
}

/** 공고 상세 필드(사람인식). 작성 폼·diff 비교가 이 목록을 기준으로 돈다. */
export interface JobPostingFields {
  title: string;
  jobRole: string;
  employmentType: string;
  careerLevel: string;
  careerYearsMin: number | null;
  careerYearsMax: number | null;
  educationLevel: string;
  salaryText: string | null;
  salaryNegotiable: boolean;
  workLocation: string | null;
  workHours: string | null;
  deadlineDate: string | null;
  alwaysOpen: boolean;
  mainTasks: string | null;
  requirements: string | null;
  preferred: string | null;
  benefits: string | null;
  hiringProcess: string | null;
  headcount: string | null;
  tags: string[];
}

export interface CompanyJobPosting extends JobPostingFields {
  id: number;
  companyUserId: number;
  companyName: string | null;
  trustGrade: TrustGrade | null;
  status: JobPostingStatus;
  rejectReason: string | null;
  viewCount: number;
  hasPendingRevision: boolean | null;
  publishedAt: string | null;
  closedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

/** 작성/수정 요청. submit=true 면 제출(정책에 따라 검토 대기 또는 즉시 게시). */
export interface JobPostingUpsertPayload extends JobPostingFields {
  submit?: boolean;
}

export interface JobPostingPage {
  items: CompanyJobPosting[];
  total: number;
  page: number;
  size: number;
}

// ── 라벨 사전 ──

export const EMPLOYMENT_TYPE_LABELS: Record<string, string> = {
  FULL_TIME: "정규직",
  CONTRACT: "계약직",
  INTERN: "인턴",
  PART_TIME: "아르바이트",
  FREELANCE: "프리랜서",
};

export const CAREER_LEVEL_LABELS: Record<string, string> = {
  NEW: "신입",
  EXPERIENCED: "경력",
  ANY: "경력무관",
};

export const EDUCATION_LEVEL_LABELS: Record<string, string> = {
  ANY: "학력무관",
  HIGH_SCHOOL: "고졸 이상",
  COLLEGE: "초대졸 이상",
  BACHELOR: "학사 이상",
  MASTER: "석사 이상",
  DOCTOR: "박사",
};

export const JOB_POSTING_STATUS_LABELS: Record<JobPostingStatus, string> = {
  DRAFT: "임시저장",
  PENDING_REVIEW: "검토 대기",
  PUBLISHED: "게시 중",
  REJECTED: "반려됨",
  CLOSED: "마감",
};

export const TRUST_GRADE_LABELS: Record<TrustGrade, string> = {
  BASIC: "기본",
  VERIFIED: "인증 기업",
  PARTNER: "파트너",
};

export const APPLICATION_STATUS_LABELS: Record<CompanyApplicationStatus, string> = {
  PENDING: "검토 중",
  APPROVED: "승인",
  REJECTED: "반려",
};

/** diff/상세 표시에 쓰는 필드 라벨(표시 순서 포함). */
export const JOB_POSTING_FIELD_LABELS: Array<{ key: keyof JobPostingFields; label: string }> = [
  { key: "title", label: "제목" },
  { key: "jobRole", label: "직무명" },
  { key: "employmentType", label: "고용형태" },
  { key: "careerLevel", label: "경력조건" },
  { key: "careerYearsMin", label: "경력(최소)" },
  { key: "careerYearsMax", label: "경력(최대)" },
  { key: "educationLevel", label: "학력" },
  { key: "salaryText", label: "급여" },
  { key: "salaryNegotiable", label: "급여 협의 가능" },
  { key: "workLocation", label: "근무지역" },
  { key: "workHours", label: "근무시간" },
  { key: "deadlineDate", label: "마감일" },
  { key: "alwaysOpen", label: "상시 채용" },
  { key: "headcount", label: "채용인원" },
  { key: "mainTasks", label: "주요업무" },
  { key: "requirements", label: "자격요건" },
  { key: "preferred", label: "우대사항" },
  { key: "benefits", label: "복리후생" },
  { key: "hiringProcess", label: "전형절차" },
  { key: "tags", label: "태그" },
];
