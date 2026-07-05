/**
 * 기업 계정 도메인 — 기업 전환 신청(company_application), role 전환 승인, 기업 프로필(company_profile).
 *
 * <p>흐름: USER 가 기업 신청 → 관리자 승인(단일 트랜잭션: users.role='COMPANY' +
 * user_role_change_history + company_profile 생성) → COMPANY 계정이
 * {@code companyjobposting} 도메인에서 채용공고를 등록한다.
 */
package com.careertuner.company;
