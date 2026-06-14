package com.careertuner.community.moderation.dto;

/**
 * Ollama /api/chat 응답 DTO.
 * 전체 응답에서 message.content (JSON 문자열)만 필요하므로 나머지 필드는 무시한다.
 * Jackson은 알 수 없는 필드를 기본적으로 무시하지 않으므로,
 * OllamaClient에서 ObjectMapper 설정으로 처리한다.
 */
public record OllamaChatResponse(Message message) {

    public record Message(String role, String content) {}
}
