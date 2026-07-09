package com.careertuner.community.moderation.client;

import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * 커뮤니티 검열/태그/추출 최종 폴백(Mock). 자체 Ollama·Claude·OpenAI 가 모두 미설정/실패했을 때
 * {@link ModerationLlmGateway} 가 마지막으로 호출한다.
 *
 * <p>외부 호출 없이 스키마 종류(properties 키)를 보고 <b>미판정 placeholder</b>를 돌려준다.
 * 반환값은 "정상 통과" 판정이 아니다 — 게이트웨이가 mock=true 로 표시해 돌려주고, 호출부는
 * COMPLETED 대신 UNMODERATED 로 기록해 재시도 스케줄러가 provider 복구 후 다시 집게 한다.
 * <ul>
 *   <li>검열(toxic) — 즉시 차단하지 않되 confidence=null 로 "판정 없음"을 남긴다(0.0 확신과 구분).</li>
 *   <li>태그(tags)·추출(questions) — 빈 결과. 없는 것을 지어내지 않는다.</li>
 * </ul>
 * 반환 JSON 은 각 스키마의 required 필드 구조를 지켜 호출부 파싱이 깨지지 않게 한다.
 */
@Component
public class MockModerationClient {

    public String chat(String systemPrompt, String userText, Map<String, Object> jsonSchema) {
        Set<String> props = propertyKeys(jsonSchema);
        if (props.contains("toxic")) {
            // MODERATION_SCHEMA(toxic/category/confidence) — 판정 아님. confidence=null 이
            // "파싱 성공 + confidence 누락"과 같은 경로(UNMODERATED)로 흐르게 한다.
            return "{\"toxic\":false,\"category\":\"normal\",\"confidence\":null}";
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
