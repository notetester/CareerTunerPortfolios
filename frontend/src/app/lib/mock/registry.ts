// 데모/목 라우트 레지스트리 공통 계약. 도메인별 mock 모듈(./domains/*.ts)이 이 타입·헬퍼를 공유한다.
// index.ts 가 각 도메인의 `<name>Routes: MockRoute[]` 를 모아 하나의 routes 로 합친다(공통 인프라, additive).

export interface MockContext {
  method: string;
  path: string; // 쿼리스트링 제거된 경로 (예: /fit-analyses/201/learning-tasks/2011)
  query: URLSearchParams; // 파싱된 쿼리스트링 (목록/필터/페이지네이션 핸들러용)
  params: string[]; // 정규식 캡처 그룹
  body: unknown; // JSON 파싱된 요청 본문(가능할 때)
}

export type MockHandler = (ctx: MockContext) => unknown;

export interface MockRoute {
  method: string;
  pattern: RegExp;
  handler: MockHandler;
}

/** 고정 응답 핸들러. */
export const ok = <T>(value: T): MockHandler => () => value;

/** N일 전 ISO 시각. 도메인 모듈이 상대 시간을 일관되게 만들 때 사용. */
const EPOCH = Date.now();
export const iso = (daysAgo = 0): string => new Date(EPOCH - daysAgo * 86_400_000).toISOString();

/** 쿼리에서 page/size 를 읽는다(기본 0/20). */
export function pageOf(ctx: MockContext, defaultSize = 20): { page: number; size: number } {
  return {
    page: Number(ctx.query.get("page") ?? 0) || 0,
    size: Number(ctx.query.get("size") ?? defaultSize) || defaultSize,
  };
}
