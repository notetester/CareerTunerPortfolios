package com.careertuner.ai.common.model;

import java.util.Locale;

/**
 * 사용자가 <b>각 AI 기능 사용 시 명시 선택</b>하는 모델 tier(ChatGPT/Claude 의 모델 선택과 같은 개념).
 *
 * <ul>
 *   <li>{@link #AUTO} — 기본 폴백 순서(자체 → Claude Haiku → OpenAI → 규칙/Mock 안전망). 현행 동작.</li>
 *   <li>{@link #CAREERTUNER} — 자체 모델(OSS/Ollama/FineTuned) 우선.</li>
 *   <li>{@link #CLAUDE} — Claude Haiku 우선.</li>
 *   <li>{@link #OPENAI} — OpenAI GPT 우선.</li>
 * </ul>
 *
 * <p><b>선택은 '시작 지점'만 바꾼다</b>: 고른 tier 가 미가용/실패/타임아웃이어도 하위 tier → 최종 안전망까지
 * 폴백하므로 화면은 절대 깨지지 않는다. C 처럼 판단값을 규칙엔진이 소유하는 도메인에서는 <b>모델 선택이 설명 생성에만
 * 영향</b>을 주고 판단값(fitScore 등)은 어느 모델이든 동일하다.
 */
public enum RequestedAiModel {
    AUTO,
    CAREERTUNER,
    CLAUDE,
    OPENAI;

    /** 대소문자·공백 무시 파싱. null/공백/미지값 → {@link #AUTO}(하위호환 fail-open). */
    public static RequestedAiModel parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return AUTO;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return AUTO;
        }
    }

    /** 이 선택이 대응하는 논리 tier. {@link #AUTO} 는 tier 없음({@code null}) = 기본 순서 전체 사용. */
    public AiProviderTier tier() {
        return switch (this) {
            case CAREERTUNER -> AiProviderTier.CAREERTUNER;
            case CLAUDE -> AiProviderTier.CLAUDE;
            case OPENAI -> AiProviderTier.OPENAI;
            case AUTO -> null;
        };
    }
}
