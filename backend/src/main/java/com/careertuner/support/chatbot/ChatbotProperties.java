package com.careertuner.support.chatbot;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ai.chatbot")
public class ChatbotProperties {

    /** 임베딩 모델명 (bge-m3) */
    private String embeddingModel = "bge-m3";

    /** 답변 생성 모델명 (gemma4) */
    private String chatModel = "gemma4";

    /** 코사인 유사도 임계값 (미만이면 "관련 FAQ 없음"). 검색 후보 컷오프. */
    private double similarityThreshold = 0.5;

    /**
     * FAQ 임베딩 게이트 임계값. 질문 원문 top-1 FAQ 코사인이 이 값 이상이면
     * 에이전트 우회·즉답(결정적 FAQ 경로). similarityThreshold(검색 후보 컷오프)와
     * <b>물리적으로 별개 키</b> — 게이트만 독립 튜닝 가능.
     */
    private double faqGateThreshold = 0.35;

    /** 검색 결과 상위 K개 */
    private int topK = 3;
}
