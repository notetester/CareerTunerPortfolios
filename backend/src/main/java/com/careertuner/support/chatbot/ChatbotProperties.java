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

    /** 통합 라우팅 경계 폭(deadband). |intakeScore - faqScore| 가 이 값 미만이면 화행 분류로 판정(그 이상은 argmax 결정적). */
    private double routeBoundary = 0.10;

    /** 화행 분류(QUESTION/COMMAND)에 쓰는 모델. ③ 인테이크 에이전트와 동일한 qwen3:8b. */
    private String speechActModel = "qwen3:8b";
}
