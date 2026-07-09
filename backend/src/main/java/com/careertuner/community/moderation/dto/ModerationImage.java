package com.careertuner.community.moderation.dto;

/**
 * 이미지 검열 대상 1장. 본문에서 추출한 이미지를 provider별 vision 포맷으로 넘기기 위한 공통 표현.
 *
 * @param base64Data data: 접두사 없는 순수 base64 (Ollama 는 그대로, Claude/OpenAI 는 media_type/data URL 로 감싼다)
 * @param mediaType  content-type (image/png 등)
 */
public record ModerationImage(String base64Data, String mediaType) {
}
