package com.careertuner.community.moderation.dto;

import java.util.List;
import java.util.Map;

/**
 * Ollama /api/chat 요청 DTO.
 * gemma4.md에 명시된 네이티브 API 형식을 그대로 따른다.
 *
 * @param model   모델명 (gemma4)
 * @param stream  스트리밍 비활성화 (false 고정)
 * @param think   thinking 비활성화 (false 고정)
 * @param options 생성 옵션 (temperature 등)
 * @param format  응답 JSON 스키마 (structured output 강제)
 * @param messages 시스템 프롬프트 + 사용자 입력
 */
public record OllamaChatRequest(
        String model,
        boolean stream,
        boolean think,
        Map<String, Object> options,
        Map<String, Object> format,
        List<Message> messages
) {
    public record Message(String role, String content) {}
}
