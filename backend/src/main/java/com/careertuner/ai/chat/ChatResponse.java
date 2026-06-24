package com.careertuner.ai.chat;

import java.util.List;

/**
 * 커뮤니티 챗봇 에이전트의 구조화 출력. (LangChain4j @AiService 반환 타입 → qwen3:8b JSON 스키마 출력)
 *
 * @param message      자유 생성 답변 텍스트
 * @param links        실제 검색된 글만 (접지). url 은 /community/posts/{id} 화이트리스트로 검증.
 * @param quickReplies 흥미를 좁혀가는 후속 칩
 */
public record ChatResponse(
        String message,
        List<SiteLink> links,
        List<String> quickReplies
) {
    public record SiteLink(String label, String url) {}
}
