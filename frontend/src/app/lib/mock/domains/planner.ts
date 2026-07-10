// 플래너 mock — 데모(VITE_USE_MOCK) 빌드에서 헤더 최상위 메뉴 '플래너'가 "데모 미제공" 오류로 떨어지던 구멍을 채운다.
// 메모/일정은 모듈 레벨 in-memory 로 세션 내 CRUD 가 유지된다(새로고침 시 초기 시드로 리셋).
import type { MockRoute } from "../registry";

const iso = (daysAgo: number) => new Date(Date.now() - daysAgo * 86_400_000).toISOString();
const inDays = (days: number) => new Date(Date.now() + days * 86_400_000).toISOString();

interface MemoShape {
  id: number; title: string | null; content: string | null; color: string; pinned: boolean;
  overlayVisible: boolean; opacity: number; applicationCaseId: number | null; fitAnalysisId: number | null;
  createdAt: string; updatedAt: string;
}
interface ScheduleShape {
  id: number; title: string; description: string | null; kind: string; status: string; allDay: boolean;
  timingPrecision: string; startAt: string; endAt: string | null; timezone: string;
  applicationCaseId: number | null; fitAnalysisId: number | null; sourceType: string; sourceRef: string | null;
  sourceSnapshotJson: string | null; overlayVisible: boolean; opacity: number; pinned: boolean; clickThrough: boolean;
  applicationCompanyName: string | null; applicationJobTitle: string | null; fitScore: number | null;
  applicationDeadlineDate: string | null; reminders: unknown[]; createdAt: string; updatedAt: string;
}

let nextMemoId = 9101;
let nextScheduleId = 9501;

const memos: MemoShape[] = [
  {
    id: 9001, title: "카카오 지원 체크", content: "TypeScript 리팩토링 프로젝트 정리 후 재분석 실행", color: "yellow",
    pinned: true, overlayVisible: true, opacity: 0.95, applicationCaseId: 101, fitAnalysisId: 201,
    createdAt: iso(3), updatedAt: iso(1),
  },
  {
    id: 9002, title: null, content: "SQLD 접수 일정 공식 페이지에서 확인하기", color: "blue",
    pinned: false, overlayVisible: false, opacity: 0.95, applicationCaseId: null, fitAnalysisId: null,
    createdAt: iso(2), updatedAt: iso(2),
  },
];

const scheduleItems: ScheduleShape[] = [
  {
    id: 9401, title: "카카오 서류 마감", description: "자기소개서 최종 검토 포함", kind: "DEADLINE", status: "PLANNED",
    allDay: true, timingPrecision: "DATE", startAt: inDays(3), endAt: null, timezone: "Asia/Seoul",
    applicationCaseId: 101, fitAnalysisId: 201, sourceType: "MANUAL", sourceRef: null, sourceSnapshotJson: null,
    overlayVisible: true, opacity: 0.95, pinned: true, clickThrough: false,
    applicationCompanyName: "카카오", applicationJobTitle: "프론트엔드 개발자", fitScore: 78,
    applicationDeadlineDate: inDays(3), reminders: [], createdAt: iso(4), updatedAt: iso(1),
  },
  {
    id: 9402, title: "네이버 면접 준비 세션", description: "성능 최적화 경험 STAR 정리", kind: "TASK", status: "PLANNED",
    allDay: false, timingPrecision: "DATETIME", startAt: inDays(1), endAt: inDays(1), timezone: "Asia/Seoul",
    applicationCaseId: 102, fitAnalysisId: 202, sourceType: "MANUAL", sourceRef: null, sourceSnapshotJson: null,
    overlayVisible: false, opacity: 0.95, pinned: false, clickThrough: false,
    applicationCompanyName: "네이버", applicationJobTitle: "프론트엔드 개발자", fitScore: 84,
    applicationDeadlineDate: null, reminders: [], createdAt: iso(2), updatedAt: iso(2),
  },
];

function applyMemo(id: number, body: Record<string, unknown>): MemoShape | null {
  const memo = memos.find((m) => m.id === id);
  if (!memo) return null;
  Object.assign(memo, body, { id, updatedAt: new Date().toISOString() });
  return memo;
}

function applySchedule(id: number, body: Record<string, unknown>): ScheduleShape | null {
  const item = scheduleItems.find((m) => m.id === id);
  if (!item) return null;
  Object.assign(item, body, { id, updatedAt: new Date().toISOString() });
  return item;
}

export const plannerRoutes: MockRoute[] = [
  { method: "GET", pattern: /^\/planner$/, handler: () => ({ memos: [...memos], scheduleItems: [...scheduleItems] }) },
  {
    method: "POST", pattern: /^\/planner\/memos$/, handler: ({ body }) => {
      const req = (body ?? {}) as Record<string, unknown>;
      const memo: MemoShape = {
        id: nextMemoId++, title: (req.title as string) ?? null, content: (req.content as string) ?? null,
        color: (req.color as string) ?? "yellow", pinned: !!req.pinned, overlayVisible: !!req.overlayVisible,
        opacity: typeof req.opacity === "number" ? req.opacity : 0.95,
        applicationCaseId: (req.applicationCaseId as number) ?? null, fitAnalysisId: (req.fitAnalysisId as number) ?? null,
        createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(),
      };
      memos.unshift(memo);
      return memo;
    },
  },
  { method: "PATCH", pattern: /^\/planner\/memos\/(\d+)$/, handler: ({ params, body }) => applyMemo(Number(params[0]), (body ?? {}) as Record<string, unknown>) },
  { method: "DELETE", pattern: /^\/planner\/memos\/(\d+)$/, handler: ({ params }) => { const i = memos.findIndex((m) => m.id === Number(params[0])); if (i >= 0) memos.splice(i, 1); return null; } },
  {
    method: "POST", pattern: /^\/planner\/schedule$/, handler: ({ body }) => {
      const req = (body ?? {}) as Record<string, unknown>;
      const item: ScheduleShape = {
        id: nextScheduleId++, title: (req.title as string) ?? "새 일정", description: (req.description as string) ?? null,
        kind: (req.kind as string) ?? "TASK", status: (req.status as string) ?? "PLANNED", allDay: !!req.allDay,
        timingPrecision: (req.timingPrecision as string) ?? "DATETIME", startAt: (req.startAt as string) ?? new Date().toISOString(),
        endAt: (req.endAt as string) ?? null, timezone: (req.timezone as string) ?? "Asia/Seoul",
        applicationCaseId: (req.applicationCaseId as number) ?? null, fitAnalysisId: (req.fitAnalysisId as number) ?? null,
        sourceType: (req.sourceType as string) ?? "MANUAL", sourceRef: (req.sourceRef as string) ?? null,
        sourceSnapshotJson: (req.sourceSnapshotJson as string) ?? null, overlayVisible: !!req.overlayVisible,
        opacity: typeof req.opacity === "number" ? req.opacity : 0.95, pinned: !!req.pinned, clickThrough: !!req.clickThrough,
        applicationCompanyName: null, applicationJobTitle: null, fitScore: null, applicationDeadlineDate: null,
        reminders: Array.isArray(req.reminders) ? req.reminders : [], createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(),
      };
      scheduleItems.unshift(item);
      return item;
    },
  },
  { method: "PATCH", pattern: /^\/planner\/schedule\/(\d+)$/, handler: ({ params, body }) => applySchedule(Number(params[0]), (body ?? {}) as Record<string, unknown>) },
  { method: "DELETE", pattern: /^\/planner\/schedule\/(\d+)$/, handler: ({ params }) => { const i = scheduleItems.findIndex((m) => m.id === Number(params[0])); if (i >= 0) scheduleItems.splice(i, 1); return null; } },
  {
    // 전략 → 일정 초안(적합도 기반). 데모에선 대표 지원건의 마감 역산 초안 2건을 돌려준다.
    method: "POST", pattern: /^\/planner\/strategy-drafts\/fit-analyses\/(\d+)$/, handler: ({ params }) => ({
      fitAnalysisId: Number(params[0]), applicationCaseId: 101, companyName: "카카오", jobTitle: "프론트엔드 개발자",
      generatedAt: new Date().toISOString(), staleReasons: [],
      items: [
        { title: "TypeScript 보완 마감", description: "부족 역량 보완 완료 목표", kind: "TASK", allDay: true, timingPrecision: "DATE", startAt: inDays(2), endAt: null, timezone: "Asia/Seoul", applicationCaseId: 101, fitAnalysisId: Number(params[0]), sourceType: "FIT_STRATEGY", sourceRef: null, sourceSnapshotJson: null, reminders: [], overlapCount: 0 },
        { title: "지원서 제출", description: "마감 하루 전 제출 권장", kind: "DEADLINE", allDay: true, timingPrecision: "DATE", startAt: inDays(3), endAt: null, timezone: "Asia/Seoul", applicationCaseId: 101, fitAnalysisId: Number(params[0]), sourceType: "FIT_STRATEGY", sourceRef: null, sourceSnapshotJson: null, reminders: [], overlapCount: 0 },
      ],
    }),
  },
];

// 리워드/쿠폰 mock — 프로필의 리워드 화면이 데모에서 비지 않게 최소 응답.
export const rewardRoutes: MockRoute[] = [
  { method: "GET", pattern: /^\/rewards\/me$/, handler: () => ({ level: 2, levelName: "성실 구직자", points: 340, nextLevelPoints: 500, badges: [{ code: "FIRST_FIT", name: "첫 적합도 분석", earnedAt: iso(6) }] }) },
  { method: "GET", pattern: /^\/coupons\/me$/, handler: () => ([{ id: 1, code: "WELCOME10", name: "웰컴 크레딧 10", status: "ACTIVE", expiresAt: inDays(30) }]) },
  { method: "POST", pattern: /^\/coupons\/redeem$/, handler: ({ body }) => ({ success: true, message: "데모 쿠폰이 등록되었습니다.", code: ((body ?? {}) as { code?: string }).code ?? "" }) },
];
