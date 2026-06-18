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

    /** 코사인 유사도 임계값 (미만이면 "관련 FAQ 없음") */
    private double similarityThreshold = 0.5;

    /** 검색 결과 상위 K개 */
    private int topK = 3;
}
