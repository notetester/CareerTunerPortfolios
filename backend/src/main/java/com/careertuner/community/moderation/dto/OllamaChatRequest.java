package com.careertuner.community.moderation.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

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
    /**
     * Ollama 메시지. vision 모델(gemma4)은 메시지별 {@code images}(base64 배열)로 멀티모달 입력을 받는다.
     * 텍스트 검열은 images 없이(2-arg) 그대로 쓰고, 이미지 검열만 base64 목록을 싣는다.
     * images=null 은 직렬화에서 생략(NON_NULL)해 기존 텍스트 요청의 와이어 포맷을 바꾸지 않는다.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Message(String role, String content, List<String> images) {
        public Message(String role, String content) {
            this(role, content, null);
        }
    }
}
