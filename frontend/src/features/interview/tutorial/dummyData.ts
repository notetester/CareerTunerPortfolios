import type {
  InterviewAgentStep,
  InterviewAnswer,
  InterviewQuestion,
  InterviewReport,
  InterviewSession,
  MediaAnalysis,
  MediaCapabilities,
} from "../types/interview";

/**
 * 튜토리얼 전용 더미 데이터.
 *
 * 로그인/실제 세션/AI 호출 없이도 면접 흐름 전체를 클릭으로 체험할 수 있도록,
 * interviewApi 가 튜토리얼 모드(isTutorialActive)에서 이 값들을 반환한다.
 * 모든 식별자는 음수 → 실제 데이터와 충돌하지 않는다.
 */

/** 더미 세션 식별자. 음수라 실제 세션 id 와 겹치지 않는다. */
export const TUTORIAL_SESSION_ID = -1;

export const dummySession: InterviewSession = {
  id: TUTORIAL_SESSION_ID,
  applicationCaseId: -1,
  mode: "JOB",
  startedAt: "2026-06-14T10:00:00",
  endedAt: null,
  totalScore: null,
  avgScore: null,
  createdAt: "2026-06-14T10:00:00",
};

/** 공부용 예상 질문 5개 (꼬리질문 제외). */
export const dummyQuestions: InterviewQuestion[] = [
  { id: -101, interviewSessionId: TUTORIAL_SESSION_ID, parentQuestionId: null, question: "1분 안에 자기소개를 해주세요.", questionType: "EXPECTED", sortOrder: 1 },
  { id: -102, interviewSessionId: TUTORIAL_SESSION_ID, parentQuestionId: null, question: "이 직무에 지원한 동기가 무엇인가요?", questionType: "EXPECTED", sortOrder: 2 },
  { id: -103, interviewSessionId: TUTORIAL_SESSION_ID, parentQuestionId: null, question: "가장 도전적이었던 프로젝트와 그 문제를 어떻게 해결했는지 설명해주세요.", questionType: "TECH", sortOrder: 3 },
  { id: -104, interviewSessionId: TUTORIAL_SESSION_ID, parentQuestionId: null, question: "협업 중 갈등이 생겼을 때 어떻게 대처하나요?", questionType: "PERSONALITY", sortOrder: 4 },
  { id: -105, interviewSessionId: TUTORIAL_SESSION_ID, parentQuestionId: null, question: "5년 후 본인의 커리어 목표는 무엇인가요?", questionType: "EXPECTED", sortOrder: 5 },
];

/** 꼬리질문 더미 (generateFollowUps 반환에 덧붙는다). */
export const dummyFollowUp: InterviewQuestion = {
  id: -201,
  interviewSessionId: TUTORIAL_SESSION_ID,
  parentQuestionId: -103,
  question: "그 문제를 해결하는 과정에서 본인이 내린 가장 중요한 기술적 결정은 무엇이었나요?",
  questionType: "FOLLOW_UP",
  sortOrder: 99,
};

/** 답변 평가 더미. 질문 id 로 안정적인 음수 id 를 만든다. */
export function dummyAnswer(questionId: number): InterviewAnswer {
  return {
    id: -900000 - Math.abs(questionId),
    questionId,
    answerText: null,
    audioUrl: null,
    videoUrl: null,
    score: 82,
    feedback:
      "핵심 경험을 구체적으로 제시했고 직무 연관성도 분명합니다. 다만 결과를 수치로 보강하면 설득력이 더 올라갑니다. (튜토리얼 예시 평가)",
    improvedAnswer:
      "저는 ◯◯ 상황에서 ◯◯을 맡아, ◯◯ 방식으로 문제를 해결했고 그 결과 ◯◯을 약 30% 개선했습니다. 이 경험은 지원 직무의 ◯◯ 역량과 직접 연결됩니다.",
    createdAt: "2026-06-14T10:05:00",
  };
}

/** 모범답안 더미. */
export const dummyModelAnswer =
  "STAR 구조로 답하면 좋습니다. 상황(Situation) → 과제(Task) → 행동(Action) → 결과(Result) 순으로 말하고, 특히 결과는 수치로 제시하세요. 예) '팀 빌드 시간이 길어 배포가 지연되던 상황에서(S), 빌드 최적화를 맡아(T), 캐시 전략을 도입해(A) 빌드 시간을 40% 단축했습니다(R).' (튜토리얼 예시 모범답안)";

/** 멀티에이전트 트레이스 더미 (AgentTimeline 용). 질문별로 부여한다. */
export function dummyAgentSteps(questionId: number): InterviewAgentStep[] {
  const base = Math.abs(questionId) * 10;
  return [
    { id: -(base + 1), questionId, stepNo: 1, agent: "RETRIEVER", action: "공고 요구 역량 조회", status: "DONE", summary: "직무 핵심 역량 3개 추출", detail: null, elapsedMs: 320, createdAt: "2026-06-14T10:05:01" },
    { id: -(base + 2), questionId, stepNo: 2, agent: "EVALUATOR", action: "답변 채점", status: "DONE", summary: "내용·구체성·논리성 기준 평가", detail: null, elapsedMs: 540, createdAt: "2026-06-14T10:05:02" },
    { id: -(base + 3), questionId, stepNo: 3, agent: "CRITIC", action: "평가 검증", status: "DONE", summary: "점수 일관성·근거 확인", detail: null, elapsedMs: 210, createdAt: "2026-06-14T10:05:03" },
  ];
}

/** 면접 종합 리포트 더미. */
export const dummyReport: InterviewReport = {
  totalScore: 82,
  previousScore: 74,
  questionCount: 5,
  durationLabel: "12분",
  categories: [
    { label: "답변 내용", score: 85 },
    { label: "직무 적합성", score: 80 },
    { label: "구체성", score: 78 },
    { label: "논리성", score: 84 },
    { label: "표현력", score: 83 },
  ],
  summaryFeedback: [
    "전반적으로 직무 연관성이 분명하고 답변 구조가 안정적입니다.",
    "경험을 수치로 보강하면 구체성 점수가 더 올라갑니다.",
    "압박 질문 상황에서도 침착함을 유지한 점이 좋았습니다.",
  ],
};

/** 외부 키 보유 여부 — 튜토리얼에서는 음성/아바타 기능이 켜진 것처럼 보여준다. */
export const dummyCapabilities: MediaCapabilities = {
  voiceProfiling: true,
  avatar: true,
  avatarSandbox: true,
};

/** 저장된 음성/아바타 분석 결과 더미 (단계 C 에서 채운다). */
export const dummyMediaResults: MediaAnalysis[] = [];
