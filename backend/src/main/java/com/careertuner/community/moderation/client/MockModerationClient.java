package com.careertuner.community.moderation.client;

import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * 커뮤니티 검열/태그/추출 최종 폴백(Mock). 자체 Ollama·Claude·OpenAI 가 모두 미설정/실패했을 때
 * {@link ModerationLlmGateway} 가 마지막으로 호출한다.
 *
 * <p>외부 호출 없이 스키마 종류(properties 키)를 보고 <b>안전한 기본값</b>을 돌려준다:
 * <ul>
 *   <li>검열(toxic) — "차단 안 함(정상 통과)". 오탐으로 정상 글을 막는 것보다 통과 후 관리자 검토가 안전하다.</li>
 *   <li>태그(tags)·추출(questions) — 빈 결과. 없는 것을 지어내지 않는다.</li>
 * </ul>
 * 반환 JSON 은 각 스키마의 required 필드를 채워 호출부 파싱이 깨지지 않게 한다.
 */
@Component
public class MockModerationClient {

    public String chat(String systemPrompt, String userText, Map<String, Object> jsonSchema) {
        Set<String> props = propertyKeys(jsonSchema);
        if (props.contains("toxic")) {
            // MODERATION_SCHEMA(toxic/category/confidence) — 정상 통과(차단 안 함).
            return "{\"toxic\":false,\"category\":\"normal\",\"confidence\":0.0}";
        }
        if (props.contains("tags")) {
            // TAGGING_SCHEMA(tags/confidence) — 빈 태그.
            return "{\"tags\":[],\"confidence\":0.0}";
        }
        if (props.contains("questions")) {
            // EXTRACT_SCHEMA(questions/...) — 빈 추출.
            return "{\"questions\":[]}";
        }
        return "{}";
    }

    @SuppressWarnings("unchecked")
    private Set<String> propertyKeys(Map<String, Object> schema) {
        Object props = schema == null ? null : schema.get("properties");
        if (props instanceof Map<?, ?> map) {
            return ((Map<String, Object>) map).keySet();
        }
        return Set.of();
    }
}
