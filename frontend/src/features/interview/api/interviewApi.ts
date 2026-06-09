import { api } from "@/app/lib/api";
import type {
  CreateInterviewSessionRequest,
  GenerateQuestionsRequest,
  InterviewAnswer,
  InterviewQuestion,
  InterviewReport,
  InterviewSession,
  SubmitAnswerRequest,
} from "../types/interview";

// 백엔드 계약 (구현 예정): /api/interview/**
// 컨트롤러는 ApiResponse<T> envelope 로 응답하고, api() 래퍼가 data 만 풀어서 돌려준다.

/** 내 면접 세션 목록 (최근 기록). */
export function listInterviewSessions(): Promise<InterviewSession[]> {
  return api<InterviewSession[]>("/interview/sessions", { method: "GET" });
}

/** 면접 세션 생성 (지원 건 + 모드 선택). */
export function createInterviewSession(
  request: CreateInterviewSessionRequest,
): Promise<InterviewSession> {
  return api<InterviewSession>("/interview/sessions", {
    method: "POST",
    body: JSON.stringify(request),
  });
}

/** 세션에 대한 AI 예상 질문 생성. */
export function generateExpectedQuestions(
  sessionId: number,
  request: GenerateQuestionsRequest,
): Promise<InterviewQuestion[]> {
  return api<InterviewQuestion[]>(`/interview/sessions/${sessionId}/generate-questions`, {
    method: "POST",
    body: JSON.stringify(request),
  });
}

/** 세션의 질문 목록 조회. */
export function listSessionQuestions(sessionId: number): Promise<InterviewQuestion[]> {
  return api<InterviewQuestion[]>(`/interview/sessions/${sessionId}/questions`, { method: "GET" });
}

/** 질문에 답변 제출 → AI 평가(점수/피드백/개선답변) 반환. */
export function submitAnswer(
  questionId: number,
  request: SubmitAnswerRequest,
): Promise<InterviewAnswer> {
  return api<InterviewAnswer>(`/interview/questions/${questionId}/answers`, {
    method: "POST",
    body: JSON.stringify(request),
  });
}

/** 세션 종료 → AI 종합 리포트 생성/조회. */
export function getInterviewReport(sessionId: number): Promise<InterviewReport> {
  return api<InterviewReport>(`/interview/sessions/${sessionId}/report`, { method: "GET" });
}
