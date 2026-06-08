/**
 * 관리자 홈 영역(C 담당) — 통합 결정 메모.
 *
 * <p>관리자 홈/대시보드 운영 지표는 분석 계열 명명 통일 규칙(FEATURE_OWNERSHIP "분석 계열 명명 규칙": 관리자
 * 통계·집계는 {@code analytics})에 따라 {@code com.careertuner.admin.analytics}의 통합 대시보드
 * (GET /api/admin/analytics/summary, 화면 /admin)로 제공한다.
 *
 * <p>동일 데이터를 노출하는 admin/home 전용 엔드포인트는 중복과 병합 충돌 표면을 줄이기 위해 두지 않는다.
 * 관리자 홈을 analytics와 분리해야 할 요구가 생기면 회의에서 결정한 뒤 이 패키지에 controller/service를 추가한다.
 */
package com.careertuner.admin.home;
