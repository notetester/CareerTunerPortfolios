// 데모/목: 관리자 면접 운영 도메인(면접 세션 모니터링 · RAG 지식베이스 · 학습 파이프라인 · AI 실패).
// 사용자 김데모(9001) 및 다른 지원자들의 지원건(101 카카오 / 102 네이버 / 103 토스 / 104 라인, 프론트엔드)을
// 기준으로 면접 세션·질문/답변·리포트, 지식 문서, 학습 통계, AI 실패 이력을 채운다.
// 모든 응답 타입은 admin/features/interviews · interview-knowledge 의 api 가 기대하는 RAW 페이로드와 1:1로 맞춘다(transform 없음).
import type { MockRoute, MockContext } from "../../registry";
import { iso } from "../../registry";
import type {
  AdminInterviewSessionRow,
  AdminInterviewSessionDetail,
  AdminInterviewSummary,
  AdminInterviewAiFailureRow,
  TrainingStats,
  EvalHarnessResult,
  FineTuneResult,
} from "@/admin/features/interviews/types";
import type {
  InterviewKnowledge,
  KnowledgeKind,
  AddKnowledgeRequest,
} from "@/admin/features/interview-knowledge/api";
import type { InterviewQuestion, InterviewAnswer } from "@/features/interview/types/interview";

// ── 면접 세션 목록(운영 모니터링용). 점수/모드/진행도 다양하게. ──
const sessions: AdminInterviewSessionRow[] = [
  {
    id: 7001,
    applicationCaseId: 101,
    userId: 9001,
    userEmail: "demo@careertuner.dev",
    companyName: "카카오",
    jobTitle: "프론트엔드 개발자",
    mode: "JOB",
    totalScore: 84,
    startedAt: iso(2),
    endedAt: iso(2),
    createdAt: iso(2),
    questionCount: 5,
    answeredCount: 5,
    hasReport: true,
    adminMemo: null,
  },
  {
    id: 7002,
    applicationCaseId: 102,
    userId: 9001,
    userEmail: "demo@careertuner.dev",
    companyName: "네이버",
    jobTitle: "프론트엔드 개발자",
    mode: "PRESSURE",
    totalScore: 72,
    startedAt: iso(4),
    endedAt: iso(4),
    createdAt: iso(4),
    questionCount: 6,
    answeredCount: 6,
    hasReport: false,
    adminMemo: null,
  },
  {
    id: 7003,
    applicationCaseId: 103,
    userId: 9002,
    userEmail: "jiwon@careertuner.dev",
    companyName: "토스",
    jobTitle: "프론트엔드 개발자",
    mode: "COMPANY",
    totalScore: 91,
    startedAt: iso(5),
    endedAt: iso(5),
    createdAt: iso(5),
    questionCount: 5,
    answeredCount: 5,
    hasReport: true,
    adminMemo: null,
  },
  {
    id: 7004,
    applicationCaseId: 104,
    userId: 9003,
    userEmail: "minseo@careertuner.dev",
    companyName: "라인",
    jobTitle: "프론트엔드 개발자",
    mode: "PERSONALITY",
    totalScore: null,
    startedAt: iso(1),
    endedAt: null,
    createdAt: iso(1),
    questionCount: 5,
    answeredCount: 2,
    hasReport: false,
    adminMemo: null,
  },
  {
    id: 7005,
    applicationCaseId: 101,
    userId: 9004,
    userEmail: "haneul@careertuner.dev",
    companyName: "카카오",
    jobTitle: "프론트엔드 개발자",
    mode: "BASIC",
    totalScore: 66,
    startedAt: iso(8),
    endedAt: iso(8),
    createdAt: iso(8),
    questionCount: 4,
    answeredCount: 4,
    hasReport: true,
    adminMemo: null,
  },
];

// ── 세션별 질문/답변 상세. (이 화면이 기대하는 categories/summaryFeedback 형식 리포트 포함) ──
const questions7001: InterviewQuestion[] = [
  { id: 81001, interviewSessionId: 7001, parentQuestionId: null, question: "본인이 가장 자신 있는 프론트엔드 기술과 그 이유를 설명해 주세요.", questionType: "EXPECTED", sortOrder: 1 },
  { id: 81002, interviewSessionId: 7001, parentQuestionId: null, question: "React에서 리렌더링 최적화를 어떻게 했는지 실제 경험을 들어 설명해 주세요.", questionType: "TECH", sortOrder: 2 },
  { id: 81003, interviewSessionId: 7001, parentQuestionId: 81002, question: "useMemo와 useCallback을 어떤 기준으로 구분해 사용했나요?", questionType: "FOLLOW_UP", sortOrder: 3 },
  { id: 81004, interviewSessionId: 7001, parentQuestionId: null, question: "대규모 트래픽이 예상되는 화면을 설계한다면 어떤 점을 먼저 고려하시겠어요?", questionType: "SITUATION", sortOrder: 4 },
  { id: 81005, interviewSessionId: 7001, parentQuestionId: null, question: "동료와 코드 리뷰에서 의견이 충돌했을 때 어떻게 해결했나요?", questionType: "PERSONALITY", sortOrder: 5 },
];

const answers7001: InterviewAnswer[] = [
  { id: 82001, questionId: 81001, answerText: "TypeScript 기반 React 컴포넌트 설계에 가장 자신 있습니다. 타입 안전성으로 런타임 오류를 줄이고 협업 시 인터페이스 합의가 빨라졌습니다.", audioUrl: null, videoUrl: null, score: 88, feedback: "구체적 근거와 협업 효과를 잘 연결했습니다.", improvedAnswer: "여기에 측정 가능한 결과(버그 30% 감소 등)를 한 문장 덧붙이면 더 강해집니다.", createdAt: iso(2) },
  { id: 82002, questionId: 81002, answerText: "프로필링으로 불필요한 리렌더를 찾아 React.memo와 가상화(react-window)를 적용해 목록 렌더 시간을 절반으로 줄였습니다.", audioUrl: null, videoUrl: null, score: 90, feedback: "수치와 도구가 명확합니다.", improvedAnswer: null, createdAt: iso(2) },
  { id: 82003, questionId: 81003, answerText: "참조 동일성이 자식 props로 내려가 비교에 쓰일 때만 useCallback을, 계산 비용이 큰 값에만 useMemo를 썼습니다.", audioUrl: null, videoUrl: null, score: 85, feedback: "판단 기준이 분명합니다.", improvedAnswer: null, createdAt: iso(2) },
  { id: 82004, questionId: 81004, answerText: "CDN 캐싱과 코드 스플리팅, 그리고 초기 LCP를 위한 critical CSS 인라인을 먼저 고려하겠습니다.", audioUrl: null, videoUrl: null, score: 80, feedback: "방향은 좋으나 모니터링 지표 언급이 있으면 좋겠습니다.", improvedAnswer: "Web Vitals(LCP·INP) 목표 수치와 측정 방법을 덧붙여 보세요.", createdAt: iso(2) },
  { id: 82005, questionId: 81005, answerText: "데이터를 근거로 두 방안을 작게 프로토타이핑해 비교한 뒤 팀이 합의하도록 했습니다.", audioUrl: null, videoUrl: null, score: 77, feedback: "갈등 해결 태도가 건강합니다.", improvedAnswer: null, createdAt: iso(2) },
];

const report7001 = JSON.stringify({
  totalScore: 84,
  previousScore: 76,
  questionCount: 5,
  durationLabel: "18분",
  categories: [
    { label: "답변 내용", score: 87 },
    { label: "직무 적합성", score: 85 },
    { label: "구체성", score: 82 },
    { label: "논리성", score: 84 },
    { label: "표현력", score: 80 },
  ],
  summaryFeedback: [
    "기술 답변에 수치와 도구가 함께 제시되어 설득력이 높습니다.",
    "상황형 질문에서 모니터링 지표를 더 구체화하면 완성도가 올라갑니다.",
    "전반적으로 직무 적합성이 잘 드러나는 답변 구성이었습니다.",
  ],
  questionScores: [
    { questionId: 81001, order: 1, question: "본인이 가장 자신 있는 프론트엔드 기술과 그 이유를 설명해 주세요.", score: 88, feedback: "구체적 근거와 협업 효과를 잘 연결했습니다." },
    { questionId: 81002, order: 2, question: "React에서 리렌더링 최적화를 어떻게 했는지 실제 경험을 들어 설명해 주세요.", score: 90, feedback: "수치와 도구가 명확합니다." },
    { questionId: 81003, order: 3, question: "useMemo와 useCallback을 어떤 기준으로 구분해 사용했나요?", score: 85, feedback: "판단 기준이 분명합니다." },
    { questionId: 81004, order: 4, question: "대규모 트래픽이 예상되는 화면을 설계한다면 어떤 점을 먼저 고려하시겠어요?", score: 80, feedback: "방향은 좋으나 모니터링 지표 언급이 있으면 좋겠습니다." },
    { questionId: 81005, order: 5, question: "동료와 코드 리뷰에서 의견이 충돌했을 때 어떻게 해결했나요?", score: 77, feedback: "갈등 해결 태도가 건강합니다." },
  ],
});

// 토스 COMPANY 모드 — 고득점 리포트 (질문/답변 원본은 생략, 리포트만 보존된 세션 케이스).
const report7003 = JSON.stringify({
  totalScore: 91,
  previousScore: 84,
  questionCount: 5,
  durationLabel: "22분",
  categories: [
    { label: "답변 내용", score: 93 },
    { label: "직무 적합성", score: 92 },
    { label: "구체성", score: 90 },
    { label: "논리성", score: 91 },
    { label: "표현력", score: 88 },
  ],
  summaryFeedback: [
    "제품 임팩트를 수치로 설명하는 능력이 뛰어나 컬처핏 적합성이 높습니다.",
    "기술 선택의 트레이드오프를 비즈니스 관점과 연결한 점이 인상적입니다.",
  ],
  questionScores: [
    { questionId: 83001, order: 1, question: "토스에 지원한 이유와 만들고 싶은 임팩트를 말씀해 주세요.", score: 94, feedback: "제품 지표와 연결한 동기가 설득력 있습니다." },
    { questionId: 83002, order: 2, question: "빠른 실행과 코드 품질이 충돌할 때 어떻게 판단하나요?", score: 90, feedback: "기준이 명확하고 사례가 구체적입니다." },
    { questionId: 83003, order: 3, question: "결제 흐름 개선 경험을 지표와 함께 설명해 주세요.", score: 92, feedback: "전환율 수치를 근거로 제시했습니다." },
    { questionId: 83004, order: 4, question: "오너십을 발휘해 범위를 넓힌 경험이 있나요?", score: 89, feedback: "주도성이 잘 드러납니다." },
    { questionId: 83005, order: 5, question: "장애 상황에서의 의사결정 과정을 들려주세요.", score: 90, feedback: "우선순위 판단과 회고가 체계적입니다." },
  ],
});

// 카카오 BASIC 모드 — 저득점 리포트 (개선 여지 큰 케이스, 점수 색상 데모용).
const report7005 = JSON.stringify({
  totalScore: 66,
  previousScore: null,
  questionCount: 4,
  durationLabel: "12분",
  categories: [
    { label: "답변 내용", score: 68 },
    { label: "직무 적합성", score: 70 },
    { label: "구체성", score: 58 },
    { label: "논리성", score: 67 },
    { label: "표현력", score: 65 },
  ],
  summaryFeedback: [
    "답변이 전반적으로 짧아 경험의 깊이가 드러나지 않습니다.",
    "STAR 구조로 상황-행동-결과를 나눠 말하는 연습이 필요합니다.",
    "기술 용어 사용은 정확하나 실제 적용 사례가 부족합니다.",
  ],
  questionScores: [
    { questionId: 85101, order: 1, question: "자기소개와 함께 강점을 말씀해 주세요.", score: 70, feedback: "강점 나열에 그쳐 근거 사례가 필요합니다." },
    { questionId: 85102, order: 2, question: "협업 중 어려웠던 경험을 들려주세요.", score: 62, feedback: "상황 설명이 짧아 역할이 불분명합니다." },
    { questionId: 85103, order: 3, question: "최근 학습한 기술과 적용 계획은?", score: 66, feedback: "학습 동기는 좋으나 적용 계획이 추상적입니다." },
    { questionId: 85104, order: 4, question: "5년 후 커리어 목표는 무엇인가요?", score: 66, feedback: "목표와 현재 준비의 연결 고리를 보강하세요." },
  ],
});

const questions7004: InterviewQuestion[] = [
  { id: 84001, interviewSessionId: 7004, parentQuestionId: null, question: "팀에서 본인의 역할을 어떻게 정의하나요?", questionType: "PERSONALITY", sortOrder: 1 },
  { id: 84002, interviewSessionId: 7004, parentQuestionId: null, question: "마감 압박 속에서 품질을 지킨 경험을 들려주세요.", questionType: "SITUATION", sortOrder: 2 },
  { id: 84003, interviewSessionId: 7004, parentQuestionId: null, question: "새로운 기술을 학습할 때 본인만의 방법이 있나요?", questionType: "PERSONALITY", sortOrder: 3 },
  { id: 84004, interviewSessionId: 7004, parentQuestionId: null, question: "실패했던 프로젝트에서 무엇을 배웠나요?", questionType: "PERSONALITY", sortOrder: 4 },
  { id: 84005, interviewSessionId: 7004, parentQuestionId: null, question: "협업 도구를 활용해 일정을 관리한 사례가 있나요?", questionType: "SITUATION", sortOrder: 5 },
];

const answers7004: InterviewAnswer[] = [
  { id: 85001, questionId: 84001, answerText: "프론트엔드 화면을 책임지면서 디자이너·백엔드 사이의 인터페이스 합의를 주도하는 연결자 역할을 합니다.", audioUrl: null, videoUrl: null, score: 74, feedback: "역할 정의가 명확합니다.", improvedAnswer: null, createdAt: iso(1) },
  { id: 85002, questionId: 84002, answerText: "릴리스 전날 핵심 결제 흐름만 우선순위를 두고 회귀 테스트를 집중해 일정과 품질을 모두 지켰습니다.", audioUrl: null, videoUrl: null, score: 70, feedback: "우선순위 판단이 좋습니다. 결과 수치가 있으면 더 좋습니다.", improvedAnswer: "지킨 품질을 장애율 0건 등으로 표현해 보세요.", createdAt: iso(1) },
];

interface SessionBundle {
  questions: InterviewQuestion[];
  answers: InterviewAnswer[];
  report: string | null;
}

// 상세를 가진 세션 묶음. 없는 세션은 빈 질문/답변, 리포트 null 로 응답한다.
// 7003/7005 는 질문/답변 원본 없이 리포트만 보존된 세션 케이스(리포트 운영 화면 데모).
const bundles: Record<number, SessionBundle> = {
  7001: { questions: questions7001, answers: answers7001, report: report7001 },
  7003: { questions: [], answers: [], report: report7003 },
  7004: { questions: questions7004, answers: answers7004, report: null },
  7005: { questions: [], answers: [], report: report7005 },
};

// ── 면접 AI 실패 이력(ai_usage_log 기반). 질문/평가/리포트 생성 실패 등. ──
const aiFailures: AdminInterviewAiFailureRow[] = [
  {
    id: 91001,
    userId: 9003,
    userEmail: "minseo@careertuner.dev",
    applicationCaseId: 104,
    companyName: "라인",
    jobTitle: "프론트엔드 개발자",
    featureType: "INTERVIEW_ANSWER_EVAL",
    errorMessage: "LLM 응답 파싱 실패: 점수 JSON 형식 불일치 (재시도 후 폴백 점수 적용)",
    createdAt: iso(1),
  },
  {
    id: 91002,
    userId: 9002,
    userEmail: "jiwon@careertuner.dev",
    applicationCaseId: 103,
    companyName: "토스",
    jobTitle: "프론트엔드 개발자",
    featureType: "INTERVIEW_FOLLOW_UP",
    errorMessage: "AI 모델 요청 타임아웃(30s) — 꼬리질문 생성 건너뜀",
    createdAt: iso(3),
  },
  {
    id: 91003,
    userId: 9001,
    userEmail: "demo@careertuner.dev",
    applicationCaseId: 102,
    companyName: "네이버",
    jobTitle: "프론트엔드 개발자",
    featureType: "INTERVIEW_REPORT",
    errorMessage: "리포트 생성 실패: rate limit 초과 (429)",
    createdAt: iso(6),
  },
  {
    id: 91004,
    userId: 9004,
    userEmail: "haneul@careertuner.dev",
    applicationCaseId: null,
    companyName: null,
    jobTitle: null,
    featureType: "INTERVIEW_QUESTION",
    errorMessage: null,
    createdAt: iso(9),
  },
];

// ── RAG 지식베이스 문서(세션 내 in-memory: 추가/재색인 시 갱신). ──
let knowledgeSeq = 30010;
const knowledge: InterviewKnowledge[] = [
  {
    id: 30001,
    kind: "RUBRIC",
    title: "프론트엔드 직무 면접 평가 기준",
    content: "기술 정확성·문제 해결 과정·코드 품질 이해·협업 커뮤니케이션 4축으로 0~100 채점. 구체적 사례와 수치가 있으면 가점.",
    source: "내부 평가 가이드 v3",
    indexed: true,
    createdAt: iso(40),
  },
  {
    id: 30002,
    kind: "QUESTION_BANK",
    title: "React 심화 질문 은행",
    content: "렌더링 최적화, 상태관리 선택 기준, Suspense/Concurrent, 접근성, 번들 사이즈 관리 관련 질문 모음.",
    source: "question-bank/react.md",
    indexed: true,
    createdAt: iso(33),
  },
  {
    id: 30003,
    kind: "COMPANY",
    title: "카카오 프론트엔드 인재상",
    content: "사용자 중심 사고, 대규모 트래픽 경험, 데이터 기반 의사결정을 중시. 면접에서 협업과 주도성을 함께 본다.",
    source: "company/kakao",
    indexed: true,
    createdAt: iso(21),
  },
  {
    id: 30004,
    kind: "COMPANY",
    title: "토스 프론트엔드 컬처핏",
    content: "오너십과 빠른 실행, 제품 임팩트 중심. 기술 깊이와 함께 비즈니스 임팩트를 설명할 수 있는지 평가.",
    source: "company/toss",
    indexed: false,
    createdAt: iso(12),
  },
  {
    id: 30005,
    kind: "GENERAL",
    title: "STAR 답변 구조 가이드",
    content: "Situation-Task-Action-Result 순서로 경험을 구조화하면 논리성과 구체성 점수가 함께 오른다.",
    source: null,
    indexed: true,
    createdAt: iso(7),
  },
];

// ── 학습 파이프라인 통계(세션 내 in-memory: 파인튜닝 시 샘플 누적 반영). ──
const trainingStats: TrainingStats = {
  sampleCount: 1342,
  averageScore: 78.6,
};

function rowToDetail(row: AdminInterviewSessionRow): AdminInterviewSessionDetail {
  const bundle = bundles[row.id];
  return {
    session: row,
    questions: bundle?.questions ?? [],
    answers: bundle?.answers ?? [],
    mediaResults: [],
    report: bundle?.report ?? null,
  };
}

export const adminInterviewOpsRoutes: MockRoute[] = [
  // ── 세션 상세: /admin/interview/sessions/:id ──
  {
    method: "GET",
    pattern: /^\/admin\/interview\/sessions\/(\d+)$/,
    handler: ({ params }: MockContext) => {
      const id = Number(params[0]);
      const row = sessions.find((s) => s.id === id) ?? sessions[0];
      return rowToDetail(row);
    },
  },

  // ── 세션 목록: keyword(기업/직무/이메일) · mode · hasReport · page/size 필터 ──
  // api 가 기대하는 AdminInterviewSessionPage({items,total,page,size}) 형태로 응답한다.
  {
    method: "GET",
    pattern: /^\/admin\/interview\/sessions$/,
    handler: ({ query }: MockContext) => {
      const keyword = (query.get("keyword") ?? "").trim().toLowerCase();
      const mode = query.get("mode") ?? "";
      const hasReport = query.get("hasReport");
      const page = Math.max(Number(query.get("page") ?? 1) || 1, 1);
      const size = Math.max(Number(query.get("size") ?? 20) || 20, 1);
      let rows = [...sessions];
      if (mode) rows = rows.filter((r) => r.mode === mode);
      if (hasReport === "true") rows = rows.filter((r) => r.hasReport === true);
      if (keyword) {
        rows = rows.filter(
          (r) =>
            r.companyName.toLowerCase().includes(keyword) ||
            r.jobTitle.toLowerCase().includes(keyword) ||
            r.userEmail.toLowerCase().includes(keyword),
        );
      }
      const total = rows.length;
      const items = rows.slice((page - 1) * size, page * size);
      return { items, total, page, size };
    },
  },

  // ── 운영 메모 저장: in-memory 세션 행에 반영 (모니터링/리포트 화면 공용) ──
  {
    method: "PUT",
    pattern: /^\/admin\/interview\/sessions\/(\d+)\/memo$/,
    handler: ({ params, body }: MockContext) => {
      const id = Number(params[0]);
      const memo = (body as { memo?: string } | undefined)?.memo ?? "";
      const row = sessions.find((s) => s.id === id);
      if (row) row.adminMemo = memo.trim() === "" ? null : memo;
      return null;
    },
  },

  // ── 운영 요약: 세션 수·평균 총점·AI 실패·미디어 분석 (면접 모니터링/리포트 요약 카드) ──
  {
    method: "GET",
    pattern: /^\/admin\/interview\/summary$/,
    handler: (): AdminInterviewSummary => {
      const scored = sessions.filter((s) => s.totalScore !== null);
      const avgScore = scored.length
        ? Math.round(scored.reduce((sum, s) => sum + (s.totalScore ?? 0), 0) / scored.length)
        : null;
      return {
        totalSessions: sessions.length,
        avgScore,
        aiFailures: aiFailures.length,
        mediaCount: 0,
      };
    },
  },

  // ── 면접 AI 실패 이력: ?limit ──
  {
    method: "GET",
    pattern: /^\/admin\/interview\/ai-failures$/,
    handler: ({ query }: MockContext) => {
      const limit = Number(query.get("limit") ?? 50) || 50;
      return aiFailures.slice(0, limit);
    },
  },

  // ── RAG 지식 목록: ?limit ──
  {
    method: "GET",
    pattern: /^\/admin\/interview\/knowledge$/,
    handler: ({ query }: MockContext) => {
      const limit = Number(query.get("limit") ?? 100) || 100;
      return knowledge.slice(0, limit);
    },
  },

  // ── RAG 지식 추가: 새 문서를 목록 맨 앞에 끼우고 생성 엔티티 반환 ──
  {
    method: "POST",
    pattern: /^\/admin\/interview\/knowledge$/,
    handler: ({ body }: MockContext) => {
      const req = (body as AddKnowledgeRequest | undefined) ?? { kind: "GENERAL" as KnowledgeKind, content: "" };
      const created: InterviewKnowledge = {
        id: ++knowledgeSeq,
        kind: req.kind,
        title: req.title ?? null,
        content: req.content,
        source: req.source ?? null,
        indexed: false,
        createdAt: new Date().toISOString(),
      };
      knowledge.unshift(created);
      return created;
    },
  },

  // ── RAG 재색인: 모든 문서를 색인됨으로 전환하고 처리 건수 반환 ──
  {
    method: "POST",
    pattern: /^\/admin\/interview\/knowledge\/reindex$/,
    handler: () => {
      knowledge.forEach((doc) => {
        doc.indexed = true;
      });
      return { reindexed: knowledge.length };
    },
  },

  // ── 학습 통계 ──
  {
    method: "GET",
    pattern: /^\/admin\/interview\/training\/stats$/,
    handler: () => ({ ...trainingStats }),
  },

  // ── 평가 하니스(LLM-as-judge): ?sampleSize ──
  {
    method: "POST",
    pattern: /^\/admin\/interview\/training\/eval$/,
    handler: ({ query }: MockContext): EvalHarnessResult => {
      const sampleSize = Number(query.get("sampleSize") ?? 20) || 20;
      return {
        evaluated: sampleSize,
        meanAbsDiff: 4.2,
        agreementRate: 0.86,
      };
    },
  },

  // ── 파인튜닝 잡 생성: 샘플 수를 통계에 반영하고 잡 정보 반환 ──
  {
    method: "POST",
    pattern: /^\/admin\/interview\/training\/fine-tune$/,
    handler: ({ query }: MockContext): FineTuneResult => {
      const baseModel = query.get("baseModel") ?? "mock-demo";
      return {
        sampleCount: trainingStats.sampleCount,
        baseModel,
        fileId: "file-demo-7Yc2Qm",
        jobId: "ftjob-demo-9aB3kZ",
        status: "running",
      };
    },
  },
];
