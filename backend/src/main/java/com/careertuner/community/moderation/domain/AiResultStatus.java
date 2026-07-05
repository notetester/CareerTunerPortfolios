package com.careertuner.community.moderation.domain;

public enum AiResultStatus {
    PENDING, COMPLETED, FAILED,
    /**
     * 판정 불성립 — 모든 LLM provider 실패로 Mock placeholder 를 받았거나 confidence 누락.
     * COMPLETED 가 아니므로 재시도 스케줄러(NOT EXISTS status='COMPLETED')가 다시 집는다.
     */
    UNMODERATED
}
