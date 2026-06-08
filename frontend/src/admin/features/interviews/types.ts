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
