import type {
  InterviewAnswer,
  InterviewMode,
  InterviewQuestion,
} from "@/features/interview/types/interview";

export interface AdminInterviewSessionRow {
  id: number;
  applicationCaseId: number;
  userId: number;
  userEmail: string;
  companyName: string;
  jobTitle: string;
  mode: InterviewMode;
  totalScore: number | null;
  startedAt: string | null;
  endedAt: string | null;
  createdAt: string;
  questionCount: number;
  answeredCount: number;
}

export interface AdminInterviewSessionDetail {
  session: AdminInterviewSessionRow;
  questions: InterviewQuestion[];
  answers: InterviewAnswer[];
  /** interview_session.report JSON 원문 (InterviewReport 직렬화). */
  report: string | null;
}

// ───── 학습 파이프라인 ─────

/** 학습 샘플 통계. */
export interface TrainingStats {
  sampleCount: number;
  averageScore: number | null;
}

/** 평가 하니스(LLM-as-judge) 결과 — 채점 일관성. */
export interface EvalHarnessResult {
  evaluated: number;
  meanAbsDiff: number;
  agreementRate: number;
}

/** 파인튜닝 잡 생성 결과. */
export interface FineTuneResult {
  sampleCount: number;
  baseModel: string;
  fileId: string;
  jobId: string;
  status: string;
}
