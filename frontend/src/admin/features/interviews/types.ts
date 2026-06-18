import type {
  InterviewAnswer,
  InterviewMode,
  InterviewQuestion,
  MediaAnalysis,
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
  adminMemo: string | null;
}

export interface AdminInterviewSessionPage {
  items: AdminInterviewSessionRow[];
  total: number;
  page: number;
  size: number;
}

export interface AdminInterviewSessionDetail {
  session: AdminInterviewSessionRow;
  questions: InterviewQuestion[];
  answers: InterviewAnswer[];
  /** 음성/영상 면접(아바타·음성 모의) 분석 결과. */
  mediaResults: MediaAnalysis[];
  /** interview_session.report JSON 원문 (InterviewReport 직렬화). */
  report: string | null;
}

/** 면접 AI 기능 실패 이력 한 줄 (ai_usage_log 기반). */
export interface AdminInterviewAiFailureRow {
  id: number;
  userId: number;
  userEmail: string;
  applicationCaseId: number | null;
  companyName: string | null;
  jobTitle: string | null;
  featureType: string;
  errorMessage: string | null;
  createdAt: string;
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
