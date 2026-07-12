export type InterviewSessionListStateKind = "DONE" | "REPORTED" | "RUNNING";

export interface InterviewSessionProgressLike {
  endedAt: string | null;
  totalQuestions: number;
  answeredQuestions: number;
  finished: boolean;
}

export interface InterviewSessionListState {
  kind: InterviewSessionListStateKind;
  progress: number;
  label: string;
}

/** 목록 API의 집계값을 웹·네이티브 목록에서 쓰는 동일한 상태 표현으로 바꾼다. */
export function getInterviewSessionListState(session: InterviewSessionProgressLike): InterviewSessionListState {
  const total = Math.max(0, Math.trunc(session.totalQuestions));
  const answered = Math.min(total, Math.max(0, Math.trunc(session.answeredQuestions)));
  const progress = session.finished ? 100 : total > 0 ? Math.round((answered * 100) / total) : 0;

  if (session.finished) return { kind: "DONE", progress, label: "완료" };
  if (session.endedAt != null) {
    return {
      kind: "REPORTED",
      progress,
      label: total > 0 ? `리포트 · ${answered}/${total} 답변` : "리포트",
    };
  }
  return {
    kind: "RUNNING",
    progress,
    label: progress > 0 ? `${progress}% 진행 중` : "진행 중",
  };
}
