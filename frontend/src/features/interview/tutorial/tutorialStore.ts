import { create } from "zustand";
import { TUT_STEPS } from "./tutSteps";

/**
 * 면접 튜토리얼 상태 + 단계 엔진. deploy-day v2 의 useTutorial 패턴 이식.
 *
 * `active` 가 켜지면:
 *  - interviewApi 가 실제 백엔드/AI 호출 대신 더미 데이터를 반환한다 (tutorial/dummyData).
 *  - InterviewPage 가 로그인 게이트를 우회하고 더미 세션을 주입한다 (비로그인도 체험 가능).
 *
 * 진행:
 *  - 수동 step: 풍선의 [다음] 버튼 → next().
 *  - awaitTab step: 사용자가 해당 탭으로 이동하면 InterviewPage 가 notifyTab() 으로 알리고,
 *    현재 step 의 awaitTab 과 일치하면 자동 진행한다.
 */
interface TutorialState {
  /** 튜토리얼 진행 중 여부. 더미 데이터·게이트 우회의 단일 스위치. */
  active: boolean;
  /** 현재 단계 인덱스 (TUT_STEPS). */
  step: number;
  start: () => void;
  stop: () => void;
  next: () => void;
  prev: () => void;
  /** 탭 이동 알림. 현재 step 의 awaitTab 과 일치하면 자동 진행. */
  notifyTab: (tab: string) => void;
}

export const useTutorialStore = create<TutorialState>((set, get) => ({
  active: false,
  step: 0,
  start: () => set({ active: true, step: 0 }),
  stop: () => set({ active: false, step: 0 }),
  next: () => {
    const { step } = get();
    if (step + 1 >= TUT_STEPS.length) set({ active: false, step: 0 });
    else set({ step: step + 1 });
  },
  prev: () => set((s) => ({ step: Math.max(0, s.step - 1) })),
  notifyTab: (tab) => {
    const { active, step } = get();
    if (!active) return;
    const cur = TUT_STEPS[step];
    if (cur?.awaitTab && cur.awaitTab === tab) {
      // 탭 전환 렌더가 끝난 뒤 진행 (deploy-day advance 패턴).
      setTimeout(() => {
        if (get().active && get().step === step) get().next();
      }, 450);
    }
  },
}));

/**
 * React 컴포넌트 밖(interviewApi 레이어)에서 튜토리얼 활성 여부를 읽는다.
 * deploy-day 의 useTutorial.getState() 패턴과 동일.
 */
export const isTutorialActive = (): boolean => useTutorialStore.getState().active;
