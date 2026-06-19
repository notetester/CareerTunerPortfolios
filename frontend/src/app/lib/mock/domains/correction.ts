import type { MockRoute } from "../registry";

// 데모/목: C 도메인 자소서/스펙 첨삭(AI 첨삭) 라우트.
//
// 현재 첨삭 기능에는 호출되는 백엔드 엔드포인트가 하나도 없다(2026-06 기준):
//  - 화면 src/app/pages/Correction.tsx 는 "첨삭 API 준비 중" 안내가 박힌 정적 입력 흐름
//    샘플이다. 탭/체크리스트/최근 기록/업로드 영역이 전부 컴포넌트 내부 상수로 렌더되며
//    api() 호출이 전혀 없다(실행 버튼은 disabled "준비 중").
//  - 백엔드 com.careertuner.correction 는 package-info.java 만 있는 빈 패키지로 컨트롤러가 없다.
//  - 라우터(src/app/routes.ts)에 /correction 만 등록돼 있고 /correction/result/:id 화면은 없다.
//    알림 목 데이터의 "/correction/result/55" 링크는 표시용 placeholder이며 라우트가 없다.
//  - 코드 전반의 다른 "/correction*" 참조는 모두 SPA UI 경로(헤더·푸터·가이드 링크)이지
//    API 엔드포인트가 아니다.
//
// 즉 가로챌 api() 호출이 없으므로 등록할 목 라우트가 없다. 존재하지 않는 엔드포인트를
// 임의로 날조하지 않고 빈 배열을 유지한다(타 도메인 데이터를 날조하지 않는 레지스트리 원칙과 동일).
// 첨삭 백엔드/프런트 api 모듈이 추가되면 그때 해당 method+path 의 정확한 응답 T 로 채운다.
export const correctionRoutes: MockRoute[] = [];
