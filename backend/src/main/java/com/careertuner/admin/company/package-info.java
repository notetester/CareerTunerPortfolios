/**
 * 기업 계정 운영 콘솔 — 기업 신청 승인/반려, 채용공고 검토 큐.
 * 실제 처리 로직은 {@code company}/{@code companyjobposting} 도메인 서비스를 재사용하고,
 * 여기서는 관리자 권한 검사({@code AdminAccess})와 엔드포인트만 노출한다.
 */
package com.careertuner.admin.company;
