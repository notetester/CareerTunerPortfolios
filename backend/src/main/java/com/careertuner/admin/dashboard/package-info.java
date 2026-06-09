/**
 * 관리자 대시보드(C 담당) — 운영 종합 현황(도메인 횡단 KPI).
 *
 * <p>이름 근거(왜 dashboard인가): 사용자 영역의 {@code dashboard}(전체 준비 현황)와 짝을 이루는 관리자 화면이다.
 * 분석에 한정된 {@code admin/analytics}와 달리, 회원·지원 건·적합도 분석·면접·AI 사용 등 여러 도메인의 카운트를
 * 한 화면에 모은 운영자 랜딩이다.
 * <ul>
 *   <li>{@code admin/dashboard}: 도메인 횡단 현황 카운트. "숫자/현황" 중심.</li>
 *   <li>{@code admin/analytics}: 분석·AI 깊은 통계. "분석 통계" 중심.</li>
 *   <li>{@code admin/home}: 처리 대기 큐 + 바로가기. "할 일" 중심.</li>
 * </ul>
 * GET /api/admin/dashboard/overview 제공. 모든 카운트는 읽기 전용 집계로 타 도메인 데이터를 수정하지 않는다.
 * 명명 규칙은 docs/FEATURE_MODULE_STRUCTURE.md "2.1 분석 계열 명명 규칙" 참조.
 */
package com.careertuner.admin.dashboard;
