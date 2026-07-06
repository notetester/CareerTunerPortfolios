/**
 * 기업 채용공고 게시판 도메인 (company_job_posting / company_job_posting_revision).
 *
 * <p>주의: 기존 {@code jobposting} 패키지(지원 건 내부의 공고 원문 텍스트)와 이름이 유사하지만
 * 완전히 별개 도메인이다. 이 패키지는 기업 계정이 작성해 게시판에 노출하는 공고를 다룬다.
 *
 * <p>상태 흐름: DRAFT → (제출) → 신뢰등급 정책에 따라 PENDING_REVIEW 또는 즉시 PUBLISHED
 * → 게시 중 수정은 정책에 따라 revision 검토 → CLOSED.
 * PUBLISHED 시 AFTER_COMMIT 리스너가 관심 사용자에게 RECOMMENDED_JOB 알림을 발행한다.
 */
package com.careertuner.companyjobposting;
