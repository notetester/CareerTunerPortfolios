package com.careertuner.ai.chat;

/**
 * 세션 목록(사이드바) 항목 — 인테이크(지원건) 세션 1건 요약.
 * application_case_id 가 있는(지원 건이 확정된) 대화만 목록에 오른다.
 *
 * @param mode      면접 모드 코드(BASIC/JOB/PERSONALITY/PRESSURE/RESUME/COMPANY). 슬롯 미설정 시 null.
 * @param updatedAt 마지막 갱신 시각(epoch millis). 상대시각("방금"/"2시간 전") 표시용.
 */
public record ChatSessionSummary(Long conversationId, String title, String mode, Long updatedAt) {}
