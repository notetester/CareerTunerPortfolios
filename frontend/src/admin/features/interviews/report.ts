import type { InterviewReport } from "@/features/interview/types/interview";

export type { InterviewReport };

/** interview_session.report JSON 파싱 — 이 구조(categories/summaryFeedback 배열)가 아니면 null.
 *  (구버전/시드 리포트는 {summary, strengths, weaknesses} 등 다른 형식일 수 있음) */
export function parseReport(raw: string | null): InterviewReport | null {
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as Partial<InterviewReport>;
    if (!Array.isArray(parsed?.categories) || !Array.isArray(parsed?.summaryFeedback)) {
      return null;
    }
    return parsed as InterviewReport;
  } catch {
    return null;
  }
}
