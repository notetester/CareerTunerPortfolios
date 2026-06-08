/**
 * 관리자 대시보드 영역(C 담당) — 통합 결정 메모.
 *
 * <p>관리자 대시보드(운영 통계/요약)는 {@code com.careertuner.admin.analytics}의
 * GET /api/admin/analytics/summary 와 화면 /admin(AdminDashboardPage)으로 통일해 제공한다.
 * (FEATURE_OWNERSHIP "분석 계열 명명 규칙": 관리자 통계·집계는 analytics로 통일)
 *
 * <p>중복 엔드포인트를 피하기 위해 admin/dashboard 전용 API는 두지 않는다. 분리가 필요하면 회의 후 추가한다.
 */
package com.careertuner.admin.dashboard;
