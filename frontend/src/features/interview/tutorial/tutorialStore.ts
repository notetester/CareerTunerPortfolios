import { create } from "zustand";
import { TUT_STEPS } from "./tutSteps";

/**
 * 면접 데모/튜토리얼 상태 + 단계 엔진. deploy-day v2 의 useTutorial 패턴 이식.
 *
 * 하나의 "데모 코어"(API 0, 더미로 실제처럼 동작)를 두 모드가 공유한다:
 *  - "tutorial" : 코어 + 가이드 풍선(순서 안내·요소 짚기). 강사·팀원 체험용.
 *  - "demo"     : 코어만(풍선 off). 체험판(가치 판단) + 면접 시연용.
 *
 * 두 모드 모두:
 *  - interviewApi 가 백엔드/AI 대신 더미를 반환한다 (isDataMockActive).
 *  - InterviewPage 가 로그인 게이트를 우회하고 더미 세션을 주입한다.
 * 풍선/awaitTab 자동 진행은 "tutorial" 에서만 동작한다.
 */
export type TutorialMode = "off" | "tutorial" | "demo";

interface TutorialState {
  mode: TutorialMode;
  /** 튜토리얼 단계 인덱스 (TUT_STEPS). demo 모드에서는 사용하지 않는다. */
  step: number;
  startTutorial: () => void;
  startDemo: () => void;
  stop: () => void;
  next: () => void;
  prev: () => void;
  /** 탭 이동 알림. tutorial 모드에서 현재 step 의 awaitTab 과 일치하면 자동 진행. */
  notifyTab: (tab: string) => void;
}

export const useTutorialStore = create<TutorialState>((set, get) => ({
  mode: "off",
  step: 0,
  startTutorial: () => set({ mode: "tutorial", step: 0 }),
  startDemo: () => set({ mode: "demo", step: 0 }),
  stop: () => set({ mode: "off", step: 0 }),
  next: () => {
    const { step } = get();
    if (step + 1 >= TUT_STEPS.length) set({ mode: "off", step: 0 });
    else set({ step: step + 1 });
  },
  prev: () => set((s) => ({ step: Math.max(0, s.step - 1) })),
  notifyTab: (tab) => {
    const { mode, step } = get();
    if (mode !== "tutorial") return;
    const cur = TUT_STEPS[step];
    if (cur?.awaitTab && cur.awaitTab === tab) {
      // 탭 전환 렌더가 끝난 뒤 진행 (deploy-day advance 패턴).
      setTimeout(() => {
        if (get().mode === "tutorial" && get().step === step) get().next();
      }, 450);
    }
  },
}));

/**
 * API 레이어용: 데모/튜토리얼 둘 다 더미 데이터를 쓴다.
 * React 컴포넌트 밖(interviewApi)에서 호출한다.
 */
export const isDataMockActive = (): boolean => useTutorialStore.getState().mode !== "off";
