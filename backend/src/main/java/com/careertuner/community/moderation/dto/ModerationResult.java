package com.careertuner.community.moderation.dto;

/**
 * AI 검열 판정 결과.
 *
 * <p>confidence 는 nullable — 응답 JSON 에 confidence 가 없으면 0.0 이 아니라 null 로 파싱된다.
 * null(누락)은 "낮은 확신(0.0)"과 다른 상태로, 호출부는 판정 불성립으로 보고
 * COMPLETED 대신 UNMODERATED 로 기록해 재검열 대상에 남긴다.
 */
public record ModerationResult(boolean toxic, String category, Double confidence) {}
