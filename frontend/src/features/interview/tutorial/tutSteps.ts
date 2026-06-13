/**
 * 면접 튜토리얼 단계 정의. deploy-day v2 의 TUT_STEPS 패턴을 면접 탭 투어에 맞게 단순화.
 *
 * 진행 방식:
 *  - `tab` 이 있으면 진입 시 해당 탭으로 자동 전환 후 수동 [다음].
 *  - `awaitTab` 이 있으면 사용자가 그 탭을 직접 눌러야 다음으로 진행(클릭 유도).
 *  - `targetId` (data-tut 속성값)가 있으면 그 요소를 스포트라이트, 없으면 화면 중앙 풍선.
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
    body: "면접 모드 선택부터 리포트까지, 전체 흐름을 1분 안에 클릭하며 살펴봅니다. 더미 데이터라 로그인 없이도 안전하게 체험돼요.",
  },
  {
    key: "modes",
    tab: "modes",
    targetId: "tut-tab-modes",
    title: "1. 면접 모드 선택",
    body: "지원 건과 면접 모드(직무·인성·압박 등)를 고르는 곳입니다. 여기서 면접 세션이 시작돼요.",
  },
  {
    key: "questions",
    awaitTab: "questions",
    targetId: "tut-tab-questions",
    title: "2. 예상 면접 질문",
    body: "'예상 면접 질문' 탭을 눌러보세요. AI가 공고에 맞춘 질문을 만들고, 답변하면 바로 평가해 줍니다.",
  },
  {
    key: "practice",
    awaitTab: "practice",
    targetId: "tut-tab-practice",
    title: "3. 복습 테스트",
    body: "'복습 테스트' 탭을 눌러보세요. 모범답안 없이 랜덤으로 출제해 제대로 소화했는지 점검합니다.",
  },
  {
    key: "live",
    awaitTab: "live",
    targetId: "tut-tab-live",
    title: "4. 음성 모의면접",
    body: "'음성 모의면접' 탭을 눌러보세요. AI 면접관이 음성으로 묻고, 말하기 지표(속도·필러·톤)를 분석합니다.",
  },
  {
    key: "avatar",
    awaitTab: "avatar",
    targetId: "tut-tab-avatar",
    title: "5. 아바타 화상 면접",
    body: "'아바타 화상 면접' 탭을 눌러보세요. 아바타 면접관과 화상으로 진행하며 표정·자세까지 분석합니다.",
  },
  {
    key: "evaluation",
    awaitTab: "evaluation",
    targetId: "tut-tab-evaluation",
    title: "6. 답변 평가 기준",
    body: "'답변 평가 기준' 탭을 눌러보세요. AI가 어떤 기준으로 채점하는지 미리 확인할 수 있어요.",
  },
  {
    key: "correction",
    awaitTab: "correction",
    targetId: "tut-tab-correction",
    title: "7. AI 첨삭",
    body: "'AI 첨삭' 탭을 눌러보세요. 답변을 더 나은 표현으로 다듬는 방법을 안내합니다.",
  },
  {
    key: "report",
    awaitTab: "report",
    targetId: "tut-tab-report",
    title: "8. 면접 리포트",
    body: "'면접 리포트' 탭을 눌러보세요. 항목별 점수와 AI 종합 피드백을 한눈에 볼 수 있습니다.",
  },
  {
    key: "done",
    title: "끝! 이제 직접 해보세요",
    body: "전체 흐름을 다 봤어요. 로그인하고 실제 지원 건으로 면접을 시작하면 AI가 맞춤 질문부터 리포트까지 만들어 줍니다.",
  },
];
