// 공개 채용공고 게시판 타입 — 도메인 타입은 company 기능 모듈의 것을 그대로 공유한다.
export type {
  CompanyJobPosting,
  JobPostingPage,
} from "@/features/company/types/company";

/** 게시판 검색 파라미터. */
export interface JobBoardSearchParams {
  keyword?: string;
  jobRole?: string;
  location?: string;
  employmentType?: string;
  careerLevel?: string;
  sort?: "latest" | "deadline" | "views";
  page?: number;
  size?: number;
}

export interface JobBoardAnalyzeResult {
  applicationCaseId: number;
}
