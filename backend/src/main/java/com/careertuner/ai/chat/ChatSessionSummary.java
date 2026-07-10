package com.careertuner.ai.chat;

/**
 * 대화 목록(사이드바) 항목 — 인테이크(지원건) 세션과 일반 상담 대화 공용 요약.
 *
 * @param title     인테이크는 "{회사} {직무}", 일반 대화는 첫 사용자 발화 요약(스탬프 전이면 null).
 * @param mode      면접 모드 코드(BASIC/JOB/PERSONALITY/PRESSURE/RESUME/COMPANY). 인테이크 외 null.
 * @param updatedAt 마지막 갱신 시각(epoch millis). 상대시각("방금"/"2시간 전") 표시용.
 * @param kind      INTAKE(지원건 세션) | GENERAL(일반 상담). 프론트가 열 때 오케 모드 진입 여부를 가른다.
 */
public record ChatSessionSummary(Long conversationId, String title, String mode, Long updatedAt, String kind) {}
