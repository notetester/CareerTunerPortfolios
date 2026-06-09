/**
 * 관리자 홈(C 담당) — 운영자가 "지금 처리할 것"을 보는 작업 진입점.
 *
 * <p>이름 근거(왜 home인가): 사용자 영역의 {@code home}(로그인 후 할 일/진입점)과 짝을 이루는 관리자 화면이다.
 * 같은 분석 계열이라도 역할이 다르므로 admin/analytics·admin/dashboard 와 구분한다.
 * <ul>
 *   <li>{@code admin/home}: 처리 대기 큐(적합도 분석 실패, 미분석 지원 건, 최근 신규 분석) + 운영 바로가기. "할 일" 중심.</li>
 *   <li>{@code admin/dashboard}: 도메인 횡단 현황 카운트. "숫자/현황" 중심.</li>
 *   <li>{@code admin/analytics}: 분석·AI 깊은 통계(점수 분포, 부족 역량, 사용량 추이). "분석 통계" 중심.</li>
 * </ul>
 * GET /api/admin/home/summary 제공. 명명 규칙은 docs/FEATURE_MODULE_STRUCTURE.md "2.1 분석 계열 명명 규칙" 참조.
 * (규칙은 절대 강제가 아니며, 필요 시 이유를 남기고 조정한다.)
 */
package com.careertuner.admin.home;
