import { create } from "zustand";

/**
 * 면접 튜토리얼 상태. deploy-day v2 의 useTutorial 패턴을 면접 페이지에 이식한다.
 *
 * `active` 가 켜지면:
 *  - interviewApi 가 실제 백엔드/AI 호출 대신 더미 데이터를 반환한다 (tutorial/dummyData).
 *  - InterviewPage 가 로그인 게이트를 우회한다 (비로그인도 체험 가능).
 *
 * step 엔진(스포트라이트 자동 진행)은 단계 B 에서 이 store 위에 얹는다.
 */
interface TutorialState {
  /** 튜토리얼 진행 중 여부. 더미 데이터·게이트 우회의 단일 스위치. */
  active: boolean;
  /** 현재 단계 인덱스 (TUT_STEPS 기준, 단계 B 에서 사용). */
  step: number;
  start: () => void;
  stop: () => void;
  next: () => void;
  prev: () => void;
  goStep: (i: number) => void;
}

export const useTutorialStore = create<TutorialState>((set) => ({
  active: false,
  step: 0,
  start: () => set({ active: true, step: 0 }),
  stop: () => set({ active: false, step: 0 }),
  next: () => set((s) => ({ step: s.step + 1 })),
  prev: () => set((s) => ({ step: Math.max(0, s.step - 1) })),
  goStep: (i) => set({ step: Math.max(0, i) }),
}));

/**
 * React 컴포넌트 밖(interviewApi 레이어)에서 튜토리얼 활성 여부를 읽는다.
 * deploy-day 의 useTutorial.getState() 패턴과 동일.
 */
export const isTutorialActive = (): boolean => useTutorialStore.getState().active;
