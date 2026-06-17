/**
 * 면접 튜토리얼 단계 정의. deploy-day v2 의 TUT_STEPS 패턴을 면접 탭 투어에 맞게 구성.
 *
 * 진행 방식:
 *  - `tab` 이 있으면 진입 시 해당 탭으로 자동 전환 후 수동 [다음].
 *  - `awaitTab` 이 있으면 사용자가 그 탭을 직접 눌러야 다음으로 진행(클릭 유도).
 *  - `targetId` (data-tut 속성값)가 있으면 그 요소를 스포트라이트, 없으면 중앙 풍선.
 *
 * 흐름: "탭 눌러보세요"(awaitTab) → 그 탭의 핵심 요소/기능 설명(수동) 으로 짚어 나간다.
 */
export interface TutStep {
  key: string;
  /** 진입 시 이 탭으로 자동 전환 (awaitTab 과 동시 사용하지 않는다). */
  tab?: string;
  /** 사용자가 이 탭으로 직접 이동하면 자동 진행한다. */
  awaitTab?: string;
  /** 스포트라이트할 DOM (data-tut 속성값). 없으면 중앙 풍선. */
  targetId?: string;
  title: string;
  body: string;
}

export const TUT_STEPS: TutStep[] = [
  {
    key: "intro",
    title: "AI 가상 면접 둘러보기",
    body: "면접 모드 선택부터 리포트까지, 전체 흐름을 클릭하며 살펴봅니다. 더미 데이터라 로그인 없이도 안전하게 체험돼요.",
  },
  {
    key: "modes",
    tab: "modes",
    targetId: "tut-modes-grid",
    title: "1. 면접 모드 선택",
    body: "위에서 지원 건을 고르고 여기서 면접 모드(직무·인성·압박 등)를 선택하면 면접 세션이 시작됩니다. 모드에 따라 질문 성격이 달라져요.",
  },
  {
    key: "questions-go",
    awaitTab: "questions",
    targetId: "tut-tab-questions",
    title: "2. 예상 면접 질문으로",
    body: "'예상 면접 질문' 탭을 눌러보세요.",
  },
  {
    key: "questions-list",
    targetId: "tut-q-list",
    title: "AI가 만든 예상 질문",
    body: "공고에 맞춰 AI가 만든 질문들입니다. 각 질문에 답변을 쓰고 '답변 평가'를 누르면 즉시 점수·피드백·개선답변이 나오고, '모범답안 보기'로 답안지를, 평가 후엔 꼬리질문까지 받을 수 있어요.",
  },
  {
    key: "practice-go",
    awaitTab: "practice",
    targetId: "tut-tab-practice",
    title: "3. 복습 테스트로",
    body: "'복습 테스트' 탭을 눌러보세요.",
  },
  {
    key: "practice",
    targetId: "tut-panel-practice",
    title: "복습 테스트",
    body: "공부한 질문을 모범답안 없이 랜덤 순서로 풀고, 마지막에 한 번에 채점합니다. AI 채점 사고과정(에이전트 트레이스)도 함께 볼 수 있어요.",
  },
  {
    key: "live-go",
    awaitTab: "live",
    targetId: "tut-tab-live",
    title: "4. 음성 모의면접으로",
    body: "'음성 모의면접' 탭을 눌러보세요.",
  },
  {
    key: "live",
    targetId: "tut-panel-live",
    title: "음성 모의면접",
    body: "AI 면접관이 음성으로 질문하고, 말 속도·필러·톤 등 말하기 지표를 분석합니다. '데모 면접 시작'으로 진행 흐름을 미리 볼 수 있어요.",
  },
  {
    key: "avatar-go",
    awaitTab: "avatar",
    targetId: "tut-tab-avatar",
    title: "5. 아바타 화상 면접으로",
    body: "'아바타 화상 면접' 탭을 눌러보세요.",
  },
  {
    key: "avatar",
    targetId: "tut-panel-avatar",
    title: "아바타 화상 면접",
    body: "아바타 면접관과 화상으로 진행하며 표정·시선·자세까지 분석합니다. '데모 면접 시작'으로 진행 흐름을 확인해 보세요.",
  },
  {
    key: "evaluation-go",
    awaitTab: "evaluation",
    targetId: "tut-tab-evaluation",
    title: "6. 답변 평가 기준으로",
    body: "'답변 평가 기준' 탭을 눌러보세요.",
  },
  {
    key: "evaluation",
    targetId: "tut-panel-evaluation",
    title: "답변 평가 기준",
    body: "AI가 답변을 채점하는 8가지 기준과 점수 구간을 미리 확인할 수 있어요.",
  },
  {
    key: "correction-go",
    awaitTab: "correction",
    targetId: "tut-tab-correction",
    title: "7. AI 첨삭으로",
    body: "'AI 첨삭' 탭을 눌러보세요.",
  },
  {
    key: "correction",
    targetId: "tut-panel-correction",
    title: "AI 첨삭",
    body: "작성한 답변을 더 나은 표현으로 다듬는 방법을 안내합니다.",
  },
  {
    key: "report-go",
    awaitTab: "report",
    targetId: "tut-tab-report",
    title: "8. 면접 리포트로",
    body: "'면접 리포트' 탭을 눌러보세요.",
  },
  {
    key: "report-score",
    targetId: "tut-report-score",
    title: "면접 리포트 — 총점",
    body: "면접 전체의 총점, 진행 질문 수, 소요 시간을 한눈에 보여줍니다. 이전 면접 점수와 비교도 돼요.",
  },
  {
    key: "report-categories",
    targetId: "tut-report-categories",
    title: "항목별 점수 + 종합 피드백",
    body: "항목별 점수와 AI 종합 피드백으로 강점과 보완점을 정리해 줍니다.",
  },
  {
    key: "done",
    title: "끝! 이제 직접 해보세요",
    body: "전체 흐름을 다 봤어요. 로그인하고 실제 지원 건으로 면접을 시작하면 AI가 맞춤 질문부터 리포트까지 만들어 줍니다.",
  },
];
